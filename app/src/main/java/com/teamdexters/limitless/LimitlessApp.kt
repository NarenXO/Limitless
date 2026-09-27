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
        CoroutineScope(Dispatchers.IO).launch {
            DatabaseSeeder.seedIfEmpty(userReportDao, this@LimitlessApp)
        }
    }
}
