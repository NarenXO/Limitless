package com.teamdexters.limitless.ui.speech.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Data Access Object for phrase usage tracking.
 * All queries are scoped to a [languageCode] so Tamil/Hindi records
 * do not influence English (or each other's) predictions.
 */
@Dao
interface PhraseUsageDao {

    @Upsert
    suspend fun insertOrUpdate(phrase: PhraseUsageEntity)

    /** Returns all records for a given language, ranked by usage count. */
    @Query("SELECT * FROM phrase_usage WHERE languageCode = :languageCode ORDER BY usageCount DESC")
    suspend fun getAllPhrases(languageCode: String = "en"): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage WHERE languageCode = :languageCode ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getTopPhrases(languageCode: String = "en", limit: Int): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage WHERE languageCode = :languageCode AND timeOfDaySlot = :slot ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getPhrasesByTimeSlot(languageCode: String = "en", slot: String, limit: Int): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage WHERE id = :id LIMIT 1")
    suspend fun getPhrase(id: String): PhraseUsageEntity?

    /** Count of records for a given language (used for cold-start detection). */
    @Query("SELECT COUNT(*) FROM phrase_usage WHERE languageCode = :languageCode")
    suspend fun getCount(languageCode: String = "en"): Int
}
