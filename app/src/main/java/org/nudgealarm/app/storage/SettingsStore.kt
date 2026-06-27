package org.nudgealarm.app.storage

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

enum class AlarmSound(val displayName: String, private val uriString: String?, val category: String) {
    // Alarm sounds
    DEFAULT_ALARM("Default Alarm", "content://settings/system/alarm_alert", "Alarms"),
    ALARM_CLOCK("Alarm Clock", "content://settings/system/alarm_alert", "Alarms"),

    // Notification sounds
    DEFAULT_NOTIFICATION("Default Notification", "content://settings/system/notification_sound", "Notifications"),

    // Ringtone sounds
    DEFAULT_RINGTONE("Default Ringtone", "content://settings/system/ringtone", "Ringtones"),

    // System UI sounds (these are commonly available)
    BEEP("Beep", "content://settings/system/notification_sound", "Effects"),

    // Silent
    SILENT("Silent", null, "Other");

    val uri: Uri?
        get() = uriString?.let { Uri.parse(it) }

    companion object {
        fun fromName(name: String): AlarmSound {
            return entries.find { it.name == name } ?: DEFAULT_ALARM
        }

        /**
         * Get all available ringtones from the device
         */
        fun getDeviceRingtones(context: Context): List<Pair<String, Uri>> {
            val ringtones = mutableListOf<Pair<String, Uri>>()
            val manager = RingtoneManager(context)
            manager.setType(RingtoneManager.TYPE_ALL)

            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                // Use getActualDefaultRingtoneUri approach - get the real URI not positional
                val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                val baseUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                val uri = Uri.parse("$baseUri/$id")
                android.util.Log.d("AlarmSound", "Ringtone: $title -> $uri")
                ringtones.add(title to uri)
            }
            return ringtones
        }
    }
}

/**
 * Helper class to preview sounds
 */
class SoundPreviewPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(uri: Uri?) {
        stop() // Stop any currently playing sound

        if (uri == null) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            // Sound might not be available, ignore
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALARM_SOUND = "alarm_sound"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_CUSTOM_SOUND_NAME = "custom_sound_name"
        private const val KEY_CHANNEL_VERSION = "channel_version"
        private const val KEY_QUIET_MODE = "quiet_mode"
        private const val KEY_STALE_TASK_THRESHOLD_DAYS = "stale_task_threshold_days"
        private const val KEY_ALERT_ONLY_WHEN_ACTIVE = "alert_only_when_active"
        private const val KEY_MIN_ALERT_INTERVAL_MINUTES = "min_alert_interval_minutes"
    }

    /**
     * Channel version - incremented each time sound/vibration settings change.
     * Used to create unique notification channel IDs to work around Android's
     * limitation where channel settings become locked after creation.
     */
    var channelVersion: Int
        get() = prefs.getInt(KEY_CHANNEL_VERSION, 1)
        private set(value) = prefs.edit().putInt(KEY_CHANNEL_VERSION, value).apply()

    fun incrementChannelVersion(): Int {
        val newVersion = channelVersion + 1
        channelVersion = newVersion
        return newVersion
    }

    var alarmSound: AlarmSound
        get() = AlarmSound.fromName(prefs.getString(KEY_ALARM_SOUND, AlarmSound.DEFAULT_ALARM.name) ?: AlarmSound.DEFAULT_ALARM.name)
        set(value) = prefs.edit().putString(KEY_ALARM_SOUND, value.name).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var customSoundUri: Uri?
        get() = prefs.getString(KEY_CUSTOM_SOUND_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_CUSTOM_SOUND_URI, value?.toString()).apply()

    var customSoundName: String?
        get() = prefs.getString(KEY_CUSTOM_SOUND_NAME, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_SOUND_NAME, value).apply()

    /** When true, notifications are delivered silently (no sound, no vibration, no heads-up). */
    var quietMode: Boolean
        get() = prefs.getBoolean(KEY_QUIET_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_MODE, value).apply()

    /**
     * Tasks that are still ACTIVE from a previous day are auto-expired when the
     * rule's recurrence interval is <= this many days.
     * Default 1 = clear daily (and sub-daily) tasks at day boundary; keep every-2+-day tasks.
     */
    var staleTaskThresholdDays: Int
        get() = prefs.getInt(KEY_STALE_TASK_THRESHOLD_DAYS, 1)
        set(value) = prefs.edit().putInt(KEY_STALE_TASK_THRESHOLD_DAYS, value).apply()

    /** When true, alerts (sound/vibration) are suppressed when the screen is off. */
    var alertOnlyWhenActive: Boolean
        get() = prefs.getBoolean(KEY_ALERT_ONLY_WHEN_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_ONLY_WHEN_ACTIVE, value).apply()

    /**
     * Minimum minutes between alert sounds/vibrations. 0 = no limit (alert every poll cycle).
     * E.g. 60 = at most once per hour regardless of nag interval.
     */
    var minAlertIntervalMinutes: Int
        get() = prefs.getInt(KEY_MIN_ALERT_INTERVAL_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_MIN_ALERT_INTERVAL_MINUTES, value).apply()

    /**
     * Get the effective sound URI to use for notifications.
     * Returns custom sound if set, otherwise the selected AlarmSound's URI.
     */
    fun getEffectiveSoundUri(): Uri? {
        return customSoundUri ?: alarmSound.uri
    }
}
