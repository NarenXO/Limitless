// CommunityReportViewModel.kt
package com.teamdexters.limitless.ui.community

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.teamdexters.limitless.data.local.dao.UserReportDao
import com.teamdexters.limitless.data.local.entity.UserReportEntity

@HiltViewModel
class CommunityReportViewModel @Inject constructor(
    private val userReportDao: UserReportDao
) : ViewModel() {
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

    fun fetchCurrentLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: android.location.Location? ->
                if (loc != null) {
                    _location.value = loc
                } else {
                    _location.value = android.location.Location("dummy").apply {
                        latitude = 0.0
                        longitude = 0.0
                    }
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
                photoUri = photoUriValue
                // TODO(Naren): Store this calculated trustScore on UserReportEntity.trustScore
                // trustScore = trustScore
            )
            
            try {
                userReportDao.insertReport(report)
                _submitResult.value = true
            } catch (e: Exception) {
                _submitResult.value = false
            }
        }
    }
}
