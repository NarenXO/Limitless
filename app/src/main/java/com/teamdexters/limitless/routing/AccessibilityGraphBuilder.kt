package com.teamdexters.limitless.routing

import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao

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
        val mappedRooms = roomDao.getAllRooms()
        val roomConnections = connectionDao.getAllConnections()

        if (mappedRooms.isEmpty()) {
            Log.w("LIMITLESS_TRACE", "GraphBuilder: No mapped rooms found. Returning empty graph.")
            return AccessibilityGraph(emptyMap(), emptyMap(), 0, 0)
        }

        if (roomConnections.isEmpty()) {
            Log.w("LIMITLESS_TRACE", "GraphBuilder: No connections found. Graph will have isolated nodes.")
        }

        // Convert MappedRoomEntity -> GraphNode (filtering for isFullyMapped == true)
        val validRooms = mappedRooms.filter { it.isFullyMapped }
        val nodesMap = validRooms.associate { entity ->
            entity.roomId to GraphNode(
                roomId = entity.roomId,
                roomName = entity.roomName,
                buildingName = entity.buildingName,
                floorLevel = entity.floorLevel,
                hasRamp = entity.hasRamp,
                hasStairs = entity.hasStairs,
                hasWideDoor = entity.hasWideDoor,
                hasObstacles = entity.hasObstacles,
                obstacleCount = entity.obstacleCount,
                doorWidthCm = entity.doorWidthCm,
                latitude = entity.latitude,
                longitude = entity.longitude
            )
        }

        val adjList = mutableMapOf<String, MutableList<GraphEdge>>()

        // Convert RoomConnectionEntity -> GraphEdge and build adjacency list
        for (connection in roomConnections) {
            if (!nodesMap.containsKey(connection.fromRoomId) || !nodesMap.containsKey(connection.toRoomId)) {
                Log.w("LIMITLESS_TRACE", "GraphBuilder: Skipping edge from ${connection.fromRoomId} to ${connection.toRoomId} because one or both nodes are missing or not fully mapped.")
                continue
            }

            val edge = GraphEdge(
                fromRoomId = connection.fromRoomId,
                toRoomId = connection.toRoomId,
                distanceMeters = connection.distanceMeters,
                connectionType = connection.connectionType,
                hasRamp = connection.hasRamp,
                hasStairs = connection.hasStairs,
                doorWidthCm = connection.doorWidthCm,
                isBidirectional = connection.isBidirectional
            )

            adjList.getOrPut(connection.fromRoomId) { mutableListOf() }.add(edge)

            if (connection.isBidirectional) {
                val reverseEdge = GraphEdge(
                    fromRoomId = connection.toRoomId,
                    toRoomId = connection.fromRoomId,
                    distanceMeters = connection.distanceMeters,
                    connectionType = connection.connectionType,
                    hasRamp = connection.hasRamp,
                    hasStairs = connection.hasStairs,
                    doorWidthCm = connection.doorWidthCm,
                    isBidirectional = true
                )
                adjList.getOrPut(connection.toRoomId) { mutableListOf() }.add(reverseEdge)
            }
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
