package com.teamdexters.limitless.ui.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun ThreeDPathOverlay(
    safetyStatus: String = "CLEAR",
    distanceText: String = "Step 1: North Exit • 3m",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val pathColor = when (safetyStatus) {
        "HAZARD" -> Color(0xFFFF1744) // Red
        "PARTIALLY_CLEAR" -> Color(0xFFFFD600) // Yellow
        else -> Color(0xFF00C853) // Green
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val bottomY = size.height * 0.9f
        val horizonY = size.height * 0.4f
        
        val path = Path().apply {
            moveTo(centerX - 150f, bottomY)
            lineTo(centerX + 150f, bottomY)
            lineTo(centerX + 30f, horizonY)
            lineTo(centerX - 30f, horizonY)
            close()
        }
        
        drawPath(
            path = path,
            color = pathColor.copy(alpha = 0.3f)
        )
        
        val arrowY = bottomY - (bottomY - horizonY) * arrowOffset
        val arrowWidth = 150f - (150f - 30f) * arrowOffset
        
        val arrowPath = Path().apply {
            moveTo(centerX - arrowWidth * 0.6f, arrowY + 40f)
            lineTo(centerX, arrowY)
            lineTo(centerX + arrowWidth * 0.6f, arrowY + 40f)
        }
        
        drawPath(
            path = arrowPath,
            color = Color.White,
            style = Stroke(width = 8f)
        )
    }
}
