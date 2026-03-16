package org.nudgealarm.app.database

import kotlinx.coroutines.flow.Flow
import org.nudgealarm.app.core.config.AppConfig
import org.nudgealarm.app.core.config.ReminderConfig

/**
 * Repository for managing reminder configurations in the database.
 */
class ReminderRepository(private val dao: ReminderDao) {

    /**
     * Get all reminders as a reactive Flow.
     */
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>> {
        return dao.getAllFlow()
    }

    /**
     * Get all reminders (one-time read).
     */
    suspend fun getAllReminders(): List<ReminderEntity> {
        return dao.getAll()
    }

    /**
     * Get enabled reminders as ReminderConfig list.
     */
    suspend fun getEnabledAsConfigs(): List<ReminderConfig> {
        return dao.getEnabled().map { it.toReminderConfig() }
    }

    /**
     * Get all reminders as AppConfig (for service compatibility).
     */
    suspend fun getAsAppConfig(): AppConfig {
        val configs = dao.getEnabled().map { it.toReminderConfig() }
        return AppConfig(reminders = configs)
    }

    /**
     * Get a reminder by ID.
     */
    suspend fun getById(id: String): ReminderEntity? {
        return dao.getById(id)
    }

    /**
     * Save a reminder (insert or update).
     */
    suspend fun save(reminder: ReminderEntity) {
        dao.insert(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Save a new reminder from user input.
     */
    suspend fun create(
        title: String,
        schedule: String,
        nagIntervalMinutes: Int = 5,
        maxNags: Int = 100
    ): ReminderEntity {
        val id = generateId(title)
        val reminder = ReminderEntity(
            id = id,
            title = title,
            schedule = schedule,
            nagIntervalMinutes = nagIntervalMinutes,
            maxNags = maxNags
        )
        dao.insert(reminder)
        return reminder
    }

    /**
     * Update an existing reminder.
     */
    suspend fun update(reminder: ReminderEntity) {
        dao.update(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Delete a reminder.
     */
    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    /**
     * Toggle enabled status.
     */
    suspend fun toggleEnabled(id: String) {
        dao.toggleEnabled(id)
    }

    /**
     * Set enabled status directly.
     */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    /**
     * Import reminders from ReminderConfig list (from YAML).
     */
    suspend fun importFromConfigs(configs: List<ReminderConfig>) {
        val entities = configs.map { ReminderEntity.fromReminderConfig(it) }
        dao.insertAll(entities)
    }

    /**
     * Check if there are any reminders.
     */
    suspend fun hasReminders(): Boolean {
        return dao.getCount() > 0
    }

    /**
     * Clear all reminders.
     */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    private fun generateId(title: String): String {
        val sanitized = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val timestamp = System.currentTimeMillis() % 10000
        return "${sanitized}_$timestamp"
    }
}
