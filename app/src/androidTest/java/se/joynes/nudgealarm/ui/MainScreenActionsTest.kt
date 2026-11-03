package se.joynes.nudgealarm.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.joynes.nudgealarm.storage.SavedGame
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

@RunWith(Parameterized::class)
class MainScreenActionsTest(private val action: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases() = listOf(
            "share", "save", "copy", "paste_ok", "paste_failure", "rename", "new_game",
            "load_file", "export_data", "import_data", "event_log", "preset", "saved_game",
            "delete_saved", "delete_quest", "complete_all"
        ).map { arrayOf(it) }
    }
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<String>()
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        // Confirm buttons live outside the scrollable dialog body.
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }

    @Test fun actionReachesTheCorrectCallbackAndDestructiveActionsRequireConfirmation() {
        compose.setContent { NudgeAlarmTheme { Fixture() } }
        when (action) {
            "load_file" -> { click("> LOAD FROM FILE"); assertEquals(listOf("file"), events) }
            "export_data" -> { click("> EXPORT DATA"); assertEquals(listOf("export"), events) }
            "import_data" -> { click("> IMPORT DATA"); assertEquals(listOf("import"), events) }
            "event_log" -> { click("> EVENT LOG [0]"); assertEquals(listOf("log"), events) }
            "rename" -> {
                click("> RENAME GAME")
                compose.onNodeWithText("Game Name").performTextReplacement("  Renamed  ")
                click("SAVE")
                assertEquals(listOf("rename:Renamed"), events)
            }
            "preset", "saved_game", "new_game", "delete_saved" -> {
                click("> SELECT GAME")
                when (action) {
                    "preset" -> { click(PRESET_CONFIGS.first().name); assertEquals(listOf("preset:fitness_focus"), events) }
                    "saved_game" -> { click("> My saved game"); assertEquals(listOf("load:saved"), events) }
                    "delete_saved" -> {
                        click("X"); assertTrue(events.isEmpty()); click("< CANCEL")
                        click("X"); click("DELETE"); assertEquals(listOf("delete:saved"), events)
                    }
                    else -> {
                        click(">> NEW GAME")
                        compose.onNodeWithText("Game Name").performTextReplacement("  New routines  ")
                        click("CREATE")
                        assertEquals(listOf("new:New routines", "edit-list"), events)
                    }
                }
            }
            "delete_quest" -> {
                compose.onNodeWithTag("active-quest-a").performScrollTo().performClick()
                click("DELETE PERMANENTLY"); assertTrue(events.isEmpty()); click("< CANCEL")
                compose.onNodeWithTag("active-quest-a").performScrollTo().performClick()
                click("DELETE PERMANENTLY"); click("DELETE")
                assertEquals(listOf("remove:a"), events)
            }
            "complete_all" -> {
                click("COMPLETE ALL"); assertTrue(events.isEmpty())
                compose.onNodeWithText("COMPLETE ALL?").assertIsDisplayed()
                // Dialog and underlying button share the label; the dialog owns the visible node.
                compose.onAllNodesWithText("COMPLETE ALL").filterToOne(hasAnyAncestor(isDialog())).performClick()
                assertEquals(listOf("done:a,b"), events)
            }
            else -> {
                click("> SAVE / SHARE")
                when (action) {
                    "share" -> { click(">> SHARE"); assertEquals(listOf("share"), events) }
                    "save" -> { click(">> SAVE TO FILE"); assertEquals(listOf("save"), events) }
                    "copy" -> { click(">> COPY TO CLIPBOARD"); compose.onNodeWithText("COPIED!").assertIsDisplayed(); assertEquals(listOf("copy"), events) }
                    else -> {
                        click(">> PASTE FROM CLIPBOARD"); click("IMPORT NOW")
                        compose.onNodeWithText(if (action == "paste_ok") "IMPORT SUCCESS!" else "IMPORT FAILED").assertIsDisplayed()
                        assertEquals(listOf("paste:fixture yaml"), events)
                        click("OK")
                    }
                }
            }
        }
    }

    @Composable private fun Fixture() {
        var menu by remember { mutableStateOf(action !in listOf("delete_quest", "complete_all")) }
        val clipboard = LocalClipboardManager.current
        LaunchedEffect(Unit) { clipboard.setText(AnnotatedString("fixture yaml")) }
        MainScreen(
            uiState = MainUiState(isServiceRunning = true, hasGameLoaded = true, currentGameName = "Test", totalRulesCount = 2,
                activeReminders = listOf(ActiveReminderUi("a", "Laundry", "09:00"), ActiveReminderUi("b", "Dishes", "09:00")),
                savedGames = listOf(SavedGame("saved", "My saved game", 2, false))),
            hasNotificationPermission = true, quietMode = false, onSetQuietMode = {}, onMenuClick = { menu = true },
            showMenu = menu, onMenuDismiss = { menu = false }, onRequestNotificationPermission = {},
            onSelectFile = { events.add("file") }, onSaveGame = { events.add("save") }, onShareGame = { events.add("share") },
            onRenameGame = { events.add("rename:$it") },
            onExportToClipboard = { result -> events.add("copy"); result("reminders: []") },
            onImportFromClipboard = { text, result -> events.add("paste:$text"); result(if (action == "paste_failure") Result.failure(Exception("Invalid YAML")) else Result.success(2)) },
            onExportData = { events.add("export") }, onImportData = { events.add("import") },
            onLoadPreset = { events.add("preset:$it") }, onLoadSavedGame = { events.add("load:${it.id}") },
            onDeleteSavedGame = { events.add("delete:$it") },
            onCreateNewSchema = { name, done -> events.add("new:$name"); done() },
            onStartService = {}, onStopService = {}, onNavigateToPermissions = {},
            onNavigateToEventLog = { events.add("log") }, onNavigateToRoutines = {},
            onNavigateToEditReminders = { events.add("edit-list") }, onEditReminder = {}, onMarkDone = {},
            onMarkAllDone = { events.add("done:${it.joinToString(",")}") }, onSnooze = { _, _ -> },
            onCancel = {}, onCancelAll = {}, onDeletePermanently = { events.add("remove:$it") })
    }
}
