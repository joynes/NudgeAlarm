package se.joynes.nudgealarm.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeHistoryTest {
    @Test fun startsWithThreeSimpleDefaults() {
        assertEquals(15, SnoozeHistory().lastMinutes)
        assertEquals(listOf(15, 60, 120), SnoozeHistory().suggestions)
    }

    @Test fun remembersLastChoiceAndRanksFrequentChoicesAboveRecentOnes() {
        val history = SnoozeHistory().record(97, 1).record(97, 2).record(45, 3)
        assertEquals(45, history.lastMinutes)
        assertEquals(listOf(45, 97), history.recent)
        assertEquals(listOf(97, 45, 15), history.suggestions)
    }

    @Test fun equallyFrequentChoicesPreferMostRecentAndDuplicatesStayUnique() {
        val history = SnoozeHistory().record(60, 1).record(15, 2).record(60, 3).record(15, 4)
        assertEquals(listOf(15, 60), history.recent)
        assertEquals(listOf(15, 60, 120), history.suggestions)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroDurationIsRejected() {
        SnoozeHistory().record(0, 1)
    }
}
