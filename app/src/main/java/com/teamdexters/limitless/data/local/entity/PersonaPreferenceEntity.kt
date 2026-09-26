package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing user's accessibility persona preference.
 * Singleton table (single row) storing the current selected persona.
 * Used to customize UI and guidance based on user's accessibility needs.
 */
@Entity(tableName = "persona_preferences")
data class PersonaPreferenceEntity(
    /**
     * Primary key for the preference record.
     * Fixed value of 1 (singleton pattern - only one row exists).
     */
    @PrimaryKey
    val id: Int = 1,

    /**
     * Selected accessibility persona for the user.
     * Valid values: "BLIND", "DEAF", "SPEECH", "MOBILITY", "NONE"
     * - BLIND: For users with visual impairments
     * - DEAF: For users with hearing impairments
     * - SPEECH: For users with speech impairments
     * - MOBILITY: For users with mobility impairments
     * - NONE: No specific accessibility needs
     */
    val selectedPersona: String,

    /**
     * Timestamp when the preference was last updated.
     * Default value is current system time in milliseconds.
     */
    val updatedAt: Long = System.currentTimeMillis()
)