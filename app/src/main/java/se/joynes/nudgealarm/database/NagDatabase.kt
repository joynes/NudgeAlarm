package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NagStateEntity::class, NagHistoryEntity::class, ReminderEntity::class, SavedPlaceEntity::class, SavedPlaceLocationEntity::class],
    version = 7,
    exportSchema = false
)
abstract class NagDatabase : RoomDatabase() {

    abstract fun nagStateDao(): NagStateDao
    abstract fun nagHistoryDao(): NagHistoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN sticky INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_places (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, activeLocationId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_place_locations (id TEXT NOT NULL PRIMARY KEY, placeId TEXT NOT NULL, label TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, radiusMeters INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_place_locations_placeId ON saved_place_locations(placeId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE reminders SET sortOrder = rowid - 1")
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
