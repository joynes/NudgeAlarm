package se.joynes.nudgealarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.joynes.nudgealarm.core.event.Event

class QuestEditGesturesTest {
    @Test fun questEventsUseTheirStructuredRuleId() {
        val events = listOf(
            Event.NextEventComputed(ruleId="water",triggerTime=1L),
            Event.ReminderTriggered(ruleId="water"),
            Event.NagFired(ruleId="water",nagCount=1,maxNags=2),
            Event.UserTappedDone(ruleId="water"),
            Event.UserTappedSnooze(ruleId="water",duration="30m"),
            Event.UserTappedCancel(ruleId="water"),
            Event.SnoozeEnded(ruleId="water"),
            Event.CronParsed(ruleId="water",schedule="0 8 * * *",nextTime="08:00")
        )
        events.forEach { assertEquals("water",it.questRuleId()) }
    }

    @Test fun unrelatedEventsNeverInferAnIdFromTheirText() {
        assertNull(Event.Debug(detail="REMINDER_TRIGGERED: water").questRuleId())
        assertNull(Event.ServiceStarted().questRuleId())
        assertNull(Event.NavigatedTo(screen="water").questRuleId())
    }
}
