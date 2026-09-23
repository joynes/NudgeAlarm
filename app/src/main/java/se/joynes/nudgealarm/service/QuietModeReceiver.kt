package se.joynes.nudgealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.joynes.nudgealarm.notification.QuietModeController

class QuietModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        QuietModeController.expireIfNeeded(context)
    }
}
