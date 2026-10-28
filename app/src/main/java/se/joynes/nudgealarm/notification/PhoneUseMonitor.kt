package se.joynes.nudgealarm.notification

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import se.joynes.nudgealarm.storage.SettingsStore

/** Unlock broadcasts act immediately; optional usage timestamps use the existing nag poll. */
internal class PhoneUseMonitor(
    private val context: Context,
    private val usageAccessAllowed: () -> Boolean = { hasUsageAccess(context) },
    private val queryLatestUse: (Long, Long) -> Long? = { start, end -> queryLatestInteraction(context, start, end) },
    private val onUnlock: () -> Unit
) {
    private val settings = SettingsStore(context)
    private val attention = ReminderAttentionStore(context)
    private var registered = false
    private var lastQueryAt = 0L
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) handleUnlock()
        }
    }

    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun stop() {
        if (registered) context.unregisterReceiver(receiver)
        registered = false
    }

    internal fun handleUnlock(now: Long = System.currentTimeMillis()) {
        if (settings.interactionGraceSource == "app" || settings.interactionGraceMinutes == 0) return
        attention.recordInteraction(now, force = true)
        onUnlock()
    }

    fun collectRecentUsage(now: Long) {
        if (settings.interactionGraceSource != "usage" || settings.interactionGraceMinutes == 0 || !usageAccessAllowed()) return
        val start = maxOf(now - settings.interactionGraceMinutes * 60_000L, lastQueryAt.takeIf { it <= now } ?: 0L)
        if (start >= now) return
        try {
            val latest = queryLatestUse(start, now) ?: return
            if (latest in start..now && latest > attention.lastInteractionAt) attention.recordInteraction(latest, force = true)
            lastQueryAt = now
        } catch (_: SecurityException) {
            // Access can be revoked; unlock detection keeps working.
        } catch (_: IllegalStateException) {
            // Usage history may be unavailable while Android is locking/unlocking.
        }
    }

    companion object {
        fun hasUsageAccess(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

        internal fun isPhoneUseEvent(type: Int) = type == UsageEvents.Event.USER_INTERACTION || type == UsageEvents.Event.KEYGUARD_HIDDEN

        private fun queryLatestInteraction(context: Context, start: Long, end: Long): Long? {
            val manager = context.getSystemService(UsageStatsManager::class.java)
            val events = if (Build.VERSION.SDK_INT >= 35) manager.queryEvents(
                UsageEventsQuery.Builder(start, end).setEventTypes(UsageEvents.Event.USER_INTERACTION, UsageEvents.Event.KEYGUARD_HIDDEN).build()
            ) else manager.queryEvents(start, end)
            if (events == null) return null
            val event = UsageEvents.Event()
            var latest = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // Never read or retain package names, activity names or event extras.
                if (isPhoneUseEvent(event.eventType) && event.timeStamp in start..end) latest = maxOf(latest, event.timeStamp)
            }
            return latest
        }
    }
}
