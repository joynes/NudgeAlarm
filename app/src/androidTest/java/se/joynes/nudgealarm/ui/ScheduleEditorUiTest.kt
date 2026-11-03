package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

@RunWith(Parameterized::class)
class ScheduleEditorUiTest(private val mode: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun modes() = listOf("weekly", "monthly", "once", "cron").map { arrayOf(it) }
    }
    @get:Rule val compose = createComposeRule()
    private var saved = ""
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }

    @Test fun editingTheScheduleSavesTheChosenDaysAndRejectsInvalidCron() {
        val initial = when (mode) {
            "monthly" -> "0 9 1,15 1,6 *"
            "once" -> "once:1793692800000"
            "cron" -> "0 9 15 * 1"
            else -> "0 9 * * *"
        }
        compose.setContent { NudgeAlarmTheme {
            EditRemindersScreen(EditRemindersUiState(isLoading = false, editingReminder = ReminderEntity("test", "Laundry", initial)),
                onAddNew = {}, onEdit = {}, onDelete = {}, onToggleEnabled = {},
                onSave = { _, schedule, _, _, _, _ -> saved = schedule },
                onCreateSavedPlace = { _, _, _, _ -> }, onAddSavedPosition = { _, _, _, _, _ -> },
                onSelectSavedPosition = { _, _ -> }, onCancelEdit = {}, onBack = {})
        } }
        when (mode) {
            "cron" -> {
                compose.onNodeWithText("min hour dom month dow").performScrollTo().performTextReplacement("invalid")
                compose.onNodeWithTag("quest-save").assertIsNotEnabled()
                compose.onNodeWithText("min hour dom month dow").performTextReplacement("*/15 * * * *")
                compose.onNodeWithTag("quest-save").assertIsEnabled().performClick()
                assertEquals("*/15 * * * *", saved)
            }
            "monthly" -> {
                click("1"); click("1"); click("Jan"); click("Jan")
                click("1st"); click("15th"); click("1+15"); click("ALL MONTHS")
                compose.onNodeWithTag("quest-save").performClick()
                assertEquals("0 9 1,15 * *", saved)
            }
            "once" -> {
                compose.onNodeWithText("ONE-TIME").assertExists()
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = 1793692800000 }
                val date = "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                click(date); click("CANCEL")
                click(date); click("OK")
                compose.onNodeWithTag("quest-save").performClick()
                assertTrue(saved.startsWith("once:"))
                assertNotNull(saved.removePrefix("once:").toLongOrNull())
            }
            else -> {
                click("09:00")
                click("CANCEL")
                click("09:00")
                click("OK")
                compose.onAllNodesWithText("S")[0].performClick()
                compose.onNodeWithTag("quest-save").performClick()
                assertEquals(Triple(9, 0, (1..6).toSet()), parseCronSchedule(saved))
            }
        }
    }
}
