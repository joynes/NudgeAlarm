package se.joynes.nudgealarm.storage

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.notification.ReminderAttentionStore
import se.joynes.nudgealarm.notification.PhoneUseMonitor
import se.joynes.nudgealarm.notification.repeatAlertAcknowledged
import se.joynes.nudgealarm.database.NagStateEntity

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

    @Test fun eachNewDeliveryNeedsANewUseAndSnoozeRestartsItsOwnAcknowledgement() {
        val store = ReminderAttentionStore(context)
        val a = NagStateEntity("attention-a@1", "attention-a", 1, "A", 1, 1, 100, 300000, "ACTIVE", null, null)
        val b = a.copy(occurrenceKey = "attention-b@1", ruleId = "attention-b")
        fun quiet(nag: NagStateEntity, now: Long = 5000) = repeatAlertAcknowledged(
            store.firstPresentedAt(nag), nag.snoozedUntil, store.lastInteractionAt, 15, now)
        store.recordPresentations(listOf(a), listOf(a, b), 1000)
        store.recordInteraction(2000, force = true)
        assertTrue(quiet(a)); assertFalse(quiet(b))
        store.recordPresentations(listOf(a, b), listOf(a, b), 3000)
        assertEquals(1000L, ReminderAttentionStore(context).firstPresentedAt(a))
        assertTrue(quiet(a)); assertFalse(quiet(b))
        store.recordInteraction(4000, force = true)
        assertTrue(quiet(a)); assertTrue(quiet(b))
        val resumed = a.copy(snoozeCount = 1)
        store.recordPresentations(listOf(resumed), listOf(resumed, b), 5000)
        assertFalse(quiet(resumed)); assertTrue(quiet(b))
        assertNull(store.firstPresentedAt(a))
        store.recordInteraction(6000, force = true)
        assertTrue(quiet(resumed, 6000))
        assertFalse(quiet(resumed, 906000))
        store.recordPresentations(emptyList(), emptyList(), 906000)
        assertTrue(store.presentationSnapshot().isEmpty())
    }

    @Test fun unlockDetectionRespectsModeAndOffAndDoesNotNeedUsageAccess() {
        val settings = SettingsStore(context)
        val beforeSource = settings.interactionGraceSource
        val beforeMinutes = settings.interactionGraceMinutes
        val store = ReminderAttentionStore(context)
        var calls = 0
        val monitor = PhoneUseMonitor(context) { calls++ }
        try {
            monitor.start(); monitor.start()
            store.recordInteraction(1, force = true)
            settings.interactionGraceMinutes = 15
            settings.interactionGraceSource = "app"
            monitor.handleUnlock(2000)
            assertEquals(1L, store.lastInteractionAt)
            settings.interactionGraceSource = "unlock"
            monitor.handleUnlock(3000)
            assertEquals(3000L, store.lastInteractionAt)
            settings.interactionGraceSource = "usage"
            monitor.handleUnlock(4000)
            assertEquals(4000L, store.lastInteractionAt)
            settings.interactionGraceMinutes = 0
            monitor.handleUnlock(5000)
            assertEquals(4000L, store.lastInteractionAt)
            assertEquals(2, calls)
        } finally {
            monitor.stop(); monitor.stop()
            settings.interactionGraceSource = beforeSource
            settings.interactionGraceMinutes = beforeMinutes
        }
    }

    @Test fun usagePollingRequiresOptInAndAccessAndNeverTreatsAnOldEventAsNewUse() {
        val settings = SettingsStore(context)
        val beforeSource = settings.interactionGraceSource
        val beforeMinutes = settings.interactionGraceMinutes
        val attention = ReminderAttentionStore(context)
        var access = false
        var reported = 3000L
        val windows = mutableListOf<Pair<Long, Long>>()
        val monitor = PhoneUseMonitor(context, usageAccessAllowed = { access }, queryLatestUse = { start, end ->
            windows.add(start to end); reported
        }) {}
        try {
            settings.interactionGraceMinutes = 15
            settings.interactionGraceSource = "usage"
            attention.recordInteraction(2000, force = true)
            monitor.collectRecentUsage(4000)
            assertTrue(windows.isEmpty())
            access = true
            monitor.collectRecentUsage(4000)
            assertEquals(3000L, attention.lastInteractionAt)
            assertEquals(listOf(0L to 4000L), windows)
            reported = 2500 // History before the previous query must not renew a break.
            monitor.collectRecentUsage(5000)
            assertEquals(3000L, attention.lastInteractionAt)
            assertEquals(4000L to 5000L, windows.last())
            access = false
            monitor.collectRecentUsage(6000)
            assertEquals(2, windows.size)
            access = true
            settings.interactionGraceSource = "unlock"
            monitor.collectRecentUsage(7000)
            assertEquals(2, windows.size)
            settings.interactionGraceSource = "usage"
            settings.interactionGraceMinutes = 0
            monitor.collectRecentUsage(8000)
            assertEquals(2, windows.size)
        } finally {
            settings.interactionGraceSource = beforeSource
            settings.interactionGraceMinutes = beforeMinutes
        }
    }
}
