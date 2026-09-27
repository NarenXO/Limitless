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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun LocationDetailScreen(
    locationId: Long = 1L,
    onBackClick: () -> Unit = {},
    locationTitle: String = "Location",
    isVerified: Boolean = false,
    overallScore: Int = 0,
    objectScore: Float = 0f,
    ocrScore: Float = 0f,
    brightnessScore: Float = 0f,
    checklistScore: Float = 0f,
    photoUri: String? = null,
    timestamp: Long = 0L,
    coordinates: String = ""
) {
    Scaffold(
        containerColor = Color(0xFFF7F1EE)
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
                    .background(Color(0xFFE0F2F4)),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri.isNullOrEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "No photo available",
                        tint = Color.Gray,
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
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VerifiedBadge(isVerified = isVerified)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overall Score Display
                val scoreColor = when {
                    overallScore <= 40 -> Color(0xFFF791A9)
                    overallScore <= 70 -> Color(0xFFDDDD7B)
                    else -> Color(0xFFBAD6DA)
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(scoreColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$overallScore",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1F1F)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accessibility Score", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Breakdown Section
                Text("Analysis Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                Spacer(modifier = Modifier.height(16.dp))

                BreakdownRow("Object Detection", objectScore, 100f)
                BreakdownRow("Signage OCR", ocrScore, 100f)
                BreakdownRow("Brightness", brightnessScore, 100f)
                BreakdownRow("Checklist", checklistScore, 100f)

                Spacer(modifier = Modifier.height(24.dp))

                // Photo Evidence Details
                Text("Scan Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Coordinates: $coordinates", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1F1F1F))
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                Text("Time: $dateStr", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1F1F1F))
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1F1F1F))
            Text("${value.toInt()}/${max.toInt()}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1F1F1F))
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = value / max,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFFF791A9),
            trackColor = Color(0xFFFFDBDF)
        )
    }
}
