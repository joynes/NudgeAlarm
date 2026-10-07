package se.joynes.nudgealarm.storage

import android.content.Context
import android.net.Uri
import java.io.File
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.nudgealarm.core.event.Event
import se.joynes.nudgealarm.location.GeofenceStateStore
import se.joynes.nudgealarm.location.GeofenceSpec

@RunWith(AndroidJUnit4::class)
class DebugExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reportContainsPresenceHistoryAndLogsWithoutResettingPresence() = runBlocking {
        val store = GeofenceStateStore(context)
        val spec = GeofenceSpec("debug-home", "position", 59.0, 18.0, 150)
        store.replace(listOf(spec), "debug-generation")
        store.ready("debug-generation")
        store.transition(listOf("debug-generation:0"), true)
        EventLogStore(context).add(Event.Debug(detail = "debug export marker"))
        try {
            val snapshot = DebugExportStore(context).snapshot()
            assertEquals("nudgealarm-debug", snapshot["format"].asString)
            assertTrue(snapshot["geofenceState"].asJsonObject["inside_debug-home"].asBoolean)
            assertTrue(snapshot["geofenceHistory"].toString().contains("enter"))
            assertTrue(snapshot["events"].toString().contains("debug export marker"))
            assertTrue(snapshot.has("questLocationDecisions"))
            assertTrue(snapshot.has("permissions"))
            assertTrue(snapshot.has("nagStates"))
            assertTrue(store.allows(spec))
        } finally { store.invalidate("Test finished") }
    }

    @Test fun legacyAndMalformedEntriesDoNotEraseOtherEvents() {
        context.getSharedPreferences("event_log", Context.MODE_PRIVATE).edit().putString("events",
            """[{"type":"j","timestamp":1,"ruleId":"washer"},{"type":"ConfigLoaded","timestamp":2},{"type":"Debug","timestamp":3,"detail":"valid"}]""").commit()
        val log = EventLogStore(context)
        assertEquals(3, log.getAll().size)
        assertTrue(log.getAll()[0].message.contains("washer"))
        assertEquals("DEBUG: valid", log.getAll()[2].message)
        log.add(Event.ServiceStarted(4))
        assertEquals(4, log.getAll().size)
        val raw = context.getSharedPreferences("event_log", Context.MODE_PRIVATE).getString("events", "[]")!!
        assertEquals("ServiceStarted", JsonParser.parseString(raw).asJsonArray.last().asJsonObject["type"].asString)
        log.clear()
    }

    @Test fun normalExportIncludesDiagnosticsAndImportDoesNotRestorePresence() = runBlocking {
        val store = GeofenceStateStore(context)
        val spec = GeofenceSpec("backup-home", "position", 59.0, 18.0, 150)
        store.replace(listOf(spec), "backup-generation")
        store.ready("backup-generation")
        store.transition(listOf("backup-generation:0"), true)
        val file = File.createTempFile("debug-export-test", ".json", context.cacheDir)
        try {
            val export = DataExportStore(context)
            assertTrue(export.exportToUri(Uri.fromFile(file)).isSuccess)
            val json = JsonParser.parseString(file.readText()).asJsonObject
            assertTrue(json["diagnostics"].asJsonObject["geofenceState"].asJsonObject["inside_backup-home"].asBoolean)
            store.transition(listOf("backup-generation:0"), false)
            assertTrue(export.importFromUri(Uri.fromFile(file)).isSuccess)
            assertFalse(store.allows(spec))
        } finally {
            file.delete()
            store.invalidate("Test finished")
        }
    }
}
