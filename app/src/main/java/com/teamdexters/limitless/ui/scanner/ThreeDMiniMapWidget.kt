package com.teamdexters.limitless.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ThreeDMiniMapWidget(
    pathClarity: PathClarityStatus?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(120.dp)
            .border(2.dp, Color(0xFF1F1F1F).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)), // SurfaceTint
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw Isometric Grid
                val gridColor = Color(0xFFBAD6DA).copy(alpha = 0.5f)
                for (i in -2..4) {
                    // Iso horizontal-ish lines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, h * 0.5f + i * 20f),
                        end = Offset(w, h * 0.5f - w * 0.5f + i * 20f),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, h * 0.5f - i * 20f),
                        end = Offset(w, h * 0.5f + w * 0.5f - i * 20f),
                        strokeWidth = 1f
                    )
                }

                // Draw Path extending in 3D perspective
                val pathColor = when (pathClarity) {
                    PathClarityStatus.CLEAR -> Color(0xFF00C853)
                    PathClarityStatus.PARTIALLY_CLEAR -> Color(0xFFDDDD7B)
                    PathClarityStatus.BLOCKED_DANGER -> Color(0xFFF791A9)
                    null -> Color(0xFFBAD6DA)
                }

                val pathStartX = w * 0.5f
                val pathStartY = h * 0.8f
                val pathEndX = w * 0.5f
                val pathEndY = h * 0.2f

                drawLine(
                    color = pathColor,
                    start = Offset(pathStartX, pathStartY),
                    end = Offset(pathEndX, pathEndY),
                    strokeWidth = 8f
                )

                // User Location Dot
                drawCircle(
                    color = Color.Blue,
                    radius = 6f,
                    center = Offset(pathStartX, pathStartY)
                )

                // Camera Field of View Cone
                val fovPath = Path().apply {
                    moveTo(pathStartX, pathStartY)
                    lineTo(pathStartX - 20f, pathStartY - 30f)
                    lineTo(pathStartX + 20f, pathStartY - 30f)
                    close()
                }
                drawPath(
                    path = fovPath,
                    color = Color.Blue.copy(alpha = 0.2f)
                )

                // Hazard/Destination Indicator at the end of path
                if (pathClarity == PathClarityStatus.BLOCKED_DANGER) {
                    drawCircle(
                        color = Color(0xFFF791A9), // Red
                        radius = 8f,
                        center = Offset(pathEndX, pathEndY)
                    )
                } else if (pathClarity == PathClarityStatus.CLEAR) {
                    drawCircle(
                        color = Color(0xFF00C853), // Green
                        radius = 8f,
                        center = Offset(pathEndX, pathEndY)
                    )
                }
            }
        }
    }
}
