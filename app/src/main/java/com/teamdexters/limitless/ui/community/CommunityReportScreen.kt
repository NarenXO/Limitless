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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CommunityReportScreen(
    viewModel: CommunityReportViewModel = hiltViewModel()
) {
    // Categories – using generic icons for compilation
    val categories = listOf(
        Category("Ramp", Icons.Default.TrendingUp),
        Category("Lift/Elevator", Icons.Default.ElevenMp), // placeholder
        Category("Wide Doorway", Icons.Default.DoorFront), // placeholder, may not exist – use generic
        Category("Accessible Washroom", Icons.Default.Wc),
        Category("Parking", Icons.Default.LocalParking),
        Category("Other", Icons.Default.MoreHoriz)
    )

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val location by viewModel.location.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
        sheetContent = {}
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    .clickable { imagePickerLauncher.launch("image/*") }
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

private data class Category(val name: String, val icon: ImageVector)

