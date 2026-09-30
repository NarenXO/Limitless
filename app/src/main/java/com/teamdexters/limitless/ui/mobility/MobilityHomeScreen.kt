package com.teamdexters.limitless.ui.mobility

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.routing.*
import com.teamdexters.limitless.ui.routing.RouteMapScreen
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilityHomeScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: MobilityViewModel = viewModel()
    val graph by viewModel.graph.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()

    var selectedOrigin by rememberSaveable { mutableStateOf("LIMITLESS_ROOM_KCG_ENTRANCE") }
    var selectedDestination by rememberSaveable { mutableStateOf("LIMITLESS_ROOM_KCG_LIBRARY") }
    var preferRamp by rememberSaveable { mutableStateOf(true) }

    BackHandler {
        if (activeRoute != null) {
            viewModel.stopNavigation()
        } else {
            onBack()
        }
    }

    // IF A ROUTE IS ACTIVE, SHOW THE NEW 2D CANVAS ROUTE MAP SCREEN
    if (activeRoute != null && graph != null) {
        RouteMapScreen(
            route = activeRoute!!,
            graph = graph!!,
            voiceNavigator = viewModel.voiceNavigator,
            onNavigateBack = { viewModel.stopNavigation() }
        )
    } else {
        // SETUP / DESTINATION PICKER SCREEN
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Mobility & Wheelchair Navigation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LimitlessBackground)
                )
            },
            containerColor = LimitlessBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isSeeding) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = LimitlessPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Loading campus map...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                } else {
                    // Graph status banner
                    val nodeCount = graph?.totalNodes ?: 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (nodeCount > 0) PersonaMobility.copy(alpha = 0.3f) else HighlightBox
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (nodeCount > 0) "✓ $nodeCount rooms loaded in graph" else "⚠ No rooms found — check DB seed",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                    }

                    // Destination picker card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceTint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Select Destination (V2 A* Engine)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )

                            Text(
                                "From: Main Entrance → To:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )

                            // Row 1: Library & Auditorium
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedDestination = "LIMITLESS_ROOM_KCG_LIBRARY" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedDestination == "LIMITLESS_ROOM_KCG_LIBRARY") PersonaMobility else LimitlessBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Library (1F)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { selectedDestination = "LIMITLESS_ROOM_KCG_AUDITORIUM" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedDestination == "LIMITLESS_ROOM_KCG_AUDITORIUM") PersonaMobility else LimitlessBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Auditorium", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Row 2: Canteen & Hallway
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedDestination = "LIMITLESS_ROOM_KCG_CANTEEN" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedDestination == "LIMITLESS_ROOM_KCG_CANTEEN") PersonaMobility else LimitlessBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Canteen", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { selectedDestination = "LIMITLESS_ROOM_KCG_HALLWAY" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedDestination == "LIMITLESS_ROOM_KCG_HALLWAY") PersonaMobility else LimitlessBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Hallway", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Ramp preference toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Prefer Wheelchair Ramps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = preferRamp,
                                    onCheckedChange = { preferRamp = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PersonaMobility,
                                        checkedTrackColor = LimitlessPrimary
                                    )
                                )
                            }
                        }
                    }

                    // Compute & Launch Route Button
                    Button(
                        onClick = {
                            val currentGraph = graph
                            if (currentGraph == null || currentGraph.totalNodes == 0) {
                                Toast.makeText(context, "Graph not ready yet. Please wait.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.calculateRoute(selectedOrigin, selectedDestination, preferRamp)
                            if (viewModel.activeRoute.value == null) {
                                Toast.makeText(context, "No accessible route found!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PersonaMobility),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Calculate & Open 2D Floor Map",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Re-seed / refresh graph button
                    OutlinedButton(
                        onClick = {
                            viewModel.refreshGraph()
                            Toast.makeText(context, "Graph refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Refresh Campus Graph (${graph?.totalNodes ?: 0} rooms loaded)",
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
