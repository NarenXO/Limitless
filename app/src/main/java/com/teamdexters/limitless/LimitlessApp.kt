package com.teamdexters.limitless

import android.app.Application
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import com.teamdexters.limitless.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LimitlessApp : Application() {

    @Inject
    lateinit var userReportDao: UserReportDao

    @Inject
    lateinit var mappedRoomDao: MappedRoomDao

    @Inject
    lateinit var roomConnectionDao: RoomConnectionDao

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("LIMITLESS_TRACE", "=================== APP COLD START ===================")
        android.util.Log.d("LIMITLESS_TRACE", "Hilt initialization complete. Triggering database auto-seeding.")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(1000) // Small safe delay to avoid race condition on cold start
                DatabaseSeeder.seedIfEmpty(userReportDao, mappedRoomDao, roomConnectionDao, this@LimitlessApp)
                android.util.Log.d("LIMITLESS_TRACE", "LimitlessApp: Cold-start DB seeding executed safely")
            } catch (e: Exception) {
                android.util.Log.e("LIMITLESS_TRACE", "LimitlessApp: Error during DB seeding", e)
            }
        }
    }
}
