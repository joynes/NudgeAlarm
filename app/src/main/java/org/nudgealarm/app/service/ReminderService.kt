package org.nudgealarm.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nudgealarm.app.core.config.AppConfig
import org.nudgealarm.app.core.config.ReminderConfig
import org.nudgealarm.app.core.cron.CronExpression
import org.nudgealarm.app.core.event.Event
import org.nudgealarm.app.database.AnalyticsRepository
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.NagRepository
import org.nudgealarm.app.database.NagStateEntity
import org.nudgealarm.app.database.NagStatus
import org.nudgealarm.app.database.ReminderRepository
import org.nudgealarm.app.notification.ChannelSetup
import org.nudgealarm.app.notification.ReminderNotification
import org.nudgealarm.app.storage.ConfigLoader
import org.nudgealarm.app.storage.EventLogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReminderService : Service() {

    data class ActiveReminder(
        val ruleId: String,
        val title: String,
        val triggeredAt: Long,
        val snoozedUntil: Long? = null,
        val occurrenceKey: String,
        val snoozeCount: Int = 0
    )

    companion object {
        const val ACTION_START = "org.nudgealarm.app.ACTION_START"
        const val ACTION_STOP = "org.nudgealarm.app.ACTION_STOP"
        const val ACTION_RELOAD = "org.nudgealarm.app.ACTION_RELOAD"
        const val ACTION_DONE_FROM_UI = "org.nudgealarm.app.ACTION_DONE_FROM_UI"
        const val ACTION_DONE_ALL_FROM_UI = "org.nudgealarm.app.ACTION_DONE_ALL_FROM_UI"
        const val ACTION_SNOOZE_FROM_UI = "org.nudgealarm.app.ACTION_SNOOZE_FROM_UI"
        const val ACTION_CANCEL_FROM_UI = "org.nudgealarm.app.ACTION_CANCEL_FROM_UI"
        const val ACTION_CANCEL_ALL_FROM_UI = "org.nudgealarm.app.ACTION_CANCEL_ALL_FROM_UI"
        const val ACTION_REFRESH_NOTIFICATION = "org.nudgealarm.app.ACTION_REFRESH_NOTIFICATION"
        const val EXTRA_CONFIG_URI = "config_uri"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_RULE_IDS = "rule_ids"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        private const val PREFS_NAME = "reminder_service"
        private const val PREF_CONFIG_URI = "config_uri"

        var isRunning = false
            private set

        var currentConfig: AppConfig? = null
            private set

        var nextTriggerTime: Long? = null
            private set

        var nextTriggerRuleId: String? = null
            private set

        // Active reminders exposed to UI (updated from database)
        val activeReminders = mutableMapOf<String, ActiveReminder>()

        // Tracks when the last audible alert was sent (for min-interval rate limiting)
        var lastAlertTimeMs: Long = 0L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var schedulerJob: Job? = null

    private lateinit var eventLog: EventLogStore
    private lateinit var configLoader: ConfigLoader
    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var nagRepository: NagRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var settingsStore: org.nudgealarm.app.storage.SettingsStore

    private var config: AppConfig? = null

    override fun onCreate() {
        super.onCreate()
        eventLog = EventLogStore(this)
        configLoader = ConfigLoader(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        settingsStore = org.nudgealarm.app.storage.SettingsStore(this)

        // Initialize database and repositories
        val database = NagDatabase.getInstance(this)
        nagRepository = NagRepository(database.nagStateDao())
        analyticsRepository = AnalyticsRepository(database.nagHistoryDao())
        reminderRepository = ReminderRepository(database.reminderDao())

        ChannelSetup.createChannels(this)
        eventLog.add(Event.Debug(detail = "ReminderService.onCreate() - channels created, database initialized"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        eventLog.add(Event.Debug(detail = "onStartCommand action=${intent?.action} flags=$flags startId=$startId"))
        when (intent?.action) {
            ACTION_START -> {
                val uriString = intent.getStringExtra(EXTRA_CONFIG_URI)
                if (uriString != null) {
                    saveConfigUri(uriString)
                }
                startService()
            }
            ACTION_STOP -> {
                stopService()
            }
            ACTION_RELOAD -> {
                reloadConfig()
            }
            ActionReceiver.ACTION_DONE -> {
                val ruleId = intent.getStringExtra(ActionReceiver.EXTRA_RULE_ID)
                if (ruleId != null) handleDone(ruleId)
            }
            ActionReceiver.ACTION_SNOOZE_15M -> {
                val ruleId = intent.getStringExtra(ActionReceiver.EXTRA_RULE_ID)
                if (ruleId != null) handleSnooze(ruleId, 15.minutes)
            }
            ActionReceiver.ACTION_SNOOZE_1H -> {
                val ruleId = intent.getStringExtra(ActionReceiver.EXTRA_RULE_ID)
                if (ruleId != null) handleSnooze(ruleId, 1.hours)
            }
            ActionReceiver.ACTION_SNOOZE_24H -> {
                val ruleId = intent.getStringExtra(ActionReceiver.EXTRA_RULE_ID)
                if (ruleId != null) handleSnooze(ruleId, 24.hours)
            }
            ACTION_DONE_FROM_UI -> {
                val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
                if (ruleId != null) handleDone(ruleId)
            }
            ACTION_DONE_ALL_FROM_UI -> {
                val ruleIds = intent.getStringArrayExtra(EXTRA_RULE_IDS)
                if (ruleIds != null) handleDoneAll(ruleIds.toList())
            }
            ACTION_SNOOZE_FROM_UI -> {
                val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                if (ruleId != null) handleSnooze(ruleId, minutes.minutes)
            }
            ACTION_CANCEL_FROM_UI -> {
                val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
                if (ruleId != null) handleCancel(ruleId)
            }
            ACTION_CANCEL_ALL_FROM_UI -> {
                val ruleIds = intent.getStringArrayExtra(EXTRA_RULE_IDS)
                if (ruleIds != null) handleCancelAll(ruleIds.toList())
            }
            ACTION_REFRESH_NOTIFICATION -> {
                serviceScope.launch { refreshCombinedNotification(silent = true) }
            }
            else -> {
                // Service restarted by system, try to resume
                if (!isRunning) {
                    startService()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        isRunning = false
        eventLog.add(Event.ServiceStopped(reason = "onDestroy"))
        super.onDestroy()
    }

    private fun startService() {
        if (isRunning) return

        isRunning = true
        eventLog.add(Event.ServiceStarted())

        startForeground(
            ReminderNotification.SERVICE_NOTIFICATION_ID,
            ReminderNotification.buildServiceNotification(this)
        )

        // Resume any active nags from previous run
        resumeActiveNags()

        loadConfigAndStart()
    }

    private fun resumeActiveNags() {
        serviceScope.launch {
            try {
                val activeNags = nagRepository.getActiveNags()
                eventLog.add(Event.Debug(detail = "Resuming ${activeNags.size} active nags from database"))
                refreshCombinedNotification(silent = true) // restore silently on startup
            } catch (e: Exception) {
                eventLog.add(Event.Debug(detail = "Error resuming nags: ${e.message}"))
            }
        }
    }

    private fun stopService() {
        isRunning = false
        schedulerJob?.cancel()
        currentConfig = null
        nextTriggerTime = null
        nextTriggerRuleId = null
        activeReminders.clear()
        notificationManager.cancel(ReminderNotification.COMBINED_NOTIFICATION_ID)

        eventLog.add(Event.ServiceStopped(reason = "user"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun reloadConfig() {
        schedulerJob?.cancel()
        loadConfigAndStart()
    }

    private fun loadConfigAndStart() {
        serviceScope.launch {
            // Try loading from database first
            try {
                val hasDbReminders = reminderRepository.hasReminders()
                eventLog.add(Event.Debug(detail = "loadConfigAndStart: hasDbReminders=$hasDbReminders"))

                if (hasDbReminders) {
                    // Load from database
                    val appConfig = reminderRepository.getAsAppConfig()
                    config = appConfig
                    currentConfig = appConfig
                    val rulesSummary = appConfig.reminders.joinToString(", ") { "${it.id}(${it.schedule})" }
                    eventLog.add(Event.ConfigLoaded(ruleCount = appConfig.reminders.size))
                    eventLog.add(Event.Debug(detail = "Config from database: $rulesSummary"))
                    startScheduler(appConfig)
                    return@launch
                }
            } catch (e: Exception) {
                eventLog.add(Event.Debug(detail = "Error loading from database: ${e.message}"))
            }

            // Fall back to YAML file
            val uriString = getConfigUri()
            eventLog.add(Event.Debug(detail = "loadConfigAndStart configUri=$uriString"))
            if (uriString == null) {
                eventLog.add(Event.ConfigError(error = "No reminders configured. Add reminders in Edit Reminders."))
                updateServiceNotification("No reminders")
                return@launch
            }

            val uri = Uri.parse(uriString)
            eventLog.add(Event.Debug(detail = "Loading config from uri=$uri scheme=${uri.scheme} path=${uri.path}"))
            val result = configLoader.loadFromUri(uri)

            result.fold(
                onSuccess = { appConfig ->
                    config = appConfig
                    currentConfig = appConfig
                    val rulesSummary = appConfig.reminders.joinToString(", ") { "${it.id}(${it.schedule})" }
                    eventLog.add(Event.ConfigLoaded(ruleCount = appConfig.reminders.size))
                    eventLog.add(Event.Debug(detail = "Config rules: $rulesSummary"))
                    startScheduler(appConfig)
                },
                onFailure = { error ->
                    eventLog.add(Event.ConfigError(error = "${error.message ?: "Unknown error"}\n${error.stackTraceToString().take(200)}"))
                    updateServiceNotification("Config error")
                }
            )
        }
    }

    private fun startScheduler(appConfig: AppConfig) {
        val pollIntervalMs = 5 * 60 * 1000L // 5 minutes
        eventLog.add(Event.SchedulerLoopIteration(detail = "Starting poll scheduler with ${appConfig.reminders.size} rules, poll every ${pollIntervalMs / 1000}s"))

        // Cleanup old database entries
        serviceScope.launch {
            nagRepository.cleanup()
        }

        schedulerJob = serviceScope.launch {
            var iteration = 0
            while (isActive) {
                iteration++
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance()
                val tz = java.util.TimeZone.getDefault()
                val dayOfWeekCal = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val dayOfWeekCron = if (dayOfWeekCal == java.util.Calendar.SUNDAY) 0 else dayOfWeekCal - 1
                val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.getDefault()).format(Date(now))
                eventLog.add(Event.SchedulerLoopIteration(detail = "Poll #$iteration at $nowStr tz=${tz.id} dayOfWeek=$dayOfWeekCron(${dayNames[dayOfWeekCron]})"))

                // Check each rule: should it have fired?
                for (rule in appConfig.reminders) {
                    try {
                        checkAndFireRule(rule, now)
                    } catch (e: Exception) {
                        eventLog.add(Event.ConfigError(error = "Error checking rule ${rule.id}: ${e.message}"))
                    }
                }

                // Compute next upcoming trigger for UI display
                updateNextTriggerDisplay(appConfig)

                // Refresh combined notification and sync activeReminders
                refreshCombinedNotification()

                eventLog.add(Event.SchedulerLoopIteration(detail = "Poll #$iteration done, sleeping ${pollIntervalMs / 1000}s"))
                delay(pollIntervalMs)
            }
            eventLog.add(Event.SchedulerLoopIteration(detail = "Scheduler loop ended (isActive=false)"))
        }
    }

    private suspend fun checkAndFireRule(rule: ReminderConfig, now: Long) {
        if (rule.schedule.startsWith("once:")) {
            // One-time event: schedule is "once:TIMESTAMP"
            val timestamp = rule.schedule.removePrefix("once:").toLongOrNull() ?: return
            val scheduledTime = (timestamp / 60000) * 60000

            if (timestamp <= now) {
                val (isNew, occurrenceKey) = nagRepository.tryFire(rule, scheduledTime)

                if (isNew) {
                    val triggerStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                    eventLog.add(Event.SchedulerLoopIteration(detail = "TRIGGER (once): ${rule.id} '${rule.title}' (scheduled $triggerStr, key=$occurrenceKey)"))
                    fireReminder(rule, occurrenceKey, scheduledTime)

                    // Auto-delete one-time reminder after firing (prevents re-activation)
                    reminderRepository.delete(rule.id)
                    eventLog.add(Event.Debug(detail = "One-time reminder ${rule.id} auto-deleted"))
                }
            }
        } else {
            val cron = CronExpression.parse(rule.schedule)

            // Get the trigger time that is <= now (check if we're in a matching minute)
            // We look back 2 hours to catch any missed triggers (e.g., if Android killed the process)
            val checkFrom = now - (2 * 60 * 60 * 1000) // look back 2 hours
            val nextTrigger = cron.nextTriggerTime(checkFrom)

            // Round to minute for consistent deduplication
            val scheduledTime = (nextTrigger / 60000) * 60000

            if (nextTrigger <= now) {
                // This rule should have triggered. Try to fire (atomic deduplication).
                val (isNew, occurrenceKey) = nagRepository.tryFire(rule, scheduledTime)

                if (isNew) {
                    val triggerStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nextTrigger))
                    eventLog.add(Event.SchedulerLoopIteration(detail = "TRIGGER: ${rule.id} '${rule.title}' (scheduled $triggerStr, key=$occurrenceKey)"))
                    fireReminder(rule, occurrenceKey, scheduledTime)
                }
                // If not new, it's a duplicate - silently ignore
            }
        }
    }

    private fun updateNextTriggerDisplay(appConfig: AppConfig) {
        val now = System.currentTimeMillis()
        val nextEvent = appConfig.reminders.mapNotNull { rule ->
            try {
                if (rule.schedule.startsWith("once:")) {
                    val timestamp = rule.schedule.removePrefix("once:").toLongOrNull() ?: return@mapNotNull null
                    if (timestamp > now) rule to timestamp else null
                } else {
                    val cron = CronExpression.parse(rule.schedule)
                    val nextTime = cron.nextTriggerTime(now)
                    rule to nextTime
                }
            } catch (e: Exception) {
                null
            }
        }.minByOrNull { it.second }

        if (nextEvent != null) {
            val (rule, triggerTime) = nextEvent
            nextTriggerTime = triggerTime
            nextTriggerRuleId = rule.id
            updateServiceNotification(rule.title, triggerTime)
        }
    }

    private fun fireReminder(rule: ReminderConfig, occurrenceKey: String, scheduledTime: Long) {
        eventLog.add(Event.Debug(detail = "fireReminder: acquiring wake lock for ${rule.id}"))
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NudgeAlarm:ReminderTrigger"
        ).apply { acquire(10_000) }

        try {
            eventLog.add(Event.ReminderTriggered(ruleId = rule.id))
            // Notification is handled by the scheduler loop's refreshCombinedNotification() call
        } finally {
            wakeLock.release()
            eventLog.add(Event.Debug(detail = "fireReminder: wake lock released for ${rule.id}"))
        }
    }

    private fun handleDone(ruleId: String) {
        eventLog.add(Event.UserTappedDone(ruleId = ruleId))

        serviceScope.launch {
            withContext(NonCancellable) {
                var nagState = nagRepository.markDoneByRuleId(ruleId)

                if (nagState != null) {
                    // Active reminder - record to history
                    analyticsRepository.recordCompletion(nagState, NagStatus.COMPLETED)
                } else {
                    // No active nag - this is an early completion from Today's Schedule
                    // Create a completed entry for the most recent scheduled time
                    val rule = config?.reminders?.find { it.id == ruleId }
                    if (rule != null) {
                        val now = System.currentTimeMillis()
                        val scheduledTime = if (rule.schedule.startsWith("once:")) {
                            val ts = rule.schedule.removePrefix("once:").toLongOrNull() ?: now
                            (ts / 60000) * 60000
                        } else {
                            val cron = CronExpression.parse(rule.schedule)
                            // Use 2-hour lookback (same as scheduler) so we get today's trigger,
                            // not tomorrow's, even if called minutes after the scheduled time
                            val nextTrigger = cron.nextTriggerTime(now - (2 * 60 * 60 * 1000))
                            (nextTrigger / 60000) * 60000
                        }

                        // Try to create the nag state entry
                        val (isNew, occurrenceKey) = nagRepository.tryFire(rule, scheduledTime)
                        if (isNew) {
                            // Immediately mark as completed
                            nagRepository.markDone(occurrenceKey)
                            nagState = nagRepository.getByKey(occurrenceKey)
                            if (nagState != null) {
                                analyticsRepository.recordCompletion(nagState, NagStatus.COMPLETED)
                            }
                            eventLog.add(Event.Debug(detail = "Early completion for $ruleId scheduled at $scheduledTime"))
                        }
                    }
                }
            }

            refreshCombinedNotification(silent = true) // user tapped DONE, update silently
        }
    }

    private fun handleCancel(ruleId: String) {
        eventLog.add(Event.UserTappedCancel(ruleId = ruleId))

        serviceScope.launch {
            withContext(NonCancellable) {
                val nagState = nagRepository.markCancelledByRuleId(ruleId)
                if (nagState != null) {
                    // Record to history
                    analyticsRepository.recordCompletion(nagState, NagStatus.CANCELLED)
                } else {
                    // Quest hasn't triggered yet - create a CANCELLED entry so it's filtered from Quest Log
                    val rule = config?.reminders?.find { it.id == ruleId }
                    if (rule != null) {
                        nagRepository.createCancelledEntry(ruleId, rule.title)
                    }
                }
            }

            refreshCombinedNotification(silent = true) // user cancelled, update silently
        }
    }

    private fun handleDoneAll(ruleIds: List<String>) {
        eventLog.add(Event.UiAction(action = "Mark all done from UI: ${ruleIds.joinToString()}"))

        serviceScope.launch {
            withContext(NonCancellable) {
                for (ruleId in ruleIds) {
                    val nagState = nagRepository.markDoneByRuleId(ruleId)
                    if (nagState != null) {
                        analyticsRepository.recordCompletion(nagState, NagStatus.COMPLETED)
                    }
                }
            }
            refreshCombinedNotification(silent = true)
        }
    }

    private fun handleCancelAll(ruleIds: List<String>) {
        eventLog.add(Event.UiAction(action = "Cancel all from UI: ${ruleIds.joinToString()}"))

        serviceScope.launch {
            withContext(NonCancellable) {
                for (ruleId in ruleIds) {
                    val nagState = nagRepository.markCancelledByRuleId(ruleId)
                    if (nagState != null) {
                        analyticsRepository.recordCompletion(nagState, NagStatus.CANCELLED)
                    } else {
                        val rule = config?.reminders?.find { it.id == ruleId }
                        if (rule != null) {
                            nagRepository.createCancelledEntry(ruleId, rule.title)
                        }
                    }
                }
            }
            refreshCombinedNotification(silent = true)
        }
    }

    private fun handleSnooze(ruleId: String, duration: kotlin.time.Duration) {
        eventLog.add(Event.UserTappedSnooze(ruleId = ruleId, duration = "${duration.inWholeMinutes}m"))

        serviceScope.launch {
            withContext(NonCancellable) {
                nagRepository.snoozeByRuleId(ruleId, duration)
            }
            refreshCombinedNotification(silent = true) // user snoozed, update silently
        }
    }

    private fun updateServiceNotification(statusText: String) {
        val notification = ReminderNotification.buildServiceNotification(
            this,
            nextRuleTitle = null,
            nextTriggerTime = null,
            activeRulesCount = 0
        )
        notificationManager.notify(ReminderNotification.SERVICE_NOTIFICATION_ID, notification)
    }

    private fun updateServiceNotification(ruleTitle: String, triggerTime: Long) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerTime))
        val notification = ReminderNotification.buildServiceNotification(
            this,
            nextRuleTitle = ruleTitle,
            nextTriggerTime = timeStr,
            activeRulesCount = activeReminders.size
        )
        notificationManager.notify(ReminderNotification.SERVICE_NOTIFICATION_ID, notification)
    }

    private suspend fun refreshCombinedNotification(silent: Boolean = false) {
        val now = System.currentTimeMillis()

        // Suppress when screen is off and "active only" is enabled
        val screenSilent = settingsStore.alertOnlyWhenActive && !powerManager.isInteractive

        // Suppress when within the minimum alert interval
        val minIntervalMs = settingsStore.minAlertIntervalMinutes * 60_000L
        val rateLimited = !silent && minIntervalMs > 0 && (now - lastAlertTimeMs) < minIntervalMs

        val effectiveSilent = silent || screenSilent || rateLimited

        // Track last audible alert time
        if (!effectiveSilent) lastAlertTimeMs = now
        val allNags = nagRepository.getActiveNags()

        // Clear expired snoozes
        for (nag in allNags) {
            if (nag.status == NagStatus.SNOOZED.name) {
                val snoozedUntil = nag.snoozedUntil
                if (snoozedUntil != null && snoozedUntil <= now) {
                    nagRepository.clearSnooze(nag.occurrenceKey)
                }
            }
        }

        val activeNags = allNags.filter {
            it.status == NagStatus.ACTIVE.name ||
            (it.status == NagStatus.SNOOZED.name && (it.snoozedUntil ?: Long.MAX_VALUE) <= now)
        }

        // Cancel old per-quest notifications
        for (nag in allNags) {
            notificationManager.cancel(ReminderNotification.getNotificationIdForRule(nag.ruleId))
        }

        if (activeNags.isEmpty()) {
            notificationManager.cancel(ReminderNotification.COMBINED_NOTIFICATION_ID)
        } else {
            val notification = ReminderNotification.buildCombinedNotification(this, activeNags, onlyAlertOnce = effectiveSilent)
            notificationManager.notify(ReminderNotification.COMBINED_NOTIFICATION_ID, notification)
        }

        // Update activeReminders map for UI
        activeReminders.clear()
        for (nag in allNags) {
            activeReminders[nag.ruleId] = ActiveReminder(
                ruleId = nag.ruleId,
                title = nag.title,
                triggeredAt = nag.triggeredAt,
                snoozedUntil = nag.snoozedUntil,
                occurrenceKey = nag.occurrenceKey,
                snoozeCount = nag.snoozeCount
            )
        }
    }

    private fun saveConfigUri(uri: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_CONFIG_URI, uri)
            .apply()
    }

    private fun getConfigUri(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_CONFIG_URI, null)
    }
}
