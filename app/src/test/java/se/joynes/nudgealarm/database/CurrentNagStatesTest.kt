package se.joynes.nudgealarm.database

import org.junit.Assert.*
import org.junit.Test

class CurrentNagStatesTest {
    private fun occurrence(time: Long, status: String = "ACTIVE", until: Long? = null, id: String = "quest") =
        NagStateEntity("$id@$time", id, time, "Test quest", time + 1, 1, 100, 300000, status, until, time)

    @Test fun reportedOldActiveOccurrenceCannotOverrideLatestSnoozeRegardlessOfOrder() {
        val old = occurrence(1792342800000)
        val paused = occurrence(1792951200000, "SNOOZED", 1793302205372)
        val now = 1793261896481L
        assertEquals(listOf(paused), currentNagStates(listOf(paused, old), now))
        assertEquals(listOf(paused), currentNagStates(listOf(old, paused), now))
        assertTrue(currentNagStates(listOf(old, paused), now).none { it.status == "ACTIVE" })
    }

    @Test fun newRecurrenceCannotBreakAnExistingQuestPauseAndOtherQuestsStillAppear() {
        val paused = occurrence(1000, "SNOOZED", 10000)
        val fresh = occurrence(2000)
        val other = occurrence(3000, id = "other")
        assertEquals(setOf(paused, other), currentNagStates(listOf(fresh, paused, other), 5000).toSet())
        assertEquals(setOf(fresh, other), currentNagStates(listOf(fresh, paused, other), 10000).toSet())
    }

    @Test fun expiredSnoozeIsDueOnceAndFinishedHistoryCannotResurface() {
        val old = occurrence(1000)
        val expired = occurrence(2000, "SNOOZED", 3000)
        val done = occurrence(4000, "COMPLETED")
        assertEquals(listOf(expired), currentNagStates(listOf(old, expired, done), 5000))
        assertEquals(emptyList<NagStateEntity>(), currentNagStates(listOf(done), 5000))
    }
}
