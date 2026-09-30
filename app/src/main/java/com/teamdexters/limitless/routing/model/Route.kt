package com.teamdexters.limitless.routing.model

/**
 * Navigation turn direction enum for accessible turn-by-turn guidance.
 */
enum class TurnType {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    ARRIVE
}

/**
 * Represents a single turn-by-turn navigation step in a route.
 *
 * @param instruction         Human-readable instruction (e.g., "Turn left towards the ramp entrance").
 * @param distanceMeters      Distance covered in this step (in meters).
 * @param turnType            Turn direction classification.
 * @param latitude            Latitude of the step waypoint.
 * @param longitude           Longitude of the step waypoint.
 * @param accessibilityNotes  Optional accessibility information (e.g., "Gentle slope (1:12 ramp)").
 */
data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val turnType: TurnType,
    val latitude: Double,
    val longitude: Double,
    val accessibilityNotes: String? = null
)

/**
 * Complete route container containing navigation steps, metrics, and accessibility warnings.
 *
 * @param steps                 List of sequential navigation steps.
 * @param totalDistanceMeters   Total physical distance of the route in meters.
 * @param estimatedTimeSeconds Estimated walking/rolling time in seconds.
 * @param isFullyAccessible     False if forced to fall back to standard path or contains unverified segments.
 * @param fallbackWarning       Non-null warning string if accessibility data is missing or obstacles exist.
 */
data class Route(
    val steps: List<RouteStep>,
    val totalDistanceMeters: Int,
    val estimatedTimeSeconds: Int,
    val isFullyAccessible: Boolean,
    val fallbackWarning: String? = null
)

/**
 * User-configurable accessibility constraints for pathfinding calculations.
 *
 * @param requireRamp               If true, routes will avoid staircases and prioritize ramps.
 * @param requireLift               If true, multi-floor transitions require elevators.
 * @param requireWideDoorway        If true, doorways narrower than 90 cm will be penalized.
 * @param requireAccessibleWashroom If true, prioritizes routes with accessible restrooms.
 */
data class AccessibilityFilter(
    val requireRamp: Boolean = false,
    val requireLift: Boolean = false,
    val requireWideDoorway: Boolean = false,
    val requireAccessibleWashroom: Boolean = false
)
