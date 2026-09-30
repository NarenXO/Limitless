package com.teamdexters.limitless.ui.roommapping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingHomeScreen(
    mappedRoomDao: MappedRoomDao?,
    roomConnectionDao: RoomConnectionDao?,
    onMapNewRoom: () -> Unit,
    onOpenRoom: (String) -> Unit
) {
    val context = LocalContext.current
    
    val roomCount by (mappedRoomDao?.getRoomCount() ?: kotlinx.coroutines.flow.flowOf(0)).collectAsState(initial = 0)
    val connectionCount by (roomConnectionDao?.getConnectionCount() ?: kotlinx.coroutines.flow.flowOf(0)).collectAsState(initial = 0)
    val rooms by (mappedRoomDao?.getAllRooms() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE))
            .padding(16.dp)
    ) {
        // Header Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mapped Accessibility Graph",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1F1F1F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rooms Mapped: $roomCount | Connections: $connectionCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1F1F1F)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Big CTA Button
        Button(
            onClick = onMapNewRoom,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("+ Map New Room (Scan QR)", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (rooms.isEmpty()) {
            // Empty State
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Map Icon",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No rooms mapped yet. Tap + to start mapping your first room.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Mapped Rooms List
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rooms) { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRoom(room.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val photoFile = File(context.filesDir, "mapped_rooms/${room.id}/photo_0.jpg")
                            
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFFF7F1EE), RoundedCornerShape(8.dp))
                            ) {
                                if (photoFile.exists()) {
                                    AsyncImage(
                                        model = photoFile,
                                        contentDescription = "Room Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = room.roomName,
                                    color = Color(0xFF1F1F1F),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Badges
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (room.hasRamp) {
                                        BadgeChip(text = "Ramp Available", color = Color(0xFFBAD6DA))
                                    }
                                    if (room.hasWideDoor) {
                                        BadgeChip(text = "Wide Door (>90cm)", color = Color(0xFFDDDD7B))
                                    }
                                    if (room.hasStairs) {
                                        BadgeChip(text = "Stairs Warning", color = Color(0xFFF791A9))
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

@Composable
fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF1F1F1F),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
