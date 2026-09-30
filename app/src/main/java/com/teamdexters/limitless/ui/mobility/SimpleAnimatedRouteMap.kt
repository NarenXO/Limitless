package com.teamdexters.limitless.ui.mobility

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.routing.Route

@Composable
fun SimpleAnimatedRouteMap(
    route: Route,
    currentStepIndex: Int,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    // Colors based on constraints
    val surfaceTint = Color(0xFFE0F2F4)
    val primaryColor = Color(0xFFF791A9)
    val textPrimary = Color(0xFF1F1F1F)

    var drawProgress by remember { mutableStateOf(0f) }
    
    // Animate the path drawing in when the route first appears
    LaunchedEffect(route) {
        drawProgress = 0f
        // Start animation immediately
        drawProgress = 1f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = drawProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "path_draw"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(surfaceTint, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics { contentDescription = "Animated Live Navigation Map" },
        contentAlignment = Alignment.Center
    ) {
        if (route.steps.isEmpty()) {
            Text("No path data to display.", color = textPrimary)
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate logical positions for nodes
            val stepsCount = route.steps.size
            // Create positions along a simple path (zigzag or straight line to keep it simple and clean)
            
            val nodePositions = mutableListOf<Offset>()
            
            // Generate simple positions: straight horizontal line with slight vertical offsets
            // to keep it within the canvas bounds.
            val paddingX = 40f
            val paddingY = 40f
            
            // Total nodes is steps + 1
            val totalNodes = stepsCount + 1
            
            for (i in 0 until totalNodes) {
                val fractionX = if (totalNodes > 1) (i.toFloat() / (totalNodes - 1).toFloat()) else 0.5f
                val x = paddingX + fractionX * (canvasWidth - 2 * paddingX)
                
                // Zig zag vertically a bit
                val y = canvasHeight / 2f + if (i % 2 == 0) -20f else 20f
                nodePositions.add(Offset(x, y))
            }

            // Draw full path with Primary Color
            val path = Path()
            if (nodePositions.isNotEmpty()) {
                path.moveTo(nodePositions[0].x, nodePositions[0].y)
                for (i in 1 until nodePositions.size) {
                    path.lineTo(nodePositions[i].x, nodePositions[i].y)
                }
            }

            // Path stroke
            drawPath(
                path = path,
                color = primaryColor.copy(alpha = 0.4f),
                style = Stroke(width = 12f)
            )

            // Draw the animated path
            androidx.compose.ui.graphics.drawscope.clipRect(
                left = 0f, 
                top = 0f, 
                right = canvasWidth * animatedProgress, 
                bottom = canvasHeight
            ) {
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 12f)
                )
            }

            // Draw dots for rooms
            for (i in 0 until totalNodes) {
                // If animated progress has reached this node, draw it
                if (animatedProgress >= (i.toFloat() / maxOf(1, totalNodes - 1))) {
                    drawCircle(
                        color = Color.White,
                        radius = 16f,
                        center = nodePositions[i]
                    )
                    drawCircle(
                        color = if (isNavigating && currentStepIndex == i) primaryColor else textPrimary,
                        radius = 12f,
                        center = nodePositions[i]
                    )
                }
            }

            // Draw moving traveler dot
            if (isNavigating && nodePositions.isNotEmpty() && currentStepIndex < nodePositions.size) {
                // Current position of the traveler
                val travelerPos = nodePositions[currentStepIndex]
                
                // Outer pulse ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = 32f,
                    center = travelerPos
                )
                
                // Inner solid dot
                drawCircle(
                    color = primaryColor,
                    radius = 18f,
                    center = travelerPos
                )
                
                // White center
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = travelerPos
                )
            }
        }
    }
}
