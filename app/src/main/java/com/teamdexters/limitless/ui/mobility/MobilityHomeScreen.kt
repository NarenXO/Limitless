package com.teamdexters.limitless.ui.mobility

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamdexters.limitless.routing.GraphNode
import com.teamdexters.limitless.routing.RouteStep
import com.teamdexters.limitless.routing.StepDirection
import com.teamdexters.limitless.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilityHomeScreen(
    viewModel: MobilityViewModel = hiltViewModel(),
    onNavigateToRoom: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val graph by viewModel.graph.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()

    var selectedDestinationId by remember { mutableStateOf<String?>(null) }
    // Current location is handled by the viewModel, pass null for dynamic resolution
    val startRoomId: String? = null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Accessible Navigation",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Text(
                        "Where to?",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Location: Main Entrance (Default)", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isSeeding) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(color = Color(0xFFF791A9))
                }
            }
        } else {
            val nodes = graph?.nodes?.values?.toList() ?: emptyList()
            if (nodes.isEmpty()) {
                item {
                    Text("No rooms found in database.", modifier = Modifier.padding(16.dp), color = Color(0xFF1F1F1F))
                }
            } else {
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
                            // Calculate Path
                            viewModel.calculateAndNavigateRoute(startRoomId, selectedDestinationId!!, preferRamp = true)
                            android.util.Log.d("LIMITLESS_TRACE", "Mobility -> [$startRoomId] to [$selectedDestinationId]")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Navigate Accessible Route", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Path Result Section
        if (activeRoute != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Your Accessible Route:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Path String
                    val pathString = if (activeRoute?.steps?.isNotEmpty() == true) {
                        activeRoute!!.steps.first().fromRoomName + " -> " + activeRoute!!.steps.joinToString(" -> ") { it.toRoomName }
                    } else "Arrived"
                    Text(
                        text = pathString,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFF791A9)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Step List
                    activeRoute?.steps?.forEach { step ->
                        RouteStepItem(step)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Actions
                    Button(
                        onClick = { viewModel.voiceNavigator.startNavigation(activeRoute!!) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F2F4)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Turn-by-Turn Guidance", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.stopNavigation() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Route", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (selectedDestinationId != null && graph?.nodes?.isNotEmpty() == true) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color(0xFFFFDBDF), RoundedCornerShape(8.dp)).padding(16.dp)
                ) {
                    Text("Select a destination and tap Navigate to see your route.", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun DestinationCard(node: GraphNode, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 52.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFE797) else Color(0xFFE0F2F4)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(node.roomName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1F1F1F))
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
        Text(label, fontSize = 12.sp, color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RouteStepItem(step: RouteStep) {
    val stepIcon = when (step.direction) {
        StepDirection.STRAIGHT -> Icons.Default.ArrowUpward
        StepDirection.LEFT -> Icons.Default.ArrowBack
        StepDirection.RIGHT -> Icons.Default.ArrowForward
        StepDirection.RAMP_UP -> Icons.Default.ArrowUpward
        StepDirection.RAMP_DOWN -> Icons.Default.ArrowDownward
        StepDirection.STAIRS_UP -> Icons.Default.ArrowUpward
        StepDirection.STAIRS_DOWN -> Icons.Default.ArrowDownward
        StepDirection.DESTINATION -> Icons.Default.Place
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0F2F4), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = stepIcon,
                contentDescription = null,
                tint = Color(0xFF1F1F1F),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.spokenInstruction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F1F1F)
                )
            }
        }
    }
}
