package com.teamdexters.limitless.data.seed

import com.teamdexters.limitless.data.local.entity.UserReportEntity

object ChennaiSeedData {
    fun getSeedReports(): List<UserReportEntity> {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L

        return listOf(
            createSeedItem("Chennai Central Railway Station", 13.0827, 80.2707, "RAMP", "Ramp & Lift", 4, 12, "sample_uri_1", now - 2 * dayInMillis),
            createSeedItem("Marina Beach Promenade", 13.0499, 80.2824, "WASHROOM", "Accessible Washroom & Ramp", 3, 8, null, now - 5 * dayInMillis),
            createSeedItem("Express Avenue Mall, Royapettah", 13.0587, 80.2641, "ELEVATOR", "Lift & Wide Doorway", 5, 10, "sample_uri_2", now - 1 * dayInMillis),
            createSeedItem("Phoenix MarketCity, Velachery", 12.9915, 80.2170, "ELEVATOR", "Lift, Washroom, Parking", 5, 11, "sample_uri_3", now - 3 * dayInMillis),
            createSeedItem("T. Nagar Bus Stand", 13.0405, 80.2337, "RAMP", "Ramp", 2, 2, null, now - 10 * dayInMillis),
            createSeedItem("Rajiv Gandhi Government General Hospital", 13.0815, 80.2777, "RAMP", "Ramp, Lift, Washroom", 4, 9, "sample_uri_4", now - 7 * dayInMillis),
            createSeedItem("Anna University Campus, Guindy", 13.0102, 80.2357, "RAMP", "Ramp & Wide Doorway", 4, 5, null, now - 12 * dayInMillis),
            createSeedItem("Government Museum, Egmore", 13.0732, 80.2609, "RAMP", "Ramp & Lift", 3, 4, null, now - 8 * dayInMillis),
            createSeedItem("Kapaleeshwarar Temple, Mylapore", 13.0334, 80.2697, "RAMP", "Ramp", 3, 6, "sample_uri_5", now - 4 * dayInMillis),
            createSeedItem("Besant Nagar Beach", 13.0003, 80.2667, "RAMP", "Accessible Walkway", 4, 7, "sample_uri_6", now - 14 * dayInMillis),
            createSeedItem("VR Chennai Mall, Anna Nagar", 13.0853, 80.1913, "ELEVATOR", "Lift & Parking", 5, 12, "sample_uri_7", now - 6 * dayInMillis),
            createSeedItem("Chennai International Airport T1", 12.9941, 80.1709, "RAMP", "Ramp, Lift, Washroom", 5, 10, "sample_uri_8", now - 2 * dayInMillis),
            createSeedItem("Sathyam Cinemas, Royapettah", 13.0560, 80.2612, "RAMP", "Ramp & Washroom", 4, 8, null, now - 9 * dayInMillis),
            createSeedItem("IIT Madras Campus, Adyar", 12.9915, 80.2336, "ENTRANCE", "Wide Doorway & Ramp", 5, 11, "sample_uri_9", now - 1 * dayInMillis),
            createSeedItem("Koyambedu CMBT Bus Terminus", 13.0694, 80.1948, "RAMP", "Ramp & Washroom", 3, 5, null, now - 11 * dayInMillis)
        )
    }

    private fun createSeedItem(
        name: String,
        lat: Double,
        lng: Double,
        category: String,
        features: String,
        rating: Int,
        confirmations: Int,
        photo: String?,
        time: Long
    ): UserReportEntity {
        // Computed trustScore using the Phase 2 formula
        val computedTrustScore = minOf(100, (confirmations * 15) + (if (!photo.isNullOrEmpty()) 25 else 0))

        val hasRamp = features.contains("Ramp", ignoreCase = true)
        val hasElevator = features.contains("Lift", ignoreCase = true)
        val hasWashroom = features.contains("Washroom", ignoreCase = true)

        return UserReportEntity(
            locationName = name,
            latitude = lat,
            longitude = lng,
            category = category,
            description = "Features: $features | Rating: $rating",
            hasRamp = hasRamp,
            hasElevator = hasElevator,
            hasAccessibleRestroom = hasWashroom,
            photoUri = photo,
            confirmationCount = confirmations,
            syncStatus = "SYNCED",
            timestamp = time
            // TODO(Naren): Store this calculated trustScore on UserReportEntity.trustScore when the field is added
            // trustScore = computedTrustScore
        )
    }
}
