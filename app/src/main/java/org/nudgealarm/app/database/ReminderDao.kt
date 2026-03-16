package org.nudgealarm.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    /**
     * Get all reminders as a Flow for reactive updates.
     */
    @Query("SELECT * FROM reminders ORDER BY title ASC")
    fun getAllFlow(): Flow<List<ReminderEntity>>

    /**
     * Get all reminders (one-time read).
     */
    @Query("SELECT * FROM reminders ORDER BY title ASC")
    suspend fun getAll(): List<ReminderEntity>

    /**
     * Get only enabled reminders.
     */
    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY title ASC")
    suspend fun getEnabled(): List<ReminderEntity>

    /**
     * Get a reminder by ID.
     */
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    /**
     * Insert a new reminder.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    /**
     * Insert multiple reminders.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    /**
     * Update an existing reminder.
     */
    @Update
    suspend fun update(reminder: ReminderEntity)

    /**
     * Delete a reminder.
     */
    @Delete
    suspend fun delete(reminder: ReminderEntity)

    /**
     * Delete a reminder by ID.
     */
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Get total count of reminders.
     */
    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun getCount(): Int

    /**
     * Toggle enabled status.
     */
    @Query("UPDATE reminders SET enabled = NOT enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Set enabled status directly.
     */
    @Query("UPDATE reminders SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Delete all reminders.
     */
    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
