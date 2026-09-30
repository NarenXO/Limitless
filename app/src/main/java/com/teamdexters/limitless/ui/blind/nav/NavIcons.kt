package com.teamdexters.limitless.ui.blind.nav

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Flat 2D navigation icons for turn-by-turn directions.
 * All icons are single-color (TextPrimary) with no gradients or photorealistic elements.
 */

@Composable
fun LeftTurnIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Canvas(
        modifier = modifier,
        onDraw = {
            val strokeWidth = 8.dp.toPx()
            val path = Path().apply {
                // Arrow pointing left with slight curve
                moveTo(size.width * 0.7f, size.height * 0.3f)
                lineTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.7f, size.height * 0.7f)
                
                // Arrowhead
                moveTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.45f, size.height * 0.35f)
                moveTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.45f, size.height * 0.65f)
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    )
}

@Composable
fun RightTurnIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Canvas(
        modifier = modifier,
        onDraw = {
            val strokeWidth = 8.dp.toPx()
            val path = Path().apply {
                // Arrow pointing right with slight curve
                moveTo(size.width * 0.3f, size.height * 0.3f)
                lineTo(size.width * 0.7f, size.height * 0.5f)
                lineTo(size.width * 0.3f, size.height * 0.7f)
                
                // Arrowhead
                moveTo(size.width * 0.7f, size.height * 0.5f)
                lineTo(size.width * 0.55f, size.height * 0.35f)
                moveTo(size.width * 0.7f, size.height * 0.5f)
                lineTo(size.width * 0.55f, size.height * 0.65f)
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    )
}

@Composable
fun StraightIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Canvas(
        modifier = modifier,
        onDraw = {
            val strokeWidth = 8.dp.toPx()
            val path = Path().apply {
                // Straight upward arrow
                moveTo(size.width * 0.5f, size.height * 0.7f)
                lineTo(size.width * 0.5f, size.height * 0.3f)
                
                // Arrowhead
                moveTo(size.width * 0.5f, size.height * 0.3f)
                lineTo(size.width * 0.35f, size.height * 0.45f)
                moveTo(size.width * 0.5f, size.height * 0.3f)
                lineTo(size.width * 0.65f, size.height * 0.45f)
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    )
}

@Composable
fun ArriveIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    Canvas(
        modifier = modifier,
        onDraw = {
            val strokeWidth = 8.dp.toPx()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.width * 0.35f
            
            // Circle
            drawPath(
                path = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            left = centerX - radius,
                            top = centerY - radius,
                            right = centerX + radius,
                            bottom = centerY + radius
                        )
                    )
                },
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Checkmark
            val checkPath = Path().apply {
                moveTo(centerX - radius * 0.4f, centerY)
                lineTo(centerX - radius * 0.1f, centerY + radius * 0.3f)
                lineTo(centerX + radius * 0.4f, centerY - radius * 0.3f)
            }
            
            drawPath(
                path = checkPath,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    )
}

@Composable
fun TurnIcon(
    turnType: TurnType,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    when (turnType) {
        TurnType.LEFT -> LeftTurnIcon(modifier = modifier, color = color)
        TurnType.RIGHT -> RightTurnIcon(modifier = modifier, color = color)
        TurnType.STRAIGHT -> StraightIcon(modifier = modifier, color = color)
        TurnType.ARRIVE -> ArriveIcon(modifier = modifier, color = color)
    }
}
