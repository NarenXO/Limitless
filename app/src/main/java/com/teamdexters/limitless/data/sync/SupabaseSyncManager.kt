package com.teamdexters.limitless.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
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

    private val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
        }
    }

    private fun isOnline(): Boolean {
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
                    supabase.postgrest["community_reports"].upsert(pendingReports)
                    
                    // Mark as synced locally
                    pendingReports.forEach { report ->
                        val updatedReport = report.copy(syncStatus = "SYNCED")
                        // Assume insertReport replaces on conflict or update exist. The DAO has insertReport returning Long.
                        // For a real implementation, we should have an update method.
                        // TODO(Naren): Add updateReport method to UserReportDao
                        userReportDao.insertReport(updatedReport)
                    }
                }

                // c. Pull remote reports
                val remoteReports = supabase.postgrest["community_reports"]
                    .select()
                    .decodeList<UserReportEntity>()

                // d. Conflict resolution
                remoteReports.forEach { remoteReport ->
                    // For simplicity, we just insert all remote reports if they don't exist.
                    // Or if they exist we can compare timestamp.
                    // TODO(Naren): Properly implement conflict resolution by fetching local by ID and comparing timestamps.
                    val localReport = remoteReport.copy(syncStatus = "SYNCED")
                    userReportDao.insertReport(localReport)
                }
                
                Log.d("SupabaseSync", "Sync completed successfully.")
            }
        } catch (e: Exception) {
            // f. Fail gracefully when offline or when using placeholder credentials
            Log.e("SupabaseSync", "Sync failed: ${e.message}")
        }
    }
}
