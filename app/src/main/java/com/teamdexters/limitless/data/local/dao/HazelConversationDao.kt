package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.HazelConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HazelConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(entity: HazelConversationEntity): Long

    @Query("SELECT * FROM hazel_conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int = 10): List<HazelConversationEntity>

    @Query("SELECT * FROM hazel_conversations ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentConversations(limit: Int = 20): Flow<List<HazelConversationEntity>>

    @Query("DELETE FROM hazel_conversations")
    suspend fun clearAll()
}
