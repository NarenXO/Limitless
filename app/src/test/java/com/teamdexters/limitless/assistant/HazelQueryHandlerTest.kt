package com.teamdexters.limitless.assistant

import com.teamdexters.limitless.assistant.cloud.GeminiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeminiClient] and [HazelQueryHandler] offline/online fallback behavior.
 */
class HazelQueryHandlerTest {

    @Test
    fun testOfflineFallbackMessageConstant() {
        assertEquals(
            "I couldn't understand that. Try again or use the app manually.",
            HazelQueryHandler.OFFLINE_FALLBACK_MESSAGE
        )
    }

    @Test
    fun testGeminiResponseParsingSuccess() {
        val jsonResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "The capital of France is Paris."
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val client = GeminiClient(apiKeyOverride = "test_key")
        val parsed = client.parseGeminiResponse(jsonResponse)
        assertEquals("The capital of France is Paris.", parsed)
    }

    @Test
    fun testGeminiClientFailsWithEmptyApiKey() = runBlocking {
        val client = GeminiClient(apiKeyOverride = "")
        val result = client.queryGemini("What is the capital of France?")
        assertTrue("Expected failure with empty API key", result.isFailure)
    }
}
