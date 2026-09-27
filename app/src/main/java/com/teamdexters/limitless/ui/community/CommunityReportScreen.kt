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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*

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

    val reports by viewModel.reports.collectAsState(initial = emptyList())

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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Community Reports (${reports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports) { report ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1EE))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = report.locationName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Category: ${report.category}", style = MaterialTheme.typography.bodySmall)
                                Text("Description: ${report.description}", style = MaterialTheme.typography.bodySmall)
                                Text("Trust Score: ${report.trustScore}", style = MaterialTheme.typography.bodySmall)
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
                .padding(innerPadding)
        ) {
            // Sync status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F2F4))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = if (isOnline) "Online and Synced" else "Offline",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "Synced with Community" else "Offline (Saved locally)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category selector
            Text(text = "Select Category", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    Card(
                        modifier = Modifier
                            .size(96.dp)
                            .clickable { viewModel.onCategorySelected(category) }
                            .semantics { contentDescription = "" },
                        shape = RoundedCornerShape(8.dp),
                        border = if (selectedCategory == category) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = category.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Rating selector
            Text(text = "Rate Accessibility", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { star ->
                    Icon(
                        imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "",
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { viewModel.onRatingSelected(star) }
                    )
                }
            }

            // Photo upload
            Text(text = "Add Photo", style = MaterialTheme.typography.titleMedium)
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
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
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
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            // Location
            Text(text = "Location", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    if (!locationPermissionState.status.isGranted) {
                        locationPermissionState.launchPermissionRequest()
                    } else {
                        viewModel.fetchCurrentLocation(context)
                    }
                },
                modifier = Modifier.semantics { contentDescription = "" }
            ) {
                Text("Use Current Location")
            }
            location?.let { loc ->
                Text(text = "Lat: ${loc.latitude}, Lng: ${loc.longitude}")
            }

            // Submit button
            Button(
                onClick = { viewModel.submitReport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9), contentColor = Color.White)
            ) {
                Text(text = "Submit Report")
            }
            }
        }
    }
}

data class Category(val name: String, val icon: ImageVector)

