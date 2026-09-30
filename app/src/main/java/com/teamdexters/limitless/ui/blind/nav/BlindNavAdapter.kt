package com.teamdexters.limitless.ui.blind.nav

// TODO(Naren integration): swap MockRouteStep/MockRoute/
//   MockRouter for Salman's AccessibleRouter + Route +
//   RouteStep from mobility module during merge.

/**
 * Mock turn type for navigation instructions.
 */
enum class TurnType {
    LEFT,
    RIGHT,
    STRAIGHT,
    ARRIVE
}

/**
 * Mock route step with instruction, distance, and turn type.
 */
data class MockRouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val turnType: TurnType
)

/**
 * Mock route containing a list of steps.
 */
data class MockRoute(
    val destination: String,
    val steps: List<MockRouteStep>
)

/**
 * Mock router that generates sample routes.
 * In production, this will be replaced by Salman's AccessibleRouter.
 */
object MockRouter {

    /**
     * Get a sample route with 5 steps around a landmark.
     */
    fun getRoute(destination: String): MockRoute {
        return MockRoute(
            destination = destination,
            steps = listOf(
                MockRouteStep(
                    instruction = "Head straight toward the main entrance",
                    distanceMeters = 50,
                    turnType = TurnType.STRAIGHT
                ),
                MockRouteStep(
                    instruction = "Turn left at the corridor",
                    distanceMeters = 30,
                    turnType = TurnType.LEFT
                ),
                MockRouteStep(
                    instruction = "Continue straight for 20 meters",
                    distanceMeters = 20,
                    turnType = TurnType.STRAIGHT
                ),
                MockRouteStep(
                    instruction = "Turn right at the elevator",
                    distanceMeters = 15,
                    turnType = TurnType.RIGHT
                ),
                MockRouteStep(
                    instruction = "You have arrived at your destination",
                    distanceMeters = 0,
                    turnType = TurnType.ARRIVE
                )
            )
        )
    }
}
