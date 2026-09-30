package se.joynes.nudgealarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnoozePickerTest {
    @Test fun futureTimeRoundsUpToNextMinute() {
        assertEquals(1, minutesUntilSnooze(60_001L, 60_000L))
        assertEquals(2, minutesUntilSnooze(120_001L, 60_000L))
    }

    @Test fun pastOrPresentTimeIsRejected() {
        assertNull(minutesUntilSnooze(60_000L, 60_000L))
        assertNull(minutesUntilSnooze(59_999L, 60_000L))
    }
}
