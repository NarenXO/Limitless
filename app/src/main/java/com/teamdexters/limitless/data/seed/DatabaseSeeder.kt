package com.teamdexters.limitless.data.seed

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.data.local.dao.UserReportDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DatabaseSeeder {
    private const val PREFS_NAME = "limitless_prefs"
    private const val KEY_HAS_SEEDED_CHENNAI = "has_seeded_chennai_data"

    suspend fun seedIfEmpty(userReportDao: UserReportDao, context: Context) {
        withContext(Dispatchers.IO) {
            val reportCount = userReportDao.getReportCount()

            if (reportCount == 0) {
                try {
                    val reports = ChennaiSeedData.getSeedReports()
                    reports.forEach { report ->
                        userReportDao.insertReport(report)
                    }
                    
                    Log.d("DatabaseSeeder", "Successfully seeded Chennai dataset.")
                } catch (e: Exception) {
                    Log.e("DatabaseSeeder", "Error seeding data: ${e.message}")
                }
            } else {
                Log.d("DatabaseSeeder", "Chennai dataset already seeded.")
            }
        }
    }
}
