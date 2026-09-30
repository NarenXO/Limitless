package com.teamdexters.limitless.routing

enum class StepDirection {
    STRAIGHT,
    LEFT,
    RIGHT,
    RAMP_UP,
    RAMP_DOWN,
    STAIRS_UP,
    STAIRS_DOWN,
    DESTINATION
}

data class RouteStep(
    val fromRoomId: String,
    val fromRoomName: String,
    val toRoomId: String,
    val toRoomName: String,
    val direction: StepDirection,
    val connectionType: String, // doorway, hallway, ramp, stairs, elevator
    val distanceMeters: Float,
    val spokenInstruction: String
)

data class Route(
    val steps: List<RouteStep>,
    val totalDistanceMeters: Float,
    val hasStairs: Boolean,
    val hasRamps: Boolean,
    val isFullyAccessible: Boolean,
    val summaryText: String
)
