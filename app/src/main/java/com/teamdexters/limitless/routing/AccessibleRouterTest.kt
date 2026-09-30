package com.teamdexters.limitless.routing

import com.teamdexters.limitless.routing.engine.AccessibleRouter
import com.teamdexters.limitless.routing.model.AccessibilityFilter
import com.teamdexters.limitless.routing.model.Route

/**
 * Verification test harness for the Accessible Routing Engine.
 */
object AccessibleRouterTest {

    private val router = AccessibleRouter()

    /**
     * Test 1: Route from KCG Main Gate to Library 2nd Floor with ramp requirement.
     */
    fun testRampRequiredRoute(): Route {
        val filter = AccessibilityFilter(requireRamp = true, requireLift = true)
        return router.findRoute(
            startNodeId = "KCG_MAIN_GATE",
            destinationNodeId = "LIBRARY_2ND_FLOOR",
            filter = filter
        )
    }

    /**
     * Test 2: Unconstrained route from KCG Main Gate to Library 2nd Floor (all filters OFF).
     */
    fun testUnconstrainedRoute(): Route {
        val filter = AccessibilityFilter(requireRamp = false, requireLift = false)
        return router.findRoute(
            startNodeId = "KCG_MAIN_GATE",
            destinationNodeId = "LIBRARY_2ND_FLOOR",
            filter = filter
        )
    }

    /**
     * Verification runner. Checks obstacle avoidance, ramp selection, and fallback warnings.
     */
    fun verifyAll(): String = buildString {
        appendLine("=== ACCESSIBLE ROUTER TEST HARNESS ===")

        val rampRoute = testRampRequiredRoute()
        appendLine("\n1. Ramp + Lift Required Route:")
        appendLine("   - Steps Count: ${rampRoute.steps.size}")
        appendLine("   - Total Distance: ${rampRoute.totalDistanceMeters}m")
        appendLine("   - Est. Time: ${rampRoute.estimatedTimeSeconds}s")
        appendLine("   - Fully Accessible: ${rampRoute.isFullyAccessible}")
        appendLine("   - Fallback Warning: ${rampRoute.fallbackWarning ?: "None"}")

        val hasStairsInRampRoute = rampRoute.steps.any { step ->
            step.accessibilityNotes?.contains("Staircase (no ramp)", ignoreCase = true) == true
        }
        appendLine("   - Contains Stairs: $hasStairsInRampRoute (Expected: false)")

        val unconstrainedRoute = testUnconstrainedRoute()
        appendLine("\n2. Unconstrained Route (Filters OFF):")
        appendLine("   - Steps Count: ${unconstrainedRoute.steps.size}")
        appendLine("   - Total Distance: ${unconstrainedRoute.totalDistanceMeters}m")
        appendLine("   - Fully Accessible: ${unconstrainedRoute.isFullyAccessible}")

        appendLine("\n=== VERIFICATION COMPLETE ===")
    }
}
