// CommunityReportViewModel.kt
package com.teamdexters.limitless.ui.community

enum class ViewMode { LIST, MAP }
enum class ReportFilterCategory(val label: String) {
    ALL("All"),
    RAMP("Ramp Access"),
    LIFT("Elevator / Lift"),
    WASHROOM("Accessible Washroom"),
    DOORWAY("Wide Doorway"),
    VERIFIED("Team-Verified Only")
}
enum class SortOrder(val label: String) {
    NEWEST("Newest First"),
    TRUST_SCORE("Highest Trust Score"),
    NEAREST("Nearest Distance")
}

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.UserReportEntity

import dagger.hilt.android.qualifiers.ApplicationContext
import com.teamdexters.limitless.data.seed.DatabaseSeeder

@HiltViewModel
class CommunityReportViewModel @Inject constructor(
    private val userReportDao: UserReportDao,
    @ApplicationContext private val context: Context
) : ViewModel() {
    init {
        // TODO(Naren): Call DatabaseSeeder.seedIfEmpty(dao, context) inside Application.onCreate for global app pre-population
        viewModelScope.launch {
            DatabaseSeeder.seedIfEmpty(userReportDao, context)
        }
    }
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory

    private val _rating = MutableStateFlow(0)
    val rating: StateFlow<Int> = _rating

    private val _imageUri = MutableStateFlow<android.net.Uri?>(null)
    val imageUri: StateFlow<android.net.Uri?> = _imageUri

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    fun onCategorySelected(category: Category) {
        _selectedCategory.value = category
    }

    fun onRatingSelected(star: Int) {
        _rating.value = star
    }

    fun onImageSelected(uri: android.net.Uri?) {
        _imageUri.value = uri
    }

    private val _submitResult = MutableStateFlow<Boolean?>(null)
    val submitResult: StateFlow<Boolean?> = _submitResult

    private val _isOnline = MutableStateFlow(true) // Should be updated via ConnectivityManager in a real app
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode

    private val _reportFilterCategory = MutableStateFlow(ReportFilterCategory.ALL)
    val reportFilterCategory: StateFlow<ReportFilterCategory> = _reportFilterCategory

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun setFilterCategory(category: ReportFilterCategory) { _reportFilterCategory.value = category }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    // Expose real-time Flow of all reports from Room DAO
    val reports: kotlinx.coroutines.flow.Flow<List<UserReportEntity>> = userReportDao.getAllReports()

    val filteredReports = combine(reports, _reportFilterCategory, _sortOrder) { reportList, filter, sort ->
        val filtered = reportList.filter { report ->
            when (filter) {
                ReportFilterCategory.ALL -> true
                ReportFilterCategory.RAMP -> report.hasRamp
                ReportFilterCategory.LIFT -> report.hasElevator
                ReportFilterCategory.WASHROOM -> report.hasAccessibleRestroom
                ReportFilterCategory.DOORWAY -> report.hasWideDoorway
                ReportFilterCategory.VERIFIED -> report.isVerified
            }
        }
        
        when (sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.timestamp }
            SortOrder.TRUST_SCORE -> filtered.sortedByDescending { it.confirmationCount } // Assuming confirmation count translates to trust score for now
            SortOrder.NEAREST -> filtered // Dummy distance sorting, no real distance logic provided
        }
    }


    fun confirmReport(reportId: Long) {
        viewModelScope.launch {
            userReportDao.incrementConfirmationCount(reportId)
        }
    }

    fun fetchCurrentLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: android.location.Location? ->
                    if (loc != null) {
                        _location.value = loc
                    }
                }
        } catch (e: SecurityException) {
            // Handle missing permissions
        }
    }

    fun submitReport() {
        val currentCategory = _selectedCategory.value?.name ?: "OTHER"
        val currentLat = _location.value?.latitude ?: 0.0
        val currentLatDouble = currentLat.toDouble()
        val currentLng = _location.value?.longitude ?: 0.0
        val currentLngDouble = currentLng.toDouble()
        
        viewModelScope.launch {
            val confirmationCount = 1
            val photoUriValue = _imageUri.value?.toString()
            val trustScore = minOf(100, (confirmationCount * 15) + (if (!photoUriValue.isNullOrEmpty()) 25 else 0))

            val report = UserReportEntity(
                locationName = "Community Location",
                latitude = currentLatDouble,
                longitude = currentLngDouble,
                category = currentCategory.uppercase(),
                description = "Rating: ${_rating.value} stars",
                hasRamp = currentCategory.equals("Ramp", ignoreCase = true),
                hasElevator = currentCategory.equals("Lift/Elevator", ignoreCase = true),
                hasAccessibleRestroom = currentCategory.equals("Accessible Washroom", ignoreCase = true),
                photoUri = photoUriValue,
                trustScore = trustScore
            )
            
            try {
                userReportDao.insertReport(report)
                _submitResult.value = true
            } catch (e: Exception) {
                _submitResult.value = false
            }
        }
    }

    fun submitObstacleReport(description: String) {
        val currentLat = _location.value?.latitude ?: 0.0
        val currentLng = _location.value?.longitude ?: 0.0
        
        viewModelScope.launch {
            val report = UserReportEntity(
                locationName = "Reported Obstacle",
                latitude = currentLat,
                longitude = currentLng,
                category = "OBSTACLE",
                description = description,
                hasRamp = false,
                hasElevator = false,
                hasAccessibleRestroom = false,
                photoUri = null,
                trustScore = 15,
                timestamp = System.currentTimeMillis()
            )
            
            try {
                userReportDao.insertReport(report)
            } catch (e: Exception) {
                // handle error if needed
            }
        }
    }
}
