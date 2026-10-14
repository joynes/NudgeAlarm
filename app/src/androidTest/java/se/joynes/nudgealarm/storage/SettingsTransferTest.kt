package se.joynes.nudgealarm.storage

import android.content.Context
import android.net.Uri
import java.io.File
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        listOf("snooze_picker", "settings", "settings_discussions").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun importRestoresSettingsAndBothSnoozeVariationsButKeepsRuntimePauseAndHistory() {
        val settings = SettingsStore(context)
        settings.enableQuietModeUntil(123456789L)
        val snooze = SnoozePreferences(context)
        snooze.record(97)
        snooze.saveClockShortcut("old", "Old", 9, 0)
        val store = SettingsTransferStore(context)
        val incoming = SettingsConfiguration(vibrationEnabled = false, oldReminderRetentionMinutes = 90,
            minAlertIntervalMinutes = 37, snooze = SnoozeConfiguration(listOf(5, 25), 2, false, 0, 25, false, "clock"),
            clockShortcuts = listOf(SnoozeClockShortcut("lunch", "Lunch", 12, 0)), discussions = mapOf("recentCount" to "Hide distractions"))
        store.apply(incoming)
        assertEquals(incoming, store.snapshot())
        assertTrue(settings.quietMode)
        assertEquals(123456789L, settings.quietModeUntil)
        assertEquals(97, snooze.read().lastMinutes)
    }

    @Test fun invalidImportMakesNoPartialWrites() {
        val store = SettingsTransferStore(context)
        val before = store.snapshot()
        assertTrue(runCatching { store.apply(before.copy(vibrationEnabled = false, oldReminderRetentionMinutes = 0)) }.isFailure)
        assertEquals(before, store.snapshot())
    }

    @Test fun documentExportAndExternalEditImportRoundTrip() {
        val file = File.createTempFile("settings-transfer-test", ".json", context.cacheDir)
        try {
            val store = SettingsTransferStore(context)
            val uri = Uri.fromFile(file)
            store.exportToUri(uri)
            val original = store.readFromUri(uri)
            assertEquals(store.snapshot(), original)
            val root = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
            val entries = root["settings"].asJsonObject
            entries["quickPickCount"].asJsonObject.addProperty("value", 12)
            entries["quickPickCount"].asJsonObject.addProperty("discussion", "AI and I chose four rows.")
            file.writeText(root.toString())
            val edited = store.readFromUri(uri)
            assertEquals(9, store.snapshot().snooze.quickPickCount) // Reading/previewing never applies.
            store.apply(edited)
            store.exportToUri(uri)
            assertEquals(12, store.readFromUri(uri).snooze.quickPickCount)
            assertEquals("AI and I chose four rows.", store.readFromUri(uri).discussions["quickPickCount"])
            file.writeText("invalid JSON")
            assertTrue(runCatching { store.readFromUri(uri) }.isFailure)
            assertEquals(12, store.snapshot().snooze.quickPickCount)
        } finally { file.delete() }
    }

    @Test fun persistedConfigurationSurvivesNewStoreAndCorruptConfigurationFallsBackSafely() {
        val config = SnoozeConfiguration(listOf(10, 30), 5, false, 2, 30, false, "clock")
        SnoozePreferences(context).saveConfiguration(config)
        assertEquals(config, SnoozePreferences(context).configuration())
        context.getSharedPreferences("snooze_picker", Context.MODE_PRIVATE).edit().putString("configuration", "{\"quickPickCount\":999}").commit()
        assertEquals(SnoozeConfiguration(), SnoozePreferences(context).configuration())
    }
}
