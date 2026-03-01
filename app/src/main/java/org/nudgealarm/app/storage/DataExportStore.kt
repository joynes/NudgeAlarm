package org.nudgealarm.app.storage

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.NagHistoryEntity
import org.nudgealarm.app.database.NagStateEntity
import org.nudgealarm.app.database.ReminderEntity

data class ExportedSettings(
    @SerializedName("alarmSound") val alarmSound: String,
    @SerializedName("vibrationEnabled") val vibrationEnabled: Boolean,
    @SerializedName("customSoundUri") val customSoundUri: String?,
    @SerializedName("customSoundName") val customSoundName: String?,
    @SerializedName("quietMode") val quietMode: Boolean
)

data class DataExport(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("exportedAt") val exportedAt: Long,
    @SerializedName("reminders") val reminders: List<ReminderEntity>,
    @SerializedName("nagStates") val nagStates: List<NagStateEntity>,
    @SerializedName("nagHistory") val nagHistory: List<NagHistoryEntity>,
    @SerializedName("settings") val settings: ExportedSettings
)

class DataExportStore(private val context: Context) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val db = NagDatabase.getInstance(context)
    private val settingsStore = SettingsStore(context)

    suspend fun exportToUri(uri: Uri): Result<Int> {
        return try {
            val reminders = db.reminderDao().getAll()
            val nagStates = db.nagStateDao().getAll()
            val nagHistory = db.nagHistoryDao().getAll()

            val settings = ExportedSettings(
                alarmSound = settingsStore.alarmSound.name,
                vibrationEnabled = settingsStore.vibrationEnabled,
                customSoundUri = settingsStore.customSoundUri?.toString(),
                customSoundName = settingsStore.customSoundName,
                quietMode = settingsStore.quietMode
            )

            val export = DataExport(
                exportedAt = System.currentTimeMillis(),
                reminders = reminders,
                nagStates = nagStates,
                nagHistory = nagHistory,
                settings = settings
            )

            val json = gson.toJson(export)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: return Result.failure(Exception("Could not open output stream"))

            Result.success(reminders.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Int> {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return Result.failure(Exception("Could not open input stream"))

            val export = gson.fromJson(json, DataExport::class.java)
                ?: return Result.failure(Exception("Invalid export file"))

            if (export.version != 1) {
                return Result.failure(Exception("Unsupported export version: ${export.version}"))
            }

            // Replace all data
            db.reminderDao().deleteAll()
            db.nagStateDao().deleteAll()
            db.nagHistoryDao().deleteAll()

            if (export.reminders.isNotEmpty()) db.reminderDao().insertAll(export.reminders)
            if (export.nagStates.isNotEmpty()) db.nagStateDao().insertAll(export.nagStates)
            if (export.nagHistory.isNotEmpty()) db.nagHistoryDao().insertAll(export.nagHistory)

            // Restore settings
            settingsStore.alarmSound = AlarmSound.fromName(export.settings.alarmSound)
            settingsStore.vibrationEnabled = export.settings.vibrationEnabled
            settingsStore.customSoundUri = export.settings.customSoundUri?.let { Uri.parse(it) }
            settingsStore.customSoundName = export.settings.customSoundName
            settingsStore.quietMode = export.settings.quietMode

            Result.success(export.reminders.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
