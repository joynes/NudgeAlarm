package se.joynes.nudgealarm.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal const val MAX_SNOOZE_HOURS = 168
internal const val MAX_SNOOZE_MINUTES = MAX_SNOOZE_HOURS * 60 + 59

internal data class SnoozeChoice(val minutes: Int, val uses: Int, val lastUsed: Long)

internal data class SnoozeHistory(val choices: List<SnoozeChoice> = emptyList()) {
    val recent: List<Int>
        get() = choices.sortedByDescending { it.lastUsed }.map { it.minutes }.take(6)

    val lastMinutes: Int get() = recent.firstOrNull() ?: 15

    val suggestions: List<Int>
        get() = (choices.sortedWith(compareByDescending<SnoozeChoice> { it.uses }
            .thenByDescending { it.lastUsed }).map { it.minutes } +
            listOf(5, 10, 15, 30, 60, 120, 240, 480, 1440))
            .distinct().take(9).sorted()

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

    fun configuration(): SnoozeConfiguration = runCatching {
        val value = JSONObject(prefs.getString("configuration", "{}") ?: "{}")
        val defaults = SnoozeConfiguration()
        SnoozeConfiguration(
            durationPresets = value.optJSONArray("durationPresets")?.let { array ->
                (0 until array.length()).map { array.getInt(it) }
            } ?: defaults.durationPresets,
            quickPickCount = value.optInt("quickPickCount", defaults.quickPickCount),
            adaptive = value.optBoolean("adaptive", defaults.adaptive),
            recentCount = value.optInt("recentCount", defaults.recentCount),
            defaultMinutes = value.optInt("defaultMinutes", defaults.defaultMinutes),
            rememberLast = value.optBoolean("rememberLast", defaults.rememberLast),
            defaultMode = value.optString("defaultMode", defaults.defaultMode)
        )
    }.getOrElse { SnoozeConfiguration() }

    fun saveConfiguration(config: SnoozeConfiguration) {
        val value = JSONObject().put("durationPresets", JSONArray(config.durationPresets.sorted()))
            .put("quickPickCount", config.quickPickCount).put("adaptive", config.adaptive)
            .put("recentCount", config.recentCount).put("defaultMinutes", config.defaultMinutes)
            .put("rememberLast", config.rememberLast).put("defaultMode", config.defaultMode)
        prefs.edit().putString("configuration", value.toString()).apply()
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }

    fun replaceClockShortcuts(shortcuts: List<SnoozeClockShortcut>) {
        require(shortcuts.size <= 48 && shortcuts.distinctBy { it.id }.size == shortcuts.size)
        require(shortcuts.all { it.name.length <= 60 && it.id.length <= 100 })
        writeClockShortcuts(shortcuts)
    }

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

    fun clockShortcuts(): List<SnoozeClockShortcut> = try {
        val array = JSONArray(prefs.getString("clock_shortcuts", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                SnoozeClockShortcut(item.getString("id"), item.getString("name"), item.getInt("hour"), item.getInt("minute"))
            }.getOrNull()
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }

    fun saveClockShortcut(id: String?, name: String, hour: Int, minute: Int) {
        val shortcut = SnoozeClockShortcut(id ?: UUID.randomUUID().toString(), name.trim(), hour, minute)
        replaceClockShortcuts(clockShortcuts().filterNot { it.id == shortcut.id } + shortcut)
    }

    fun deleteClockShortcut(id: String) = writeClockShortcuts(clockShortcuts().filterNot { it.id == id })

    private fun writeClockShortcuts(shortcuts: List<SnoozeClockShortcut>) {
        val array = JSONArray()
        shortcuts.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("hour", it.hour).put("minute", it.minute))
        }
        prefs.edit().putString("clock_shortcuts", array.toString()).apply()
    }
}
