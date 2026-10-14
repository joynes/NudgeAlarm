package se.joynes.nudgealarm.storage

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal class SettingsTransferStore(private val context: Context) {
    private val settings = SettingsStore(context)
    private val snooze = SnoozePreferences(context)
    private val notes = context.getSharedPreferences("settings_discussions", Context.MODE_PRIVATE)

    fun snapshot() = SettingsConfiguration(settings.alarmSound.name, settings.vibrationEnabled,
        settings.customSoundUri?.toString(), settings.customSoundName, settings.oldReminderRetentionMinutes,
        settings.alertOnlyWhenActive, settings.minAlertIntervalMinutes, snooze.configuration(), snooze.clockShortcuts(),
        runCatching { JsonParser.parseString(notes.getString("notes", "{}")).asJsonObject.entrySet().associate { it.key to it.value.asString } }.getOrDefault(emptyMap()))

    fun exportToUri(uri: Uri) {
        val json = SettingsConfigurationCodec.encode(snapshot())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open output file")
    }

    fun readFromUri(uri: Uri): SettingsConfiguration {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val buffer = CharArray(256_001)
            var total = 0
            while (total < buffer.size) {
                val read = reader.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            require(total <= 256_000) { "Settings file exceeds 256 KB" }
            String(buffer, 0, total)
        } ?: error("Could not open input file")
        return SettingsConfigurationCodec.decode(text)
    }

    fun apply(configuration: SettingsConfiguration) {
        // Revalidate all fields before the first write, even if the caller constructed the object directly.
        val config = SettingsConfigurationCodec.decode(SettingsConfigurationCodec.encode(configuration))
        snooze.saveConfiguration(config.snooze)
        snooze.replaceClockShortcuts(config.clockShortcuts)
        settings.alarmSound = AlarmSound.valueOf(config.alarmSound)
        settings.vibrationEnabled = config.vibrationEnabled
        settings.customSoundUri = config.customSoundUri?.let { Uri.parse(it) }
        settings.customSoundName = config.customSoundName
        settings.oldReminderRetentionMinutes = config.oldReminderRetentionMinutes
        settings.alertOnlyWhenActive = config.alertOnlyWhenActive
        settings.minAlertIntervalMinutes = config.minAlertIntervalMinutes
        val json = JsonObject()
        config.discussions.forEach { (key, value) -> json.addProperty(key, value) }
        notes.edit().putString("notes", json.toString()).apply()
    }
}
