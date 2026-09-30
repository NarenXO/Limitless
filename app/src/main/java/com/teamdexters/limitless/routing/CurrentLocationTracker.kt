package com.teamdexters.limitless.routing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentLocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mappedRoomDao: MappedRoomDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("limitless_location", Context.MODE_PRIVATE)

    fun setCurrentRoom(roomId: String) {
        prefs.edit().putString(KEY_ROOM_ID, roomId).apply()
        Log.d("LIMITLESS_TRACE", "CurrentLocationTracker: Updated to roomId=$roomId")
    }

    fun getCurrentRoomId(): String? = prefs.getString(KEY_ROOM_ID, null)

    suspend fun getCurrentRoom(): MappedRoomEntity? {
        val id = getCurrentRoomId() ?: return null
        return mappedRoomDao.getRoomById(id)
    }

    fun clear() {
        prefs.edit().remove(KEY_ROOM_ID).apply()
        Log.d("LIMITLESS_TRACE", "CurrentLocationTracker: Cleared")
    }

    companion object {
        private const val KEY_ROOM_ID = "current_room_id"
    }
}
