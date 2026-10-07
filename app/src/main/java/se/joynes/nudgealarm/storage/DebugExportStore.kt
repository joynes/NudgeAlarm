package se.joynes.nudgealarm.storage

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import se.joynes.nudgealarm.BuildConfig
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.database.SavedPlaceRepository
import se.joynes.nudgealarm.location.GeofenceMonitor
import se.joynes.nudgealarm.location.GeofenceSpec
import se.joynes.nudgealarm.location.GeofenceStateStore
import se.joynes.nudgealarm.service.ReminderService
import java.util.TimeZone

/** Read-only report. Exported device presence must never be restored on import. */
internal class DebugExportStore(private val context: Context) {
    suspend fun snapshot(): JsonObject {
        val db = NagDatabase.getInstance(context)
        val gson = GsonBuilder().serializeNulls().create()
        val store = GeofenceStateStore(context)
        val places = SavedPlaceRepository(db.savedPlaceDao())
        val monitor = GeofenceMonitor(context, places)
        val reminders = db.reminderDao().getAll()
        val currentRules = ReminderService.currentConfig?.reminders.orEmpty()
        val rules = if (reminders.isNotEmpty()) reminders.map { it.toReminderConfig() } else currentRules
        val prerequisite = monitor.prerequisites()
        val decisions = JsonArray()
        for (rule in rules) {
            val position = rule.placeId?.let { places.getActiveLocation(it) }
            val spec = position?.let(GeofenceSpec::from)
            val reason = when {
                !rule.enabled -> "disabled"
                rule.placeId == null -> "no_place_condition"
                prerequisite != null -> prerequisite
                spec == null -> "missing_active_position"
                store.allows(spec) -> "inside_matching_registered_place"
                else -> "outside_unknown_or_registration_mismatch"
            }
            decisions.add(JsonObject().apply {
                addProperty("ruleId", rule.id)
                addProperty("placeId", rule.placeId)
                addProperty("positionId", position?.id)
                addProperty("locationAllowsNow", rule.enabled && (rule.placeId == null ||
                    (prerequisite == null && spec != null && store.allows(spec))))
                addProperty("reason", reason)
            })
        }
        return JsonObject().apply {
            addProperty("format", "nudgealarm-debug")
            addProperty("schemaVersion", 1)
            addProperty("capturedAt", System.currentTimeMillis())
            addProperty("privacy", "Contains saved coordinates, quest titles and logs. Share only with a trusted recipient.")
            add("app", JsonObject().apply {
                addProperty("versionCode", BuildConfig.VERSION_CODE)
                addProperty("versionName", BuildConfig.VERSION_NAME)
                addProperty("package", context.packageName)
                addProperty("sdk", Build.VERSION.SDK_INT)
                addProperty("manufacturer", Build.MANUFACTURER)
                addProperty("model", Build.MODEL)
                addProperty("timeZone", TimeZone.getDefault().id)
            })
            add("permissions", JsonObject().apply {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.POST_NOTIFICATIONS).forEach {
                    addProperty(it, ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED)
                }
                addProperty("notificationsEnabled", context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
                addProperty("ignoringBatteryOptimizations", context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
                addProperty("powerSaveMode", context.getSystemService(PowerManager::class.java).isPowerSaveMode)
            })
            addProperty("serviceRunning", ReminderService.isRunning)
            addProperty("quietMode", SettingsStore(context).quietMode)
            add("alertSettings", JsonObject().apply {
                val settings = SettingsStore(context)
                addProperty("alertOnlyWhenActive", settings.alertOnlyWhenActive)
                addProperty("minAlertIntervalMinutes", settings.minAlertIntervalMinutes)
                addProperty("oldReminderRetentionMinutes", settings.oldReminderRetentionMinutes)
            })
            add("postedNotifications", JsonArray().apply {
                context.getSystemService(NotificationManager::class.java).activeNotifications.forEach { notification ->
                    add(JsonObject().apply {
                        addProperty("id", notification.id)
                        addProperty("postedAt", notification.postTime)
                        addProperty("title", notification.notification.extras.getCharSequence("android.title")?.toString())
                        addProperty("text", notification.notification.extras.getCharSequence("android.text")?.toString())
                    })
                }
            })
            addProperty("geofencePrerequisiteProblem", prerequisite)
            add("geofenceState", store.snapshot())
            add("geofenceHistory", JsonArray().apply {
                store.history().lines().filter { it.isNotBlank() }.forEach { add(JsonParser.parseString(it)) }
            })
            add("questLocationDecisions", decisions)
            add("reminders", gson.toJsonTree(reminders))
            add("runtimeRules", JsonArray().apply {
                currentRules.forEach { rule -> add(JsonObject().apply {
                    addProperty("id", rule.id); addProperty("title", rule.title)
                    addProperty("schedule", rule.schedule); addProperty("placeId", rule.placeId)
                    addProperty("enabled", rule.enabled)
                }) }
            })
            add("nagStates", gson.toJsonTree(db.nagStateDao().getAll()))
            add("savedPlaces", gson.toJsonTree(places.getAll()))
            val rawEvents = context.getSharedPreferences("event_log", Context.MODE_PRIVATE).getString("events", "[]")!!
            add("events", runCatching { JsonParser.parseString(rawEvents) }.getOrElse { JsonArray() })
            addProperty("rawEventLog", rawEvents)
            addProperty("note", "Presence is Android's cached geofence state, not a fresh GPS fix. The report does not request GPS or change monitoring.")
        }
    }
}
