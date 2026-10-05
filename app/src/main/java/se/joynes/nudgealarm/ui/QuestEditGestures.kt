package se.joynes.nudgealarm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import se.joynes.nudgealarm.core.event.Event

/** Shared accessible shortcut; callers with a tap menu keep that menu. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.questEditGesture(onEdit: () -> Unit, onClick: () -> Unit = onEdit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onEdit, onLongClickLabel = "Edit Quest")

/** Use structured IDs, never parse human-readable log text. */
internal fun Event.questRuleId(): String? = when (this) {
    is Event.NextEventComputed -> ruleId
    is Event.ReminderTriggered -> ruleId
    is Event.NagFired -> ruleId
    is Event.UserTappedDone -> ruleId
    is Event.UserTappedSnooze -> ruleId
    is Event.UserTappedCancel -> ruleId
    is Event.SnoozeEnded -> ruleId
    is Event.CronParsed -> ruleId
    else -> null
}
