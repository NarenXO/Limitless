package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MappedRoomDao {
    @Query("SELECT * FROM mapped_rooms")
    fun getAllMappedRooms(): Flow<List<MappedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: MappedRoomEntity)
    
    @Query("SELECT COUNT(*) FROM mapped_rooms")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM mapped_rooms")
    suspend fun clearAll()
}
