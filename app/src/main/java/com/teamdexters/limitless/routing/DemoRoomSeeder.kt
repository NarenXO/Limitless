package com.teamdexters.limitless.routing

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.flow.first

class DemoRoomSeeder(
    private val context: Context,
    private val roomDao: MappedRoomDao,
    private val connectionDao: RoomConnectionDao
) {
    suspend fun seedIfEmpty() {
        val existingRooms = roomDao.getAllRooms().first()
        if (existingRooms.isNotEmpty()) {
            Log.d("LIMITLESS_TRACE", "DemoRoomSeeder: Database already has rooms. Skipping seed.")
            return
        }

        Log.d("LIMITLESS_TRACE", "DemoRoomSeeder: Seeding default campus rooms and connections...")
        val ts = System.currentTimeMillis()

        val demoRooms = listOf(
            MappedRoomEntity(
                id = "room_entrance",
                roomName = "Main Entrance",
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                notes = "",
                timestamp = ts
            ),
            MappedRoomEntity(
                id = "room_library",
                roomName = "Central Library",
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                notes = "",
                timestamp = ts
            ),
            MappedRoomEntity(
                id = "room_canteen",
                roomName = "Student Canteen",
                hasRamp = false,
                hasStairs = true,
                hasWideDoor = true,
                hasObstacles = true,
                obstacleCount = 2,
                notes = "",
                timestamp = ts
            ),
            MappedRoomEntity(
                id = "room_restroom_1",
                roomName = "Accessible Restroom (Floor 1)",
                hasRamp = true,
                hasStairs = false,
                hasWideDoor = true,
                hasObstacles = false,
                obstacleCount = 0,
                notes = "",
                timestamp = ts
            )
        )

        demoRooms.forEach { roomDao.insertRoom(it) }

        val roomConnections = listOf(
            RoomConnectionEntity(
                id = java.util.UUID.randomUUID().toString(),
                fromRoomId = "room_entrance",
                toRoomId = "room_library",
                connectionType = "Hallway",
                hasRamp = true,
                hasStairs = false,
                distanceMeters = 15f,
                doorWidthCm = 95f,
                timestamp = ts
            ),
            RoomConnectionEntity(
                id = java.util.UUID.randomUUID().toString(),
                fromRoomId = "room_library",
                toRoomId = "room_canteen",
                connectionType = "Staircase",
                hasRamp = false,
                hasStairs = true,
                distanceMeters = 25f,
                doorWidthCm = 90f,
                timestamp = ts
            ),
            RoomConnectionEntity(
                id = java.util.UUID.randomUUID().toString(),
                fromRoomId = "room_entrance",
                toRoomId = "room_restroom_1",
                connectionType = "Hallway",
                hasRamp = true,
                hasStairs = false,
                distanceMeters = 10f,
                doorWidthCm = 100f,
                timestamp = ts
            )
        )

        roomConnections.forEach { connectionDao.insertConnection(it) }
        Log.d("LIMITLESS_TRACE", "DemoRoomSeeder: Seeding complete.")
    }
}
