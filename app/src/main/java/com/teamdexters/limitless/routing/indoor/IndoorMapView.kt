package com.teamdexters.limitless.routing.indoor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D Canvas-based floor plan visualization for indoor navigation.
 *
 * @param userX            Current user X coordinate (meters).
 * @param userY            Current user Y coordinate (meters).
 * @param headingDegrees   Current compass heading in degrees.
 * @param waypoints        List of floor waypoints to draw.
 * @param targetWaypoint   Currently selected target waypoint (optional).
 */
@Composable
fun IndoorMapView(
    userX: Float,
    userY: Float,
    headingDegrees: Float,
    waypoints: List<IndoorWaypoint> = kcgIndoorWaypoints,
    targetWaypoint: IndoorWaypoint? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    var drawUserX by remember { mutableFloatStateOf(userX) }
    var drawUserY by remember { mutableFloatStateOf(userY) }
    var drawHeading by remember { mutableFloatStateOf(headingDegrees) }

    val shouldUpdate by remember(userX, userY, headingDegrees) {
        derivedStateOf {
            val distMoved = kotlin.math.hypot((userX - drawUserX).toDouble(), (userY - drawUserY).toDouble())
            var headingDiff = kotlin.math.abs(headingDegrees - drawHeading)
            if (headingDiff > 180f) headingDiff = 360f - headingDiff
            distMoved > 0.5 || headingDiff > 3.0f
        }
    }

    if (shouldUpdate) {
        drawUserX = userX
        drawUserY = userY
        drawHeading = headingDegrees
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceTint)
            .semantics {
                contentDescription = "Indoor map showing your position relative to key locations"
            }
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Scale meters to canvas pixels (floor bounds: 40m x 20m)
            val padding = 32f
            val maxMetersX = 35f
            val maxMetersY = 18f

            val scaleX = (canvasWidth - padding * 2) / maxMetersX
            val scaleY = (canvasHeight - padding * 2) / maxMetersY
            val scale = minOf(scaleX, scaleY)

            fun toCanvasX(xMeters: Float): Float = padding + xMeters * scale
            fun toCanvasY(yMeters: Float): Float = canvasHeight - (padding + yMeters * scale)

            // 1. Draw floor outline
            val floorOutlineRect = Size(maxMetersX * scale, maxMetersY * scale)
            val floorOrigin = Offset(padding, canvasHeight - padding - floorOutlineRect.height)

            drawRect(
                color = PersonaMobility.copy(alpha = 0.25f),
                topLeft = floorOrigin,
                size = floorOutlineRect
            )
            drawRect(
                color = TextPrimary.copy(alpha = 0.40f),
                topLeft = floorOrigin,
                size = floorOutlineRect,
                style = Stroke(width = 3f)
            )

            // 2. Draw waypoints
            waypoints.forEach { wp ->
                val cx = toCanvasX(wp.xMeters)
                val cy = toCanvasY(wp.yMeters)
                val isTarget = targetWaypoint?.id == wp.id

                val markerColor = if (isTarget) PersonaMobility else TextPrimary.copy(alpha = 0.70f)
                val radius = if (isTarget) 14f else 10f

                drawCircle(
                    color = markerColor,
                    center = Offset(cx, cy),
                    radius = radius
                )
                if (isTarget) {
                    drawCircle(
                        color = TextPrimary,
                        center = Offset(cx, cy),
                        radius = radius,
                        style = Stroke(width = 3f)
                    )
                }

                // Label text
                val textLayoutResult = textMeasurer.measure(
                    text = wp.name,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(cx - textLayoutResult.size.width / 2f, cy + 12f)
                )
            }

            // 3. Draw User Position Dot (PersonaMobility)
            val userCx = toCanvasX(drawUserX)
            val userCy = toCanvasY(drawUserY)

            drawCircle(
                color = PersonaMobility,
                center = Offset(userCx, userCy),
                radius = 16f
            )
            drawCircle(
                color = TextPrimary,
                center = Offset(userCx, userCy),
                radius = 16f,
                style = Stroke(width = 4f)
            )

            // 4. Draw Heading Arrow
            val rad = Math.toRadians(drawHeading.toDouble())
            val arrowLength = 32f
            val endX = userCx + (arrowLength * sin(rad)).toFloat()
            val endY = userCy - (arrowLength * cos(rad)).toFloat()

            drawLine(
                color = TextPrimary,
                start = Offset(userCx, userCy),
                end = Offset(endX, endY),
                strokeWidth = 5f
            )

            // Arrow tip triangle
            val tipPath = Path().apply {
                moveTo(endX, endY)
                val leftRad = rad + Math.toRadians(140.0)
                val rightRad = rad - Math.toRadians(140.0)
                val wingLen = 12f
                lineTo(
                    (endX + wingLen * sin(leftRad)).toFloat(),
                    (endY - wingLen * cos(leftRad)).toFloat()
                )
                lineTo(
                    (endX + wingLen * sin(rightRad)).toFloat(),
                    (endY - wingLen * cos(rightRad)).toFloat()
                )
                close()
            }
            drawPath(path = tipPath, color = TextPrimary)
        }
    }
}
