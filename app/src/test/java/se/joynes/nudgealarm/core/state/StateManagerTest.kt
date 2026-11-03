package se.joynes.nudgealarm.core.state

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class StateManagerTest {
    @Test fun missingRulesAreSafeAndDoNotCreateState() {
        val manager = StateManager()
        manager.markDone("missing")
        manager.snooze("missing", 5.minutes)
        manager.clearSnooze("missing")
        assertEquals(0, manager.incrementNag("missing"))
        assertFalse(manager.isDone("missing"))
        assertFalse(manager.isSnoozed("missing"))
        assertNull(manager.getSnoozedUntil("missing"))
        assertNull(manager.getState("missing"))
    }

    @Test fun independentRulesCanBeSnoozedCompletedResetAndCleared() {
        val manager = StateManager()
        manager.initState("laundry", 123)
        manager.initState("dishes", 456)
        assertEquals(123L, manager.getState("laundry")!!.occurrenceTime)
        assertEquals(1, manager.incrementNag("laundry"))
        assertEquals(2, manager.incrementNag("laundry"))
        val before = System.currentTimeMillis()
        manager.snooze("laundry", 5.minutes)
        assertTrue(manager.isSnoozed("laundry"))
        assertTrue(manager.getSnoozedUntil("laundry")!! >= before + 300000)
        assertFalse(manager.isSnoozed("dishes"))
        manager.clearSnooze("laundry")
        assertFalse(manager.isSnoozed("laundry"))
        manager.markDone("laundry")
        assertTrue(manager.isDone("laundry"))
        assertFalse(manager.isDone("dishes"))
        manager.initState("laundry", 789)
        assertFalse(manager.isDone("laundry"))
        assertEquals(0, manager.getState("laundry")!!.nagCount)
        manager.clear("laundry")
        assertNull(manager.getState("laundry"))
        assertNotNull(manager.getState("dishes"))
        manager.clearAll()
        assertNull(manager.getState("dishes"))
    }
}
