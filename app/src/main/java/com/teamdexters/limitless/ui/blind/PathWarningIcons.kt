package com.teamdexters.limitless.ui.blind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Flat 2D single-color icons for path feature warnings.
 * All icons use TextPrimary color and are 96dp size.
 */

/**
 * Stairs icon - flat stair pattern
 */
@Composable
fun StairsIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(96.dp),
        onDraw = {
            val path = Path().apply {
                // Draw 3 steps
                val stepWidth = size.width / 3f
                val stepHeight = size.height / 4f

                // Bottom step
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width, size.height - stepHeight)
                lineTo(0f, size.height - stepHeight)
                close()

                // Middle step
                moveTo(0f, size.height - stepHeight)
                lineTo(size.width * 0.66f, size.height - stepHeight)
                lineTo(size.width * 0.66f, size.height - stepHeight * 2f)
                lineTo(0f, size.height - stepHeight * 2f)
                close()

                // Top step
                moveTo(0f, size.height - stepHeight * 2f)
                lineTo(size.width * 0.33f, size.height - stepHeight * 2f)
                lineTo(size.width * 0.33f, size.height - stepHeight * 3f)
                lineTo(0f, size.height - stepHeight * 3f)
                close()
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 8f)
            )
        }
    )
}

/**
 * Ramp icon - flat diagonal ramp
 */
@Composable
fun RampIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(96.dp),
        onDraw = {
            val path = Path().apply {
                // Draw ramp shape
                val padding = size.width * 0.15f
                val rampWidth = size.width - padding * 2f
                val rampHeight = size.height - padding * 2f

                // Ground line
                moveTo(padding, size.height - padding)
                lineTo(size.width - padding, size.height - padding)

                // Ramp diagonal
                moveTo(padding, size.height - padding)
                lineTo(size.width - padding, padding)

                // Top platform
                moveTo(size.width - padding, padding)
                lineTo(size.width - padding, size.height - padding)

                // Vertical support
                moveTo(padding, size.height - padding)
                lineTo(padding, size.height - padding - rampHeight * 0.3f)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 8f)
            )
        }
    )
}

/**
 * Curb icon - flat down-step
 */
@Composable
fun CurbIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(96.dp),
        onDraw = {
            val path = Path().apply {
                // Draw curb/step down
                val padding = size.width * 0.2f
                val stepWidth = size.width - padding * 2f
                val stepHeight = size.height * 0.3f

                // Top level
                moveTo(padding, padding)
                lineTo(size.width - padding, padding)

                // Step down
                moveTo(size.width - padding, padding)
                lineTo(size.width - padding, padding + stepHeight)

                // Bottom level
                moveTo(size.width - padding, padding + stepHeight)
                lineTo(padding, padding + stepHeight)

                // Vertical drop
                moveTo(padding, padding)
                lineTo(padding, padding + stepHeight)

                // Bottom level extension
                moveTo(padding, padding + stepHeight)
                lineTo(padding, size.height - padding)

                // Ground line
                moveTo(padding, size.height - padding)
                lineTo(size.width - padding, size.height - padding)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 8f)
            )
        }
    )
}

/**
 * Zebra crossing icon - flat crossing lines
 */
@Composable
fun ZebraIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(96.dp),
        onDraw = {
            val path = Path().apply {
                // Draw zebra crossing stripes
                val padding = size.width * 0.15f
                val stripeWidth = (size.width - padding * 2f) / 5f
                val stripeHeight = size.height * 0.6f
                val startY = (size.height - stripeHeight) / 2f

                // Draw 5 vertical stripes
                for (i in 0 until 5) {
                    val x = padding + i * stripeWidth
                    moveTo(x, startY)
                    lineTo(x, startY + stripeHeight)
                }

                // Top and bottom borders
                moveTo(padding, startY)
                lineTo(size.width - padding, startY)
                moveTo(padding, startY + stripeHeight)
                lineTo(size.width - padding, startY + stripeHeight)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 8f)
            )
        }
    )
}

/**
 * Traffic light icon - flat circle-in-square
 */
@Composable
fun TrafficLightIcon(
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(96.dp),
        onDraw = {
            val path = Path().apply {
                // Draw square frame
                val padding = size.width * 0.2f
                val squareSize = size.width - padding * 2f

                moveTo(padding, padding)
                lineTo(size.width - padding, padding)
                lineTo(size.width - padding, size.height - padding)
                lineTo(padding, size.height - padding)
                close()

                // Draw circle in center
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = squareSize * 0.25f

                addOval(
                    androidx.compose.ui.geometry.Rect(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius
                    )
                )
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 8f)
            )
        }
    )
}

/**
 * Select the appropriate icon based on path feature type.
 */
@Composable
fun PathFeatureIcon(
    featureType: PathFeatureType,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    when (featureType) {
        PathFeatureType.STAIRS -> StairsIcon(modifier = modifier, color = color)
        PathFeatureType.RAMP -> RampIcon(modifier = modifier, color = color)
        PathFeatureType.CURB -> CurbIcon(modifier = modifier, color = color)
        PathFeatureType.ZEBRA_CROSSING -> ZebraIcon(modifier = modifier, color = color)
        PathFeatureType.TRAFFIC_LIGHT -> TrafficLightIcon(modifier = modifier, color = color)
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun StairsIconPreview() {
    Box(modifier = Modifier.size(96.dp)) {
        StairsIcon()
    }
}

@Preview(showBackground = true)
@Composable
fun RampIconPreview() {
    Box(modifier = Modifier.size(96.dp)) {
        RampIcon()
    }
}

@Preview(showBackground = true)
@Composable
fun CurbIconPreview() {
    Box(modifier = Modifier.size(96.dp)) {
        CurbIcon()
    }
}

@Preview(showBackground = true)
@Composable
fun ZebraIconPreview() {
    Box(modifier = Modifier.size(96.dp)) {
        ZebraIcon()
    }
}

@Preview(showBackground = true)
@Composable
fun TrafficLightIconPreview() {
    Box(modifier = Modifier.size(96.dp)) {
        TrafficLightIcon()
    }
}
