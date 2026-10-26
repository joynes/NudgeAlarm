package se.joynes.nudgealarm.storage

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.notification.ReminderAttentionStore

class ReminderAttentionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun interactionPersistsAndHighFrequencyTouchesAreCoalesced() {
        val store = ReminderAttentionStore(context)
        store.recordInteraction(10000L, force = true)
        store.recordInteraction(10100L)
        assertEquals(10000L, ReminderAttentionStore(context).lastInteractionAt)
        store.recordInteraction(11000L)
        assertEquals(11000L, ReminderAttentionStore(context).lastInteractionAt)
        store.recordInteraction(1L) // A clock correction cannot leave grace stuck in the future.
        assertEquals(1L, store.lastInteractionAt)
    }

    @Test fun openingInteractingAndReturningToAppRenewTheGracePeriod() {
        val store = ReminderAttentionStore(context)
        store.recordInteraction(1L, force = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(store.lastInteractionAt > 1L)
            store.recordInteraction(1L, force = true)
            scenario.onActivity { it.onUserInteraction() }
            assertTrue(store.lastInteractionAt > 1L)
            scenario.moveToState(Lifecycle.State.CREATED)
            store.recordInteraction(1L, force = true)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(ReminderAttentionStore(context).lastInteractionAt > 1L)
        }
    }

    @Test fun graceSettingPersistsWithFifteenMinuteDefaultAndCanBeOff() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val previous = SettingsStore(context).interactionGraceMinutes
        try {
            prefs.edit().remove("interaction_grace_minutes").commit()
            assertEquals(15, SettingsStore(context).interactionGraceMinutes)
            SettingsStore(context).interactionGraceMinutes = 47
            assertEquals(47, SettingsStore(context).interactionGraceMinutes)
            SettingsStore(context).interactionGraceMinutes = 0
            assertEquals(0, SettingsStore(context).interactionGraceMinutes)
        } finally { SettingsStore(context).interactionGraceMinutes = previous }
    }
}
