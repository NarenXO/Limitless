package com.teamdexters.limitless.ui.community

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*

import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.ui.theme.HighlightBox
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch

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
    val submitResult by viewModel.submitResult.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewMode by viewModel.viewMode.collectAsState()
    val reportFilterCategory by viewModel.reportFilterCategory.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val reports by viewModel.filteredReports.collectAsState(initial = emptyList())
    val analyticsData by viewModel.analyticsData.collectAsState()

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(submitResult) {
        if (submitResult == true) {
            scaffoldState.snackbarHostState.showSnackbar("Report submitted successfully!")
            scaffoldState.bottomSheetState.hide()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp, // Hide completely by default, show on CTA tap
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
                    Text("Submit Accessibility Report", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBAD6DA), contentColor = TextPrimary),
                            modifier = Modifier.height(52.dp)
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
        var isBannerExpanded by remember { mutableStateOf(false) }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1EE))
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Header
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Text("Community Accessibility", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // View Mode Switcher
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.setViewMode(ViewMode.LIST) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewMode == ViewMode.LIST) Color(0xFFF791A9) else Color(0xFFE0F2F4),
                                contentColor = if (viewMode == ViewMode.LIST) Color.White else TextPrimary
                            )
                        ) {
                            Text("Feed List", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.setViewMode(ViewMode.MAP) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewMode == ViewMode.MAP) Color(0xFFF791A9) else Color(0xFFE0F2F4),
                                contentColor = if (viewMode == ViewMode.MAP) Color.White else TextPrimary
                            )
                        ) {
                            Text("Map View", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }
            
            // Expandable Profile/Analytics Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { isBannerExpanded = !isBannerExpanded },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        if (!isBannerExpanded) {
                            Text("Sanjeevi • 15 Contributed • 95/100 Trust Score [ Tap for Stats ▾ ]", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        } else {
                            Text("Sanjeevi • 15 Contributed • 95/100 Trust Score [ Tap to Collapse ▴ ]", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Total Reports: ${analyticsData?.totalReports ?: 0}", color = TextPrimary)
                            Text("Verified Accessible: ${analyticsData?.verifiedCount ?: 0}", color = TextPrimary)
                            Text("Average Rating: ${analyticsData?.averageScore ?: 0}/5", color = TextPrimary)
                            Text("Ramp Access: ${((analyticsData?.rampPercentage ?: 0f) * 100).toInt()}% • Elevators: ${((analyticsData?.elevatorPercentage ?: 0f) * 100).toInt()}%", color = TextPrimary)
                        }
                    }
                }
            }

            // Submit Report CTA Button (Top)
            item {
                Button(
                    onClick = { coroutineScope.launch { scaffoldState.bottomSheetState.expand() } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9), contentColor = Color.White)
                ) {
                    Text("+ Submit Accessibility Report (Zero Typing)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Filters & Sort
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Filter Chips Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ReportFilterCategory.values()) { filter ->
                            val isSelected = reportFilterCategory == filter
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) HighlightBox else Color(0xFFE0F2F4))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFF791A9) else Color.Transparent,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clickable { viewModel.setFilterCategory(filter) }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(filter.label, color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Sort Dropdown Pill
                    var expandedSort by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Button(
                            onClick = { expandedSort = true },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F2F4), contentColor = TextPrimary)
                        ) {
                            Text("Sort: ${sortOrder.label} ▾", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        DropdownMenu(expanded = expandedSort, onDismissRequest = { expandedSort = false }) {
                            SortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        expandedSort = false
                                    }
                                )
                            }
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
                
                // Submit Report CTA Button (Bottom)
                item {
                    Button(
                        onClick = { coroutineScope.launch { scaffoldState.bottomSheetState.expand() } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9), contentColor = Color.White)
                    ) {
                        Text("+ Submit Accessibility Report (Zero Typing)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
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
fun ReportCard(report: com.teamdexters.limitless.data.local.entity.UserReportEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Report for ${report.locationName}" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFBAD6DA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.locationName, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
                    Text(report.category, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
                
                val rating = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                val isVerified = rating >= 4 || report.trustScore >= 70
                if (isVerified) {
                    Box(
                        modifier = Modifier.background(Color(0xFFBAD6DA), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Verified Accessible", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rating = report.description.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
                Row {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (star <= rating) Color(0xFFDDDD7B) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("$rating / 5", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(report.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

data class Category(val name: String, val icon: ImageVector)
