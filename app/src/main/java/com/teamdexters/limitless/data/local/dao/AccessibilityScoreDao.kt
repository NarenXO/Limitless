package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.AccessibilityScoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AccessibilityScoreEntity operations.
 * Provides CRUD operations and queries for AI-analyzed accessibility scores.
 */
@Dao
interface AccessibilityScoreDao {

    /**
     * Inserts a new accessibility score into the database.
     * If a score with the same ID exists, it will be replaced.
     *
     * @param score The AccessibilityScoreEntity to insert
     * @return The ID of the inserted score
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: AccessibilityScoreEntity): Long

    /**
     * Retrieves all accessibility scores from the database, ordered by timestamp (newest first).
     * Returns a Flow that emits updated lists whenever the database changes.
     *
     * @return Flow emitting list of all AccessibilityScoreEntity objects
     */
    @Query("SELECT * FROM accessibility_scores ORDER BY timestamp DESC")
    fun getAllScores(): Flow<List<AccessibilityScoreEntity>>

    /**
     * Retrieves a specific accessibility score by its ID.
     *
     * @param id The ID of the score to retrieve
     * @return The AccessibilityScoreEntity if found, null otherwise
     */
    @Query("SELECT * FROM accessibility_scores WHERE id = :id")
    suspend fun getScoreById(id: Long): AccessibilityScoreEntity?

    /**
     * Deletes a specific accessibility score from the database.
     *
     * @param score The AccessibilityScoreEntity to delete
     */
    @Delete
    suspend fun deleteScore(score: AccessibilityScoreEntity)
}