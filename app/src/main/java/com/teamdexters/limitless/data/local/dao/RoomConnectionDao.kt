package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: RoomConnectionEntity): Long

    @Query("SELECT * FROM room_connections")
    suspend fun getAllConnections(): List<RoomConnectionEntity>

    @Query("SELECT * FROM room_connections")
    fun observeAllConnections(): Flow<List<RoomConnectionEntity>>

    @Query("SELECT * FROM room_connections WHERE fromRoomId = :roomId OR (isBidirectional = 1 AND toRoomId = :roomId)")
    suspend fun getConnectionsForRoom(roomId: String): List<RoomConnectionEntity>

    @Query("SELECT COUNT(*) FROM room_connections")
    suspend fun getConnectionCount(): Int

    @Query("DELETE FROM room_connections WHERE id = :id")
    suspend fun deleteConnection(id: Long)

    @Query("DELETE FROM room_connections WHERE fromRoomId = :roomId OR toRoomId = :roomId")
    suspend fun deleteConnectionsForRoom(roomId: String)

    @Query("DELETE FROM room_connections")
    suspend fun clearAll()
}
