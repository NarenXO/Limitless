package com.teamdexters.limitless.data.seed

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseSeeder {
    private const val PREFS_NAME = "limitless_prefs"
    private const val KEY_HAS_SEEDED_CHENNAI = "has_seeded_chennai_data"
    private const val KEY_HAS_SEEDED_ROOMS = "has_seeded_rooms_data"

    suspend fun seedIfEmpty(
        userReportDao: UserReportDao,
        mappedRoomDao: MappedRoomDao,
        roomConnectionDao: RoomConnectionDao,
        context: Context
    ) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasSeededChennai = prefs.getBoolean(KEY_HAS_SEEDED_CHENNAI, false)
            val hasSeededRooms = prefs.getBoolean(KEY_HAS_SEEDED_ROOMS, false)

            if (!hasSeededChennai) {
                try {
                    val reports = ChennaiSeedData.getSeedReports()
                    reports.forEach { report ->
                        userReportDao.insertReport(report)
                    }
                    
                    prefs.edit().putBoolean(KEY_HAS_SEEDED_CHENNAI, true).apply()
                    Log.d("LIMITLESS_TRACE", "Successfully seeded Chennai dataset.")
                } catch (e: Exception) {
                    Log.e("LIMITLESS_TRACE", "Error seeding data: ${e.message}")
                }
            } else {
                Log.d("LIMITLESS_TRACE", "Chennai dataset already seeded.")
            }

            if (!hasSeededRooms || mappedRoomDao.getCount() == 0) {
                try {
                    val rooms = listOf(
                        MappedRoomEntity(id = "LIMITLESS_ROOM_DEMO_ENTRANCE", name = "Main Entrance", hasRamp = true, hasWideDoor = true, isFullyMapped = true),
                        MappedRoomEntity(id = "LIMITLESS_ROOM_DEMO_HALLWAY", name = "Ground Floor Hallway", hasRamp = false, hasWideDoor = true, isFullyMapped = true),
                        MappedRoomEntity(id = "LIMITLESS_ROOM_DEMO_LIBRARY", name = "Library", hasRamp = true, hasWideDoor = true, isFullyMapped = true),
                        MappedRoomEntity(id = "LIMITLESS_ROOM_DEMO_CANTEEN", name = "Canteen", hasRamp = true, hasWideDoor = false, isFullyMapped = true),
                        MappedRoomEntity(id = "LIMITLESS_ROOM_DEMO_RESTROOM", name = "Accessible Restroom", hasRamp = false, hasWideDoor = true, isFullyMapped = true)
                    )

                    val connections = listOf(
                        RoomConnectionEntity("LIMITLESS_ROOM_DEMO_ENTRANCE", "LIMITLESS_ROOM_DEMO_HALLWAY", hasSteps = false, isElevator = false),
                        RoomConnectionEntity("LIMITLESS_ROOM_DEMO_HALLWAY", "LIMITLESS_ROOM_DEMO_LIBRARY", hasSteps = false, isElevator = false),
                        RoomConnectionEntity("LIMITLESS_ROOM_DEMO_HALLWAY", "LIMITLESS_ROOM_DEMO_CANTEEN", hasSteps = false, isElevator = false),
                        RoomConnectionEntity("LIMITLESS_ROOM_DEMO_HALLWAY", "LIMITLESS_ROOM_DEMO_RESTROOM", hasSteps = false, isElevator = false)
                    )

                    rooms.forEach { mappedRoomDao.insertRoom(it) }
                    connections.forEach { roomConnectionDao.insertConnection(it) }

                    prefs.edit().putBoolean(KEY_HAS_SEEDED_ROOMS, true).apply()
                    Log.d("LIMITLESS_TRACE", "Successfully seeded demo rooms and connections.")
                } catch (e: Exception) {
                    Log.e("LIMITLESS_TRACE", "Error seeding demo rooms: ${e.message}")
                }
            } else {
                Log.d("LIMITLESS_TRACE", "Demo rooms already seeded.")
            }
        }
    }
}
