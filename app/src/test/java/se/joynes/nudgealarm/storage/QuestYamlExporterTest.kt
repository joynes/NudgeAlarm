package se.joynes.nudgealarm.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.nudgealarm.core.config.ConfigParser
import se.joynes.nudgealarm.database.ReminderEntity

class QuestYamlExporterTest {
    @Test
    fun exportPreservesSelectionOrderAndSettings() {
        val reminders = listOf(
            ReminderEntity(
                id = "second",
                title = "Second \"quoted\" quest",
                schedule = "0 12 * * *",
                nagIntervalMinutes = 10,
                maxNags = 7,
                sticky = true,
                enabled = false,
                placeId = "home"
            ),
            ReminderEntity(id = "first", title = "First quest", schedule = "0 8 * * 1-5")
        )

        val yaml = QuestYamlExporter.export(reminders)
        val parsed = ConfigParser.parse(yaml).getOrThrow().reminders

        assertEquals(listOf("second", "first"), parsed.map { it.id })
        assertEquals("Second \"quoted\" quest", parsed.first().title)
        assertEquals(10, parsed.first().nagInterval.inWholeMinutes)
        assertEquals(7, parsed.first().maxNags)
        assertTrue(parsed.first().sticky)
        assertFalse(parsed.first().enabled)
        assertEquals("home", parsed.first().placeId)
    }
}
