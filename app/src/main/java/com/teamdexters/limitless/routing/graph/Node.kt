package com.teamdexters.limitless.routing.graph

/**
 * Represents a topological waypoint node in the campus network graph.
 *
 * @param id   Unique identifier for the node (e.g., "MAIN_GATE").
 * @param name Human-readable location name (e.g., "KCG Campus Main Gate").
 * @param lat  Latitude coordinate.
 * @param lon  Longitude coordinate.
 */
data class Node(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
)
