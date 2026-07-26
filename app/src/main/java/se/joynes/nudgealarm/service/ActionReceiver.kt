package se.joynes.nudgealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "se.joynes.nudgealarm.ACTION_DONE"
        const val ACTION_SNOOZE_15M = "se.joynes.nudgealarm.ACTION_SNOOZE_15M"
        const val ACTION_SNOOZE_1H = "se.joynes.nudgealarm.ACTION_SNOOZE_1H"
        const val ACTION_SNOOZE_24H = "se.joynes.nudgealarm.ACTION_SNOOZE_24H"
        const val EXTRA_RULE_ID = "rule_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return

        val serviceIntent = Intent(context, ReminderService::class.java).apply {
            action = intent.action
            putExtra(EXTRA_RULE_ID, ruleId)
        }

        context.startService(serviceIntent)
    }
}
