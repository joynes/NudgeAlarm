package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NagStateEntity::class, NagHistoryEntity::class, ReminderEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NagDatabase : RoomDatabase() {

    abstract fun nagStateDao(): NagStateDao
    abstract fun nagHistoryDao(): NagHistoryDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: NagDatabase? = null

        fun getInstance(context: Context): NagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NagDatabase::class.java,
                    "nag_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
