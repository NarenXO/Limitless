package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_connections")
data class RoomConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromRoomId: String,
    val toRoomId: String,
    val connectionType: String = "doorway",   // doorway | hallway | ramp | stairs | elevator
    val hasRamp: Boolean = false,
    val hasStairs: Boolean = false,
    val doorWidthCm: Int = 80,
    val distanceMeters: Float = 5f,
    val isBidirectional: Boolean = true,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
