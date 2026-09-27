package com.teamdexters.limitless.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSyncManager @Inject constructor(
    private val userReportDao: UserReportDao,
    private val context: Context
) {
    // TODO(Naren): Replace with production Supabase URL and anon key
    private val SUPABASE_URL = "https://placeholder.supabase.co"
    private val SUPABASE_KEY = "placeholder-key"

    // TODO(Naren): Initialize actual Supabase client when io.github.jan-tennert.supabase dependency is added
    // private val supabase: SupabaseClient by lazy {
    //     createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_KEY) {
    //         install(Postgrest)
    //     }
    // }

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    suspend fun syncReports() {
        if (!isOnline()) {
            Log.d("SupabaseSync", "Offline mode. Skipping sync.")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                // a. Fetch unsynced local reports
                val pendingReports = userReportDao.getPendingSyncReports()

                // b. Push to Supabase table community_reports
                if (pendingReports.isNotEmpty()) {
                    // TODO(Naren): Push via supabase.postgrest["community_reports"].upsert(pendingReports)
                    Log.d("SupabaseSync", "Would push ${pendingReports.size} reports to Supabase.")

                    // Mark as synced locally
                    for (report in pendingReports) {
                        userReportDao.updateSyncStatus(report.id, "SYNCED")
                    }
                }

                // c. Pull remote reports
                // TODO(Naren): Pull via supabase.postgrest["community_reports"].select().decodeList<UserReportEntity>()
                Log.d("SupabaseSync", "Sync completed successfully (stub).")
            }
        } catch (e: Exception) {
            // f. Fail gracefully when offline or when using placeholder credentials
            Log.e("SupabaseSync", "Sync failed: ${e.message}")
        }
    }
}
