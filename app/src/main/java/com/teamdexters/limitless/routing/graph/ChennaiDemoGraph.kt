package com.teamdexters.limitless.routing.graph

/**
 * Bundled graph containing topological map data for KCG College of Technology campus,
 * Karapakkam / Sholinganallur, Chennai.
 *
 * Includes ~18 nodes and realistic edges with accessibility parameters (ramps, stairs,
 * elevators, door widths, washrooms, and unverified pathways).
 */
object ChennaiDemoGraph {

    val nodes: Map<String, Node> = listOf(
        Node("KCG_MAIN_GATE",           "KCG Main Gate",                  12.90650, 80.22800),
        Node("BUS_STOP",                "OMR Bus Stop (Karapakkam)",       12.90630, 80.22720),
        Node("MAIN_PLAZA",              "Campus Main Plaza",              12.90670, 80.22830),
        Node("ADMIN_BLOCK",             "Admin Block Entrance",           12.90680, 80.22850),
        Node("TECH_BLOCK_STAIRS",       "Tech Block Entrance (Stairs)",   12.90720, 80.22900),
        Node("TECH_BLOCK_RAMP",         "Tech Block Ramp Entrance",       12.90730, 80.22880),
        Node("TECH_BLOCK_LOBBY",        "Tech Block Central Lobby",       12.90740, 80.22910),
        Node("LIBRARY_ENTRANCE_STAIRS", "Library Entrance (Staircase)",   12.90750, 80.22940),
        Node("LIBRARY_ENTRANCE_RAMP",   "Library Ramp Entrance",          12.90755, 80.22935),
        Node("LIBRARY_GROUND",          "Library Ground Floor",           12.90760, 80.22950),
        Node("LIBRARY_LIFT_LOBBY",      "Library Elevator Lobby",         12.90765, 80.22960),
        Node("LIBRARY_2ND_FLOOR",       "Library 2nd Floor (Quiet Zone)", 12.90770, 80.22950),
        Node("CAFETERIA",               "Student Cafeteria",              12.90620, 80.22900),
        Node("RESTROOM_ACCESSIBLE",     "Ground Floor Accessible Restroom",12.90700, 80.22870),
        Node("RESTROOM_STANDARD",       "Standard Restroom (Narrow Door)",12.90745, 80.22925),
        Node("AUDITORIUM",              "Main Auditorium Entrance",       12.90600, 80.22780),
        Node("SPORTS_COMPLEX",          "Sports Complex Pathway",         12.90580, 80.22850),
        Node("UNVERIFIED_SHORTCUT",     "Rear Service Pathway (Unverified)", 12.90780, 80.23000)
    ).associateBy { it.id }

    val edges: List<Edge> = listOf(
        // Gate to Plaza & Bus Stop
        Edge("BUS_STOP", "KCG_MAIN_GATE", 80.0, hasRamp = true, doorwayWidthCm = 150),
        Edge("KCG_MAIN_GATE", "MAIN_PLAZA", 45.0, hasRamp = true, doorwayWidthCm = 200),

        // Plaza to Admin & Auditorium & Cafeteria
        Edge("MAIN_PLAZA", "ADMIN_BLOCK", 30.0, hasRamp = true, doorwayWidthCm = 110),
        Edge("MAIN_PLAZA", "AUDITORIUM", 70.0, hasRamp = true, doorwayWidthCm = 120),
        Edge("MAIN_PLAZA", "CAFETERIA", 85.0, hasRamp = true, doorwayWidthCm = 100),
        Edge("MAIN_PLAZA", "RESTROOM_ACCESSIBLE", 40.0, hasRamp = true, doorwayWidthCm = 100, hasAccessibleWashroom = true),

        // Plaza to Tech Block (2 alternative paths: Stairs vs Ramp)
        Edge("MAIN_PLAZA", "TECH_BLOCK_STAIRS", 60.0, hasStairs = true, doorwayWidthCm = 110),
        Edge("MAIN_PLAZA", "TECH_BLOCK_RAMP", 75.0, hasRamp = true, doorwayWidthCm = 110),
        Edge("TECH_BLOCK_STAIRS", "TECH_BLOCK_LOBBY", 15.0, hasStairs = true, doorwayWidthCm = 95),
        Edge("TECH_BLOCK_RAMP", "TECH_BLOCK_LOBBY", 20.0, hasRamp = true, doorwayWidthCm = 110),

        // Tech Block Lobby to Restroom & Library
        Edge("TECH_BLOCK_LOBBY", "RESTROOM_STANDARD", 20.0, hasStairs = false, doorwayWidthCm = 75, hasAccessibleWashroom = false),
        Edge("TECH_BLOCK_LOBBY", "LIBRARY_ENTRANCE_STAIRS", 45.0, hasStairs = true, doorwayWidthCm = 100),
        Edge("TECH_BLOCK_LOBBY", "LIBRARY_ENTRANCE_RAMP", 55.0, hasRamp = true, doorwayWidthCm = 100),

        // Library Entrance (Stairs vs Ramp) to Library Ground Floor
        Edge("LIBRARY_ENTRANCE_STAIRS", "LIBRARY_GROUND", 15.0, hasStairs = true, doorwayWidthCm = 100),
        Edge("LIBRARY_ENTRANCE_RAMP", "LIBRARY_GROUND", 20.0, hasRamp = true, doorwayWidthCm = 110),

        // Library Ground Floor to Elevator vs Staircase to 2nd Floor
        Edge("LIBRARY_GROUND", "LIBRARY_LIFT_LOBBY", 25.0, hasRamp = true, doorwayWidthCm = 100),
        Edge("LIBRARY_LIFT_LOBBY", "LIBRARY_2ND_FLOOR", 10.0, hasLift = true, doorwayWidthCm = 110),
        Edge("LIBRARY_GROUND", "LIBRARY_2ND_FLOOR", 35.0, hasStairs = true, doorwayWidthCm = 90),

        // Cafeteria to Sports & Unverified Shortcut
        Edge("CAFETERIA", "SPORTS_COMPLEX", 65.0, hasRamp = true, doorwayWidthCm = 120),
        Edge("LIBRARY_GROUND", "UNVERIFIED_SHORTCUT", 50.0, hasRamp = true, isVerifiedAccessible = false),
        Edge("UNVERIFIED_SHORTCUT", "LIBRARY_2ND_FLOOR", 40.0, hasStairs = true, isVerifiedAccessible = false)
    ).flatMap { edge ->
        // Return bidirectional edge list
        listOf(edge, edge.copy(fromNodeId = edge.toNodeId, toNodeId = edge.fromNodeId))
    }
}
