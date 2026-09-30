package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: RoomConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: RoomConnectionEntity)

    @Query("SELECT * FROM room_connections WHERE fromRoomId = :roomId OR toRoomId = :roomId")
    fun getConnectionsForRoom(roomId: String): Flow<List<RoomConnectionEntity>>
    
    @Query("SELECT COUNT(*) FROM room_connections")
    fun getConnectionCount(): Flow<Int>
}
