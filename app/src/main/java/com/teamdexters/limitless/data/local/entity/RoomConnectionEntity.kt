package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity

@Entity(tableName = "room_connections", primaryKeys = ["sourceRoomId", "targetRoomId"])
data class RoomConnectionEntity(
    val sourceRoomId: String,
    val targetRoomId: String,
    val hasSteps: Boolean = false,
    val isElevator: Boolean = false
)
