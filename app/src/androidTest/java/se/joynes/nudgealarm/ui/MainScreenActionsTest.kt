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
            "delete_saved", "delete_quest", "complete_all", "first_launch", "start_loaded",
            "stop_service", "permission", "resume", "empty_day", "change_game", "edit_loaded"
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
            "first_launch" -> { click("SELECT GAME"); compose.onNodeWithText("= SELECT GAME =").assertIsDisplayed() }
            "start_loaded" -> { click(">> PRESS START"); assertEquals(listOf("start"), events) }
            "stop_service" -> { click("> STOP GAME"); assertEquals(listOf("stop"), events) }
            "permission" -> { click(">> ENABLE NOTIFICATIONS"); assertEquals(listOf("permission"), events) }
            "resume" -> { compose.onNodeWithContentDescription("Unmute").performClick(); assertEquals(listOf("quiet:null"), events) }
            "empty_day" -> { compose.onNodeWithText("All quests completed for today!").assertIsDisplayed(); assertTrue(events.isEmpty()) }
            "change_game" -> { click("CHANGE GAME"); click(PRESET_CONFIGS.first().name); assertEquals(listOf("preset:fitness_focus"), events) }
            "edit_loaded" -> { click("EDIT QUESTS"); assertEquals(listOf("edit-list"), events) }
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
        var menu by remember { mutableStateOf(action !in listOf("delete_quest", "complete_all", "first_launch", "start_loaded", "permission", "resume", "empty_day", "change_game", "edit_loaded")) }
        val clipboard = LocalClipboardManager.current
        LaunchedEffect(Unit) { clipboard.setText(AnnotatedString("fixture yaml")) }
        MainScreen(
            uiState = MainUiState(isServiceRunning = action !in listOf("first_launch", "start_loaded", "change_game", "edit_loaded"),
                hasGameLoaded = action != "first_launch", currentGameName = if (action == "first_launch") null else "Test", totalRulesCount = 2,
                activeReminders = if (action in listOf("first_launch", "start_loaded", "empty_day", "change_game", "edit_loaded")) emptyList() else listOf(ActiveReminderUi("a", "Laundry", "09:00"), ActiveReminderUi("b", "Dishes", "09:00")),
                savedGames = listOf(SavedGame("saved", "My saved game", 2, false))),
            hasNotificationPermission = action != "permission", quietMode = action == "resume", onSetQuietMode = { events.add("quiet:$it") }, onMenuClick = { menu = true },
            showMenu = menu, onMenuDismiss = { menu = false }, onRequestNotificationPermission = { events.add("permission") },
            onSelectFile = { events.add("file") }, onSaveGame = { events.add("save") }, onShareGame = { events.add("share") },
            onRenameGame = { events.add("rename:$it") },
            onExportToClipboard = { result -> events.add("copy"); result("reminders: []") },
            onImportFromClipboard = { text, result -> events.add("paste:$text"); result(if (action == "paste_failure") Result.failure(Exception("Invalid YAML")) else Result.success(2)) },
            onExportData = { events.add("export") }, onImportData = { events.add("import") },
            onLoadPreset = { events.add("preset:$it") }, onLoadSavedGame = { events.add("load:${it.id}") },
            onDeleteSavedGame = { events.add("delete:$it") },
            onCreateNewSchema = { name, done -> events.add("new:$name"); done() },
            onStartService = { events.add("start") }, onStopService = { events.add("stop") }, onNavigateToPermissions = {},
            onNavigateToEventLog = { events.add("log") }, onNavigateToRoutines = {},
            onNavigateToEditReminders = { events.add("edit-list") }, onEditReminder = {}, onMarkDone = {},
            onMarkAllDone = { events.add("done:${it.joinToString(",")}") }, onSnooze = { _, _ -> },
            onCancel = {}, onCancelAll = {}, onDeletePermanently = { events.add("remove:$it") })
    }
}
