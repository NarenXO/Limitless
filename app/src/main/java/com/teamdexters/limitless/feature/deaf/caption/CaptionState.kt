package com.teamdexters.limitless.feature.deaf.caption

/**
 * Represents a single line of caption text.
 */
data class CaptionLine(
    val id: String,
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Engine status for the caption system.
 */
enum class CaptionEngineStatus {
    IDLE,
    LISTENING,
    MODEL_MISSING,
    MIC_DENIED,
    ERROR
}

/**
 * UI state holder for the caption system.
 */
data class CaptionUiState(
    val status: CaptionEngineStatus = CaptionEngineStatus.IDLE,
    val partialText: String = "",
    val captionLines: List<CaptionLine> = emptyList()
)
