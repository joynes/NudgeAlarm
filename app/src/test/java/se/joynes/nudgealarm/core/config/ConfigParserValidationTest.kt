package se.joynes.nudgealarm.core.config

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ConfigParserValidationTest {
    private val base = "reminders:\n  - id: laundry\n    title: Laundry\n    schedule: '0 9 * * *'\n"

    @Test fun explicitOptionsAndWhitespaceInDurationsSurviveParsing() {
        val rule = ConfigParser.parse(base + "    nag_interval: ' 2H '\n    max_nags: 7\n    sound: bell\n    vibration: gentle\n    snooze_options: [' 5M ', '1h']\n").getOrThrow().reminders.single()
        assertEquals(2.hours, rule.nagInterval)
        assertEquals(7, rule.maxNags)
        assertEquals("bell", rule.sound)
        assertEquals("gentle", rule.vibration)
        assertEquals(listOf(5.minutes, 1.hours), rule.snoozeOptions)
    }

    @Test fun missingRequiredFieldsAndMalformedDurationsAreRejected() {
        for (line in listOf("  - id: laundry\n", "    title: Laundry\n", "    schedule: '0 9 * * *'\n")) {
            val yaml = if (line.startsWith("  -")) base.replace(line, "  - title: Laundry\n").replace("    title: Laundry\n", "") else base.replace(line, "")
            assertTrue(yaml, ConfigParser.parse(yaml).isFailure)
        }
        listOf("badm", "badh", "30s", "", "999999999999999m").forEach {
            assertTrue(it, ConfigParser.parse(base + "    nag_interval: '$it'\n").isFailure)
        }
        listOf("", "[]", "reminders: nope", "reminders: [5]", "[").forEach {
            assertTrue(it, ConfigParser.parse(it).isFailure)
        }
    }

    @Test fun omittedOptionalFieldsUseTheDocumentedDefaults() {
        val rule = ConfigParser.parse(base).getOrThrow().reminders.single()
        assertEquals(5.minutes, rule.nagInterval)
        assertEquals(listOf(5.minutes, 15.minutes), rule.snoozeOptions)
        assertEquals("alarm", rule.sound)
        assertEquals("strong", rule.vibration)
        assertFalse(rule.sticky)
        assertTrue(rule.enabled)
        assertNull(rule.placeId)
        assertTrue(ConfigParser.parse("reminders: []").getOrThrow().reminders.isEmpty())
    }
}
