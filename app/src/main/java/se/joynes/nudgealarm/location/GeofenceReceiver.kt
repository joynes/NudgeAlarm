package se.joynes.nudgealarm.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import se.joynes.nudgealarm.service.ReminderService

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GeofenceMonitor.ACTION_TRANSITION) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val store = GeofenceStateStore(context)
        val changed = if (event.hasError()) {
            store.record("geofence_error", "errorCode=${event.errorCode}")
            store.invalidate("Place monitoring unavailable; waiting for Android Location to recover")
            true
        } else when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT ->
                store.transition(event.triggeringGeofences.orEmpty().map { it.requestId },
                    event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
            else -> false
        }
        // Update an already running scheduler; a stopped app must not restart itself.
        if (changed && ReminderService.isRunning) {
            runCatching { context.startService(Intent(context, ReminderService::class.java)
                .setAction(ReminderService.ACTION_LOCATION_CHANGED)
                .putExtra(ReminderService.EXTRA_LOCATION_ENTERED,
                    !event.hasError() && event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)) }
        }
    }
}
