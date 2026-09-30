package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_connections")
data class RoomConnectionEntity(
    @PrimaryKey val id: String,
    val fromRoomId: String,
    val toRoomId: String,
    val connectionType: String,
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val distanceMeters: Float,
    val doorWidthCm: Float,
    val timestamp: Long
)
