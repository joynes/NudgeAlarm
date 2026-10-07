package se.joynes.nudgealarm.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.core.event.Event

class EventCodecTest {
    private val gson = GsonBuilder().registerTypeHierarchyAdapter(Event::class.java, EventLogStore.EventAdapter()).create()

    @Test fun everyEventRoundTripsWithStableTypeAndFields() {
        val events = listOf(Event.ServiceStarted(1), Event.ServiceStopped(1, "user"), Event.ServiceKilled(1),
            Event.ConfigLoaded(1, 2), Event.ConfigReloaded(1, 3), Event.ConfigError(1, "error"),
            Event.NextEventComputed(1, "home", 42), Event.WaitingUntil(1, 42, 10), Event.ReminderTriggered(1, "home"),
            Event.NagFired(1, "home", 2, 5), Event.UserTappedDone(1, "home"), Event.UserTappedSnooze(1, "home", "1h"),
            Event.UserTappedCancel(1, "home"), Event.SnoozeEnded(1, "home"), Event.DeviceBooted(1),
            Event.BatteryOptimizationChanged(1, true), Event.AppForegrounded(1), Event.AppBackgrounded(1),
            Event.UiAction(1, "export"), Event.NavigatedTo(1, "log"), Event.ConfigFileSelected(1, "file"),
            Event.TestConfigCreated(1, "test"), Event.SchedulerLoopIteration(1, "poll"),
            Event.CronParsed(1, "home", "* * * * *", "later"), Event.Debug(1, "detail"), Event.Crash(1, "Exception", "bad", "stack"))
        assertEquals(26, events.map(::stableEventType).distinct().size)
        events.forEach { event ->
            val json = gson.toJson(event, Event::class.java)
            assertEquals(stableEventType(event), JsonParser.parseString(json).asJsonObject["type"].asString)
            assertEquals(event, gson.fromJson(json, Event::class.java))
        }
    }

    @Test fun obfuscatedLegacyEntryPreservesEvidence() {
        val decoded = gson.fromJson("""{"type":"j","timestamp":42,"ruleId":"washer","detail":"home=false"}""", Event::class.java)
        assertEquals(42L, decoded.timestamp)
        assertTrue(decoded.message.contains("washer"))
        assertTrue(decoded.message.contains("home=false"))
    }
}
