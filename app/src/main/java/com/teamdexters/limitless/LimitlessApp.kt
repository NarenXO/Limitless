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
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("LIMITLESS_CRASH", "FATAL CRASH in thread ${thread.name}: ${throwable.localizedMessage}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate()
        android.util.Log.d("LIMITLESS_TRACE", "=================== APP COLD START ===================")
        android.util.Log.d("LIMITLESS_TRACE", "Hilt initialization complete. Triggering database auto-seeding.")
        CoroutineScope(Dispatchers.IO).launch {
            DatabaseSeeder.seedIfEmpty(userReportDao, this@LimitlessApp)
        }
    }
}
