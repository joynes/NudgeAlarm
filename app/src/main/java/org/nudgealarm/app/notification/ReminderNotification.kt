package org.nudgealarm.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.nudgealarm.app.MainActivity
import org.nudgealarm.app.R
import org.nudgealarm.app.core.config.ReminderConfig
import org.nudgealarm.app.database.NagStateEntity
import org.nudgealarm.app.service.ActionReceiver

object ReminderNotification {
    const val SERVICE_NOTIFICATION_ID = 1
    const val SUMMARY_NOTIFICATION_ID = 999
    const val COMBINED_NOTIFICATION_ID = 999
    private const val REMINDER_NOTIFICATION_BASE_ID = 1000
    private const val REMINDER_GROUP_KEY = "nudgealarm_reminders"

    fun getNotificationIdForRule(ruleId: String): Int {
        return REMINDER_NOTIFICATION_BASE_ID + ruleId.hashCode().and(0x7FFFFFFF) % 1000
    }

    fun buildServiceNotification(
        context: Context,
        nextRuleTitle: String? = null,
        nextTriggerTime: String? = null,
        activeRulesCount: Int = 0
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            nextRuleTitle != null && nextTriggerTime != null ->
                "Next: $nextRuleTitle at $nextTriggerTime"
            activeRulesCount > 0 ->
                "$activeRulesCount active reminders"
            else ->
                "Monitoring reminders"
        }

        return NotificationCompat.Builder(context, ChannelSetup.SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NudgeAlarm Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun buildReminderNotification(
        context: Context,
        rule: ReminderConfig,
        nagCount: Int = 1
    ): Notification {
        return buildReminderNotificationFromNag(
            context,
            rule.id,
            rule.title,
            nagCount,
            rule.maxNags
        )
    }

    /**
     * Build a reminder notification from nag state (used when resuming from database).
     */
    fun buildReminderNotificationFromNag(
        context: Context,
        ruleId: String,
        title: String,
        nagCount: Int,
        maxNags: Int
    ): Notification {
        val notificationId = getNotificationIdForRule(ruleId)

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = createActionIntent(context, ActionReceiver.ACTION_DONE, ruleId)
        val snooze15mIntent = createActionIntent(context, ActionReceiver.ACTION_SNOOZE_15M, ruleId)
        val snooze1hIntent = createActionIntent(context, ActionReceiver.ACTION_SNOOZE_1H, ruleId)
        val snooze24hIntent = createActionIntent(context, ActionReceiver.ACTION_SNOOZE_24H, ruleId)

        val displayTitle = if (nagCount > 1) {
            "$title ($nagCount/$maxNags)"
        } else {
            title
        }

        return NotificationCompat.Builder(context, ChannelSetup.getReminderChannelId(context))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(displayTitle)
            .setContentText("Tap DONE when complete")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(REMINDER_GROUP_KEY)
            .addAction(0, "DONE", doneIntent)
            .addAction(0, "15m", snooze15mIntent)
            .addAction(0, "1h", snooze1hIntent)
            .addAction(0, "24h", snooze24hIntent)
            .setAutoCancel(false)
            .build()
    }

    fun buildGroupSummaryNotification(
        context: Context,
        activeQuests: List<String>
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("${activeQuests.size} active quests")
        activeQuests.forEach { title -> inboxStyle.addLine(title) }

        return NotificationCompat.Builder(context, ChannelSetup.getReminderChannelId(context))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${activeQuests.size} active quests")
            .setContentText(activeQuests.joinToString(", "))
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(REMINDER_GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(false)
            .build()
    }

    fun buildCombinedNotification(
        context: Context,
        activeNags: List<NagStateEntity>,
        onlyAlertOnce: Boolean = false
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            COMBINED_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (activeNags.size == 1) {
            val nag = activeNags[0]
            val doneIntent = createActionIntent(context, ActionReceiver.ACTION_DONE, nag.ruleId)
            val snooze15mIntent = createActionIntent(context, ActionReceiver.ACTION_SNOOZE_15M, nag.ruleId)
            val snooze1hIntent = createActionIntent(context, ActionReceiver.ACTION_SNOOZE_1H, nag.ruleId)

            NotificationCompat.Builder(context, ChannelSetup.getReminderChannelId(context))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(nag.title)
                .setContentText("Tap DONE when complete")
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(onlyAlertOnce)
                .addAction(0, "DONE", doneIntent)
                .addAction(0, "15m", snooze15mIntent)
                .addAction(0, "1h", snooze1hIntent)
                .setAutoCancel(false)
                .build()
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${activeNags.size} active quests")
            activeNags.forEach { nag -> inboxStyle.addLine(nag.title) }

            NotificationCompat.Builder(context, ChannelSetup.getReminderChannelId(context))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${activeNags.size} active quests")
                .setContentText(activeNags.joinToString(", ") { it.title })
                .setStyle(inboxStyle)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(onlyAlertOnce)
                .addAction(0, "Open app", contentIntent)
                .setAutoCancel(false)
                .build()
        }
    }

    private fun createActionIntent(
        context: Context,
        action: String,
        ruleId: String
    ): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(ActionReceiver.EXTRA_RULE_ID, ruleId)
        }

        val requestCode = (action.hashCode() + ruleId.hashCode()).and(0x7FFFFFFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
