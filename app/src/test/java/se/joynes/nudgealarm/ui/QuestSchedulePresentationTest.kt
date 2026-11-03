package se.joynes.nudgealarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.*

class QuestSchedulePresentationTest {
    @Test fun readableSchedulesPreserveTheDaysAndMonthlyRestrictions() {
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        days.forEachIndexed { index, day -> assertEquals("$day 09:05", cronToReadable("5 9 * * $index")) }
        assertEquals("Every day 09:05", cronToReadable("5 9 * * *"))
        assertEquals("Weekdays 09:05", cronToReadable("5 9 * * 1-5"))
        assertEquals("Weekends 09:05", cronToReadable("5 9 * * 0,6"))
        assertEquals("Mon, Wed, Fri 09:05", cronToReadable("5 9 * * 1,3,5"))
        assertEquals("Day 1,15 09:05", cronToReadable("5 9 1,15 * *"))
        assertEquals("Day 15 Jan,Jun 09:05", cronToReadable("5 9 15 1,6 *"))
        for (raw in listOf("bad", "once:invalid", "*/5 9 * * *", "5 */2 * * *", "5 9 1 * 2")) {
            assertEquals(raw, cronToReadable(raw))
        }
        assertTrue(cronToReadable("once:1800000000000").startsWith("One-time "))
    }

    @Test fun frequencyAccentsDistinguishFrequentDailyWeeklyMonthlyAndRareQuests() {
        assertEquals(MegadriveRed, frequencyColor("*/10 * * * *"))
        assertEquals(MegadriveOrange, frequencyColor("0 */2 * * *"))
        assertEquals(MegadriveGold, frequencyColor("0 9 * * *"))
        assertEquals(MegadriveCyan, frequencyColor("0 9 * * 1,4"))
        assertEquals(MegadriveGreen, frequencyColor("0 9 1 * *"))
        assertEquals(MegadrivePurple, frequencyColor("0 9 1 1 *"))
        assertEquals(MegadrivePurple, frequencyColor("once:1800000000000"))
        assertEquals(MegadriveCyan, frequencyColor("invalid"))
    }
}
