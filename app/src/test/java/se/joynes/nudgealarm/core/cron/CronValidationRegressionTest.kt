package se.joynes.nudgealarm.core.cron

import org.junit.Assert.*
import org.junit.Test

class CronValidationRegressionTest {
    @Test fun reversedAndMalformedRangesAreRejectedInsteadOfSilentlyChangingTheSchedule() {
        for (text in listOf("30-10/2", "1-2-3/2", "1,8-3", "1,2-3-4")) {
            val result = runCatching { CronField.parse(text, 0, 59) }
            assertTrue("Accepted invalid cron field: $text", result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun validSteppedAndMixedFieldsMatchExactlyTheirExpectedValues() {
        for ((text, expected) in listOf("5-15/5" to setOf(5, 10, 15), "5/20" to setOf(5, 25, 45),
            "1-3,8,10-12" to setOf(1, 2, 3, 8, 10, 11, 12))) {
            val field = CronField.parse(text, 0, 59)
            assertEquals(expected, (0..59).filter { field.matches(it) }.toSet())
            for (start in 0..60) assertEquals(expected.filter { it >= start }.minOrNull(), field.nextMatch(start, 59))
        }
    }

    @Test fun malformedPartsAndOutOfRangeValuesFailWithValidationErrors() {
        for (text in listOf("*/0", "*/bad", "a-3/2", "1-a/2", "a/2", "60/2", "*/1/2", "1,60", "1,a-3", "1,2-a", "a-3", "1-a")) {
            assertTrue(text, runCatching { CronField.parse(text, 0, 59) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
