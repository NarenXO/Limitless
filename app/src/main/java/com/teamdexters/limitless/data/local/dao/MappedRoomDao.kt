package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MappedRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: MappedRoomEntity)
    
    @Query("SELECT * FROM mapped_rooms")
    fun getAllRooms(): Flow<List<MappedRoomEntity>>
}
