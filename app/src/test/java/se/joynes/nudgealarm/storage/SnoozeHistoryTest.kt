package se.joynes.nudgealarm.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeHistoryTest {
    @Test fun startsWithNineDefaultsInAscendingOrder() {
        assertEquals(15, SnoozeHistory().lastMinutes)
        assertEquals(listOf(5, 10, 15, 30, 60, 120, 240, 480, 1440), SnoozeHistory().suggestions)
    }

    @Test fun remembersLastChoiceAndRanksFrequentChoicesAboveRecentOnes() {
        val history = SnoozeHistory().record(97, 1).record(97, 2).record(45, 3)
        assertEquals(45, history.lastMinutes)
        assertEquals(listOf(45, 97), history.recent)
        assertEquals(listOf(5, 10, 15, 30, 45, 60, 97, 120, 240), history.suggestions)
    }

    @Test fun equallyFrequentChoicesPreferMostRecentAndDuplicatesStayUnique() {
        val history = SnoozeHistory().record(60, 1).record(15, 2).record(60, 3).record(15, 4)
        assertEquals(listOf(15, 60), history.recent)
        assertEquals(listOf(5, 10, 15, 30, 60, 120, 240, 480, 1440), history.suggestions)
    }

    @Test fun selectsTheNineMostUsefulHistoryChoicesThenSortsByDuration() {
        val history = (1..12).fold(SnoozeHistory()) { previous, value -> previous.record(value * 7, value.toLong()) }
            .record(7, 100).record(7, 101)
        assertEquals(listOf(7, 35, 42, 49, 56, 63, 70, 77, 84), history.suggestions)
        assertEquals(9, history.suggestions.distinct().size)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroDurationIsRejected() {
        SnoozeHistory().record(0, 1)
    }
}
