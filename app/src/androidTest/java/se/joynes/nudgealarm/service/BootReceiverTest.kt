package se.joynes.nudgealarm.service

import android.content.*
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.storage.EventLogStore

class BootReceiverTest {
    private class BootContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val started = mutableListOf<Intent>()
        var reject = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("boot_regression_$name", mode)
        override fun startForegroundService(service: Intent): ComponentName {
            if (reject) throw IllegalStateException("Startup unavailable")
            started.add(service)
            return service.component!!
        }
    }

    @Test fun rebootAndUpdateRestoreOnlyConfiguredRemindersAndHandleStartupFailure() {
        val context = BootContext()
        listOf("app_state", "reminder_service", "event_log").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent("unrelated"))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(context.started.isEmpty())
        context.getSharedPreferences("app_state", 0).edit().putString("current_game_id", "test").commit()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(ReminderService.ACTION_START, context.started.single().action)
        assertFalse(context.started.single().hasExtra(ReminderService.EXTRA_CONFIG_URI))
        context.getSharedPreferences("reminder_service", 0).edit().putString("config_uri", "file:///test.yaml").commit()
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals("file:///test.yaml", context.started.last().getStringExtra(ReminderService.EXTRA_CONFIG_URI))
        context.reject = true
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(EventLogStore(context).getAll().any { it.message.contains("Startup unavailable") })
    }
}
