package se.joynes.nudgealarm.ui

import android.app.Activity
import android.content.Context
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
            "save_yaml", "load_yaml", "backup", "debug_report", "settings_export", "selected_export"
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
        val requests = mutableListOf<Intent>()
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
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { NudgeAlarmApp() }
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithText(">> GAME LOADED").fetchSemanticsNodes().isNotEmpty() }
            when (operation) {
                "selected_export" -> {
                    click("EDIT"); click("SELECT")
                    compose.onNodeWithTag("select_file_fixture").performClick()
                    click("EXPORT 1")
                }
                "settings_export" -> {
                    click("OPTIONS")
                    compose.onNodeWithTag("open-settings-transfer").performScrollTo().performClick()
                    compose.onNodeWithTag("export-settings").performClick()
                }
                else -> {
                    compose.onNodeWithContentDescription("Open menu").performClick()
                    when (operation) {
                        "save_yaml" -> { click("> SAVE / SHARE"); click(">> SAVE TO FILE") }
                        "load_yaml" -> click("> LOAD FROM FILE")
                        "backup" -> { click("> EXPORT DATA"); click("EXPORT") }
                        "debug_report" -> {
                            compose.onNodeWithText("> EVENT LOG", substring = true).performClick()
                            click("EXPORT DEBUG REPORT")
                        }
                    }
                }
            }
            if (operation == "load_yaml") {
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
            input.delete(); output.delete()
        }
    }
}
