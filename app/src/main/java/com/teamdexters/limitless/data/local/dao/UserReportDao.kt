package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserReportEntity operations.
 * Provides CRUD operations and queries for user accessibility reports.
 */
@Dao
interface UserReportDao {

    /**
     * Inserts a new user report into the database.
     * If a report with the same ID exists, it will be replaced.
     *
     * @param report The UserReportEntity to insert
     * @return The ID of the inserted report
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: UserReportEntity): Long

    /**
     * Retrieves all user reports from the database, ordered by timestamp (newest first).
     * Returns a Flow that emits updated lists whenever the database changes.
     *
     * @return Flow emitting list of all UserReportEntity objects
     */
    @Query("SELECT * FROM user_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<UserReportEntity>>

    @Query("SELECT * FROM user_reports")
    suspend fun getAllReportsOnce(): List<UserReportEntity>

    @Query("SELECT COUNT(*) FROM user_reports")
    suspend fun getReportCount(): Int

    /**
     * Retrieves all user reports that are pending synchronization with the remote database.
     * Used for syncing local data to Supabase.
     *
     * @return List of UserReportEntity objects with syncStatus = "PENDING"
     */
    @Query("SELECT * FROM user_reports WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncReports(): List<UserReportEntity>

    /**
     * Updates the synchronization status of a specific report.
     * Used to mark reports as synced or failed after sync attempts.
     *
     * @param id The ID of the report to update
     * @param status The new sync status ("PENDING", "SYNCED", or "FAILED")
     */
    @Query("UPDATE user_reports SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    /**
     * Deletes a specific user report from the database.
     *
     * @param report The UserReportEntity to delete
     */
    @Delete
    suspend fun deleteReport(report: UserReportEntity)

    /**
     * Increments the confirmation count of a report by 1.
     * Used for community upvoting features.
     */
    @Query("UPDATE user_reports SET confirmationCount = confirmationCount + 1 WHERE id = :id")
    suspend fun incrementConfirmationCount(id: Long)
}