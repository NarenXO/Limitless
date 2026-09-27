package com.teamdexters.limitless.ui.blind

// TODO(Naren integration): swap MockRouteStep for real Route class from Salman's mobility module during merge.

/**
 * Turn type for navigation instructions.
 */
enum class TurnType {
    LEFT,
    RIGHT,
    STRAIGHT,
    ARRIVE
}

/**
 * Mock route step for turn-by-turn navigation.
 * Self-contained data class for Phase 5 testing.
 * 
 * @param instruction Human-readable navigation instruction
 * @param distanceMeters Distance to this step in meters
 * @param turnType Type of turn or action at this step
 */
data class MockRouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val turnType: TurnType
)

/**
 * Sample mock route for testing turn-by-turn navigation.
 * Represents a short walking route with 5 steps.
 */
val sampleMockRoute = listOf(
    MockRouteStep("Head straight for 30 meters", 30, TurnType.STRAIGHT),
    MockRouteStep("In 20 meters, turn left onto the ramp", 20, TurnType.LEFT),
    MockRouteStep("Continue straight for 40 meters", 40, TurnType.STRAIGHT),
    MockRouteStep("Turn right at the entrance", 10, TurnType.RIGHT),
    MockRouteStep("You have arrived", 0, TurnType.ARRIVE)
)
