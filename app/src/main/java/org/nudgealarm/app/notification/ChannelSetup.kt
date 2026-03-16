package org.nudgealarm.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import org.nudgealarm.app.storage.SettingsStore

object ChannelSetup {
    private const val REMINDER_CHANNEL_PREFIX = "reminder_channel_v"
    const val SERVICE_CHANNEL_ID = "service_channel"

    /**
     * Get the current reminder channel ID based on the channel version.
     * This creates unique channel IDs to work around Android's limitation
     * where channel settings become locked after creation.
     */
    fun getReminderChannelId(context: Context): String {
        val settingsStore = SettingsStore(context)
        return "$REMINDER_CHANNEL_PREFIX${settingsStore.channelVersion}"
    }

    fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsStore = SettingsStore(context)
        val reminderChannelId = "$REMINDER_CHANNEL_PREFIX${settingsStore.channelVersion}"

        // Reminder channel - high importance for heads-up notifications
        val reminderChannel = NotificationChannel(
            reminderChannelId,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminder notifications"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // Respect Do Not Disturb mode (don't bypass)
            setBypassDnd(false)
            enableVibration(settingsStore.vibrationEnabled)
            if (settingsStore.vibrationEnabled) {
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }
            // Use getEffectiveSoundUri() to respect custom sound selection
            val soundUri = settingsStore.getEffectiveSoundUri()
            android.util.Log.d("ChannelSetup", "Creating channel with soundUri=$soundUri customUri=${settingsStore.customSoundUri} alarmSound=${settingsStore.alarmSound}")
            if (soundUri != null) {
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        // Use notification volume instead of alarm volume
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
        }

        // Service channel - low importance for ongoing service notification
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the reminder service running"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    /**
     * Recreate the reminder channel with updated settings.
     * Creates a new channel with an incremented version number to ensure
     * Android applies the new sound/vibration settings.
     * Also cleans up old channel versions.
     */
    fun recreateReminderChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsStore = SettingsStore(context)

        // Delete old channel before incrementing version
        val oldChannelId = "$REMINDER_CHANNEL_PREFIX${settingsStore.channelVersion}"
        notificationManager.deleteNotificationChannel(oldChannelId)

        // Increment version to create a fresh channel
        settingsStore.incrementChannelVersion()

        // Clean up any other old reminder channels (in case of leftover channels)
        notificationManager.notificationChannels
            .filter { it.id.startsWith(REMINDER_CHANNEL_PREFIX) }
            .forEach { notificationManager.deleteNotificationChannel(it.id) }

        android.util.Log.d("ChannelSetup", "Creating new channel with version ${settingsStore.channelVersion}")
        createChannels(context)
    }
}
