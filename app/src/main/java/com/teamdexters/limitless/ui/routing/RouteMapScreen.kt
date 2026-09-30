package com.teamdexters.limitless.ui.routing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.routing.*
import kotlin.math.max

val LimitlessBackground = Color(0xFFF7F1EE)
val LimitlessPrimary = Color(0xFFF791A9)
val PersonaMobility = Color(0xFFFFE797)
val SurfaceTint = Color(0xFFE0F2F4)
val TextPrimary = Color(0xFF1F1F1F)
val HighlightBox = Color(0xFFFFDBDF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen(
    route: Route,
    graph: AccessibilityGraph,
    voiceNavigator: VoiceNavigator,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress by voiceNavigator.navState.collectAsState()

    BackHandler {
        voiceNavigator.stopNavigation()
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(LimitlessBackground),
        containerColor = LimitlessBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Accessible Route Navigation",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            voiceNavigator.stopNavigation()
                            onNavigateBack()
                        },
                        modifier = Modifier.semantics { contentDescription = "Navigate Back and Stop Navigation" }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LimitlessBackground)
            )
        },
        bottomBar = {
            BottomControlBar(
                progress = progress,
                onRepeat = { voiceNavigator.repeatCurrentInstruction() },
                onNext = { voiceNavigator.nextStep() },
                onStop = {
                    voiceNavigator.stopNavigation()
                    onNavigateBack()
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status Section
            StatusSection(progress = progress, isFullyAccessible = route.isFullyAccessible, route = route)
            
            // Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                RouteCanvas(route = route, graph = graph, progress = progress)
            }

            // Current Step Card
            CurrentStepCard(progress = progress)
        }

        // Arrival Banner
        if (progress.isCompleted) {
            AlertDialog(
                onDismissRequest = { /* Require explicit finish */ },
                title = { Text("Navigation Complete", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("You have arrived at your destination.", color = TextPrimary) },
                confirmButton = {
                    Button(
                        onClick = {
                            voiceNavigator.stopNavigation()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LimitlessPrimary)
                    ) {
                        Text("Finish Navigation", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = LimitlessBackground
            )
        }
    }
}

@Composable
fun StatusSection(progress: NavigationProgress, isFullyAccessible: Boolean, route: Route) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(SurfaceTint, RoundedCornerShape(8.dp))
                .border(1.dp, LimitlessPrimary, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Remaining: ${progress.remainingDistanceMeters.toInt()}m | Step ${progress.currentStepIndex + 1}/${route.steps.size}",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isFullyAccessible) {
            Box(
                modifier = Modifier
                    .background(PersonaMobility, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Accessible, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "♿ Ramp Verified",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun RouteCanvas(route: Route, graph: AccessibilityGraph, progress: NavigationProgress) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(12.dp)).border(2.dp, SurfaceTint, RoundedCornerShape(12.dp))) {
        val routeNodeIds = route.steps.flatMap { listOf(it.fromRoomId, it.toRoomId) }.toSet()
        val routeNodes = routeNodeIds.mapNotNull { graph.nodes[it] }

        if (routeNodes.isEmpty()) return@Canvas

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        val hasCoords = routeNodes.all { it.latitude != null && it.longitude != null }
        val positions = mutableMapOf<String, Offset>()

        if (hasCoords) {
            routeNodes.forEach {
                if (it.longitude!! < minX) minX = it.longitude
                if (it.longitude!! > maxX) maxX = it.longitude
                if (it.latitude!! < minY) minY = it.latitude
                if (it.latitude!! > maxY) maxY = it.latitude
            }

            val margin = 100f
            val drawWidth = size.width - 2 * margin
            val drawHeight = size.height - 2 * margin

            val rangeX = max(maxX - minX, 0.00001)
            val rangeY = max(maxY - minY, 0.00001)

            routeNodes.forEach { node ->
                val normX = ((node.longitude!! - minX) / rangeX).toFloat()
                val normY = 1f - ((node.latitude!! - minY) / rangeY).toFloat() 
                
                positions[node.roomId] = Offset(
                    x = margin + normX * drawWidth,
                    y = margin + normY * drawHeight
                )
            }
        } else {
            val margin = 100f
            val spacingY = (size.height - 2 * margin) / max(1, route.steps.size)
            
            val orderedIds = mutableListOf<String>()
            route.steps.forEach { step ->
                if (orderedIds.lastOrNull() != step.fromRoomId) orderedIds.add(step.fromRoomId)
                if (step.direction != StepDirection.DESTINATION) {
                    if (orderedIds.lastOrNull() != step.toRoomId) orderedIds.add(step.toRoomId)
                }
            }
            
            orderedIds.forEachIndexed { index, id ->
                positions[id] = Offset(
                    x = size.width / 2f,
                    y = margin + index * spacingY
                )
            }
        }

        // Draw edges
        graph.adjacencyList.values.flatten().forEach { edge ->
            val fromPos = positions[edge.fromRoomId]
            val toPos = positions[edge.toRoomId]
            
            if (fromPos != null && toPos != null) {
                val isRouteEdge = route.steps.any { 
                    (it.fromRoomId == edge.fromRoomId && it.toRoomId == edge.toRoomId) ||
                    (it.fromRoomId == edge.toRoomId && it.toRoomId == edge.fromRoomId)
                }
                
                if (isRouteEdge) {
                    drawLine(
                        color = LimitlessPrimary,
                        start = fromPos,
                        end = toPos,
                        strokeWidth = 10f
                    )
                } else {
                    drawLine(
                        color = Color.LightGray,
                        start = fromPos,
                        end = toPos,
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }
        }

        // Draw Nodes
        val nodeWidth = 240f
        val nodeHeight = 120f
        
        positions.forEach { (id, center) ->
            val node = graph.nodes[id] ?: return@forEach
            val isOnRoute = routeNodeIds.contains(id)
            val topLeft = Offset(center.x - nodeWidth / 2, center.y - nodeHeight / 2)
            
            if (isOnRoute) {
                drawRoundRect(
                    color = LimitlessPrimary.copy(alpha = 0.3f),
                    topLeft = topLeft,
                    size = Size(nodeWidth, nodeHeight),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRoundRect(
                    color = LimitlessPrimary,
                    topLeft = topLeft,
                    size = Size(nodeWidth, nodeHeight),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 6f)
                )
            } else {
                drawRoundRect(
                    color = SurfaceTint,
                    topLeft = topLeft,
                    size = Size(nodeWidth, nodeHeight),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRoundRect(
                    color = Color.Gray,
                    topLeft = topLeft,
                    size = Size(nodeWidth, nodeHeight),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3f)
                )
            }
            
            val textLayoutResult = textMeasurer.measure(
                text = node.roomName,
                style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            val textOffset = Offset(
                center.x - textLayoutResult.size.width / 2,
                center.y - textLayoutResult.size.height / 2
            )
            drawText(
                textMeasurer = textMeasurer,
                text = node.roomName,
                topLeft = textOffset,
                style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
        }
        
        // Draw Current Position Indicator
        val activeStep = progress.currentStep
        if (activeStep != null) {
            val currentPos = positions[activeStep.fromRoomId]
            if (currentPos != null) {
                drawCircle(
                    color = PersonaMobility,
                    radius = 30f,
                    center = currentPos
                )
                drawCircle(
                    color = TextPrimary,
                    radius = 30f,
                    center = currentPos,
                    style = Stroke(width = 6f)
                )
            }
            
            if (route.steps.isNotEmpty()) {
                val destId = route.steps.last().toRoomId
                val destPos = positions[destId]
                if (destPos != null) {
                    drawCircle(
                        color = Color.Transparent,
                        radius = 40f,
                        center = destPos
                    )
                    drawCircle(
                        color = LimitlessPrimary,
                        radius = 40f,
                        center = destPos,
                        style = Stroke(width = 8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f))
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentStepCard(progress: NavigationProgress) {
    val step = progress.currentStep
    if (step == null) return

    val icon = when (step.direction) {
        StepDirection.LEFT -> Icons.Default.ArrowBack
        StepDirection.RIGHT -> Icons.Default.ArrowForward
        StepDirection.STRAIGHT -> Icons.Default.ArrowUpward
        StepDirection.RAMP_UP, StepDirection.RAMP_DOWN -> Icons.Default.Accessible
        StepDirection.STAIRS_UP, StepDirection.STAIRS_DOWN -> Icons.Default.Menu
        StepDirection.DESTINATION -> Icons.Default.Check
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = "Current instruction: ${step.spokenInstruction}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceTint)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.spokenInstruction,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (step.direction != StepDirection.DESTINATION) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Distance: ${step.distanceMeters.toInt()}m",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BottomControlBar(
    progress: NavigationProgress,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onRepeat, enabled = !progress.isCompleted)
                .semantics { contentDescription = "Repeat instruction" }
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(if (progress.isCompleted) Color.LightGray else SurfaceTint, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = TextPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text("Repeat", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onNext,
            enabled = !progress.isCompleted,
            colors = ButtonDefaults.buttonColors(containerColor = PersonaMobility, disabledContainerColor = Color.LightGray),
            modifier = Modifier
                .height(56.dp)
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "Next Step" },
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.NavigateNext, contentDescription = null, tint = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Next Step", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onStop)
                .semantics { contentDescription = "Stop Navigation" }
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(HighlightBox, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text("Stop", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
