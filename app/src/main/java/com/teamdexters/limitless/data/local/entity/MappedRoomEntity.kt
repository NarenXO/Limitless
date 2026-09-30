package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mapped_rooms")
data class MappedRoomEntity(
    @PrimaryKey val id: String,
    val roomName: String,
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val hasWideDoor: Boolean,
    val hasObstacles: Boolean,
    val obstacleCount: Int,
    val notes: String,
    val timestamp: Long
)
