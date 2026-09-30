package com.teamdexters.limitless.ui.scanner

import android.graphics.Bitmap
import android.graphics.RectF

sealed class DoorWidthCategory(val label: String, val estimatedCm: String) {
    object Narrow : DoorWidthCategory("Narrow", "<80cm")
    object Standard : DoorWidthCategory("Standard", "80-90cm")
    object Wide : DoorWidthCategory("Wide", ">90cm")
}

object DoorwayDepthEstimator {
    // Note: This is a heuristic estimation. For real ARCore depth API, upgrade in future phase.
    fun estimateDoorWidth(bitmap: Bitmap, doorwayBoundingBox: RectF): DoorWidthCategory {
        val frameWidth = bitmap.width.toFloat()
        if (frameWidth <= 0) return DoorWidthCategory.Standard
        
        val boxWidth = doorwayBoundingBox.width()
        val ratio = boxWidth / frameWidth
        
        return when {
            ratio > 0.55f -> DoorWidthCategory.Wide
            ratio >= 0.35f -> DoorWidthCategory.Standard
            else -> DoorWidthCategory.Narrow
        }
    }
}
