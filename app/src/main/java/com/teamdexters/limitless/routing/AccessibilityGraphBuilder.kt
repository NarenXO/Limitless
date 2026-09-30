package com.teamdexters.limitless.routing

import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import kotlinx.coroutines.flow.first

data class AccessibilityGraph(
    val nodes: Map<String, GraphNode>,
    val adjacencyList: Map<String, List<GraphEdge>>,
    val totalNodes: Int,
    val totalEdges: Int
)

class AccessibilityGraphBuilder(
    private val roomDao: MappedRoomDao,
    private val connectionDao: RoomConnectionDao
) {
    suspend fun buildGraph(): AccessibilityGraph {
        val mappedRooms = roomDao.getAllRooms().first()
        val roomConnections = connectionDao.getAllConnections().first()

        if (mappedRooms.isEmpty()) {
            Log.w("LIMITLESS_TRACE", "GraphBuilder: No mapped rooms found. Returning empty graph.")
            return AccessibilityGraph(emptyMap(), emptyMap(), 0, 0)
        }

        if (roomConnections.isEmpty()) {
            Log.w("LIMITLESS_TRACE", "GraphBuilder: No connections found. Graph will have isolated nodes.")
        }

        val nodesMap = mappedRooms.associate { entity ->
            entity.id to GraphNode(
                roomId = entity.id,
                roomName = entity.roomName,
                buildingName = "Campus",
                floorLevel = 1,
                hasRamp = entity.hasRamp,
                hasStairs = entity.hasStairs,
                hasWideDoor = entity.hasWideDoor,
                hasObstacles = entity.hasObstacles,
                obstacleCount = entity.obstacleCount,
                doorWidthCm = 0,
                latitude = 0.0,
                longitude = 0.0
            )
        }

        val adjList = mutableMapOf<String, MutableList<GraphEdge>>()

        for (connection in roomConnections) {
            if (!nodesMap.containsKey(connection.fromRoomId) || !nodesMap.containsKey(connection.toRoomId)) {
                Log.w("LIMITLESS_TRACE", "GraphBuilder: Skipping edge from ${connection.fromRoomId} to ${connection.toRoomId} because one or both nodes are missing.")
                continue
            }

            val edge = GraphEdge(
                fromRoomId = connection.fromRoomId,
                toRoomId = connection.toRoomId,
                distanceMeters = connection.distanceMeters,
                connectionType = connection.connectionType,
                hasRamp = connection.hasRamp,
                hasStairs = connection.hasStairs,
                doorWidthCm = connection.doorWidthCm.toInt(),
                isBidirectional = true,
                notes = ""
            )

            adjList.getOrPut(connection.fromRoomId) { mutableListOf() }.add(edge)
            
            // Assume all connections bidirectional for now since RoomConnectionEntity lacks it
            val reverseEdge = GraphEdge(
                fromRoomId = connection.toRoomId,
                toRoomId = connection.fromRoomId,
                distanceMeters = connection.distanceMeters,
                connectionType = connection.connectionType,
                hasRamp = connection.hasRamp,
                hasStairs = connection.hasStairs,
                doorWidthCm = connection.doorWidthCm.toInt(),
                isBidirectional = true,
                notes = ""
            )
            adjList.getOrPut(connection.toRoomId) { mutableListOf() }.add(reverseEdge)
        }

        val totalEdges = adjList.values.sumOf { it.size }
        Log.d("LIMITLESS_TRACE", "GraphBuilder: Built graph with ${nodesMap.size} nodes, $totalEdges edges")

        return AccessibilityGraph(
            nodes = nodesMap,
            adjacencyList = adjList,
            totalNodes = nodesMap.size,
            totalEdges = totalEdges
        )
    }
}
