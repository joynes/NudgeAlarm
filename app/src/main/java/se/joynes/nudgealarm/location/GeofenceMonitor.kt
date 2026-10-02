package se.joynes.nudgealarm.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.database.SavedPlaceRepository
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GeofenceMonitor(context: Context, private val places: SavedPlaceRepository) {
    private val context = context.applicationContext
    private val store = GeofenceStateStore(context)
    private val client by lazy { LocationServices.getGeofencingClient(this.context) }
    private val pendingIntent by lazy {
        PendingIntent.getBroadcast(this.context, 7401,
            Intent(this.context, GeofenceReceiver::class.java).setAction(ACTION_TRANSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
    private var registered: List<GeofenceSpec>? = null

    fun prerequisites(): String? {
        if (listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }) return "Allow precise Location > Allow all the time in Android settings"
        if (!(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled)
            return "Turn on Android Location to monitor places"
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS)
            return "Google Play services is required for battery-saving place monitoring"
        return null
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(rules: List<ReminderConfig>) = withContext(NonCancellable) {
        mutex.withLock {
            val specs = rules.filter { it.enabled }.mapNotNull { it.placeId }.distinct().sorted()
                .mapNotNull { places.getActiveLocation(it)?.let(GeofenceSpec::from) }
            if (specs.isNotEmpty()) prerequisites()?.let {
                val previouslyRegistered = !registered.isNullOrEmpty() || store.requests().isNotEmpty()
                registered = null
                store.invalidate(it)
                if (previouslyRegistered) removeRegistrations()
                return@withLock
            }
            if (specs.size > 100) {
                registered = null
                store.invalidate("Too many places: Android supports up to 100 monitored places")
                removeRegistrations()
                return@withLock
            }
            if (registered == specs && (specs.isEmpty() || store.requests().size == specs.size)) return@withLock
            if (specs.isEmpty() && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
                registered = emptyList()
                store.invalidate("No place-linked reminders to monitor")
                return@withLock
            }
            val generation = UUID.randomUUID().toString()
            store.replace(specs, generation)
            try {
                withTimeout(30_000) {
                    client.removeGeofences(pendingIntent).awaitCompletion()
                    if (specs.isNotEmpty()) {
                        val requests = store.requests()
                        val fences = specs.map { spec ->
                            Geofence.Builder().setRequestId(requests.getValue(spec.placeId))
                                .setCircularRegion(spec.latitude, spec.longitude, spec.radiusMeters.toFloat())
                                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                                .setNotificationResponsiveness(120_000)
                                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                                .build()
                        }
                        client.addGeofences(GeofencingRequest.Builder()
                            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
                            .addGeofences(fences).build(), pendingIntent).awaitCompletion()
                    }
                }
                registered = specs
                if (specs.isEmpty()) store.invalidate("No place-linked reminders to monitor") else store.ready(generation)
            } catch (_: Exception) {
                registered = null
                store.invalidate("Place monitoring unavailable. Check Location and Google Play services; retrying automatically.")
            }
        }
    }

    suspend fun allows(rule: ReminderConfig): Boolean {
        if (rule.placeId == null) return true
        prerequisites()?.let { store.invalidate(it); return false }
        val position = places.getActiveLocation(rule.placeId) ?: return false
        return store.allows(GeofenceSpec.from(position))
    }

    suspend fun stop() = withContext(NonCancellable) {
        mutex.withLock {
            registered = null
            store.invalidate("Place monitoring is paused while reminders are stopped")
            removeRegistrations()
        }
    }

    private suspend fun removeRegistrations() {
        runCatching { withTimeout(30_000) { client.removeGeofences(pendingIntent).awaitCompletion() } }
    }

    companion object {
        const val ACTION_TRANSITION = "se.joynes.nudgealarm.GEOFENCE_TRANSITION"
        private val mutex = Mutex()
    }
}

private suspend fun Task<Void>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
