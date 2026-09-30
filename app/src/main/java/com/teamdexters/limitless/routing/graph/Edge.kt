package com.teamdexters.limitless.routing.graph

/**
 * Represents a connection edge between two nodes in the campus routing graph.
 *
 * @param fromNodeId            Source node ID.
 * @param toNodeId              Destination node ID.
 * @param distanceMeters        Physical length of the edge segment in meters.
 * @param hasRamp               True if edge includes an accessible ramp.
 * @param hasStairs             True if edge contains stairs.
 * @param hasLift               True if edge utilizes an elevator.
 * @param doorwayWidthCm        Clear doorway opening width in cm (if applicable).
 * @param hasAccessibleWashroom True if this segment connects to an accessible restroom.
 * @param isVerifiedAccessible  True if accessibility attributes are verified by survey data.
 */
data class Edge(
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Double,
    val hasRamp: Boolean = false,
    val hasStairs: Boolean = false,
    val hasLift: Boolean = false,
    val doorwayWidthCm: Int? = null,
    val hasAccessibleWashroom: Boolean = false,
    val isVerifiedAccessible: Boolean = true
)
