package se.joynes.nudgealarm.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import se.joynes.nudgealarm.service.QuietModeReceiver
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.SettingsStore

object QuietModeController {
    private const val REQUEST_CODE = 4102

    fun enableFor(context: Context, minutes: Int): Long {
        val until = System.currentTimeMillis() + minutes * 60_000L
        SettingsStore(context).enableQuietModeUntil(until)
        ChannelSetup.recreateReminderChannel(context)
        schedule(context, until)
        refreshService(context)
        return until
    }

    fun disable(context: Context, refreshService: Boolean = true) {
        SettingsStore(context).quietMode = false
        cancelAlarm(context)
        ChannelSetup.recreateReminderChannel(context)
        if (refreshService) refreshService(context)
    }

    /** Returns true when an expired timed quiet mode was cleared. */
    fun expireIfNeeded(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val settings = SettingsStore(context)
        val until = settings.quietModeUntil
        if (!settings.quietMode || until <= 0L) return false
        if (until > now) {
            schedule(context, until)
            return false
        }
        disable(context)
        return true
    }

    fun schedule(context: Context, until: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            until,
            pendingIntent(context)
        )
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, QuietModeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun refreshService(context: Context) {
        if (!ReminderService.isRunning) return
        context.startService(Intent(context, ReminderService::class.java).apply {
            action = ReminderService.ACTION_REFRESH_NOTIFICATION
        })
    }
}
