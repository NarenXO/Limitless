package com.teamdexters.limitless.ui.speech.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Self-contained Room Database for local phrase prediction tracking in Speech mode.
 *
 * Version history:
 * - v1: Original schema (phraseText as primary key).
 * - v2: Added `id` (composite key) and `languageCode` for per-language prediction scoping.
 *       Destructive migration: dev build only — production should use proper migration.
 */
@Database(entities = [PhraseUsageEntity::class], version = 2, exportSchema = false)
abstract class PhraseUsageDatabase : RoomDatabase() {

    abstract fun phraseUsageDao(): PhraseUsageDao

    companion object {
        @Volatile
        private var INSTANCE: PhraseUsageDatabase? = null

        fun getDatabase(context: Context): PhraseUsageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhraseUsageDatabase::class.java,
                    "phrase_usage_db"
                )
                    .fallbackToDestructiveMigration() // safe for dev: resets on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
