package com.teamdexters.limitless.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class CommunityReportDto(
    val id: String,
    val locationName: String,
    val category: String,
    val rating: Int,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val photoUri: String? = null,
    val trustScore: Int = 0,
    val confirmationCount: Int = 0,
    val timestamp: Long
)
