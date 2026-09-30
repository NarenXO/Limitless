package com.teamdexters.limitless

import android.app.Application
import com.teamdexters.limitless.data.local.dao.UserReportDao
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

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("LIMITLESS_TRACE", "=================== APP COLD START ===================")
        android.util.Log.d("LIMITLESS_TRACE", "Hilt initialization complete. Triggering database auto-seeding.")
        CoroutineScope(Dispatchers.IO).launch {
            DatabaseSeeder.seedIfEmpty(userReportDao, this@LimitlessApp)
        }
    }
}
