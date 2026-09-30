package com.teamdexters.limitless.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.teamdexters.limitless.data.local.dao.AccessibilityScoreDao
import com.teamdexters.limitless.data.local.dao.PersonaPreferenceDao
import com.teamdexters.limitless.data.local.dao.TaggedLocationDao
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.AccessibilityScoreEntity
import com.teamdexters.limitless.data.local.entity.PersonaPreferenceEntity
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao

/**
 * Main Room database for the Limitless application.
 * Provides singleton access to all database entities and DAOs.
 *
 * Database version 1 includes:
 * - UserReportEntity: User-reported accessibility issues/features
 * - AccessibilityScoreEntity: AI-analyzed accessibility scores
 * - TaggedLocationEntity: QR/NFC/OSM tagged locations
 * - PersonaPreferenceEntity: User's accessibility persona preference
 */
@Database(
    entities = [
        UserReportEntity::class,
        AccessibilityScoreEntity::class,
        TaggedLocationEntity::class,
        PersonaPreferenceEntity::class,
        MappedRoomEntity::class,
        RoomConnectionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LimitlessDatabase : RoomDatabase() {

    /**
     * Provides access to UserReportEntity operations.
     * Used for CRUD operations on user accessibility reports.
     */
    abstract fun userReportDao(): UserReportDao

    /**
     * Provides access to AccessibilityScoreEntity operations.
     * Used for CRUD operations on AI-analyzed accessibility scores.
     */
    abstract fun accessibilityScoreDao(): AccessibilityScoreDao

    /**
     * Provides access to TaggedLocationEntity operations.
     * Used for CRUD operations on QR/NFC/OSM tagged locations.
     */
    abstract fun taggedLocationDao(): TaggedLocationDao

    /**
     * Provides access to PersonaPreferenceEntity operations.
     * Used for managing the user's accessibility persona preference.
     */
    abstract fun personaPreferenceDao(): PersonaPreferenceDao

    abstract fun mappedRoomDao(): MappedRoomDao

    abstract fun roomConnectionDao(): RoomConnectionDao

    companion object {
        /**
         * Singleton instance of the LimitlessDatabase.
         * Volatile to ensure visibility across threads.
         */
        @Volatile
        private var INSTANCE: LimitlessDatabase? = null

        /**
         * Returns the singleton instance of the LimitlessDatabase.
         * Uses double-checked locking pattern for thread-safe lazy initialization.
         *
         * @param context Application context for database creation
         * @return The singleton LimitlessDatabase instance
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `mapped_rooms` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `hasRamp` INTEGER NOT NULL, `hasWideDoor` INTEGER NOT NULL, `isFullyMapped` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `room_connections` (`sourceRoomId` TEXT NOT NULL, `targetRoomId` TEXT NOT NULL, `hasSteps` INTEGER NOT NULL, `isElevator` INTEGER NOT NULL, PRIMARY KEY(`sourceRoomId`, `targetRoomId`))")
            }
        }

        fun getDatabase(context: Context): LimitlessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LimitlessDatabase::class.java,
                    "limitless_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}