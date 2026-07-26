package se.joynes.nudgealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.joynes.nudgealarm.core.event.Event
import se.joynes.nudgealarm.storage.EventLogStore

/**
 * Receives system broadcasts to restart the service after:
 * - Phone reboot (BOOT_COMPLETED)
 * - App update (MY_PACKAGE_REPLACED)
 *
 * This ensures reminders continue working without user intervention.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventLog = EventLogStore(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                eventLog.add(Event.Debug(detail = "BootReceiver: BOOT_COMPLETED received"))
                startServiceIfConfigured(context, eventLog, "boot")
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                eventLog.add(Event.Debug(detail = "BootReceiver: MY_PACKAGE_REPLACED received (app updated)"))
                startServiceIfConfigured(context, eventLog, "app_update")
            }
        }
    }

    private fun startServiceIfConfigured(context: Context, eventLog: EventLogStore, reason: String) {
        // Check if there's a saved config URI (meaning user had the service running before)
        val prefs = context.getSharedPreferences("reminder_service", Context.MODE_PRIVATE)
        val configUri = prefs.getString("config_uri", null)

        // Also check if there's a current game saved
        val appStatePrefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        val currentGameId = appStatePrefs.getString("current_game_id", null)

        if (configUri != null || currentGameId != null) {
            eventLog.add(Event.Debug(detail = "BootReceiver: Starting service after $reason (configUri=$configUri, gameId=$currentGameId)"))

            try {
                val serviceIntent = Intent(context, ReminderService::class.java).apply {
                    action = ReminderService.ACTION_START
                    if (configUri != null) {
                        putExtra(ReminderService.EXTRA_CONFIG_URI, configUri)
                    }
                }
                context.startForegroundService(serviceIntent)
                eventLog.add(Event.ServiceStarted())
            } catch (e: Exception) {
                eventLog.add(Event.Debug(detail = "BootReceiver: Failed to start service: ${e.message}"))
            }
        } else {
            eventLog.add(Event.Debug(detail = "BootReceiver: No config found, not starting service after $reason"))
        }
    }
}
