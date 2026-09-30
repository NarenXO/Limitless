package com.teamdexters.limitless.ui.hazel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.hazel.HazelMemoryStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazelHistoryBottomSheet(
    onDismiss: () -> Unit,
    memoryStore: HazelMemoryStore
) {
    val coroutineScope = rememberCoroutineScope()
    val history by memoryStore.observeRecentTurns(20).collectAsState(initial = emptyList())

    val limitlessBackground = Color(0xFFF7F1EE)
    val limitlessPrimary = Color(0xFFF791A9)
    val textPrimary = Color(0xFF1F1F1F)
    val surfaceTint = Color(0xFFE0F2F4)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = limitlessBackground,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = textPrimary.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hazel Conversation History",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { 
                    coroutineScope.launch { memoryStore.clearHistory() } 
                }) {
                    Text("Clear", color = limitlessPrimary, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (history.isEmpty()) {
                Text(
                    text = "No recent conversations.",
                    color = textPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(history) { turn ->
                        val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(turn.timestamp))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = timeString,
                                fontSize = 12.sp,
                                color = textPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            
                            // User message
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp),
                                    color = surfaceTint,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = turn.userMessage,
                                        color = limitlessPrimary,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            
                            // Hazel response
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
                                    color = Color.White.copy(alpha = 0.5f), // Fallback slight contrast for Hazel
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = turn.hazelResponse,
                                            color = textPrimary,
                                            fontSize = 14.sp
                                        )
                                        if (turn.wasActionExecuted) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = limitlessPrimary.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = "Action Executed",
                                                    color = limitlessPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
