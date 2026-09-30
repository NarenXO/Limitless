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
import androidx.compose.ui.geometry.Offset
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

    // Mild Adaptive Color Tint from Photo
    val adaptiveSurfaceTint by remember(photoPath) {
        derivedStateOf {
            if (!photoPath.isNullOrEmpty()) {
                val file = File(photoPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) extractMildAmbientColor(bitmap) else Color(0xFFE0F2F4)
                } else Color(0xFFE0F2F4)
            } else Color(0xFFE0F2F4)
        }
    }

    // Smooth User Movement Animation
    val animatedStepIndex by animateFloatAsState(
        targetValue = activeStepIndex.toFloat(),
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "userStepAnim"
    )

    // Pulsing Aura Ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
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
            .height(280.dp)
            .border(2.dp, Color(0xFFF791A9), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = adaptiveSurfaceTint)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height

                // Helper: 3D Isometric Rhombus Coordinate Mapper
                // u: 0..1 (left-to-bottom axis), v: 0..1 (top-to-right axis)
                val topCorner = Offset(w * 0.5f, h * 0.18f)
                val rightCorner = Offset(w * 0.92f, h * 0.48f)
                val bottomCorner = Offset(w * 0.5f, h * 0.82f)
                val leftCorner = Offset(w * 0.08f, h * 0.48f)

                fun projectIso(u: Float, v: Float): Offset {
                    val x = topCorner.x + (rightCorner.x - topCorner.x) * v + (leftCorner.x - topCorner.x) * u
                    val y = topCorner.y + (rightCorner.y - topCorner.y) * v + (leftCorner.y - topCorner.y) * u
                    return Offset(x, y)
                }

                // 1. Draw 3D Isometric Floor Rectangle Box
                val isoBoxPath = Path().apply {
                    moveTo(topCorner.x, topCorner.y)
                    lineTo(rightCorner.x, rightCorner.y)
                    lineTo(bottomCorner.x, bottomCorner.y)
                    lineTo(leftCorner.x, leftCorner.y)
                    close()
                }
                drawPath(isoBoxPath, color = Color.White.copy(alpha = 0.5f))
                drawPath(isoBoxPath, color = Color(0xFF1F1F1F).copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))

                // 2. Draw 3D Extruded Wall Back Lines
                val wallH = h * 0.12f
                drawLine(Color(0xFF1F1F1F).copy(alpha = 0.4f), topCorner, Offset(topCorner.x, topCorner.y - wallH), strokeWidth = 1.5.dp.toPx())
                drawLine(Color(0xFF1F1F1F).copy(alpha = 0.4f), leftCorner, Offset(leftCorner.x, leftCorner.y - wallH), strokeWidth = 1.5.dp.toPx())
                drawLine(Color(0xFF1F1F1F).copy(alpha = 0.4f), rightCorner, Offset(rightCorner.x, rightCorner.y - wallH), strokeWidth = 1.5.dp.toPx())
                drawLine(
                    Color(0xFF1F1F1F).copy(alpha = 0.3f),
                    Offset(leftCorner.x, leftCorner.y - wallH),
                    Offset(topCorner.x, topCorner.y - wallH),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    Color(0xFF1F1F1F).copy(alpha = 0.3f),
                    Offset(topCorner.x, topCorner.y - wallH),
                    Offset(rightCorner.x, rightCorner.y - wallH),
                    strokeWidth = 1.5.dp.toPx()
                )

                // 3. Draw 3D Floor Grid Lines inside Isometric Rectangle
                val divisions = 5
                for (i in 1 until divisions) {
                    val frac = i / divisions.toFloat()
                    // Parallel to u-axis
                    val p1 = projectIso(0f, frac)
                    val p2 = projectIso(1f, frac)
                    drawLine(Color(0xFF1F1F1F).copy(alpha = 0.15f), p1, p2, strokeWidth = 1.dp.toPx())

                    // Parallel to v-axis
                    val p3 = projectIso(frac, 0f)
                    val p4 = projectIso(frac, 1f)
                    drawLine(Color(0xFF1F1F1F).copy(alpha = 0.15f), p3, p4, strokeWidth = 1.dp.toPx())
                }

                // 4. Calculate Waypoint Nodes inside 3D Rectangle Box
                val totalSteps = if (steps.isEmpty()) 3 else steps.size
                val nodes3D = mutableListOf<Pair<Float, Float>>()
                for (i in 0 until totalSteps) {
                    val progress = i / (totalSteps - 1).coerceAtLeast(1).toFloat()
                    val u = 0.15f + progress * 0.7f
                    val v = when (i % 3) {
                        0 -> 0.2f + progress * 0.4f
                        1 -> 0.8f - progress * 0.3f
                        else -> 0.35f + progress * 0.5f
                    }
                    nodes3D.add(Pair(u, v))
                }

                val screenNodes = nodes3D.map { projectIso(it.first, it.second) }

                // 5. Draw Glowing Route Lines
                for (i in 0 until screenNodes.size - 1) {
                    val p1 = screenNodes[i]
                    val p2 = screenNodes[i + 1]
                    val isCurrent = (i == activeStepIndex)
                    val isPassed = (i < activeStepIndex)

                    val color = when {
                        isCurrent -> Color(0xFFF791A9)
                        isPassed -> Color(0xFFBAD6DA)
                        else -> Color(0xFF1F1F1F).copy(alpha = 0.4f)
                    }

                    val strokeW = if (isCurrent) 6.dp.toPx() else 3.5.dp.toPx()
                    drawLine(color = color, start = p1, end = p2, strokeWidth = strokeW)

                    // Direction Chevron Indicator Dot
                    val mid = Offset((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f)
                    drawCircle(color = color, radius = 3.5.dp.toPx(), center = mid)
                }

                // 6. Draw 3D Waypoint Nodes
                screenNodes.forEachIndexed { idx, point ->
                    val isCurrent = idx == activeStepIndex
                    val isPassed = idx < activeStepIndex
                    val nodeColor = when {
                        isCurrent -> Color(0xFFF791A9)
                        isPassed -> Color(0xFFBAD6DA)
                        else -> Color(0xFF00C853)
                    }

                    drawCircle(color = nodeColor, radius = if (isCurrent) 10.dp.toPx() else 7.dp.toPx(), center = point)
                    drawCircle(color = Color.White, radius = if (isCurrent) 4.dp.toPx() else 2.5.dp.toPx(), center = point)
                }

                // 7. Calculate & Draw Interpolated "YOU ARE HERE" Beacon
                val currIdx = animatedStepIndex.toInt().coerceIn(0, nodes3D.size - 1)
                val nextIdx = (currIdx + 1).coerceAtMost(nodes3D.size - 1)
                val frac = animatedStepIndex - currIdx

                val currU = nodes3D[currIdx].first
                val currV = nodes3D[currIdx].second
                val nextU = nodes3D[nextIdx].first
                val nextV = nodes3D[nextIdx].second

                val userU = currU + (nextU - currU) * frac
                val userV = currV + (nextV - currV) * frac
                val userPos = projectIso(userU, userV)

                // Pulsing Aura Ring
                drawCircle(
                    color = Color(0xFFF791A9).copy(alpha = pulseAlpha),
                    radius = pulseRadius.dp.toPx(),
                    center = userPos
                )

                // Solid User Center Marker
                drawCircle(color = Color(0xFFF791A9), radius = 9.dp.toPx(), center = userPos)
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = userPos)
            }

            // Top Status Header Tag
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1F1F1F).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "3D ISOMETRIC MAP • STEP ${activeStepIndex + 1} OF ${steps.size.coerceAtLeast(1)}",
                    color = Color(0xFF1F1F1F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom "YOU ARE HERE" Badge
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

// Helper: Extract mild ambient color from image bitmap
private fun extractMildAmbientColor(bitmap: Bitmap): Color {
    return try {
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

        val blendR = (avgR * 0.15f + 0xE0 * 0.85f).toInt().coerceIn(0, 255)
        val blendG = (avgG * 0.15f + 0xF2 * 0.85f).toInt().coerceIn(0, 255)
        val blendB = (avgB * 0.15f + 0xF4 * 0.85f).toInt().coerceIn(0, 255)

        Color(blendR, blendG, blendB)
    } catch (e: Exception) {
        Color(0xFFE0F2F4)
    }
}
