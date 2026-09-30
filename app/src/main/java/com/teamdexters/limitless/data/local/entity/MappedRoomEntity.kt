package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mapped_rooms")
data class MappedRoomEntity(
    @PrimaryKey val roomId: String,          // from QR: LIMITLESS_ROOM_xxx
    val roomName: String,                     // human label e.g. "Library"
    val buildingName: String = "",
    val floorLevel: Int = 0,
    val hasRamp: Boolean = false,
    val hasStairs: Boolean = false,
    val hasWideDoor: Boolean = false,
    val hasObstacles: Boolean = false,
    val obstacleCount: Int = 0,
    val doorWidthCm: Int = 80,
    val aiDescription: String = "",
    val userNotes: String = "",
    val photoDirPath: String = "",            // internal storage folder for room photos
    val photoCount: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFullyMapped: Boolean = false,       // true only after 4+ photos + save
    val timestamp: Long = System.currentTimeMillis()
)
