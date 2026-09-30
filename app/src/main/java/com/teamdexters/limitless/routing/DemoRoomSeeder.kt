package com.teamdexters.limitless.routing

// TODO(Naren): Call DemoRoomSeeder.seedIfEmpty() in MainActivity.onCreate()
// or in the LimitlessDatabase initialization callback, BEFORE any
// navigation or routing queries are made.

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity

/**
 * Pre-seeds the Room database with 5 demo rooms and 6 connections
 * so the A* router works immediately during live demos without
 * requiring QR code scanning.
 *
 * Triggered only once: when the mapped_rooms table is empty.
 */
class DemoRoomSeeder(
    private val context: Context,
    private val roomDao: MappedRoomDao,
    private val connectionDao: RoomConnectionDao
) {
    companion object {
        private const val TAG = "LIMITLESS_TRACE"
        private const val PREFS_NAME = "limitless_demo_seed"
        private const val KEY_SEEDED = "has_seeded_demo_rooms"
    }

    suspend fun seedIfEmpty() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            Log.d(TAG, "DemoRoomSeeder: Already seeded, skipping")
            return
        }

        val existingRooms = roomDao.getAllRooms()
        if (existingRooms.isNotEmpty()) {
            Log.d(TAG, "DemoRoomSeeder: Rooms already exist (${existingRooms.size}), skipping")
            prefs.edit().putBoolean(KEY_SEEDED, true).commit()
            return
        }

        Log.d(TAG, "DemoRoomSeeder: Seeding 5 demo rooms and 6 connections...")

        // === 5 DEMO ROOMS (KCG Campus) ===
        val demoRooms = listOf(
            MappedRoomEntity(
                roomId = "LIMITLESS_ROOM_KCG_ENTRANCE",
                roomName = "Main Entrance",
                buildingName = "KCG College",
                floorLevel = 0,
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                doorWidthCm = 120,
                aiDescription = "Wide automatic sliding door with ramp access. Fully wheelchair accessible.",
                isFullyMapped = true
            ),
            MappedRoomEntity(
                roomId = "LIMITLESS_ROOM_KCG_HALLWAY",
                roomName = "Central Hallway",
                buildingName = "KCG College",
                floorLevel = 0,
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                doorWidthCm = 150,
                aiDescription = "Wide open hallway connecting all ground floor rooms. Flat surface, no obstacles.",
                isFullyMapped = true
            ),
            MappedRoomEntity(
                roomId = "LIMITLESS_ROOM_KCG_LIBRARY",
                roomName = "Library",
                buildingName = "KCG College",
                floorLevel = 1,
                hasRamp = true,
                hasStairs = true,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                doorWidthCm = 100,
                aiDescription = "First floor library accessible via ramp or elevator. Wide entrance door.",
                isFullyMapped = true
            ),
            MappedRoomEntity(
                roomId = "LIMITLESS_ROOM_KCG_CANTEEN",
                roomName = "Canteen",
                buildingName = "KCG College",
                floorLevel = 0,
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                doorWidthCm = 110,
                aiDescription = "Ground floor canteen with ramp access and wide doorway. Accessible seating available.",
                isFullyMapped = true
            ),
            MappedRoomEntity(
                roomId = "LIMITLESS_ROOM_KCG_AUDITORIUM",
                roomName = "Auditorium",
                buildingName = "KCG College",
                floorLevel = 0,
                hasRamp = true,
                hasStairs = true,
                hasWideDoor = false,
                hasObstacles = true,
                obstacleCount = 2,
                doorWidthCm = 85,
                aiDescription = "Main auditorium. Stairs at front entrance but side ramp access available. Some seating obstacles near stage.",
                isFullyMapped = true
            )
        )

        // === 6 DEMO CONNECTIONS ===
        // Entrance ↔ Hallway (wide, ramp — primary accessible path)
        // Hallway ↔ Library  (via ramp to first floor)
        // Hallway ↔ Canteen  (flat doorway)
        // Hallway ↔ Auditorium (wide hallway)
        // Entrance ↔ Canteen  (direct shortcut)
        // Canteen  ↔ Auditorium (adjacent rooms)
        val demoConnections = listOf(
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_ENTRANCE",
                toRoomId = "LIMITLESS_ROOM_KCG_HALLWAY",
                connectionType = "hallway",
                hasRamp = true,
                hasStairs = false,
                doorWidthCm = 150,
                distanceMeters = 10f,
                isBidirectional = true,
                notes = "Main hallway from entrance. Fully accessible."
            ),
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_HALLWAY",
                toRoomId = "LIMITLESS_ROOM_KCG_LIBRARY",
                connectionType = "ramp",
                hasRamp = true,
                hasStairs = true,
                doorWidthCm = 100,
                distanceMeters = 20f,
                isBidirectional = true,
                notes = "Ramp on left side of hallway leads to library. Stairs also available on right."
            ),
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_HALLWAY",
                toRoomId = "LIMITLESS_ROOM_KCG_CANTEEN",
                connectionType = "doorway",
                hasRamp = false,
                hasStairs = false,
                doorWidthCm = 110,
                distanceMeters = 8f,
                isBidirectional = true,
                notes = "Flat doorway to canteen. Straight ahead."
            ),
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_HALLWAY",
                toRoomId = "LIMITLESS_ROOM_KCG_AUDITORIUM",
                connectionType = "stairs",
                hasRamp = false,
                hasStairs = true,
                doorWidthCm = 120,
                distanceMeters = 15f,
                isBidirectional = true,
                notes = "Turn right at the end of central hallway."
            ),
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_ENTRANCE",
                toRoomId = "LIMITLESS_ROOM_KCG_CANTEEN",
                connectionType = "doorway",
                hasRamp = true,
                hasStairs = false,
                doorWidthCm = 110,
                distanceMeters = 12f,
                isBidirectional = true,
                notes = "Direct path from entrance to canteen, left side."
            ),
            RoomConnectionEntity(
                fromRoomId = "LIMITLESS_ROOM_KCG_CANTEEN",
                toRoomId = "LIMITLESS_ROOM_KCG_AUDITORIUM",
                connectionType = "doorway",
                hasRamp = false,
                hasStairs = false,
                doorWidthCm = 90,
                distanceMeters = 10f,
                isBidirectional = true,
                notes = "Side door between canteen and auditorium."
            )
        )

        // Insert all rooms then all connections
        demoRooms.forEach { roomDao.insertRoom(it) }
        demoConnections.forEach { connectionDao.insertConnection(it) }

        prefs.edit().putBoolean(KEY_SEEDED, true).commit()
        Log.d(TAG, "DemoRoomSeeder: Successfully seeded ${demoRooms.size} rooms and ${demoConnections.size} connections")
    }
}
