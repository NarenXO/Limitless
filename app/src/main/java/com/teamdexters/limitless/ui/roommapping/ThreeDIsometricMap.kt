package com.teamdexters.limitless.ui.roommapping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.roommapping.RoomPhotoCapture
import com.teamdexters.limitless.roommapping.SpatialRouteStep
import java.io.File

@Composable
fun ThreeDIsometricMap(
    steps: List<SpatialRouteStep>,
    activeStepIndex: Int = 0,
    photoPath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photoCaptureUtil = remember { RoomPhotoCapture(context) }

    // 1. Extract Mild Adaptive Color from Captured Photo (or fallback to #E0F2F4)
    val adaptiveSurfaceTint by remember(photoPath) {
        derivedStateOf {
            if (!photoPath.isNullOrEmpty()) {
                val file = File(photoPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        extractMildAmbientColor(bitmap)
                    } else Color(0xFFE0F2F4)
                } else Color(0xFFE0F2F4)
            } else Color(0xFFE0F2F4)
        }
    }

    // 2. Animated User Position & Pulse
    val animatedStepIndex by animateFloatAsState(
        targetValue = activeStepIndex.toFloat(),
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "userStepAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseR"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseA"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .border(2.dp, Color(0xFFF791A9), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = adaptiveSurfaceTint)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                val w = size.width
                val h = size.height

                // A. Soft Floor Surface Floorplan Boundary
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.05f, h * 0.08f),
                    size = Size(w * 0.9f, h * 0.84f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF1F1F1F).copy(alpha = 0.15f),
                    topLeft = Offset(w * 0.05f, h * 0.08f),
                    size = Size(w * 0.9f, h * 0.84f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // B. Compute Route Nodes Along Room Floorplan
                val totalSteps = if (steps.isEmpty()) 3 else steps.size
                val nodes = mutableListOf<Offset>()
                for (i in 0 until totalSteps) {
                    val progress = i / (totalSteps - 1).coerceAtLeast(1).toFloat()
                    val x = w * (0.18f + progress * 0.64f)
                    val y = when (i % 3) {
                        0 -> h * (0.78f - progress * 0.5f)
                        1 -> h * (0.28f + progress * 0.35f)
                        else -> h * (0.55f - progress * 0.3f)
                    }
                    nodes.add(Offset(x, y))
                }

                // C. Draw Clean Polyline Path
                for (i in 0 until nodes.size - 1) {
                    val start = nodes[i]
                    val end = nodes[i + 1]
                    val isPast = i < activeStepIndex
                    val isCurrent = i == activeStepIndex

                    val lineColor = when {
                        isCurrent -> Color(0xFFF791A9) // Primary Accent
                        isPast -> Color(0xFFBAD6DA)    // Soft Pastel Blue
                        else -> Color(0xFF1F1F1F).copy(alpha = 0.3f)
                    }

                    val strokeWidth = if (isCurrent) 6.dp.toPx() else 3.5.dp.toPx()

                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth
                    )
                }

                // D. Draw Waypoint Nodes
                nodes.forEachIndexed { idx, point ->
                    val isCurrent = idx == activeStepIndex
                    val isPast = idx < activeStepIndex
                    val nodeColor = when {
                        isCurrent -> Color(0xFFF791A9)
                        isPast -> Color(0xFFBAD6DA)
                        else -> Color(0xFF1F1F1F).copy(alpha = 0.5f)
                    }

                    drawCircle(color = nodeColor, radius = if (isCurrent) 10.dp.toPx() else 7.dp.toPx(), center = point)
                    drawCircle(color = Color.White, radius = if (isCurrent) 4.dp.toPx() else 2.5.dp.toPx(), center = point)
                }

                // E. Draw Interpolated "YOU ARE HERE" Position
                val currIdx = animatedStepIndex.toInt().coerceIn(0, nodes.size - 1)
                val nextIdx = (currIdx + 1).coerceAtMost(nodes.size - 1)
                val frac = animatedStepIndex - currIdx

                val userX = nodes[currIdx].x + (nodes[nextIdx].x - nodes[currIdx].x) * frac
                val userY = nodes[currIdx].y + (nodes[nextIdx].y - nodes[currIdx].y) * frac
                val userPos = Offset(userX, userY)

                // Pulsing Aura Ring
                drawCircle(
                    color = Color(0xFFF791A9).copy(alpha = pulseAlpha),
                    radius = pulseRadius.dp.toPx(),
                    center = userPos
                )

                // Center User Dot
                drawCircle(color = Color(0xFFF791A9), radius = 8.dp.toPx(), center = userPos)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = userPos)
            }

            // Top Status Header: Clean & Simple
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "ADAPTIVE ROUTE MAP • STEP ${activeStepIndex + 1} OF ${steps.size.coerceAtLeast(1)}",
                    color = Color(0xFF1F1F1F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom "YOU ARE HERE" Label Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFF791A9), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YOU ARE HERE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Helper: Extract mild ambient pastel color from image bitmap
private fun extractMildAmbientColor(bitmap: Bitmap): Color {
    return try {
        // Downsample bitmap to 16x16 to get average RGB
        val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        val count = 16 * 16

        for (x in 0 until 16) {
            for (y in 0 until 16) {
                val pixel = scaled.getPixel(x, y)
                totalR += (pixel shr 16 and 0xFF)
                totalG += (pixel shr 8 and 0xFF)
                totalB += (pixel and 0xFF)
            }
        }

        val avgR = (totalR / count).toInt()
        val avgG = (totalG / count).toInt()
        val avgB = (totalB / count).toInt()

        // Blend 85% with soft pastel surface tint #E0F2F4 to keep the theme mild and clean
        val blendR = (avgR * 0.15f + 0xE0 * 0.85f).toInt().coerceIn(0, 255)
        val blendG = (avgG * 0.15f + 0xF2 * 0.85f).toInt().coerceIn(0, 255)
        val blendB = (avgB * 0.15f + 0xF4 * 0.85f).toInt().coerceIn(0, 255)

        Color(blendR, blendG, blendB)
    } catch (e: Exception) {
        Color(0xFFE0F2F4) // Default fallback tint
    }
}
