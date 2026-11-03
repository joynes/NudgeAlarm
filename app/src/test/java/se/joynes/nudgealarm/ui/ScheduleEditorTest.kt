package se.joynes.nudgealarm.ui

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class ScheduleEditorTest {
    @Test fun allWeeklyDayCombinationsRoundTripWithoutChangingTimeOrDays() {
        for (mask in 1..127) {
            val days = (0..6).filter { mask and (1 shl it) != 0 }.toSet()
            for ((hour, minute) in listOf(0 to 0, 23 to 59, 9 to 30)) {
                assertEquals(Triple(hour, minute, days), parseCronSchedule(buildCronSchedule(hour, minute, days)))
            }
        }
        assertEquals(Triple(9, 0, (0..6).toSet()), parseCronSchedule(buildCronSchedule(9, 0, emptySet())))
    }

    @Test fun onceSchedulesUseTheLocalTimeAndCorrectWeekday() {
        val date = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 3, 9, 47); set(Calendar.SECOND, 0) }
        assertEquals(Triple(9, 47, setOf(2)), parseCronSchedule("once:${date.timeInMillis}"))
        assertNull(parseCronSchedule("once:invalid"))
    }

    @Test fun invalidSimpleSchedulesFallBackToAdvancedEditing() {
        listOf("", "0 9 * *", "* 9 * * *", "0 bad * * *", "0 9 * * 1-a", "0 9 * * 1-2-3", "0 9 * * bad").forEach {
            assertNull(it, parseCronSchedule(it))
        }
    }

    @Test fun monthlySchedulesSortSelectionsAndHavePredictableDefaults() {
        assertEquals("47 9 1 * *", buildMonthlyCronSchedule(9, 47, emptySet(), emptySet()))
        assertEquals("0 23 1,15,31 2,12 *", buildMonthlyCronSchedule(23, 0, setOf(31, 1, 15), setOf(12, 2)))
        assertEquals("0 0 3 * *", buildMonthlyCronSchedule(0, 0, setOf(3), (1..12).toSet()))
        assertEquals((1..31).toSet(), parseCronFieldToSet("*", 1..31))
        assertEquals(setOf(1, 2, 3, 7), parseCronFieldToSet("1-3,7", 1..31))
        assertEquals(emptySet<Int>(), parseCronFieldToSet("bad,1-a,a-3", 1..31))
    }

    @Test fun correctEditorIsChosenForWeeklyMonthlyOnceAndAdvancedSchedules() {
        assertEquals(ScheduleMode.VECKA, detectScheduleMode(null))
        assertEquals(ScheduleMode.VECKA, detectScheduleMode("invalid"))
        assertEquals(ScheduleMode.ENGANG, detectScheduleMode("once:123"))
        assertEquals(ScheduleMode.MANAD, detectScheduleMode("0 9 15 * *"))
        assertEquals(ScheduleMode.CRON, detectScheduleMode("0 9 15 * 1"))
        assertEquals(ScheduleMode.VECKA, detectScheduleMode("0 9 * * 1-5"))
    }
}
