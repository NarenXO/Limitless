// CommunityReportScreen.kt
package com.teamdexters.limitless.ui.community

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*
import com.teamdexters.limitless.ui.theme.*

/**
 * Phase 1: Community Report Screen – zero‑typing UI.
 * All interactive elements have empty contentDescription for accessibility compatibility.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: CommunityReportViewModel = hiltViewModel()
) {
    // Categories – using generic icons for compilation
    val categories = listOf(
        Category("Ramp", Icons.Default.TrendingUp),
        Category("Lift/Elevator", Icons.Default.ArrowUpward), // placeholder for elevator
        Category("Wide Doorway", Icons.Default.MeetingRoom), // door icon
        Category("Accessible Washroom", Icons.Default.Wc),
        Category("Parking", Icons.Default.LocalParking),
        Category("Other", Icons.Default.MoreHoriz)
    )

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val location by viewModel.location.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current

    var showObstacleDialog by remember { mutableStateOf(false) }
    var obstacleDescription by remember { mutableStateOf("") }

    val reports by viewModel.filteredReports.collectAsState(initial = emptyList())

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.onImageSelected(uri)
    }

    // Permission handling for location (simplified)
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(submitResult) {
        if (submitResult == true) {
            scaffoldState.snackbarHostState.showSnackbar("Report submitted successfully!")
        }
    }

    if (showObstacleDialog) {
        AlertDialog(
            onDismissRequest = { showObstacleDialog = false },
            containerColor = PureWhite,
            title = { Text("Report an Obstacle", color = PureBlack, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = obstacleDescription,
                    onValueChange = { obstacleDescription = it },
                    placeholder = { Text("Describe obstacle (e.g. Broken elevator, blocked ramp)", color = NeutralGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                        focusedBorderColor = PureBlack,
                        unfocusedBorderColor = SubtleDivider
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    android.util.Log.d("LIMITLESS_TRACE", "Community Obstacle report submitted: $obstacleDescription")
                    viewModel.submitObstacleReport(obstacleDescription)
                    showObstacleDialog = false
                    obstacleDescription = ""
                }) {
                    Text("Submit Obstacle", color = PureBlack, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showObstacleDialog = false }) {
                    Text("Cancel", color = NeutralGray)
                }
            }
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        containerColor = PureWhite,
        sheetContainerColor = PureWhite,
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val coroutineScope = rememberCoroutineScope()
                // Search Bar
                val searchQuery by viewModel.searchQuery.collectAsState()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        android.util.Log.d("LIMITLESS_TRACE", "Community Search query updated: $it")
                        viewModel.updateSearchQuery(it) 
                    },
                    placeholder = { Text("Search places in Chennai...", color = NeutralGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                        focusedBorderColor = PureBlack,
                        unfocusedBorderColor = SubtleDivider
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Filter Chips
                val filterCategory by viewModel.filterCategory.collectAsState()
                val filterOptions = listOf("All", "Ramps", "Elevators", "Washrooms", "Obstacles")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filterOptions) { filter ->
                        FilterChip(
                            selected = filterCategory == filter,
                            onClick = { 
                                android.util.Log.d("LIMITLESS_TRACE", "Community Filter chip tapped: $filter")
                                viewModel.updateFilterCategory(filter) 
                            },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PureBlack,
                                selectedLabelColor = PureWhite,
                                containerColor = SoftWhite,
                                labelColor = PureBlack
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (filterCategory == filter) PureBlack else SubtleDivider,
                                selectedBorderColor = PureBlack
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Community Reports (${reports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = PureBlack
                )
                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(reports) { report ->
                        val rating = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                        val timeDiff = System.currentTimeMillis() - report.timestamp
                        val timeString = when {
                            timeDiff < 3600000 -> "${maxOf(1, timeDiff / 60000)}m ago"
                            timeDiff < 86400000 -> "${timeDiff / 3600000}h ago"
                            timeDiff < 172800000 -> "Yesterday"
                            else -> "${timeDiff / 86400000}d ago"
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, SubtleDivider),
                            colors = CardDefaults.cardColors(containerColor = SoftWhite)
                        ) {
                            Column {
                                // Header: Avatar, Name, Time, Rating
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(PureBlack, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "C",
                                            color = PureWhite,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Community User",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = PureBlack
                                        )
                                        Text(
                                            text = timeString,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeutralGray
                                        )
                                    }
                                    Row {
                                        (1..5).forEach { star ->
                                            Icon(
                                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = null,
                                                tint = PureBlack,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Image
                                if (!report.photoUri.isNullOrEmpty()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = report.photoUri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .height(200.dp)
                                            .padding(horizontal = 16.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Tags and Description
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(LightGray, RoundedCornerShape(16.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = report.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureBlack,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(LightGray, RoundedCornerShape(16.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Trust Score: ${report.trustScore}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureBlack,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (!report.description.startsWith("Rating:")) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = report.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SoftBlack
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { 
                                            android.util.Log.d("LIMITLESS_TRACE", "Community Report upvoted (Confirm Location) ID: ${report.id}")
                                            viewModel.confirmReport(report.id) 
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Confirm Location (+1) • ${report.confirmationCount}", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PureWhite)
                .padding(innerPadding)
        ) {
            // Sync status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightGray)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = if (isOnline) "Online and Synced" else "Offline",
                    tint = PureBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "Synced with Community" else "Offline (Saved locally)",
                    style = MaterialTheme.typography.bodySmall,
                    color = PureBlack
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Report Obstacle Quick Action
                Button(
                    onClick = { 
                        showObstacleDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = PureWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report Obstacle", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                
                // Category selector
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        val cardBg = if (isSelected) PureBlack else SoftWhite
                        val cardBorder = if (isSelected) PureBlack else SubtleDivider
                        val contentColor = if (isSelected) PureWhite else PureBlack
                        Card(
                            modifier = Modifier
                                .size(96.dp)
                                .clickable { viewModel.onCategorySelected(category) }
                                .semantics { contentDescription = "" },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = contentColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Rating selector
                Text(
                    text = "Rate Accessibility",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "",
                            tint = PureBlack,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { viewModel.onRatingSelected(star) }
                        )
                    }
                }

                // Photo upload
                Text(
                    text = "Add Photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { 
                            imagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .semantics { contentDescription = "" },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleDivider),
                    colors = CardDefaults.cardColors(containerColor = SoftWhite)
                ) {
                    if (imageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Location
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Button(
                    onClick = {
                        if (!locationPermissionState.status.isGranted) {
                            locationPermissionState.launchPermissionRequest()
                        } else {
                            viewModel.fetchCurrentLocation(context)
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Use Current Location", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                location?.let { loc ->
                    Text(
                        text = "Lat: ${loc.latitude}, Lng: ${loc.longitude}",
                        color = PureBlack,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Submit button
                Button(
                    onClick = { viewModel.submitReport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Submit Report", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }

}

data class Category(val name: String, val icon: ImageVector)

