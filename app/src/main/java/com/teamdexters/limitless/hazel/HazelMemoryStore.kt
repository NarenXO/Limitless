package com.teamdexters.limitless.hazel

import android.util.Log
import com.teamdexters.limitless.data.local.dao.HazelConversationDao
import com.teamdexters.limitless.data.local.entity.HazelConversationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazelMemoryStore @Inject constructor(
    private val conversationDao: HazelConversationDao
) {

    suspend fun saveTurn(
        userMessage: String,
        hazelResponse: String,
        persona: String,
        intent: String,
        wasActionExecuted: Boolean
    ) {
        val entity = HazelConversationEntity(
            userMessage = userMessage,
            hazelResponse = hazelResponse,
            persona = persona,
            intent = intent,
            wasActionExecuted = wasActionExecuted
        )
        conversationDao.insertConversation(entity)
        Log.d("LIMITLESS_TRACE", "HazelMemoryStore: Saved turn -> User: $userMessage | Hazel: $hazelResponse")
    }

    suspend fun getRecentTurns(limit: Int = 10): List<HazelConversationEntity> {
        val turns = conversationDao.getRecentConversations(limit).reversed()
        Log.d("LIMITLESS_TRACE", "HazelMemoryStore: Loaded ${turns.size} recent turns")
        return turns
    }

    fun observeRecentTurns(limit: Int = 20): Flow<List<HazelConversationEntity>> {
        return conversationDao.observeRecentConversations(limit)
    }

    suspend fun getFormattedHistoryForPrompt(limit: Int = 3): String {
        val turns = getRecentTurns(limit)
        if (turns.isEmpty()) return ""

        val builder = java.lang.StringBuilder("Recent conversation history with user:\n")
        for (turn in turns) {
            builder.append("User: ${turn.userMessage}\n")
            builder.append("Hazel: ${turn.hazelResponse}\n")
        }
        builder.append("\nUse this context to answer naturally without repeating previous answers.\n\n")
        return builder.toString()
    }

    suspend fun clearHistory() {
        conversationDao.clearAll()
        Log.d("LIMITLESS_TRACE", "HazelMemoryStore: Cleared conversation history")
    }
}
