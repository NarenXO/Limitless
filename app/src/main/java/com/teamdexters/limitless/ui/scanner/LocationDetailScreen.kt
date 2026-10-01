package com.teamdexters.limitless.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.teamdexters.limitless.ui.theme.PureBlack
import com.teamdexters.limitless.ui.theme.PureWhite
import com.teamdexters.limitless.ui.theme.SoftWhite
import com.teamdexters.limitless.ui.theme.NeutralGray
import com.teamdexters.limitless.ui.theme.LightGray
import com.teamdexters.limitless.ui.theme.SubtleDivider
import coil.compose.AsyncImage

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LocationDetailScreen(
    locationId: Long = 1L,
    onBackClick: () -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel()
) {
    LaunchedEffect(locationId) {
        viewModel.fetchScoreById(locationId)
    }
    
    val scoreEntity by viewModel.selectedScore.collectAsState()

    if (scoreEntity == null) {
        Box(modifier = Modifier.fillMaxSize().background(PureWhite), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PureBlack)
        }
        return
    }

    val locationTitle = scoreEntity?.buildingName ?: "Location"
    val isVerified = scoreEntity?.isTeamVerified ?: false
    val overallScore = scoreEntity?.overallScore ?: 0
    val objectScore = if (scoreEntity?.rampDetected == true || scoreEntity?.stairsDetected == true) 30f else 0f
    val ocrScore = 20f // Mocked based on structure
    val brightnessScore = (scoreEntity?.lightingScore ?: 0f) * 100f
    val checklistScore = (scoreEntity?.doorWidthScore ?: 0f) * 100f
    val photoUri = scoreEntity?.photoUri
    val timestamp = scoreEntity?.timestamp ?: 0L
    val coordinates = "Not recorded"
    Scaffold(
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Top: Photo Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(SoftWhite),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri.isNullOrEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "No photo available",
                        tint = NeutralGray,
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Location Photo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = locationTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VerifiedBadge(isVerified = isVerified)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overall Score Display
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SoftWhite,
                        contentColor = PureBlack
                    ),
                    border = BorderStroke(1.dp, SubtleDivider),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ACCESSIBILITY SCORE",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = NeutralGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$overallScore / 100",
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                                fontWeight = FontWeight.Black,
                                color = PureBlack
                            )
                            // Score Pill Badge
                            Surface(
                                color = PureBlack,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (overallScore >= 70) "HIGHLY ACCESSIBLE" else "OBSTACLES DETECTED",
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Progress Bar: PureBlack fill on LightGray track
                        LinearProgressIndicator(
                            progress = overallScore / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = PureBlack,
                            trackColor = LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Breakdown Section
                Text("Analysis Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PureBlack)
                Spacer(modifier = Modifier.height(16.dp))

                BreakdownRow("Object Detection", objectScore, 100f)
                BreakdownRow("Signage OCR", ocrScore, 100f)
                BreakdownRow("Brightness", brightnessScore, 100f)
                BreakdownRow("Checklist", checklistScore, 100f)

                Spacer(modifier = Modifier.height(24.dp))

                // Photo Evidence Details
                Text("Scan Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PureBlack)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Coordinates: $coordinates", style = MaterialTheme.typography.bodyMedium, color = PureBlack)
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                Text("Time: $dateStr", style = MaterialTheme.typography.bodyMedium, color = PureBlack)
            }
        }
    }
}

@Composable
fun BreakdownRow(label: String, value: Float, max: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = PureBlack)
            Text("${value.toInt()}/${max.toInt()}", style = MaterialTheme.typography.bodyMedium, color = PureBlack)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = value / max,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = PureBlack,
            trackColor = LightGray
        )
    }
}
