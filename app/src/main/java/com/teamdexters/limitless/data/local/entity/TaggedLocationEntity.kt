package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a location tagged via QR code, NFC, or OSM (OpenStreetMap).
 * Used for quick identification and retrieval of accessibility information.
 */
@Entity(tableName = "tagged_locations")
data class TaggedLocationEntity(
    /**
     * Primary key for the tagged location.
     * Can be a QR code payload string, OSM node ID, or NFC ID.
     * This field is the unique identifier for the location.
     */
    @PrimaryKey
    val tagId: String,

    /**
     * Human-readable name of the specific location.
     * Examples: "Main Entrance", "Lab 204", "Restroom Block B"
     */
    val name: String,

    /**
     * Name of the building containing this location.
     * Null if the location is not part of a building.
     */
    val buildingName: String?,

    /**
     * Floor level of the location.
     * Default value is 0 (ground floor).
     */
    val floorLevel: Int = 0,

    /**
     * Geographic latitude coordinate of the location.
     * Null if location is indoors or coordinates not available.
     */
    val latitude: Double? = null,

    /**
     * Geographic longitude coordinate of the location.
     * Null if location is indoors or coordinates not available.
     */
    val longitude: Double? = null,

    /**
     * Detailed accessibility notes for this specific location.
     * Contains specific guidance for users based on accessibility features.
     */
    val accessibilityNotes: String,

    /**
     * Indicates whether this location has been verified by the team.
     * Team-verified locations display a badge indicating reliability.
     * Default value is false (not verified).
     */
    val isTeamVerified: Boolean = false,

    /**
     * Timestamp when the location was tagged or last updated.
     * Default value is current system time in milliseconds.
     */
    val timestamp: Long = System.currentTimeMillis()
)