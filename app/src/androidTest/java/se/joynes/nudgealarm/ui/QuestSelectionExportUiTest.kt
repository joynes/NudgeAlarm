package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class QuestSelectionExportUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedQuestIdsAreSentToExport() {
        var exported = emptySet<String>()
        composeRule.setContent {
            NudgeAlarmTheme {
                EditRemindersScreen(
                    uiState = EditRemindersUiState(
                        isLoading = false,
                        reminders = listOf(
                            ReminderEntity("water", "Drink water", "0 8 * * *", sortOrder = 0),
                            ReminderEntity("walk", "Take a walk", "0 9 * * *", sortOrder = 1)
                        )
                    ),
                    onAddNew = {},
                    onEdit = {},
                    onDelete = {},
                    onToggleEnabled = {},
                    onSave = { _, _, _, _, _, _ -> },
                    onCreateSavedPlace = { _, _, _, _ -> },
                    onAddSavedPosition = { _, _, _, _, _ -> },
                    onSelectSavedPosition = { _, _ -> },
                    onCancelEdit = {},
                    onBack = {},
                    onExportSelected = { exported = it }
                )
            }
        }

        composeRule.onNodeWithText("SELECT").performClick()
        composeRule.onNodeWithText("EXPORT 0").assertIsNotEnabled()
        composeRule.onNodeWithTag("select_walk").performClick()
        composeRule.onNodeWithText("EXPORT 1").assertIsEnabled().performClick()

        assertEquals(setOf("walk"), exported)
    }
}
