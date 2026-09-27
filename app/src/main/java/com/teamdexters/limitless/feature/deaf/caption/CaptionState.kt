package com.teamdexters.limitless.feature.deaf.caption

/**
 * Data class representing a single caption line.
 *
 * @property text The recognized text content
 * @property isPartial Whether this is a partial (interim) result or final result
 */
data class CaptionLine(
    val text: String,
    val isPartial: Boolean
)
