package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NagStateEntity::class, NagHistoryEntity::class, ReminderEntity::class],
    version = 5,
    exportSchema = false
)
abstract class NagDatabase : RoomDatabase() {

    abstract fun nagStateDao(): NagStateDao
    abstract fun nagHistoryDao(): NagHistoryDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN sticky INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: NagDatabase? = null

        fun getInstance(context: Context): NagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NagDatabase::class.java,
                    "nag_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
