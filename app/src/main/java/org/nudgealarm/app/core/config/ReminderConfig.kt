package org.nudgealarm.app.core.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class ReminderConfig(
    val id: String,
    val title: String,
    val schedule: String,
    val nagInterval: Duration = 5.minutes,
    val maxNags: Int = 100,
    val sound: String = "alarm",
    val vibration: String = "strong",
    val snoozeOptions: List<Duration> = listOf(5.minutes, 15.minutes)
)
