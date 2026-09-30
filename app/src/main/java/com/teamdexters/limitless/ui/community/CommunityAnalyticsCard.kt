package com.teamdexters.limitless.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.TextPrimary

@Composable
fun CommunityAnalyticsCard(
    analyticsData: CommunityAnalyticsData,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)), // Surface Tint
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Community Impact & Analytics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.semantics { contentDescription = "Toggle Analytics Summary" }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Hide Analytics Summary" else "Show Analytics Summary",
                        tint = TextPrimary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Metric Counters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCounter("Contributed", "${analyticsData.totalReports}")
                        MetricCounter("Avg Score", "${analyticsData.averageScore}/100")
                        MetricCounter("Team-Verified", "${analyticsData.verifiedCount}")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Obstacle Breakdown
                    Text(
                        text = "Obstacle Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ObstacleBar("Ramps & Entrances", analyticsData.rampPercentage, Color(0xFFF791A9))
                    ObstacleBar("Elevators / Lifts", analyticsData.elevatorPercentage, Color(0xFFBAD6DA))
                    ObstacleBar("Accessible Restrooms", analyticsData.restroomPercentage, Color(0xFFDDDD7B))
                    ObstacleBar("General Obstacles", analyticsData.generalPercentage, Color(0xFFFFDBDF)) // HighlightBox

                    Spacer(modifier = Modifier.height(24.dp))

                    // Top Locations
                    if (analyticsData.topLocations.isNotEmpty()) {
                        Text(
                            text = "Top Nearby Verified Locations",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        analyticsData.topLocations.forEach { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = location.locationName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Score: ${location.trustScore}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFBAD6DA).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = "Team-Verified", tint = Color(0xFF1F1F1F), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Team-Verified", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCounter(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$label is $value" }
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF791A9) // Primary Accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}

@Composable
fun ObstacleBar(label: String, percentage: Float, color: Color) {
    val displayPercent = (percentage * 100).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label is $displayPercent percent" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(0.4f)
        )
        Box(
            modifier = Modifier
                .weight(0.5f)
                .height(8.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = percentage)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Text(
            text = "$displayPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier
                .weight(0.1f)
                .padding(start = 4.dp)
        )
    }
}
