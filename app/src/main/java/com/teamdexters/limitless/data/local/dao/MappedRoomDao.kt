package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MappedRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: MappedRoomEntity)

    @Update
    suspend fun updateRoom(room: MappedRoomEntity)

    @Query("SELECT * FROM mapped_rooms ORDER BY roomName ASC")
    suspend fun getAllRooms(): List<MappedRoomEntity>

    @Query("SELECT * FROM mapped_rooms ORDER BY roomName ASC")
    fun observeAllRooms(): Flow<List<MappedRoomEntity>>

    @Query("SELECT * FROM mapped_rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): MappedRoomEntity?

    @Query("SELECT * FROM mapped_rooms WHERE roomName LIKE '%' || :name || '%' LIMIT 5")
    suspend fun searchRoomsByName(name: String): List<MappedRoomEntity>

    @Query("SELECT COUNT(*) FROM mapped_rooms")
    suspend fun getRoomCount(): Int

    @Query("DELETE FROM mapped_rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("DELETE FROM mapped_rooms")
    suspend fun clearAll()
}
