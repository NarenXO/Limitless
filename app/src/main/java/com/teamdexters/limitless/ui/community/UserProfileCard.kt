package com.teamdexters.limitless.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.TextPrimary
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import com.teamdexters.limitless.ui.community.SyncStatus

@Composable
fun UserProfileCard(
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)), // Surface Tint
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Sync Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFBAD6DA).copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (syncStatus) {
                        SyncStatus.SYNCED -> {
                            Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synced with Cloud", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                        }
                        SyncStatus.SYNCING -> {
                            com.teamdexters.limitless.ui.components.SafeCircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFF791A9),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                        SyncStatus.OFFLINE -> {
                            Icon(Icons.Default.CloudOff, contentDescription = "Offline", tint = Color(0xFF1F1F1F), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saved Locally", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1F1F1F))
                        }
                        SyncStatus.ERROR -> {
                            Text("🟢", modifier = Modifier.padding(end = 4.dp))
                            Text("Local First Mode", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1F1F1F))
                        }
                        SyncStatus.IDLE -> {
                            Icon(Icons.Default.CloudDone, contentDescription = "Idle", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Waiting for sync...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        }
                    }
                }
                
                IconButton(onClick = onSyncClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Now", tint = TextPrimary)
                }
            }
            
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFF791A9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sanjeevi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Top Accessibility Inspector",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "15 Reports Contributed | Trust Score: 95/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Badge
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFBAD6DA), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Trusted Evaluator",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Trusted Evaluator",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
