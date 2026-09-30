package com.teamdexters.limitless.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mapped_rooms")
data class MappedRoomEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val hasRamp: Boolean = false,
    val hasWideDoor: Boolean = false,
    val isFullyMapped: Boolean = false
)
