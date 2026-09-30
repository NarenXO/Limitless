package com.teamdexters.limitless.ui.mobility

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamdexters.limitless.routing.GraphNode
import com.teamdexters.limitless.routing.Route
import com.teamdexters.limitless.routing.RouteStep

@Composable
fun MobilityHomeScreen(
    viewModel: MobilityViewModel = hiltViewModel(),
    onNavigateToRoom: (String) -> Unit = {}
) {
    var hasError by remember { mutableStateOf(false) }

    if (hasError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1EE))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mobility Navigation",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { hasError = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9))
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }
    } else {
        MobilityHomeScreenContent(viewModel = viewModel, onNavigateToRoom = onNavigateToRoom)
    }
}

@Composable
fun MobilityHomeScreenContent(
    viewModel: MobilityViewModel,
    onNavigateToRoom: (String) -> Unit
) {
    val context = LocalContext.current
    val graph by viewModel.graph.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val navState by viewModel.voiceNavigator.navState.collectAsState()

    var selectedDestinationId by remember { mutableStateOf<String?>(null) }
    val startRoomId: String? = null // Defaults to current location tracker logic

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Section A) TOP - Current Location + Destination (one clean card)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics { contentDescription = "Accessible Route Card" },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Accessible Route",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "From: Main Entrance", // Current Room Fallback
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val toRoom = graph?.nodes?.values?.find { it.roomId == selectedDestinationId }?.roomName ?: "Select below"
                    Text(
                        text = "To: $toRoom",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F),
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Ramp preferred", color = Color(0xFF1F1F1F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section B & C) Live Map & Path Strip (Shown when route exists)
        if (activeRoute != null) {
            val route = activeRoute!!
            
            // The Live Animated Route Map
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SimpleAnimatedRouteMap(
                        route = route,
                        currentStepIndex = navState.currentStepIndex,
                        isNavigating = navState.isNavigating
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Middle: Simple Path Strip
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Route Steps",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Horizontal scrollable strip of steps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Entrance node
                        PathStripNode("Start", isCompleted = navState.currentStepIndex > 0, isActive = navState.currentStepIndex == 0)
                        
                        route.steps.forEachIndexed { index, step ->
                            PathStripNode(
                                label = step.toRoomName,
                                isCompleted = navState.currentStepIndex > index + 1,
                                isActive = navState.currentStepIndex == index + 1
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // One line of large text under strip
                    val currentInstruction = if (navState.isCompleted) {
                        "You have arrived!"
                    } else if (navState.currentStep != null) {
                        "Next: ${navState.currentStep!!.spokenInstruction}"
                    } else {
                        "Ready to start navigation."
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0F2F4), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = currentInstruction,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            // Bottom: Live Navigation Controls
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (!navState.isNavigating && !navState.isCompleted) {
                        Button(
                            onClick = { viewModel.voiceNavigator.startNavigation(route) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .semantics { contentDescription = "Start Live Navigation" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Start Live Navigation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    } else {
                        // While navigating
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.voiceNavigator.previousStep() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(end = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !navState.isCompleted
                            ) {
                                Text("Previous", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.voiceNavigator.repeatCurrentInstruction() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !navState.isCompleted
                            ) {
                                Text("Repeat", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.voiceNavigator.nextStep() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(start = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !navState.isCompleted
                            ) {
                                Text("Next", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { viewModel.stopNavigation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .semantics { contentDescription = "Stop Navigation" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Stop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Remaining: ${navState.remainingDistanceMeters.toInt()}m (${navState.totalSteps - navState.currentStepIndex} steps)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } 
        else if (isSeeding) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Seeding mobility database...", color = Color(0xFFF791A9), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        } 
        else {
            // "Where to?" Destination List
            val nodes = graph?.nodes?.values?.toList() ?: emptyList()
            if (nodes.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(16.dp).background(Color(0xFFFFDBDF), RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Text("No accessible route found. Try another room.", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                item {
                    Text(
                        "Where to?",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1F1F1F)
                    )
                }
                
                items(nodes) { node ->
                    DestinationCard(
                        node = node,
                        isSelected = selectedDestinationId == node.roomId,
                        onClick = { selectedDestinationId = node.roomId }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (selectedDestinationId == null) {
                                Toast.makeText(context, "Please select a destination first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.calculateAndNavigateRoute(startRoomId, selectedDestinationId!!, preferRamp = true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(64.dp)
                            .semantics { contentDescription = "Navigate Accessible Route" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Navigate Accessible Route", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PathStripNode(label: String, isCompleted: Boolean, isActive: Boolean) {
    val bgColor = if (isCompleted) Color(0xFFE0F2F4) else Color.White
    val borderColor = if (isActive) Color(0xFFF791A9) else Color.Transparent
    
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(width = if (isActive) 3.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF1F1F1F),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}

@Composable
fun DestinationCard(node: GraphNode, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Destination: ${node.roomName}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFE797) else Color(0xFFE0F2F4)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(node.roomName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1F1F1F))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.hasRamp) {
                    TextChip("Ramp Available")
                }
                if (node.hasWideDoor) {
                    TextChip("Wide Door")
                }
                if (node.hasStairs) {
                    TextChip("Stairs Warning")
                }
            }
        }
    }
}

@Composable
fun TextChip(label: String) {
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium)
    }
}
