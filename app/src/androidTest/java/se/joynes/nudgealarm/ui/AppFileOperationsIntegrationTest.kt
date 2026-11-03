package se.joynes.nudgealarm.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.joynes.nudgealarm.NudgeAlarmApp
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.*
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import java.io.File

/** Android owns the document picker UI; deliver real provider URIs through its result contract. */
@RunWith(Parameterized::class)
class AppFileOperationsIntegrationTest(private val operation: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun operations() = listOf(
            "save_yaml", "load_yaml", "backup", "debug_report", "settings_export", "selected_export",
            "restore_backup", "restore_invalid", "copy_yaml", "paste_yaml", "rename", "share_yaml",
            "settings_import", "settings_invalid", "pause_all"
        ).map { arrayOf(it) }
    }
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }

    @Test fun documentResultFlowsThroughTheAppToARealReadableFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NagDatabase.getInstance(context)
        val previous = runBlocking { db.reminderDao().getAll() }
        val appState = AppStateStore(context)
        val transfer = SettingsTransferStore(context)
        val originalSettings = transfer.snapshot()
        val quiet = SettingsStore(context).quietMode
        val quietUntil = SettingsStore(context).quietModeUntil
        if (operation == "pause_all") {
            SettingsStore(context).quietMode = false
            SnoozePreferences(context).apply {
                saveConfiguration(originalSettings.snooze.copy(durationPresets = listOf(15), adaptive = false, defaultMode = "duration"))
                replaceClockShortcuts(emptyList())
            }
        }
        val previousId = appState.getCurrentGameId()
        val previousName = appState.getCurrentGameName()
        val previousRunning = appState.wasServiceRunning()
        val input = File.createTempFile("load_fixture", ".yaml", context.cacheDir)
        val output = File.createTempFile("output_fixture", ".txt", context.cacheDir)
        input.writeText("reminders:\n  - id: imported_fixture\n    title: Imported quest\n    schedule: '0 9 * * *'\n")
        val rule = ReminderEntity("file_fixture", "Laundry \"delicate\"", "0 9 * * *")
        runBlocking { db.reminderDao().deleteAll(); db.reminderDao().insert(rule) }
        appState.setCurrentGame("file_fixture", "File fixture")
        appState.setServiceWasRunning(false)
        if (operation == "restore_backup") {
            runBlocking {
                DataExportStore(context).exportToUri(android.net.Uri.fromFile(input)).getOrThrow()
                db.reminderDao().update(rule.copy(title = "Changed after backup"))
            }
        } else if (operation == "restore_invalid") input.writeText("{}")
        if (operation == "settings_import") input.writeText(SettingsConfigurationCodec.encode(originalSettings.copy(interactionGraceMinutes = 83)))
        if (operation == "settings_invalid") input.writeText("{}")
        val requests = mutableListOf<Intent>()
        val startedActivities = mutableListOf<Intent>()
        val uiContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { startedActivities.add(intent) }
        }
        val internal = File(context.filesDir, "game_file_fixture.yaml")
        val oldInternal = internal.takeIf { it.exists() }?.readBytes()
        if (operation == "share_yaml") internal.writeText(input.readText())
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, value: I, options: ActivityOptionsCompat?) {
                val intent = contract.createIntent(context, value)
                requests.add(intent)
                val target = if (intent.action == Intent.ACTION_OPEN_DOCUMENT) input else output
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.setContent { NudgeAlarmTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, LocalContext provides uiContext) { NudgeAlarmApp() }
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText(">> GAME LOADED").fetchSemanticsNodes().isNotEmpty() }
            when (operation) {
                "pause_all" -> {
                    compose.onNodeWithContentDescription("Mute").performClick()
                    compose.onNodeWithTag("quick_pick_15").performScrollTo().performClick()
                }
                "selected_export" -> {
                    click("EDIT"); click("SELECT")
                    compose.onNodeWithTag("select_file_fixture").performClick()
                    click("EXPORT 1")
                }
                "settings_export", "settings_import", "settings_invalid" -> {
                    click("OPTIONS")
                    compose.onNodeWithTag("open-settings-transfer").performScrollTo().performClick()
                    compose.onNodeWithTag(if (operation == "settings_export") "export-settings" else "import-settings").performScrollTo().performClick()
                }
                else -> {
                    compose.onNodeWithContentDescription("Open menu").performClick()
                    when (operation) {
                        "save_yaml" -> { click("> SAVE / SHARE"); click(">> SAVE TO FILE") }
                        "load_yaml" -> click("> LOAD FROM FILE")
                        "rename" -> {
                            click("> RENAME GAME")
                            compose.onNodeWithText("Game Name").performTextReplacement("Renamed from menu")
                            click("SAVE")
                        }
                        "copy_yaml", "paste_yaml", "share_yaml" -> {
                            click("> SAVE / SHARE")
                            when (operation) {
                                "copy_yaml" -> click(">> COPY TO CLIPBOARD")
                                "share_yaml" -> click(">> SHARE")
                                else -> {
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    compose.runOnIdle { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("YAML", input.readText())) }
                                    click(">> PASTE FROM CLIPBOARD"); click("IMPORT NOW")
                                }
                            }
                        }
                        "backup" -> { click("> EXPORT DATA"); click("EXPORT") }
                        "restore_backup", "restore_invalid" -> {
                            click("> IMPORT DATA")
                            compose.onNodeWithText("!! VARNING !!").assertIsDisplayed()
                            assertEquals(if (operation == "restore_backup") "Changed after backup" else rule.title,
                                runBlocking { db.reminderDao().getById(rule.id)!!.title })
                            click("JA, IMPORTERA")
                        }
                        "debug_report" -> {
                            compose.onNodeWithText("> EVENT LOG", substring = true).performClick()
                            click("EXPORT DEBUG REPORT")
                        }
                    }
                }
            }
            if (operation == "pause_all") {
                compose.waitUntil(15000) { SettingsStore(context).quietMode && SettingsStore(context).quietModeUntil > System.currentTimeMillis() }
                assertTrue(SettingsStore(context).quietModeUntil - System.currentTimeMillis() in 14 * 60000L..15 * 60000L)
                compose.onNodeWithContentDescription("Unmute").performClick()
                assertFalse(SettingsStore(context).quietMode)
                assertEquals(0L, SettingsStore(context).quietModeUntil)
            } else if (operation == "settings_import") {
                compose.waitUntil(15000) { compose.onAllNodesWithText("REVIEW SETTINGS IMPORT").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(originalSettings, transfer.snapshot())
                click("CANCEL")
                assertEquals(originalSettings, transfer.snapshot())
                compose.onNodeWithTag("import-settings").performScrollTo().performClick()
                compose.waitUntil(15000) { compose.onAllNodesWithTag("confirm-settings-import").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("confirm-settings-import").performClick()
                compose.waitUntil(15000) { SettingsStore(context).interactionGraceMinutes == 83 }
                compose.waitUntil(15000) { compose.onAllNodesWithText("Settings imported. Quests, places and active pauses were kept.").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(rule, runBlocking { db.reminderDao().getById(rule.id) })
            } else if (operation == "settings_invalid") {
                compose.waitUntil(15000) { compose.onAllNodesWithText("Import rejected; nothing changed:", substring = true).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(originalSettings, transfer.snapshot())
            } else if (operation == "rename") {
                compose.waitUntil(15000) { appState.getCurrentGameName() == "Renamed from menu" }
            } else if (operation == "copy_yaml") {
                compose.waitUntil(15000) { compose.onAllNodesWithText("COPIED!").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("COPIED!").assertIsDisplayed()
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                compose.runOnIdle { assertEquals(rule.title, ConfigLoader(context).loadFromString(clipboard.primaryClip!!.getItemAt(0).text.toString()).getOrThrow().reminders.single().title) }
            } else if (operation == "paste_yaml") {
                compose.waitUntil(15000) { compose.onAllNodesWithText("IMPORT SUCCESS!").fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(15000) { ReminderService.isRunning && ReminderService.currentConfig?.reminders?.any { it.id == "imported_fixture" } == true }
                assertEquals("Imported quest", runBlocking { db.reminderDao().getById("imported_fixture")!!.title })
            } else if (operation == "share_yaml") {
                val chooser = startedActivities.single()
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, share.action)
                assertEquals("application/x-yaml", share.type)
                val uri = share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
                assertEquals("Imported quest", ConfigLoader(context).loadFromUri(uri).getOrThrow().reminders.single().title)
            } else if (operation == "restore_backup") {
                compose.waitUntil(15000) { runBlocking { db.reminderDao().getById(rule.id)?.title == rule.title } }
                compose.waitUntil(15000) { ReminderService.isRunning && ReminderService.currentConfig?.reminders?.any { it.id == rule.id } == true }
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, requests.single().action)
            } else if (operation == "restore_invalid") {
                compose.waitUntil(15000) { EventLogStore(context).getAll().any { it.message.contains("Import failed:") } }
                assertEquals(rule.title, runBlocking { db.reminderDao().getById(rule.id)!!.title })
            } else if (operation == "load_yaml") {
                compose.waitUntil(15000) { runBlocking { db.reminderDao().getById("imported_fixture") != null } }
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, requests.single().action)
                assertEquals("Imported quest", runBlocking { db.reminderDao().getById("imported_fixture")!!.title })
                compose.waitUntil(15000) { ReminderService.isRunning && ReminderService.currentConfig?.reminders?.any { it.id == "imported_fixture" } == true }
            } else {
                compose.waitUntil(15000) { output.length() > 0 }
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, requests.single().action)
                when (operation) {
                    "save_yaml", "selected_export" -> assertEquals(rule.title, ConfigLoader(context).loadFromUri(android.net.Uri.fromFile(output)).getOrThrow().reminders.single().title)
                    "settings_export" -> assertEquals(SettingsTransferStore(context).snapshot(), SettingsConfigurationCodec.decode(output.readText()))
                    "debug_report" -> assertTrue(output.readText().contains("nudgealarm-debug"))
                    "backup" -> assertTrue(output.readText().contains("diagnostics"))
                }
            }
        } finally {
            context.stopService(Intent(context, ReminderService::class.java))
            compose.waitUntil(10000) { !ReminderService.isRunning }
            runBlocking { db.reminderDao().deleteAll(); db.reminderDao().insertAll(previous) }
            appState.setCurrentGame(previousId, previousName)
            appState.setServiceWasRunning(previousRunning)
            transfer.apply(originalSettings)
            if (operation == "pause_all") {
                se.joynes.nudgealarm.notification.QuietModeController.disable(context, refreshService = false)
                SettingsStore(context).quietMode = quiet
                SettingsStore(context).quietModeUntil = quietUntil
            }
            input.delete(); output.delete()
            if (oldInternal != null) internal.writeBytes(oldInternal) else internal.delete()
        }
    }
}
