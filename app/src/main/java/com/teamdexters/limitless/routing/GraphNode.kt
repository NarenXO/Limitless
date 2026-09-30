package com.teamdexters.limitless.routing

/**
 * Represents a room node in the accessibility navigation graph.
 * Mapped from MappedRoomEntity in the Room database.
 */
data class GraphNode(
    val roomId: String,
    val roomName: String,
    val buildingName: String,
    val floorLevel: Int,
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val hasWideDoor: Boolean,
    val hasObstacles: Boolean,
    val obstacleCount: Int,
    val doorWidthCm: Int,
    val latitude: Double?,
    val longitude: Double?
)
