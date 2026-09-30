package com.teamdexters.limitless.routing

import android.util.Log
import java.util.PriorityQueue

class AStarAccessibleRouter(
    private val graph: AccessibilityGraph
) {
    private data class NodeRecord(
        val nodeId: String,
        val gScore: Double,
        val fScore: Double,
        val previousNodeId: String?,
        val edgeFromPrevious: GraphEdge?
    )

    fun findRoute(
        fromRoomId: String,
        toRoomId: String,
        preferRamp: Boolean = true
    ): Route? {
        val startNode = graph.nodes[fromRoomId]
        val endNode = graph.nodes[toRoomId]

        if (startNode == null || endNode == null) {
            Log.w("LIMITLESS_TRACE", "AStarRouter: No route found from $fromRoomId to $toRoomId (missing nodes)")
            return null
        }

        if (fromRoomId == toRoomId) {
            val step = RouteStep(
                fromRoomId = fromRoomId,
                fromRoomName = startNode.roomName,
                toRoomId = toRoomId,
                toRoomName = startNode.roomName,
                direction = StepDirection.DESTINATION,
                connectionType = "none",
                distanceMeters = 0f,
                spokenInstruction = "You are already at ${startNode.roomName}."
            )
            return Route(
                steps = listOf(step),
                totalDistanceMeters = 0f,
                hasStairs = false,
                hasRamps = false,
                isFullyAccessible = true,
                summaryText = "You are already at ${startNode.roomName}."
            )
        }

        val openSet = PriorityQueue<NodeRecord>(compareBy { it.fScore })
        val gScores = mutableMapOf<String, Double>()
        val cameFrom = mutableMapOf<String, Pair<String, GraphEdge>>()

        gScores[fromRoomId] = 0.0
        openSet.add(
            NodeRecord(
                nodeId = fromRoomId,
                gScore = 0.0,
                fScore = heuristic(startNode, endNode),
                previousNodeId = null,
                edgeFromPrevious = null
            )
        )

        var foundGoal = false

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            if (current.nodeId == toRoomId) {
                foundGoal = true
                break
            }

            // Standard optimization: if we found a shorter path already, skip
            if (current.gScore > (gScores[current.nodeId] ?: Double.MAX_VALUE)) {
                continue
            }

            val edges = graph.adjacencyList[current.nodeId] ?: emptyList()
            for (edge in edges) {
                val neighborId = edge.toRoomId
                val neighborNode = graph.nodes[neighborId] ?: continue

                var cost = edge.distanceMeters.toDouble()
                if (edge.hasStairs && preferRamp) {
                    cost += 1000.0 // Heavy penalty to avoid stairs
                }
                if (edge.hasRamp) {
                    cost = maxOf(0.5, cost - 50.0) // Strong bonus for ramps (clamped to positive)
                }
                if (edge.doorWidthCm < 80) {
                    cost += 200.0 // Penalty for narrow doorways
                }
                if (edge.connectionType.equals("ramp", ignoreCase = true)) {
                    cost = maxOf(0.5, cost - 30.0)
                }

                val tentativeGScore = current.gScore + cost
                val currentNeighborGScore = gScores[neighborId] ?: Double.MAX_VALUE

                if (tentativeGScore < currentNeighborGScore) {
                    cameFrom[neighborId] = Pair(current.nodeId, edge)
                    gScores[neighborId] = tentativeGScore
                    val fScore = tentativeGScore + heuristic(neighborNode, endNode)
                    openSet.add(
                        NodeRecord(
                            nodeId = neighborId,
                            gScore = tentativeGScore,
                            fScore = fScore,
                            previousNodeId = current.nodeId,
                            edgeFromPrevious = edge
                        )
                    )
                }
            }
        }

        if (!foundGoal) {
            Log.w("LIMITLESS_TRACE", "AStarRouter: No route found from $fromRoomId to $toRoomId")
            return null
        }

        // Reconstruct path
        val path = mutableListOf<Pair<String, GraphEdge>>()
        var currId = toRoomId
        while (currId != fromRoomId) {
            val prev = cameFrom[currId] ?: break
            path.add(prev)
            currId = prev.first
        }
        path.reverse()

        var totalDistance = 0f
        var hasStairsInRoute = false
        var hasRampsInRoute = false
        val steps = mutableListOf<RouteStep>()

        for (pair in path) {
            val (nodeAId, edge) = pair
            val nodeA = graph.nodes[nodeAId]!!
            val nodeBId = edge.toRoomId
            val nodeB = graph.nodes[nodeBId]!!

            totalDistance += edge.distanceMeters
            if (edge.hasStairs || edge.connectionType.equals("stairs", ignoreCase = true)) hasStairsInRoute = true
            if (edge.hasRamp || edge.connectionType.equals("ramp", ignoreCase = true)) hasRampsInRoute = true

            val stepDir = when {
                edge.hasRamp || edge.connectionType.equals("ramp", ignoreCase = true) -> {
                    if (nodeB.floorLevel > nodeA.floorLevel) StepDirection.RAMP_UP else StepDirection.RAMP_DOWN
                }
                edge.hasStairs || edge.connectionType.equals("stairs", ignoreCase = true) -> {
                    if (nodeB.floorLevel > nodeA.floorLevel) StepDirection.STAIRS_UP else StepDirection.STAIRS_DOWN
                }
                edge.connectionType.equals("elevator", ignoreCase = true) -> StepDirection.STRAIGHT
                else -> {
                    val lowerNotes = edge.notes.lowercase()
                    if (lowerNotes.contains("left")) StepDirection.LEFT
                    else if (lowerNotes.contains("right")) StepDirection.RIGHT
                    else StepDirection.STRAIGHT
                }
            }

            val spokenInstruction = when {
                edge.connectionType.equals("doorway", ignoreCase = true) -> "Exit ${nodeA.roomName} through the ${if (edge.doorWidthCm >= 90) "wide " else ""}door into ${nodeB.roomName}."
                edge.connectionType.equals("ramp", ignoreCase = true) || edge.hasRamp -> "Take the accessible ramp from ${nodeA.roomName} to ${nodeB.roomName}."
                edge.connectionType.equals("stairs", ignoreCase = true) || edge.hasStairs -> "Take the stairs from ${nodeA.roomName} to ${nodeB.roomName}."
                edge.connectionType.equals("elevator", ignoreCase = true) -> "Take the elevator to floor ${nodeB.floorLevel} for ${nodeB.roomName}."
                else -> "Proceed along the hallway to ${nodeB.roomName}."
            }

            steps.add(
                RouteStep(
                    fromRoomId = nodeA.roomId,
                    fromRoomName = nodeA.roomName,
                    toRoomId = nodeB.roomId,
                    toRoomName = nodeB.roomName,
                    direction = stepDir,
                    connectionType = edge.connectionType,
                    distanceMeters = edge.distanceMeters,
                    spokenInstruction = spokenInstruction
                )
            )
        }

        // Final arrival step
        steps.add(
            RouteStep(
                fromRoomId = endNode.roomId,
                fromRoomName = endNode.roomName,
                toRoomId = endNode.roomId,
                toRoomName = endNode.roomName,
                direction = StepDirection.DESTINATION,
                connectionType = "none",
                distanceMeters = 0f,
                spokenInstruction = "You have arrived at ${endNode.roomName}."
            )
        )

        val isFullyAccessible = !hasStairsInRoute
        val summaryText = "Route to ${endNode.roomName}: ${totalDistance.toInt()}m with ${if (hasRampsInRoute) "ramp access" else "standard path"}."

        Log.d("LIMITLESS_TRACE", "AStarRouter: Found route from $fromRoomId to $toRoomId (${steps.size} steps, ${totalDistance}m)")

        return Route(
            steps = steps,
            totalDistanceMeters = totalDistance,
            hasStairs = hasStairsInRoute,
            hasRamps = hasRampsInRoute,
            isFullyAccessible = isFullyAccessible,
            summaryText = summaryText
        )
    }

    private fun heuristic(a: GraphNode, b: GraphNode): Double {
        val latA = a.latitude
        val lngA = a.longitude
        val latB = b.latitude
        val lngB = b.longitude

        if (latA != null && lngA != null && latB != null && lngB != null) {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(latB - latA)
            val dLon = Math.toRadians(lngB - lngA)
            val radLatA = Math.toRadians(latA)
            val radLatB = Math.toRadians(latB)

            val sinDLat = kotlin.math.sin(dLat / 2)
            val sinDLon = kotlin.math.sin(dLon / 2)

            val aParam = sinDLat * sinDLat + sinDLon * sinDLon * kotlin.math.cos(radLatA) * kotlin.math.cos(radLatB)
            val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(aParam), kotlin.math.sqrt(1 - aParam))
            return r * c
        }
        return 0.0
    }
}
