package com.teamdexters.limitless.routing

/**
 * Represents a connection between two rooms in the accessibility graph.
 * Mapped from RoomConnectionEntity in the Room database.
 */
data class GraphEdge(
    val fromRoomId: String,
    val toRoomId: String,
    val distanceMeters: Float,
    val connectionType: String,  // doorway, hallway, ramp, stairs, elevator
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val doorWidthCm: Int,
    val isBidirectional: Boolean,
    val notes: String = ""
)
