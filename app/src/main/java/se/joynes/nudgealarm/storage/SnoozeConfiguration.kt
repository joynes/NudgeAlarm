package se.joynes.nudgealarm.storage

internal val DEFAULT_SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60, 120, 240, 480, 1440)

internal data class SnoozeConfiguration(
    val durationPresets: List<Int> = DEFAULT_SNOOZE_PRESETS,
    val quickPickCount: Int = 9,
    val adaptive: Boolean = true,
    val recentCount: Int = 6,
    val defaultMinutes: Int = 15,
    val rememberLast: Boolean = true,
    val defaultMode: String = "duration"
) {
    init {
        require(durationPresets.size <= 48 && durationPresets.distinct().size == durationPresets.size)
        require(durationPresets.all { it in 1..MAX_SNOOZE_MINUTES })
        require(quickPickCount in 1..24 && recentCount in 0..20)
        require(defaultMinutes in 1..MAX_SNOOZE_MINUTES)
        require(defaultMode in listOf("duration", "clock"))
    }
}

internal fun SnoozeHistory.suggestions(config: SnoozeConfiguration): List<Int> {
    val learned = if (config.adaptive) choices.sortedWith(compareByDescending<SnoozeChoice> { it.uses }
        .thenByDescending { it.lastUsed }).map { it.minutes } else emptyList()
    return (learned + config.durationPresets.sorted()).distinct().take(config.quickPickCount).sorted()
}
