package se.joynes.nudgealarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGold
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadriveOrange
import se.joynes.nudgealarm.ui.theme.MegadrivePurple
import se.joynes.nudgealarm.ui.theme.MegadriveRed
import se.joynes.nudgealarm.core.cron.CronExpression
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState

private enum class ScheduleMode { VECKA, MANAD, ENGANG, CRON }

@Composable
fun EditRemindersScreen(
    uiState: EditRemindersUiState,
    onAddNew: () -> Unit,
    onEdit: (ReminderEntity) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onSave: (title: String, schedule: String, nagIntervalMinutes: Int, maxNags: Int, sticky: Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onBack: () -> Unit,
    onLoadFromFile: () -> Unit = {},
    onSaveToFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // -1 = all, -2 = weekdays (1-5), -3 = weekends (0,6), 0-6 = specific day
    var dayFilter by remember { mutableStateOf(-1) }

    // Filter reminders based on search query and day filter, then sort by time
    val filteredReminders = remember(uiState.reminders, searchQuery, dayFilter) {
        uiState.reminders
            .filter { reminder ->
                val matchesSearch = searchQuery.isBlank() ||
                    reminder.title.contains(searchQuery, ignoreCase = true) ||
                    reminder.schedule.contains(searchQuery, ignoreCase = true)
                val matchesDay = when (dayFilter) {
                    -1 -> true
                    -2 -> {
                        val parsed = parseCronSchedule(reminder.schedule)
                        parsed != null && parsed.third.any { it in 1..5 }
                    }
                    -3 -> {
                        val parsed = parseCronSchedule(reminder.schedule)
                        parsed != null && parsed.third.any { it == 0 || it == 6 }
                    }
                    else -> {
                        val parsed = parseCronSchedule(reminder.schedule)
                        parsed != null && dayFilter in parsed.third
                    }
                }
                matchesSearch && matchesDay
            }
            .sortedBy { reminder ->
                val parsed = parseCronSchedule(reminder.schedule)
                if (parsed != null) parsed.first * 60 + parsed.second else Int.MAX_VALUE
            }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!uiState.isAddingNew && uiState.editingReminder == null) {
                FloatingActionButton(
                    onClick = onAddNew,
                    containerColor = MegadriveGreen,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // === HEADER ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "QUEST EDITOR",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveGold
                    )
                    Text(
                        text = "${uiState.reminders.size} quests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onLoadFromFile,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MegadriveCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("LOAD", fontSize = 11.sp, color = MegadriveCyan)
                    }
                    OutlinedButton(
                        onClick = onSaveToFile,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MegadrivePurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("SAVE", fontSize = 11.sp, color = MegadrivePurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === SEARCH BAR ===
            if (uiState.reminders.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search quests...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MegadriveCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MegadriveCyan,
                        focusedLabelColor = MegadriveCyan
                    ),
                    shape = RoundedCornerShape(4.dp)
                )

                if (searchQuery.isNotEmpty() || dayFilter != -1) {
                    Text(
                        text = "${filteredReminders.size} of ${uiState.reminders.size} quests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MegadriveCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // === DAY FILTER CHIPS ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data class DayChip(val label: String, val value: Int)
                    val chips = listOf(
                        DayChip("ALL", -1),
                        DayChip("WEEKDAY", -2),
                        DayChip("WEEKEND", -3),
                        DayChip("Mon", 1),
                        DayChip("Tue", 2),
                        DayChip("Wed", 3),
                        DayChip("Thu", 4),
                        DayChip("Fri", 5),
                        DayChip("Sat", 6),
                        DayChip("Sun", 0),
                    )
                    chips.forEach { chip ->
                        FilterChip(
                            selected = dayFilter == chip.value,
                            onClick = { dayFilter = chip.value },
                            label = { Text(chip.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MegadrivePurple,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MegadrivePurple.copy(alpha = 0.5f),
                                selectedBorderColor = MegadrivePurple,
                                enabled = true,
                                selected = dayFilter == chip.value
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // === CONTENT ===
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MegadriveCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("LOADING...", color = MegadriveCyan)
                    }
                }
            } else if (uiState.reminders.isEmpty() && !uiState.isAddingNew) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, MegadrivePurple, RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("NO QUESTS YET", style = MaterialTheme.typography.headlineSmall, color = MegadrivePurple)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Press + to create your first quest!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (filteredReminders.isEmpty() && (searchQuery.isNotEmpty() || dayFilter != -1)) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO MATCHES", style = MaterialTheme.typography.headlineSmall, color = MegadriveOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No quests match the current filter", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // === COMPACT LIST ===
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredReminders) { reminder ->
                        CompactQuestRow(
                            reminder = reminder,
                            onEdit = { onEdit(reminder) },
                            onToggleEnabled = { onToggleEnabled(reminder.id) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )
                    }
                }
            }

        }
    }

    // === EDIT/ADD DIALOG ===
    if (uiState.isAddingNew || uiState.editingReminder != null) {
        QuestEditDialog(
            reminder = uiState.editingReminder,
            onSave = onSave,
            onDelete = if (uiState.editingReminder != null) {
                { showDeleteConfirm = uiState.editingReminder.id }
            } else null,
            onDismiss = onCancelEdit
        )
    }

    // === DELETE CONFIRMATION ===
    showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("! WARNING !", style = MaterialTheme.typography.titleLarge, color = MegadriveRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Delete this quest permanently?", color = MaterialTheme.colorScheme.onSurface)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(id)
                        onCancelEdit() // Close edit dialog too
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveRed),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }
}

/**
 * Convert cron schedule to human-readable Swedish text.
 */
private fun cronToReadable(schedule: String): String {
    if (schedule.startsWith("once:")) {
        val timestamp = schedule.removePrefix("once:").toLongOrNull() ?: return schedule
        val dateFormat = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH)
        return "One-time ${dateFormat.format(java.util.Date(timestamp))}"
    }

    val parts = schedule.trim().split(Regex("\\s+"))
    if (parts.size != 5) return schedule

    val minute = parts[0].toIntOrNull() ?: return schedule
    val hour = parts[1].toIntOrNull() ?: return schedule
    val dayOfMonth = parts[2]
    val monthField = parts[3]
    val dayOfWeek = parts[4]

    val timeStr = String.format("%02d:%02d", hour, minute)

    // Monthly schedule: dayOfMonth specific, dayOfWeek is *
    if (dayOfMonth != "*" && dayOfWeek == "*") {
        val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthStr = if (monthField == "*") {
            ""
        } else {
            val months = parseCronFieldToSet(monthField, 1..12)
            " " + months.sorted().joinToString(",") { monthNames.getOrElse(it) { it.toString() } }
        }
        return "Day $dayOfMonth$monthStr $timeStr"
    }

    // Advanced: both dayOfMonth and dayOfWeek are specific
    if (dayOfMonth != "*" && dayOfWeek != "*") {
        return schedule
    }

    val daysStr = when (dayOfWeek) {
        "*" -> "Every day"
        "0" -> "Sun"
        "1" -> "Mon"
        "2" -> "Tue"
        "3" -> "Wed"
        "4" -> "Thu"
        "5" -> "Fri"
        "6" -> "Sat"
        "1-5" -> "Weekdays"
        "0,6" -> "Weekends"
        else -> {
            if (dayOfWeek.contains(",")) {
                val days = dayOfWeek.split(",").mapNotNull { it.toIntOrNull() }
                val dayNames = days.map { d ->
                    when (d) { 0 -> "Sun"; 1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; else -> "" }
                }.filter { it.isNotEmpty() }
                dayNames.joinToString(", ")
            } else dayOfWeek
        }
    }

    return "$daysStr $timeStr"
}

/**
 * Determine accent color based on schedule frequency.
 * Red=sub-hourly, Orange=multi-daily, Gold=daily, Cyan=weekly, Green=monthly, Purple=rare/one-time
 */
private fun frequencyColor(schedule: String): Color {
    if (schedule.startsWith("once:")) return MegadrivePurple
    return try {
        val cron = CronExpression.parse(schedule)
        val next1 = cron.nextTriggerTime()
        val next2 = cron.nextTriggerTime(next1 + 60_000)
        val intervalHours = (next2 - next1) / 3_600_000.0
        when {
            intervalHours < 1 -> MegadriveRed        // sub-hourly
            intervalHours < 24 -> MegadriveOrange     // multiple per day
            intervalHours < 36 -> MegadriveGold       // daily
            intervalHours < 24 * 7 -> MegadriveCyan   // weekly
            intervalHours < 24 * 32 -> MegadriveGreen // monthly
            else -> MegadrivePurple                   // rare
        }
    } catch (_: Exception) {
        MegadriveCyan
    }
}

@Composable
private fun CompactQuestRow(
    reminder: ReminderEntity,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val readableSchedule = remember(reminder.schedule) { cronToReadable(reminder.schedule) }
    val accentColor = remember(reminder.schedule) { frequencyColor(reminder.schedule) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (!reminder.enabled) Color.Gray else accentColor,
                    shape = RoundedCornerShape(4.dp)
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Quest info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (reminder.sticky) "★ ${reminder.title}" else reminder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (reminder.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = readableSchedule,
                style = MaterialTheme.typography.bodySmall,
                color = accentColor
            )
        }

        // Toggle switch
        Switch(
            checked = reminder.enabled,
            onCheckedChange = { onToggleEnabled() },
            modifier = Modifier.size(44.dp, 24.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MegadriveGreen,
                checkedTrackColor = MegadriveGreen.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
            )
        )
    }
}

private fun parseCronSchedule(schedule: String): Triple<Int, Int, Set<Int>>? {
    if (schedule.startsWith("once:")) {
        val timestamp = schedule.removePrefix("once:").toLongOrNull() ?: return null
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK).let {
            if (it == java.util.Calendar.SUNDAY) 0 else it - 1
        }
        return Triple(hour, minute, setOf(dayOfWeek))
    }

    val parts = schedule.trim().split(Regex("\\s+"))
    if (parts.size != 5) return null

    val minute = parts[0].toIntOrNull() ?: return null
    val hour = parts[1].toIntOrNull() ?: return null

    val dayPart = parts[4]
    val selectedDays = when {
        dayPart == "*" -> (0..6).toSet()
        dayPart.contains("-") -> {
            val range = dayPart.split("-")
            if (range.size == 2) {
                val start = range[0].toIntOrNull() ?: return null
                val end = range[1].toIntOrNull() ?: return null
                (start..end).toSet()
            } else null
        }
        dayPart.contains(",") -> dayPart.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        else -> dayPart.toIntOrNull()?.let { setOf(it) }
    } ?: return null

    return Triple(hour, minute, selectedDays)
}

private fun buildCronSchedule(hour: Int, minute: Int, selectedDays: Set<Int>): String {
    val dayPart = when {
        selectedDays.isEmpty() || selectedDays.size == 7 -> "*"
        selectedDays == setOf(1, 2, 3, 4, 5) -> "1-5"
        selectedDays == setOf(0, 6) -> "0,6"
        selectedDays.size == 1 -> selectedDays.first().toString()
        else -> {
            val sorted = selectedDays.sorted()
            val isRange = sorted.zipWithNext().all { (a, b) -> b - a == 1 }
            if (isRange && sorted.size > 2) "${sorted.first()}-${sorted.last()}" else sorted.joinToString(",")
        }
    }
    return "$minute $hour * * $dayPart"
}

private fun parseCronFieldToSet(field: String, range: IntRange): Set<Int> {
    if (field == "*") return range.toSet()
    return field.split(",").flatMap { part ->
        if (part.contains("-")) {
            val bounds = part.split("-")
            val start = bounds[0].toIntOrNull() ?: return@flatMap emptyList()
            val end = bounds[1].toIntOrNull() ?: return@flatMap emptyList()
            (start..end).toList()
        } else {
            listOfNotNull(part.toIntOrNull())
        }
    }.toSet()
}

private fun buildMonthlyCronSchedule(hour: Int, minute: Int, daysOfMonth: Set<Int>, months: Set<Int>): String {
    val dayPart = if (daysOfMonth.isEmpty()) "1" else daysOfMonth.sorted().joinToString(",")
    val monthPart = if (months.isEmpty() || months.size == 12) "*" else months.sorted().joinToString(",")
    return "$minute $hour $dayPart $monthPart *"
}

private fun detectScheduleMode(schedule: String?): ScheduleMode {
    if (schedule == null) return ScheduleMode.VECKA
    if (schedule.startsWith("once:")) return ScheduleMode.ENGANG
    val parts = schedule.trim().split(Regex("\\s+"))
    if (parts.size != 5) return ScheduleMode.VECKA
    val domSpecific = parts[2] != "*"
    val dowSpecific = parts[4] != "*"
    return when {
        domSpecific && dowSpecific -> ScheduleMode.CRON
        domSpecific -> ScheduleMode.MANAD
        else -> ScheduleMode.VECKA
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestEditDialog(
    reminder: ReminderEntity?,
    onSave: (title: String, schedule: String, nagIntervalMinutes: Int, maxNags: Int, sticky: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val isExistingOnce = reminder?.schedule?.startsWith("once:") == true
    val parsed = reminder?.schedule?.let { parseCronSchedule(it) }
    val initialMode = detectScheduleMode(reminder?.schedule)
    val now = java.util.Calendar.getInstance()

    var title by remember { mutableStateOf(reminder?.title ?: "") }
    var sticky by remember { mutableStateOf(reminder?.sticky ?: false) }
    var scheduleMode by remember { mutableStateOf(initialMode) }
    var hour by remember { mutableStateOf(parsed?.first ?: now.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(parsed?.second ?: now.get(java.util.Calendar.MINUTE)) }
    var selectedDays by remember { mutableStateOf(parsed?.third ?: setOf(now.get(java.util.Calendar.DAY_OF_WEEK) - 1)) }

    // Monthly mode state
    var selectedDaysOfMonth by remember {
        mutableStateOf(
            if (initialMode == ScheduleMode.MANAD && reminder != null) {
                val parts = reminder.schedule.trim().split(Regex("\\s+"))
                parseCronFieldToSet(parts[2], 1..31)
            } else setOf(now.get(java.util.Calendar.DAY_OF_MONTH))
        )
    }
    var selectedMonths by remember {
        mutableStateOf(
            if (initialMode == ScheduleMode.MANAD && reminder != null) {
                val parts = reminder.schedule.trim().split(Regex("\\s+"))
                parseCronFieldToSet(parts[3], 1..12)
            } else setOf(now.get(java.util.Calendar.MONTH) + 1)
        )
    }

    // Cron mode state
    var rawCronText by remember {
        mutableStateOf(
            if (initialMode == ScheduleMode.CRON && reminder != null) reminder.schedule
            else ""
        )
    }
    var cronError by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // One-time date fields
    val existingCal = if (isExistingOnce) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = reminder!!.schedule.removePrefix("once:").toLong()
        }
    } else null
    var onceYear by remember { mutableStateOf(existingCal?.get(java.util.Calendar.YEAR)?.toString() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()) }
    var onceMonth by remember { mutableStateOf(((existingCal?.get(java.util.Calendar.MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) + 1).toString()) }
    var onceDay by remember { mutableStateOf((existingCal?.get(java.util.Calendar.DAY_OF_MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)).toString()) }

    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (reminder == null) ">> NEW QUEST" else ">> EDIT QUEST",
                style = MaterialTheme.typography.titleLarge,
                color = MegadriveGold,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Quest Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MegadriveCyan, focusedLabelColor = MegadriveCyan)
                )

                // Schedule mode selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data class ModeChip(val mode: ScheduleMode, val label: String)
                    val modeChips = listOf(
                        ModeChip(ScheduleMode.VECKA, "WEEKLY"),
                        ModeChip(ScheduleMode.MANAD, "MONTHLY"),
                        ModeChip(ScheduleMode.ENGANG, "ONE-TIME"),
                        ModeChip(ScheduleMode.CRON, "CRON"),
                    )
                    modeChips.forEach { chip ->
                        FilterChip(
                            selected = scheduleMode == chip.mode,
                            onClick = { scheduleMode = chip.mode },
                            label = { Text(chip.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MegadriveGold,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MegadriveGold.copy(alpha = 0.5f),
                                selectedBorderColor = MegadriveGold,
                                enabled = true,
                                selected = scheduleMode == chip.mode
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }

                // TIME section (hidden in CRON mode since time is in the expression)
                if (scheduleMode != ScheduleMode.CRON) {
                    Text("TIME", style = MaterialTheme.typography.labelMedium, color = MegadriveCyan)
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MegadriveCyan)
                    ) {
                        Text(
                            String.format("%02d:%02d", hour, minute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MegadriveCyan
                        )
                    }
                }

                when (scheduleMode) {
                    ScheduleMode.ENGANG -> {
                        // Date picker for one-time events
                        Text("DATE", style = MaterialTheme.typography.labelMedium, color = MegadrivePurple)
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadrivePurple)
                        ) {
                            Text(
                                "$onceYear-${onceMonth.padStart(2, '0')}-${onceDay.padStart(2, '0')}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MegadrivePurple
                            )
                        }
                    }

                    ScheduleMode.VECKA -> {
                        // Recurring: day-of-week selector
                        Text("DAYS", style = MaterialTheme.typography.labelMedium, color = MegadriveCyan)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            dayLabels.forEachIndexed { index, label ->
                                val isSelected = index in selectedDays
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(2.dp, if (isSelected) MegadriveGreen else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .background(if (isSelected) MegadriveGreen.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(4.dp))
                                        .clickable { selectedDays = if (isSelected) selectedDays - index else selectedDays + index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selectedDays = (0..6).toSet() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDays.size == 7) MegadriveGreen else MaterialTheme.colorScheme.outline)) {
                                Text("ALL", fontSize = 10.sp, color = if (selectedDays.size == 7) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { selectedDays = setOf(1, 2, 3, 4, 5) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDays == setOf(1, 2, 3, 4, 5)) MegadriveGreen else MaterialTheme.colorScheme.outline)) {
                                Text("M-F", fontSize = 10.sp, color = if (selectedDays == setOf(1, 2, 3, 4, 5)) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { selectedDays = setOf(0, 6) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDays == setOf(0, 6)) MegadriveGreen else MaterialTheme.colorScheme.outline)) {
                                Text("S-S", fontSize = 10.sp, color = if (selectedDays == setOf(0, 6)) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    ScheduleMode.MANAD -> {
                        // Day-of-month grid
                        Text("DAY OF MONTH", style = MaterialTheme.typography.labelMedium, color = MegadriveCyan)
                        for (row in 0..4) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                for (col in 0..6) {
                                    val day = row * 7 + col + 1
                                    if (day <= 31) {
                                        val isSelected = day in selectedDaysOfMonth
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .border(2.dp, if (isSelected) MegadriveGreen else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                .background(if (isSelected) MegadriveGreen.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(4.dp))
                                                .clickable { selectedDaysOfMonth = if (isSelected) selectedDaysOfMonth - day else selectedDaysOfMonth + day },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(day.toString(), fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }

                        // Quick presets
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { selectedDaysOfMonth = setOf(1) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDaysOfMonth == setOf(1)) MegadriveGreen else MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(4.dp)) {
                                Text("1st", fontSize = 10.sp, color = if (selectedDaysOfMonth == setOf(1)) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { selectedDaysOfMonth = setOf(15) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDaysOfMonth == setOf(15)) MegadriveGreen else MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(4.dp)) {
                                Text("15th", fontSize = 10.sp, color = if (selectedDaysOfMonth == setOf(15)) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { selectedDaysOfMonth = setOf(1, 15) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDaysOfMonth == setOf(1, 15)) MegadriveGreen else MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(4.dp)) {
                                Text("1+15", fontSize = 10.sp, color = if (selectedDaysOfMonth == setOf(1, 15)) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { selectedDaysOfMonth = (1..31).toSet() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, if (selectedDaysOfMonth.size == 31) MegadriveGreen else MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(4.dp)) {
                                Text("ALL", fontSize = 10.sp, color = if (selectedDaysOfMonth.size == 31) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Month filter
                        Text("MONTHS", style = MaterialTheme.typography.labelMedium, color = MegadriveCyan)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val monthLabels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                            monthLabels.forEachIndexed { index, label ->
                                val monthNum = index + 1
                                FilterChip(
                                    selected = monthNum in selectedMonths,
                                    onClick = {
                                        selectedMonths = if (monthNum in selectedMonths) selectedMonths - monthNum else selectedMonths + monthNum
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MegadriveGreen,
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = MegadriveGreen.copy(alpha = 0.5f),
                                        selectedBorderColor = MegadriveGreen,
                                        enabled = true,
                                        selected = monthNum in selectedMonths
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { selectedMonths = (1..12).toSet() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (selectedMonths.size == 12) MegadriveGreen else MaterialTheme.colorScheme.outline)
                        ) {
                            Text("ALL MONTHS", fontSize = 10.sp, color = if (selectedMonths.size == 12) MegadriveGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    ScheduleMode.CRON -> {
                        // Raw cron text field
                        Text("CRON EXPRESSION", style = MaterialTheme.typography.labelMedium, color = MegadriveOrange)
                        OutlinedTextField(
                            value = rawCronText,
                            onValueChange = { newValue ->
                                rawCronText = newValue
                                cronError = try {
                                    if (newValue.isBlank()) null
                                    else {
                                        CronExpression.parse(newValue.trim())
                                        null
                                    }
                                } catch (e: Exception) {
                                    e.message ?: "Invalid cron expression"
                                }
                            },
                            label = { Text("min hour dom month dow") },
                            singleLine = true,
                            isError = cronError != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MegadriveOrange,
                                focusedLabelColor = MegadriveOrange,
                                errorBorderColor = MegadriveRed
                            )
                        )
                        if (cronError != null) {
                            Text(cronError!!, color = MegadriveRed, fontSize = 12.sp)
                        } else if (rawCronText.isNotBlank()) {
                            val nextPreview = remember(rawCronText) {
                                try {
                                    val next = CronExpression.parse(rawCronText.trim()).nextTriggerTime()
                                    val dateFormat = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.ENGLISH)
                                    "Next: ${dateFormat.format(java.util.Date(next))}"
                                } catch (_: Exception) { null }
                            }
                            if (nextPreview != null) {
                                Text(nextPreview, color = MegadriveGreen, fontSize = 12.sp)
                            }
                        }
                        val exNow = remember { java.util.Calendar.getInstance() }
                        val exH = exNow.get(java.util.Calendar.HOUR_OF_DAY)
                        val exM = exNow.get(java.util.Calendar.MINUTE)
                        val exD = exNow.get(java.util.Calendar.DAY_OF_MONTH)
                        val exTime = "${exM.toString().padStart(2,'0')} $exH"
                        val exHM = "${exH.toString().padStart(2,'0')}:${exM.toString().padStart(2,'0')}"
                        val examples = remember(exH, exM, exD) {
                            listOf(
                                "$exTime * * *"     to "every day $exHM",
                                "$exTime * * 1-5"   to "weekdays $exHM",
                                "$exTime */2 * *"   to "every 2nd day $exHM",
                                "$exTime */3 * *"   to "every 3rd day $exHM",
                                "$exTime $exD * *"  to "$exD of each month $exHM",
                                "$exTime $exD */3 *" to "$exD every 3rd month $exHM",
                                "$exTime * * 5"     to "Fridays $exHM",
                                "*/15 * * * *"      to "every 15 minutes",
                            )
                        }
                        Text("Examples (tap to use):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        examples.forEach { (cron, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        rawCronText = cron
                                        cronError = null
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cron, fontSize = 11.sp, color = MegadriveOrange)
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("STICKY", style = MaterialTheme.typography.labelMedium, color = MegadriveGold)
                        Text(
                            "Never auto-expire this quest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sticky,
                        onCheckedChange = { sticky = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MegadriveGold,
                            checkedTrackColor = MegadriveGold.copy(alpha = 0.5f)
                        )
                    )
                }

                // Delete button (only when editing)
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MegadriveRed)
                    ) {
                        Text("DELETE QUEST", color = MegadriveRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val schedule = when (scheduleMode) {
                        ScheduleMode.ENGANG -> {
                            val y = onceYear.toIntOrNull() ?: return@Button
                            val m = onceMonth.toIntOrNull() ?: return@Button
                            val d = onceDay.toIntOrNull() ?: return@Button
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.YEAR, y)
                                set(java.util.Calendar.MONTH, m - 1)
                                set(java.util.Calendar.DAY_OF_MONTH, d)
                                set(java.util.Calendar.HOUR_OF_DAY, hour)
                                set(java.util.Calendar.MINUTE, minute)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            "once:${cal.timeInMillis}"
                        }
                        ScheduleMode.VECKA -> {
                            if (selectedDays.isEmpty()) return@Button
                            buildCronSchedule(hour, minute, selectedDays)
                        }
                        ScheduleMode.MANAD -> {
                            if (selectedDaysOfMonth.isEmpty()) return@Button
                            buildMonthlyCronSchedule(hour, minute, selectedDaysOfMonth, selectedMonths)
                        }
                        ScheduleMode.CRON -> {
                            if (cronError != null || rawCronText.isBlank()) return@Button
                            rawCronText.trim()
                        }
                    }
                    onSave(title.trim(), schedule, reminder?.nagIntervalMinutes ?: 5, reminder?.maxNags ?: 100, sticky)
                },
                enabled = title.isNotBlank() && when (scheduleMode) {
                    ScheduleMode.VECKA -> selectedDays.isNotEmpty()
                    ScheduleMode.MANAD -> selectedDaysOfMonth.isNotEmpty()
                    ScheduleMode.ENGANG -> true
                    ScheduleMode.CRON -> cronError == null && rawCronText.isNotBlank()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("SAVE", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("< CANCEL", color = MegadriveCyan)
            }
        }
    )

    // Time picker dialog
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("OK", color = MegadriveGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // Date picker dialog (one-time quests)
    if (showDatePicker) {
        val initCal = java.util.Calendar.getInstance().apply {
            set(
                onceYear.toIntOrNull() ?: get(java.util.Calendar.YEAR),
                (onceMonth.toIntOrNull() ?: (get(java.util.Calendar.MONTH) + 1)) - 1,
                onceDay.toIntOrNull() ?: get(java.util.Calendar.DAY_OF_MONTH),
                12, 0, 0
            )
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initCal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        // DatePicker returns UTC midnight — convert to local date fields
                        val picked = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        picked.timeInMillis = ms
                        onceYear = picked.get(java.util.Calendar.YEAR).toString()
                        onceMonth = (picked.get(java.util.Calendar.MONTH) + 1).toString()
                        onceDay = picked.get(java.util.Calendar.DAY_OF_MONTH).toString()
                    }
                    showDatePicker = false
                }) { Text("OK", color = MegadriveGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("CANCEL", color = MegadriveCyan)
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
