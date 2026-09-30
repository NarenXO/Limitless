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
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*

import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.ui.theme.HighlightBox
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: CommunityReportViewModel = hiltViewModel()
) {
    val categories = listOf(
        Category("Ramp", Icons.Default.TrendingUp),
        Category("Lift/Elevator", Icons.Default.ArrowUpward),
        Category("Wide Doorway", Icons.Default.MeetingRoom),
        Category("Accessible Washroom", Icons.Default.Wc),
        Category("Parking", Icons.Default.LocalParking),
        Category("Other", Icons.Default.MoreHoriz)
    )

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current

    val viewMode by viewModel.viewMode.collectAsState()
    val reportFilterCategory by viewModel.reportFilterCategory.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val reports by viewModel.filteredReports.collectAsState(initial = emptyList())

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.onImageSelected(uri) }

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(submitResult) {
        if (submitResult == true) {
            scaffoldState.snackbarHostState.showSnackbar("Report submitted successfully!")
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        sheetContainerColor = Color(0xFFF7F1EE),
        sheetContent = {
            // REPORT FORM
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Swipe up to Submit a Report", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                
                // Form content
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(text = "Select Category", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { category ->
                                Card(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clickable { viewModel.onCategorySelected(category) }
                                        .semantics { contentDescription = "Select category ${category.name}" },
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (selectedCategory == category) BorderStroke(2.dp, Color(0xFFF791A9)) else null,
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(imageVector = category.icon, contentDescription = null, tint = TextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = category.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(text = "Rate Accessibility", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Rate $star stars",
                                    tint = if (star <= rating) Color(0xFFDDDD7B) else Color.Gray,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { viewModel.onRatingSelected(star) }
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (!locationPermissionState.status.isGranted) {
                                    locationPermissionState.launchPermissionRequest()
                                } else {
                                    viewModel.fetchCurrentLocation(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBAD6DA), contentColor = TextPrimary)
                        ) {
                            Text("Use Current Location")
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.submitReport() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9), contentColor = Color.White)
                        ) {
                            Text(text = "Submit Report", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        // MAIN COMMUNITY FEED
        val syncStatus by viewModel.syncStatus.collectAsState()
        val analyticsData by viewModel.analyticsData.collectAsState()

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1EE))
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            item {
                // Profile Card
                UserProfileCard(
                    syncStatus = syncStatus,
                    onSyncClick = { viewModel.triggerCloudSync() },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)
                )
            }

            item {
                // Analytics Dashboard
                CommunityAnalyticsCard(
                    analyticsData = analyticsData,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            item {
                // View Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewModeTab(
                        title = "📋 List Feed",
                        isSelected = viewMode == ViewMode.LIST,
                        onClick = { viewModel.setViewMode(ViewMode.LIST) },
                        modifier = Modifier.weight(1f)
                    )
                    ViewModeTab(
                        title = "🗺️ Map View",
                        isSelected = viewMode == ViewMode.MAP,
                        onClick = { viewModel.setViewMode(ViewMode.MAP) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            item {
                // Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReportFilterCategory.values()) { filter ->
                        FilterChip(
                            selected = reportFilterCategory == filter,
                            onClick = { viewModel.setFilterCategory(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HighlightBox,
                                selectedLabelColor = TextPrimary
                            ),
                            modifier = Modifier.semantics { contentDescription = "Filter by ${filter.label}" }
                        )
                    }
                }
            }
            
            item {
                // Sort Selector
                var expandedSort by remember { mutableStateOf(false) }
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = { expandedSort = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.semantics { contentDescription = "Sort by ${sortOrder.label}" }
                    ) {
                        Text("Sort: ${sortOrder.label}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expandedSort, onDismissRequest = { expandedSort = false }) {
                        SortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    expandedSort = false
                                },
                                modifier = Modifier.semantics { contentDescription = "Select sort order ${order.label}" }
                            )
                        }
                    }
                }
            }
            
            // Content based on ViewMode
            if (viewMode == ViewMode.LIST) {
                items(reports) { report ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ReportCard(report)
                    }
                }
            } else {
                item {
                    CommunityMapView(
                        reports = reports,
                        onMapError = { viewModel.setViewMode(ViewMode.LIST) },
                        modifier = Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun ViewModeTab(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable { onClick() }.semantics { contentDescription = title },
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFFF791A9)) else null,
        colors = CardDefaults.cardColors(containerColor = if (isSelected) HighlightBox else Color(0xFFE0F2F4))
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun ReportCard(report: com.teamdexters.limitless.data.local.entity.UserReportEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Report for ${report.locationName}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFBAD6DA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("U", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.locationName, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
                    Text(report.category, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Row {
                    val rating = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                    Icon(Icons.Default.Star, contentDescription = "Rating $rating", tint = Color(0xFFDDDD7B), modifier = Modifier.size(16.dp))
                    Text("$rating/5", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(report.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

data class Category(val name: String, val icon: ImageVector)
