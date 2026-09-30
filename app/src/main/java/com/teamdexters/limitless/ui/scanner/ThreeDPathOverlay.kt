package com.teamdexters.limitless.ui.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun ThreeDPathOverlay(
    pathClarity: PathClarityStatus?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ar_arrows")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrow_offset"
    )

    val pathColor = when (pathClarity) {
        PathClarityStatus.CLEAR -> Color(0xFF00C853) // Green
        PathClarityStatus.PARTIALLY_CLEAR -> Color(0xFFDDDD7B) // Yellow
        PathClarityStatus.BLOCKED_DANGER -> Color(0xFFF791A9) // Red
        null -> Color(0xFFBAD6DA) // Default Pastel Blue
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Vanishing point
        val vpX = w / 2f
        val vpY = h * 0.4f

        // Draw 3D Perspective Ground Path
        val path = Path().apply {
            moveTo(w * 0.2f, h)
            lineTo(vpX - w * 0.05f, vpY + h * 0.1f)
            lineTo(vpX + w * 0.05f, vpY + h * 0.1f)
            lineTo(w * 0.8f, h)
            close()
        }
        
        drawPath(
            path = path,
            color = pathColor.copy(alpha = 0.2f)
        )
        drawPath(
            path = path,
            color = pathColor.copy(alpha = 0.5f),
            style = Stroke(width = 4f)
        )

        // Draw Animated 3D Waypoint Arrows along the path
        val startY = h
        val endY = vpY + h * 0.1f
        val totalDistY = startY - endY

        val numArrows = 3
        for (i in 0 until numArrows) {
            val rawProg = (i.toFloat() / numArrows) + arrowOffset
            val prog = rawProg % 1f // progress from 0 (bottom) to 1 (top)

            val currentY = startY - (totalDistY * prog)
            // Interpolate width based on Y position (perspective)
            val yRatio = (currentY - endY) / totalDistY // 1 at bottom, 0 at top
            
            val arrowWidth = (w * 0.4f) * yRatio
            val arrowHeight = (h * 0.05f) * yRatio
            
            if (yRatio > 0.1f) { // Hide very small arrows near vanishing point
                val arrowPath = Path().apply {
                    moveTo(vpX - arrowWidth / 2f, currentY + arrowHeight)
                    lineTo(vpX, currentY)
                    lineTo(vpX + arrowWidth / 2f, currentY + arrowHeight)
                }
                drawPath(
                    path = arrowPath,
                    color = pathColor.copy(alpha = 0.8f * yRatio),
                    style = Stroke(width = 6f * yRatio)
                )
            }
        }

        // Draw Floating 3D Pin at Destination / Hazard
        val pinY = vpY + h * 0.02f
        val pinRadius = w * 0.03f
        
        // Stick
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(vpX, endY),
            end = Offset(vpX, pinY),
            strokeWidth = 4f
        )
        // Head
        drawCircle(
            color = pathColor,
            radius = pinRadius,
            center = Offset(vpX, pinY - pinRadius)
        )
        drawCircle(
            color = Color.White,
            radius = pinRadius,
            center = Offset(vpX, pinY - pinRadius),
            style = Stroke(width = 4f)
        )
    }
}
