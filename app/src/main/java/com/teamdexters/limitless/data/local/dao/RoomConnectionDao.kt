package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomConnectionDao {
    @Query("SELECT * FROM room_connections WHERE sourceRoomId = :roomId OR targetRoomId = :roomId")
    fun getConnectionsForRoom(roomId: String): Flow<List<RoomConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: RoomConnectionEntity)
    
    @Query("DELETE FROM room_connections")
    suspend fun clearAll()
}
