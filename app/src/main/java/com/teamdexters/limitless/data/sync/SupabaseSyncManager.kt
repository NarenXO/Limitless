package com.teamdexters.limitless.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.UserReportEntity
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

sealed class SyncResult {
    object Success : SyncResult()
    object Offline : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class SupabaseSyncManager @Inject constructor(
    private val userReportDao: UserReportDao,
    @ApplicationContext private val context: Context
) {
    // Placeholder Supabase URL and anon key for the challenge
    private val SUPABASE_URL = "https://placeholder.supabase.co"
    private val SUPABASE_KEY = "placeholder-key"

    private val supabase = createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_KEY) {
        install(Postgrest)
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

    suspend fun syncCommunityReports(): SyncResult {
        if (!isOnline()) {
            return SyncResult.Offline
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                // Fetch local reports to resolve and push
                val allLocalReports = userReportDao.getAllReportsOnce()
                val pendingReports = userReportDao.getPendingSyncReports()

                // Try fetching remote reports
                val remoteReports = supabase.postgrest["community_reports"]
                    .select()
                    .decodeList<CommunityReportDto>()

                val remoteMap = remoteReports.associateBy { it.id.toLongOrNull() ?: -1L }

                // c. Push Unsynced Local Writes & e. Timestamp Conflict Resolution
                val toUpsert = mutableListOf<CommunityReportDto>()

                for (local in allLocalReports) {
                    val remote = remoteMap[local.id]
                    if (remote != null) {
                        if (remote.timestamp > local.timestamp) {
                            // Remote is newer, update local
                            val updatedLocal = local.copy(
                                locationName = remote.locationName,
                                category = remote.category,
                                // Assuming rating logic matches
                                latitude = remote.latitude,
                                longitude = remote.longitude,
                                description = remote.description,
                                photoUri = remote.photoUri,
                                trustScore = remote.trustScore,
                                confirmationCount = remote.confirmationCount,
                                timestamp = remote.timestamp,
                                syncStatus = "SYNCED"
                            )
                            userReportDao.insertReport(updatedLocal)
                        } else if (local.timestamp > remote.timestamp || local.syncStatus == "PENDING") {
                            // Local is newer or pending, push to remote
                            toUpsert.add(localToDto(local))
                        }
                    } else if (local.syncStatus == "PENDING") {
                        toUpsert.add(localToDto(local))
                    }
                }

                // Add missing remote records to local
                val localMap = allLocalReports.associateBy { it.id }
                for (remote in remoteReports) {
                    val id = remote.id.toLongOrNull() ?: continue
                    if (!localMap.containsKey(id)) {
                        userReportDao.insertReport(dtoToLocal(remote))
                    }
                }

                if (toUpsert.isNotEmpty()) {
                    supabase.postgrest["community_reports"].upsert(toUpsert)
                    
                    for (dto in toUpsert) {
                        val id = dto.id.toLongOrNull()
                        if (id != null) {
                            userReportDao.updateSyncStatus(id, "SYNCED")
                        }
                    }
                }

                SyncResult.Success
            }.getOrElse { e ->
                Log.e("SupabaseSyncManager", "Sync failed: ${e.message}", e)
                SyncResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun localToDto(entity: UserReportEntity): CommunityReportDto {
        val ratingInt = entity.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
        return CommunityReportDto(
            id = entity.id.toString(),
            locationName = entity.locationName,
            category = entity.category,
            rating = ratingInt,
            latitude = entity.latitude,
            longitude = entity.longitude,
            description = entity.description,
            photoUri = entity.photoUri,
            trustScore = entity.trustScore,
            confirmationCount = entity.confirmationCount,
            timestamp = entity.timestamp
        )
    }

    private fun dtoToLocal(dto: CommunityReportDto): UserReportEntity {
        return UserReportEntity(
            id = dto.id.toLongOrNull() ?: 0L,
            locationName = dto.locationName,
            latitude = dto.latitude,
            longitude = dto.longitude,
            category = dto.category,
            description = dto.description,
            hasRamp = dto.category.equals("RAMP", ignoreCase = true),
            hasElevator = dto.category.equals("ELEVATOR", ignoreCase = true),
            hasAccessibleRestroom = dto.category.equals("RESTROOM", ignoreCase = true),
            photoUri = dto.photoUri,
            trustScore = dto.trustScore,
            confirmationCount = dto.confirmationCount,
            syncStatus = "SYNCED",
            timestamp = dto.timestamp
        )
    }
}
