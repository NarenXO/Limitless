package com.teamdexters.limitless.ui.speech

import com.teamdexters.limitless.ui.speech.data.PhraseUsageDao
import com.teamdexters.limitless.ui.speech.data.PhraseUsageEntity
import java.util.Calendar

/**
 * Pure Kotlin prediction engine that calculates adaptive phrase rankings using
 * context-weighted arithmetic scoring.
 *
 * Scoring formula:
 * - Base score     : usageCount * 3
 * - Time bonus     : +5 if timeOfDaySlot matches current time slot
 * - Recency bonus  : +2 if lastUsedTimestamp is within the last 24 hours
 * - Location bonus : +4 if lastLocationTag matches current location tag
 */
class PhrasePredictionEngine(
    private val dao: PhraseUsageDao
) {

    /**
     * Determines the current time-of-day slot:
     * - "morning"   : 6:00 - 11:59 (hours 6..11)
     * - "afternoon" : 12:00 - 16:59 (hours 12..16)
     * - "evening"   : 17:00 - 20:59 (hours 17..20)
     * - "night"     : 21:00 - 5:59 (hours 21..23, 0..5)
     */
    fun getCurrentTimeOfDaySlot(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    }

    /**
     * Returns current location tag.
     * Hardcoded to "unknown" until real location context is integrated.
     */
    fun getCurrentLocationTag(): String {
        // TODO(Salman): wire to actual location context in integration
        return "unknown"
    }

    /**
     * Scores a given phrase record against current context parameters.
     */
    fun calculateScore(entity: PhraseUsageEntity): Int {
        var score = entity.usageCount * 3

        val currentTimeSlot = getCurrentTimeOfDaySlot()
        if (entity.timeOfDaySlot.equals(currentTimeSlot, ignoreCase = true)) {
            score += 5
        }

        val now = System.currentTimeMillis()
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L
        if (entity.lastUsedTimestamp > 0 && (now - entity.lastUsedTimestamp <= twentyFourHoursMs)) {
            score += 2
        }

        val currentLocation = getCurrentLocationTag()
        if (entity.lastLocationTag.equals(currentLocation, ignoreCase = true)) {
            score += 4
        }

        return score
    }

    /**
     * Returns true if any phrase usage data exists in the local DB.
     */
    suspend fun hasUsageData(): Boolean {
        return dao.getCount() > 0
    }

    /**
     * Gets adaptive phrase recommendations ranked by score.
     *
     * Cold-Start Behavior:
     * If local phrase DB is empty, returns original hardcoded phrases in default order.
     */
    suspend fun getPredictedPhrases(
        defaultPhrases: List<String>,
        limit: Int = 10
    ): List<String> {
        val dbRecords = dao.getAllPhrases().associateBy { it.phraseText }

        // Cold-start check: return static default list if no usage records exist
        if (dbRecords.isEmpty()) {
            return defaultPhrases.take(limit)
        }

        // Combine default phrases with any extra phrases stored in DB
        val allPhraseTexts = (defaultPhrases + dbRecords.keys).distinct()

        // Score each phrase and sort
        val sortedPhrases = allPhraseTexts.mapIndexed { defaultIndex, phrase ->
            val entity = dbRecords[phrase]
            val score = if (entity != null) calculateScore(entity) else 0
            val lastUsed = entity?.lastUsedTimestamp ?: 0L
            
            // Primary sort: Score DESC
            // Secondary sort: Last used DESC
            // Tertiary sort: Default order ASC
            Triple(phrase, score, Pair(lastUsed, -defaultIndex))
        }.sortedWith(
            compareByDescending<Triple<String, Int, Pair<Long, Int>>> { it.second }
                .thenByDescending { it.third.first }
                .thenByDescending { it.third.second }
        ).map { it.first }

        return sortedPhrases.take(limit)
    }

    /**
     * Increments usage statistics for a phrase card when tapped.
     */
    suspend fun recordUsage(phraseText: String) {
        val existing = dao.getPhrase(phraseText)
        val newUsageCount = (existing?.usageCount ?: 0) + 1
        val updatedEntity = PhraseUsageEntity(
            phraseText = phraseText,
            usageCount = newUsageCount,
            lastUsedTimestamp = System.currentTimeMillis(),
            timeOfDaySlot = getCurrentTimeOfDaySlot(),
            lastLocationTag = getCurrentLocationTag()
        )
        dao.insertOrUpdate(updatedEntity)
    }
}
