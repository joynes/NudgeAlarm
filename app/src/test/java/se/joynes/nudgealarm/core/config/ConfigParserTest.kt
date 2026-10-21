package se.joynes.nudgealarm.core.config

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ConfigParserTest {

    @Test
    fun parseMinimalReminderUsesDefaults() {
        val result = ConfigParser.parse(
            """
            reminders:
              - id: morning
                title: Morning routine
                schedule: "0 8 * * *"
            """.trimIndent()
        )

        assertTrue(result.isSuccess)
        val reminder = result.getOrThrow().reminders.single()
        assertEquals("morning", reminder.id)
        assertEquals("Morning routine", reminder.title)
        assertEquals("0 8 * * *", reminder.schedule)
        assertEquals(5.minutes, reminder.nagInterval)
        assertEquals(100, reminder.maxNags)
        assertFalse(reminder.sticky)
        assertEquals("alarm", reminder.sound)
        assertEquals("strong", reminder.vibration)
        assertEquals(listOf(5.minutes, 15.minutes), reminder.snoozeOptions)
    }

    @Test
    fun parseCustomReminderPreservesAllSupportedValues() {
        val reminder = ConfigParser.parse(
            """
            reminders:
              - id: workout
                title: Workout
                schedule: "30 17 * * 1-5"
                nag_interval: 2h
                max_nags: 7
                sticky: true
                sound: ringtone
                vibration: gentle
                snooze_options:
                  - 10m
                  - 1h
            """.trimIndent()
        ).getOrThrow().reminders.single()

        assertEquals(2.hours, reminder.nagInterval)
        assertEquals(7, reminder.maxNags)
        assertTrue(reminder.sticky)
        assertEquals("ringtone", reminder.sound)
        assertEquals("gentle", reminder.vibration)
        assertEquals(listOf(10.minutes, 1.hours), reminder.snoozeOptions)
    }

    @Test
    fun parseSupportsMultipleRemindersInOrder() {
        val reminders = ConfigParser.parse(
            """
            reminders:
              - id: first
                title: First
                schedule: "0 8 * * *"
              - id: second
                title: Second
                schedule: "0 9 * * *"
            """.trimIndent()
        ).getOrThrow().reminders

        assertEquals(listOf("first", "second"), reminders.map { it.id })
    }

    @Test
    fun parseTrimsAndIgnoresCaseForDurations() {
        val reminder = ConfigParser.parse(
            """
            reminders:
              - id: duration
                title: Duration
                schedule: "* * * * *"
                nag_interval: " 2H "
                snooze_options:
                  - " 15M "
            """.trimIndent()
        ).getOrThrow().reminders.single()

        assertEquals(2.hours, reminder.nagInterval)
        assertEquals(listOf(15.minutes), reminder.snoozeOptions)
    }

    @Test
    fun parseFailsWhenRemindersKeyIsMissing() {
        val result = ConfigParser.parse("version: 1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing 'reminders' key") == true)
    }

    @Test
    fun parseFailsWhenRequiredReminderFieldIsMissing() {
        val result = ConfigParser.parse(
            """
            reminders:
              - id: incomplete
                schedule: "0 8 * * *"
            """.trimIndent()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing 'title'") == true)
    }

    @Test
    fun parseFailsForUnknownDurationUnit() {
        val result = ConfigParser.parse(
            """
            reminders:
              - id: bad-duration
                title: Bad duration
                schedule: "0 8 * * *"
                nag_interval: 30s
            """.trimIndent()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unknown duration format") == true)
    }

    @Test
    fun parseFailsForNonNumericDuration() {
        val result = ConfigParser.parse(
            """
            reminders:
              - id: bad-number
                title: Bad number
                schedule: "0 8 * * *"
                nag_interval: fivem
            """.trimIndent()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid duration") == true)
    }
}
