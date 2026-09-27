package com.teamdexters.limitless.ui.speech.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Data Access Object for phrase usage tracking.
 */
@Dao
interface PhraseUsageDao {

    @Upsert
    suspend fun insertOrUpdate(phrase: PhraseUsageEntity)

    @Query("SELECT * FROM phrase_usage ORDER BY usageCount DESC")
    suspend fun getAllPhrases(): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getTopPhrases(limit: Int): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage WHERE timeOfDaySlot = :slot ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getPhrasesByTimeSlot(slot: String, limit: Int): List<PhraseUsageEntity>

    @Query("SELECT * FROM phrase_usage WHERE phraseText = :phraseText LIMIT 1")
    suspend fun getPhrase(phraseText: String): PhraseUsageEntity?

    @Query("SELECT COUNT(*) FROM phrase_usage")
    suspend fun getCount(): Int
}
