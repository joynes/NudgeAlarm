package se.joynes.nudgealarm.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_SNOOZE_HOURS = 168
internal const val MAX_SNOOZE_MINUTES = MAX_SNOOZE_HOURS * 60 + 59

internal data class SnoozeChoice(val minutes: Int, val uses: Int, val lastUsed: Long)

internal data class SnoozeHistory(val choices: List<SnoozeChoice> = emptyList()) {
    val recent: List<Int>
        get() = choices.sortedByDescending { it.lastUsed }.map { it.minutes }.take(6)

    val lastMinutes: Int get() = recent.firstOrNull() ?: 15

    val suggestions: List<Int>
        get() = (choices.sortedWith(compareByDescending<SnoozeChoice> { it.uses }
            .thenByDescending { it.lastUsed }).map { it.minutes } + listOf(15, 60, 120))
            .distinct().take(3)

    fun record(minutes: Int, now: Long): SnoozeHistory {
        require(minutes in 1..MAX_SNOOZE_MINUTES)
        val previous = choices.find { it.minutes == minutes }
        return SnoozeHistory((choices.filterNot { it.minutes == minutes } +
            SnoozeChoice(minutes, (previous?.uses ?: 0) + 1, now))
            .sortedByDescending { it.lastUsed }.take(20))
    }
}

internal class SnoozePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("snooze_picker", Context.MODE_PRIVATE)

    fun read(): SnoozeHistory = try {
        val array = JSONArray(prefs.getString("history", "[]"))
        SnoozeHistory((0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val minutes = item.optInt("minutes")
            if (minutes !in 1..MAX_SNOOZE_MINUTES) null else
                SnoozeChoice(minutes, item.optInt("uses", 1).coerceAtLeast(1), item.optLong("lastUsed"))
        })
    } catch (_: Exception) {
        SnoozeHistory()
    }

    fun record(minutes: Int) {
        val history = read().record(minutes, System.currentTimeMillis())
        val array = JSONArray()
        history.choices.forEach { choice ->
            array.put(JSONObject().put("minutes", choice.minutes)
                .put("uses", choice.uses).put("lastUsed", choice.lastUsed))
        }
        prefs.edit().putString("history", array.toString()).apply()
    }
}
