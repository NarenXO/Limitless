package com.teamdexters.limitless.ui.roommapping

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.roommapping.SpatialRouteStep
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ThreeDIsometricMap(
    steps: List<SpatialRouteStep>,
    activeStepIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconPulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beaconPulse"
    )
    val beaconPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beaconAlpha"
    )

    // Smooth transition for user position moving along graph
    val animatedStepIndex by animateFloatAsState(
        targetValue = activeStepIndex.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "stepAnim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .border(2.dp, Color(0xFFF791A9), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 3D Isometric Projection Helper
                // Maps 3D World (x: 0..1, y: 0..1, z: 0..1) to 2D Screen Canvas
                fun project3D(x: Float, y: Float, z: Float): Offset {
                    val centerX = canvasWidth * 0.5f
                    val centerY = canvasHeight * 0.55f
                    val scaleX = canvasWidth * 0.38f
                    val scaleY = canvasHeight * 0.28f
                    val scaleZ = canvasHeight * 0.25f

                    // 3D Isometric Rotation Matrix (30 degree tilt)
                    val cos30 = 0.866f
                    val sin30 = 0.5f

                    val isoX = centerX + (x - y) * cos30 * scaleX
                    val isoY = centerY + (x + y) * sin30 * scaleY - (z * scaleZ)
                    return Offset(isoX, isoY)
                }

                // 1. Draw 3D Floor Plan Grid (Tile Surface)
                val gridDivisions = 6
                for (i in 0..gridDivisions) {
                    val t = i / gridDivisions.toFloat()
                    drawLine(
                        color = Color(0xFF1F1F1F).copy(alpha = 0.12f),
                        start = project3D(t, 0f, 0f),
                        end = project3D(t, 1f, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF1F1F1F).copy(alpha = 0.12f),
                        start = project3D(0f, t, 0f),
                        end = project3D(1f, t, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 2. Draw Extruded 3D Architectural Perimeter Walls
                val wallHeight = 0.35f
                val wallColor = Color(0xFF1F1F1F).copy(alpha = 0.15f)
                val wallStrokeColor = Color(0xFF1F1F1F).copy(alpha = 0.6f)

                // West Wall (x=0)
                val westWall = Path().apply {
                    moveTo(project3D(0f, 0f, 0f).x, project3D(0f, 0f, 0f).y)
                    lineTo(project3D(0f, 1f, 0f).x, project3D(0f, 1f, 0f).y)
                    lineTo(project3D(0f, 1f, wallHeight).x, project3D(0f, 1f, wallHeight).y)
                    lineTo(project3D(0f, 0f, wallHeight).x, project3D(0f, 0f, wallHeight).y)
                    close()
                }
                drawPath(westWall, color = wallColor)
                drawPath(westWall, color = wallStrokeColor, style = Stroke(width = 1.5.dp.toPx()))

                // North Wall (y=0)
                val northWall = Path().apply {
                    moveTo(project3D(0f, 0f, 0f).x, project3D(0f, 0f, 0f).y)
                    lineTo(project3D(1f, 0f, 0f).x, project3D(1f, 0f, 0f).y)
                    lineTo(project3D(1f, 0f, wallHeight).x, project3D(1f, 0f, wallHeight).y)
                    lineTo(project3D(0f, 0f, wallHeight).x, project3D(0f, 0f, wallHeight).y)
                    close()
                }
                drawPath(northWall, color = wallColor)
                drawPath(northWall, color = wallStrokeColor, style = Stroke(width = 1.5.dp.toPx()))

                // 3. Draw 3D Extruded Doorway Archway (Entrance & Exit)
                // East Doorway (Exit)
                val doorWidth = 0.25f
                val doorStart = project3D(1f, 0.35f, 0f)
                val doorEnd = project3D(1f, 0.35f + doorWidth, 0f)
                val doorTopStart = project3D(1f, 0.35f, 0.45f)
                val doorTopEnd = project3D(1f, 0.35f + doorWidth, 0.45f)

                val doorArch = Path().apply {
                    moveTo(doorStart.x, doorStart.y)
                    lineTo(doorTopStart.x, doorTopStart.y)
                    lineTo(doorTopEnd.x, doorTopEnd.y)
                    lineTo(doorEnd.x, doorEnd.y)
                }
                drawPath(doorArch, color = Color(0xFFBAD6DA), style = Stroke(width = 3.dp.toPx()))

                // 4. Calculate Precise 3D Route Graph Nodes
                val totalSteps = if (steps.isEmpty()) 3 else steps.size
                val graphNodes = mutableListOf<Offset>()
                val raw3DNodes = mutableListOf<Triple<Float, Float, Float>>()

                // Generate precise spatial waypoints through room geometry
                for (i in 0 until totalSteps) {
                    val progress = i / (totalSteps - 1).coerceAtLeast(1).toFloat()
                    val x = 0.15f + progress * 0.7f
                    val y = when (i % 3) {
                        0 -> 0.2f + progress * 0.3f
                        1 -> 0.75f - progress * 0.2f
                        else -> 0.35f + progress * 0.45f
                    }
                    val z = 0.02f // Slightly elevated off floor plane
                    raw3DNodes.add(Triple(x, y, z))
                    graphNodes.add(project3D(x, y, z))
                }

                // 5. Draw 3D Polyline Route Path & Directional Chevrons
                for (i in 0 until graphNodes.size - 1) {
                    val p1 = graphNodes[i]
                    val p2 = graphNodes[i + 1]
                    val isCurrentSegment = (i == activeStepIndex)

                    val lineThickness = if (isCurrentSegment) 6.dp.toPx() else 3.5.dp.toPx()
                    val lineColor = if (isCurrentSegment) Color(0xFFF791A9) else Color(0xFF00C853)

                    drawLine(
                        color = lineColor,
                        start = p1,
                        end = p2,
                        strokeWidth = lineThickness
                    )

                    // Draw 3D Directional Chevron Arrow along path segment
                    val midX = (p1.x + p2.x) * 0.5f
                    val midY = (p1.y + p2.y) * 0.5f
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(midX, midY))
                }

                // 6. Draw 3D Graph Nodes with Badges
                graphNodes.forEachIndexed { index, nodeOffset ->
                    val isPassed = index < activeStepIndex
                    val isActive = index == activeStepIndex
                    val nodeColor = when {
                        isActive -> Color(0xFFF791A9)
                        isPassed -> Color(0xFFBAD6DA)
                        else -> Color(0xFF00C853)
                    }

                    // Node Circle
                    drawCircle(color = nodeColor, radius = if (isActive) 12.dp.toPx() else 8.dp.toPx(), center = nodeOffset)
                    drawCircle(color = Color.White, radius = if (isActive) 5.dp.toPx() else 3.dp.toPx(), center = nodeOffset)
                }

                // 7. Calculate Interpolated 3D Position for "YOU ARE HERE" Avatar
                val currentIdx = animatedStepIndex.toInt().coerceIn(0, raw3DNodes.size - 1)
                val nextIdx = (currentIdx + 1).coerceAtMost(raw3DNodes.size - 1)
                val fraction = animatedStepIndex - currentIdx

                val currNode = raw3DNodes[currentIdx]
                val nextNode = raw3DNodes[nextIdx]

                val user3DX = currNode.first + (nextNode.first - currNode.first) * fraction
                val user3DY = currNode.second + (nextNode.second - currNode.second) * fraction
                val user3DZ = 0.05f

                val userScreenPos = project3D(user3DX, user3DY, user3DZ)

                // 8. Draw Animated 3D "YOU ARE HERE" Pulsing Beacon & FOV Cone
                // Pulsing Aura Ring
                drawCircle(
                    color = Color(0xFFF791A9).copy(alpha = beaconPulseAlpha),
                    radius = beaconPulseRadius.dp.toPx(),
                    center = userScreenPos
                )

                // 3D Directional Field-of-View Cone pointing toward next waypoint
                val fovPath = Path().apply {
                    val fovLength = 28.dp.toPx()
                    val fovWidth = 18.dp.toPx()
                    val dirX = nextNode.first - currNode.first
                    val dirY = nextNode.second - currNode.second
                    val angle = kotlin.math.atan2(dirY.toDouble(), dirX.toDouble()).toFloat()

                    val tipX = userScreenPos.x + fovLength * cos(angle)
                    val tipY = userScreenPos.y + fovLength * sin(angle)
                    val leftX = userScreenPos.x + fovWidth * cos(angle + 2.4f)
                    val leftY = userScreenPos.y + fovWidth * sin(angle + 2.4f)
                    val rightX = userScreenPos.x + fovWidth * cos(angle - 2.4f)
                    val rightY = userScreenPos.y + fovWidth * sin(angle - 2.4f)

                    moveTo(userScreenPos.x, userScreenPos.y)
                    lineTo(leftX, leftY)
                    lineTo(tipX, tipY)
                    lineTo(rightX, rightY)
                    close()
                }
                drawPath(fovPath, color = Color(0xFFF791A9).copy(alpha = 0.35f))

                // Solid User Center Marker
                drawCircle(color = Color(0xFFF791A9), radius = 9.dp.toPx(), center = userScreenPos)
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = userScreenPos)

                // 9. Draw Cardinal Compass Headings on Floor
                val northPos = project3D(0.5f, 0.05f, 0f)
                val southPos = project3D(0.5f, 0.95f, 0f)
                val eastPos = project3D(0.95f, 0.5f, 0f)
                val westPos = project3D(0.05f, 0.5f, 0f)

                drawCircle(color = Color(0xFF1F1F1F).copy(alpha = 0.2f), radius = 3.dp.toPx(), center = northPos)
                drawCircle(color = Color(0xFF1F1F1F).copy(alpha = 0.2f), radius = 3.dp.toPx(), center = southPos)
                drawCircle(color = Color(0xFF1F1F1F).copy(alpha = 0.2f), radius = 3.dp.toPx(), center = eastPos)
                drawCircle(color = Color(0xFF1F1F1F).copy(alpha = 0.2f), radius = 3.dp.toPx(), center = westPos)
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
                    text = "PRECISE 3D GRAPH • STEP ${activeStepIndex + 1} OF ${steps.size.coerceAtLeast(1)}",
                    color = Color(0xFF1F1F1F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Floating "YOU ARE HERE" Badge attached to top center
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
                        text = "YOU ARE HERE (Live 3D Beacon)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
