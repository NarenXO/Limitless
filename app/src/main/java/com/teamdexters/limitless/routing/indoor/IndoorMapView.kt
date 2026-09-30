package com.teamdexters.limitless.routing.indoor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.PureBlack
import com.teamdexters.limitless.ui.theme.PureWhite
import com.teamdexters.limitless.ui.theme.SoftWhite
import com.teamdexters.limitless.ui.theme.LightGray
import com.teamdexters.limitless.ui.theme.SubtleDivider
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
    val density = LocalDensity.current.density

    val drawUserX by animateFloatAsState(
        targetValue = userX,
        animationSpec = tween(durationMillis = 300),
        label = "userX"
    )
    val drawUserY by animateFloatAsState(
        targetValue = userY,
        animationSpec = tween(durationMillis = 300),
        label = "userY"
    )

    var drawHeading by remember { mutableFloatStateOf(headingDegrees) }

    val shouldUpdateHeading by remember(headingDegrees) {
        derivedStateOf {
            var headingDiff = kotlin.math.abs(headingDegrees - drawHeading)
            if (headingDiff > 180f) headingDiff = 360f - headingDiff
            headingDiff > 3.0f
        }
    }

    if (shouldUpdateHeading) {
        drawHeading = headingDegrees
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SoftWhite)
            .semantics {
                contentDescription = "Indoor map showing your position relative to key locations"
            }
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Scale meters to canvas pixels: 1 meter = 30dp
            val scale = 30f * density

            fun toMapX(xMeters: Float): Float = xMeters * scale
            // Y is inverted: map origin is bottom-left, canvas origin is top-left
            fun toMapY(yMeters: Float): Float = -yMeters * scale

            val userMapX = toMapX(drawUserX)
            val userMapY = toMapY(drawUserY)

            // Auto-center on user position
            val translateX = canvasWidth / 2f - userMapX
            val translateY = canvasHeight / 2f - userMapY

            translate(left = translateX, top = translateY) {
                // 1. Draw floor outline
                val maxMetersX = 35f
                val maxMetersY = 18f
                
                val floorOutlineRect = Size(maxMetersX * scale, maxMetersY * scale)
                // Origin of the floor in this translated space
                val floorOrigin = Offset(0f, -floorOutlineRect.height)

                drawRect(
                    color = LightGray,
                    topLeft = floorOrigin,
                    size = floorOutlineRect
                )
                drawRect(
                    color = SubtleDivider,
                    topLeft = floorOrigin,
                    size = floorOutlineRect,
                    style = Stroke(width = 3f)
                )

                // 2. Draw waypoints
                waypoints.forEach { wp ->
                    val cx = toMapX(wp.xMeters)
                    val cy = toMapY(wp.yMeters)
                    val isTarget = targetWaypoint?.id == wp.id

                    val markerColor = if (isTarget) PureBlack else LightGray
                    
                    val rectSize = Size(60f, 24f)
                    val rectTopLeft = Offset(cx - rectSize.width / 2f, cy - rectSize.height / 2f)

                    drawRect(
                        color = markerColor,
                        topLeft = rectTopLeft,
                        size = rectSize
                    )
                    
                    if (isTarget || true) {
                        drawRect(
                            color = PureBlack,
                            topLeft = rectTopLeft,
                            size = rectSize,
                            style = Stroke(width = if (isTarget) 4f else 1.5f)
                        )
                    }

                    // Label text
                    val textColor = if (isTarget) PureWhite else PureBlack
                    val textLayoutResult = textMeasurer.measure(
                        text = wp.name,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(cx - textLayoutResult.size.width / 2f, cy - textLayoutResult.size.height / 2f)
                    )
                }

                // 3. Draw User Position Dot — PureBlack with PureWhite center dot
                drawCircle(
                    color = PureBlack,
                    center = Offset(userMapX, userMapY),
                    radius = 14f
                )
                drawCircle(
                    color = PureWhite,
                    center = Offset(userMapX, userMapY),
                    radius = 6f
                )

                // 4. Draw Heading Arrow
                val rad = Math.toRadians(drawHeading.toDouble())
                val arrowLength = 32f
                val endX = userMapX + (arrowLength * sin(rad)).toFloat()
                val endY = userMapY - (arrowLength * cos(rad)).toFloat()

                drawLine(
                    color = PureBlack,
                    start = Offset(userMapX, userMapY),
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
                drawPath(path = tipPath, color = PureBlack)
            }
        }
    }
}
