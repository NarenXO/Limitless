package com.teamdexters.limitless.routing.engine

import com.teamdexters.limitless.routing.graph.ChennaiDemoGraph
import com.teamdexters.limitless.routing.graph.Edge
import com.teamdexters.limitless.routing.graph.Node
import com.teamdexters.limitless.routing.model.AccessibilityFilter
import com.teamdexters.limitless.routing.model.Route
import com.teamdexters.limitless.routing.model.RouteStep
import com.teamdexters.limitless.routing.model.TurnType
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device A* / Dijkstra pathfinding router with dynamic accessibility weighting.
 */
class AccessibleRouter(
    private val graphNodes: Map<String, Node> = ChennaiDemoGraph.nodes,
    private val graphEdges: List<Edge> = ChennaiDemoGraph.edges
) {

    private companion object {
        const val IMPASSABLE_PENALTY = 100_000.0
        const val UNVERIFIED_PENALTY = 50.0
    }

    /**
     * Internal node wrapper for Dijkstra / A* priority queue navigation.
     */
    private data class PathNode(
        val nodeId: String,
        val gScore: Double,
        val fScore: Double
    ) : Comparable<PathNode> {
        override fun compareTo(other: PathNode): Int = this.fScore.compareTo(other.fScore)
    }

    /**
     * Calculates an optimal accessible route from [startNodeId] to [destinationNodeId].
     *
     * @param startNodeId       ID of starting waypoint node.
     * @param destinationNodeId ID of target waypoint node.
     * @param filter            Accessibility filter requirements.
     * @return                  Calculated [Route] containing turn instructions and warning flags.
     */
    fun findRoute(
        startNodeId: String,
        destinationNodeId: String,
        filter: AccessibilityFilter = AccessibilityFilter()
    ): Route {
        val start = graphNodes[startNodeId] ?: return createEmptyRoute("Start node '$startNodeId' not found.")
        val dest  = graphNodes[destinationNodeId] ?: return createEmptyRoute("Destination node '$destinationNodeId' not found.")

        if (startNodeId == destinationNodeId) {
            val arriveStep = RouteStep(
                instruction = "You are already at ${dest.name}.",
                distanceMeters = 0,
                turnType = TurnType.ARRIVE,
                latitude = dest.lat,
                longitude = dest.lon
            )
            return Route(
                steps = listOf(arriveStep),
                totalDistanceMeters = 0,
                estimatedTimeSeconds = 0,
                isFullyAccessible = true,
                fallbackWarning = null
            )
        }

        // 1. Attempt weighted accessibility search
        var pathResult = runPathfinding(startNodeId, destinationNodeId, filter)
        var isFallback = false

        // 2. Fallback check: if no route found or cost indicates impassable obstacles
        if (pathResult == null || pathResult.totalCost >= IMPASSABLE_PENALTY) {
            isFallback = true
            pathResult = runPathfinding(startNodeId, destinationNodeId, AccessibilityFilter()) // Unconstrained
        }

        if (pathResult == null) {
            return createEmptyRoute("No pathway exists between ${start.name} and ${dest.name}.")
        }

        // 3. Assemble steps and metrics
        val steps = generateRouteSteps(pathResult.pathEdges)
        val totalDistance = pathResult.pathEdges.sumOf { it.distanceMeters }.toInt()
        val estTimeSeconds = (totalDistance / 1.2).toInt() // 1.2 m/s average walking/wheelchair speed

        val containsUnverified = pathResult.pathEdges.any { !it.isVerifiedAccessible }
        val isFullyAccessible = !isFallback && !containsUnverified

        val fallbackWarning = when {
            isFallback -> "Warning: No fully accessible route found. Showing standard path with potential obstacles."
            containsUnverified -> "Caution: Route includes unverified accessibility segments."
            else -> null
        }

        return Route(
            steps = steps,
            totalDistanceMeters = totalDistance,
            estimatedTimeSeconds = estTimeSeconds,
            isFullyAccessible = isFullyAccessible,
            fallbackWarning = fallbackWarning
        )
    }

    private data class PathfindingResult(
        val nodeIds: List<String>,
        val pathEdges: List<Edge>,
        val totalCost: Double
    )

    private fun runPathfinding(
        startId: String,
        destId: String,
        filter: AccessibilityFilter
    ): PathfindingResult? {
        val adjacency = graphEdges.groupBy { it.fromNodeId }
        val destNode = graphNodes[destId] ?: return null

        val gScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val fScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val parentNode = mutableMapOf<String, String>()
        val parentEdge = mutableMapOf<String, Edge>()

        val openSet = PriorityQueue<PathNode>()

        gScore[startId] = 0.0
        val initialH = heuristic(startId, destNode)
        fScore[startId] = initialH
        openSet.add(PathNode(startId, 0.0, initialH))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currentId = current.nodeId

            if (currentId == destId) {
                // Reconstruct path
                val pathNodeIds = mutableListOf<String>()
                val pathEdges = mutableListOf<Edge>()
                var curr: String? = destId

                while (curr != null && curr != startId) {
                    pathNodeIds.add(0, curr)
                    val edge = parentEdge[curr]
                    if (edge != null) {
                        pathEdges.add(0, edge)
                        curr = parentNode[curr]
                    } else {
                        break
                    }
                }
                pathNodeIds.add(0, startId)
                return PathfindingResult(pathNodeIds, pathEdges, gScore.getValue(destId))
            }

            val neighbors = adjacency[currentId] ?: emptyList()
            for (edge in neighbors) {
                val neighborId = edge.toNodeId
                val edgeCost = computeEdgeCost(edge, filter)
                val tentativeG = gScore.getValue(currentId) + edgeCost

                if (tentativeG < gScore.getValue(neighborId)) {
                    parentNode[neighborId] = currentId
                    parentEdge[neighborId] = edge
                    gScore[neighborId] = tentativeG
                    val fVal = tentativeG + heuristic(neighborId, destNode)
                    fScore[neighborId] = fVal
                    openSet.add(PathNode(neighborId, tentativeG, fVal))
                }
            }
        }

        return null
    }

    private fun computeEdgeCost(edge: Edge, filter: AccessibilityFilter): Double {
        var cost = edge.distanceMeters

        if (filter.requireRamp && edge.hasStairs && !edge.hasRamp) {
            cost += IMPASSABLE_PENALTY
        }

        if (filter.requireLift && edge.hasStairs && !edge.hasLift) {
            cost += IMPASSABLE_PENALTY
        }

        if (filter.requireWideDoorway && edge.doorwayWidthCm != null && edge.doorwayWidthCm < 90) {
            cost += IMPASSABLE_PENALTY
        }

        if (filter.requireAccessibleWashroom && !edge.hasAccessibleWashroom) {
            cost += 500.0 // Soft penalty to prefer accessible washrooms
        }

        if (!edge.isVerifiedAccessible) {
            cost += UNVERIFIED_PENALTY
        }

        return cost
    }

    private fun heuristic(nodeId: String, destNode: Node): Double {
        val node = graphNodes[nodeId] ?: return 0.0
        val lat1 = Math.toRadians(node.lat)
        val lon1 = Math.toRadians(node.lon)
        val lat2 = Math.toRadians(destNode.lat)
        val lon2 = Math.toRadians(destNode.lon)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c // Earth radius in meters
    }

    private fun generateRouteSteps(edges: List<Edge>): List<RouteStep> {
        if (edges.isEmpty()) return emptyList()

        val steps = mutableListOf<RouteStep>()
        var currentBearing = 0.0

        for (i in edges.indices) {
            val edge = edges[i]
            val fromNode = graphNodes[edge.fromNodeId] ?: continue
            val toNode = graphNodes[edge.toNodeId] ?: continue

            val nextBearing = calculateBearing(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon)
            val turnType = if (i == 0) {
                TurnType.STRAIGHT
            } else {
                calculateTurnType(currentBearing, nextBearing)
            }
            currentBearing = nextBearing

            val notes = buildString {
                if (edge.hasRamp) append("Paved accessible ramp. ")
                if (edge.hasLift) append("Elevator available. ")
                if (edge.hasStairs && !edge.hasRamp) append("Staircase (no ramp). ")
                if (edge.doorwayWidthCm != null) append("Doorway width: ${edge.doorwayWidthCm} cm. ")
                if (edge.hasAccessibleWashroom) append("Accessible washroom nearby. ")
                if (!edge.isVerifiedAccessible) append("Unverified segment. ")
            }.trim().ifEmpty { null }

            val actionText = when (turnType) {
                TurnType.STRAIGHT -> "Head straight towards ${toNode.name}"
                TurnType.LEFT -> "Turn left towards ${toNode.name}"
                TurnType.RIGHT -> "Turn right towards ${toNode.name}"
                TurnType.SLIGHT_LEFT -> "Slight left towards ${toNode.name}"
                TurnType.SLIGHT_RIGHT -> "Slight right towards ${toNode.name}"
                TurnType.ARRIVE -> "Arrive at ${toNode.name}"
            }

            steps.add(
                RouteStep(
                    instruction = actionText,
                    distanceMeters = edge.distanceMeters.toInt(),
                    turnType = turnType,
                    latitude = toNode.lat,
                    longitude = toNode.lon,
                    accessibilityNotes = notes
                )
            )
        }

        // Add final ARRIVE step
        val lastEdge = edges.last()
        val finalNode = graphNodes[lastEdge.toNodeId]
        if (finalNode != null) {
            steps.add(
                RouteStep(
                    instruction = "Arrive at ${finalNode.name}",
                    distanceMeters = 0,
                    turnType = TurnType.ARRIVE,
                    latitude = finalNode.lat,
                    longitude = finalNode.lon,
                    accessibilityNotes = "Destination reached."
                )
            )
        }

        return steps
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)

        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360
    }

    private fun calculateTurnType(prevBearing: Double, nextBearing: Double): TurnType {
        var diff = (nextBearing - prevBearing + 360) % 360
        if (diff > 180) diff -= 360

        return when {
            diff in -20.0..20.0 -> TurnType.STRAIGHT
            diff in 20.0..60.0 -> TurnType.SLIGHT_RIGHT
            diff in 60.0..135.0 -> TurnType.RIGHT
            diff in -60.0..-20.0 -> TurnType.SLIGHT_LEFT
            diff in -135.0..-60.0 -> TurnType.LEFT
            else -> TurnType.RIGHT
        }
    }

    private fun createEmptyRoute(warning: String): Route {
        return Route(
            steps = emptyList(),
            totalDistanceMeters = 0,
            estimatedTimeSeconds = 0,
            isFullyAccessible = false,
            fallbackWarning = warning
        )
    }
}
