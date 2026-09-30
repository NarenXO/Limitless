package com.teamdexters.limitless.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.TextPrimary

@Composable
fun ScoreBreakdownCard(
    result: ComprehensiveScoreResult,
    onNavigateRoute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(result.badgeColorHex), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${result.totalScore}",
                        color = Color(0xFF1F1F1F),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Overall Accessibility", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                    Text("Score based on AI Analysis", color = TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            ScoreBarRow(icon = Icons.Default.CameraAlt, title = "Object Detection (30%)", score = result.objectScore)
            ScoreBarRow(icon = Icons.Default.TextFields, title = "Signage OCR (20%)", score = result.ocrScore)
            ScoreBarRow(icon = Icons.Default.LightMode, title = "Frame Brightness (15%)", score = result.brightnessScore)
            ScoreBarRow(icon = Icons.Default.Checklist, title = "Manual Checklist (35%)", score = result.checklistScore)

            Spacer(modifier = Modifier.height(24.dp))

            // Path Clarity Status
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(result.pathClarity.badgeColorHex)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconVector = when (result.pathClarity.iconName) {
                        "CheckCircle" -> Icons.Default.CheckCircle
                        "Warning" -> Icons.Default.Warning
                        else -> Icons.Default.Block
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = Color(0xFF1F1F1F),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = result.pathClarity.title,
                            color = Color(0xFF1F1F1F),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = result.pathClarity.subtext,
                            color = Color(0xFF1F1F1F).copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HighlightBox, RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = result.aiReasoningExplanation }
            ) {
                Column {
                    Text(
                        text = "AI Accessibility Analysis",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = result.aiReasoningExplanation,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onNavigateRoute,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🧭 Optimise & Navigate Accessible Route Here",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ScoreBarRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = score / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFF791A9),
                trackColor = Color(0xFFFFFFFF).copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("$score", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
