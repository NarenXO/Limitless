package com.teamdexters.limitless.routing.indoor

/**
 * Data model for indoor waypoints anchored by QR / NFC tags.
 *
 * @param id          Unique waypoint identifier.
 * @param name        Human-readable waypoint name.
 * @param floor       Floor level index (0 = Ground floor, 1 = 1st floor).
 * @param xMeters     Relative X position on floor plan (in meters from building origin).
 * @param yMeters     Relative Y position on floor plan (in meters from building origin).
 * @param qrPayload   Exact payload string encoded in physical QR code tag.
 * @param description Detailed TTS-friendly description of the waypoint location.
 */
data class IndoorWaypoint(
    val id: String,
    val name: String,
    val floor: Int,
    val xMeters: Float,
    val yMeters: Float,
    val qrPayload: String,
    val description: String
)

/**
 * Pre-configured indoor waypoints for KCG Main Building demo area.
 */
val kcgIndoorWaypoints: List<IndoorWaypoint> = listOf(
    IndoorWaypoint(
        id = "kcg-entrance",
        name = "Main Entrance",
        floor = 0,
        xMeters = 0f,
        yMeters = 0f,
        qrPayload = "LIMITLESS:kcg-entrance",
        description = "Main entrance, ground floor, automatic sliding door with accessible ramp"
    ),
    IndoorWaypoint(
        id = "kcg-reception",
        name = "Reception Desk",
        floor = 0,
        xMeters = 15f,
        yMeters = 5f,
        qrPayload = "LIMITLESS:kcg-reception",
        description = "Reception desk, ground floor, staff assistance available"
    ),
    IndoorWaypoint(
        id = "kcg-lift",
        name = "Central Elevator / Lift",
        floor = 0,
        xMeters = 25f,
        yMeters = 10f,
        qrPayload = "LIMITLESS:kcg-lift",
        description = "Central elevator, ground floor, braille buttons and voice announcements"
    ),
    IndoorWaypoint(
        id = "kcg-restroom",
        name = "Accessible Restroom",
        floor = 0,
        xMeters = 30f,
        yMeters = 2f,
        qrPayload = "LIMITLESS:kcg-restroom",
        description = "Accessible restroom, ground floor, wide automatic door and grab bars"
    ),
    IndoorWaypoint(
        id = "kcg-ramp",
        name = "Ramp to First Floor",
        floor = 0,
        xMeters = 20f,
        yMeters = 12f,
        qrPayload = "LIMITLESS:kcg-ramp",
        description = "Accessible ramp to first floor, gentle 1 to 12 incline slope"
    ),
    IndoorWaypoint(
        id = "kcg-ward",
        name = "Admin Office / Ward",
        floor = 1,
        xMeters = 25f,
        yMeters = 15f,
        qrPayload = "LIMITLESS:kcg-ward",
        description = "Admin office, first floor, double wide entrance doors"
    )
)
