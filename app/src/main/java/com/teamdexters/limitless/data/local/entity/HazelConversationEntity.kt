package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hazel_conversations")
data class HazelConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userMessage: String,
    val hazelResponse: String,
    val persona: String = "general",
    val intent: String = "query",
    val wasActionExecuted: Boolean = false
)
