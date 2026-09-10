package se.joynes.nudgealarm.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigParserPlaceTest {
    @Test
    fun parsesOptionalPlaceId() {
        val config = ConfigParser.parse(
            """
            reminders:
              - id: wash_dishes
                title: "Wash dishes"
                schedule: "0 20 * * *"
                place_id: "home-id"
            """.trimIndent()
        ).getOrThrow()

        assertEquals("home-id", config.reminders.single().placeId)
    }

    @Test
    fun oldConfigDefaultsToAnywhere() {
        val config = ConfigParser.parse(
            """
            reminders:
              - id: drink_water
                title: "Drink water"
                schedule: "0 10 * * *"
            """.trimIndent()
        ).getOrThrow()

        assertNull(config.reminders.single().placeId)
    }
}
