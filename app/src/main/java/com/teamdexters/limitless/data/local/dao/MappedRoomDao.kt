package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity

@Dao
interface MappedRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: MappedRoomEntity)
}
