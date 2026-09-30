package com.teamdexters.limitless.ui.roommapping

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import com.teamdexters.limitless.data.local.dao.RoomConnectionDao
import com.teamdexters.limitless.data.local.entity.RoomConnectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConnectionScreen(
    currentRoomId: String,
    mappedRoomDao: MappedRoomDao?,
    roomConnectionDao: RoomConnectionDao?,
    onMapAnotherRoom: () -> Unit,
    onDoneMapping: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    val allRooms by (mappedRoomDao?.getAllRooms() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val connections by (roomConnectionDao?.getConnectionsForRoom(currentRoomId) ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    
    val availableRooms = allRooms.filter { it.id != currentRoomId }
    
    var expanded by remember { mutableStateOf(false) }
    var selectedToRoomId by remember { mutableStateOf("") }
    
    val connectionTypes = listOf("Doorway", "Hallway", "Ramp", "Stairs", "Elevator")
    var selectedType by remember { mutableStateOf(connectionTypes[0]) }
    
    var hasRamp by remember { mutableStateOf(false) }
    var hasStairs by remember { mutableStateOf(false) }
    var distanceStr by remember { mutableStateOf("5") }
    var doorWidthStr by remember { mutableStateOf("80") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE))
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Connect Room: $currentRoomId",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF1F1F1F),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Dropdown Menu / Selection Card
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (selectedToRoomId.isEmpty()) "Select room to connect..." else selectedToRoomId,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF791A9),
                    unfocusedBorderColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableRooms.forEach { room ->
                    DropdownMenuItem(
                        text = { Text(room.id, color = Color(0xFF1F1F1F)) },
                        onClick = {
                            selectedToRoomId = room.id
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Connection Parameters Form
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connection Type", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Connection Type Selector Chips
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(connectionTypes) { type ->
                        FilterChip(
                            selected = (selectedType == type),
                            onClick = { selectedType = type },
                            label = { Text(type, color = Color(0xFF1F1F1F)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF791A9),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Checkboxes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasRamp,
                        onCheckedChange = { hasRamp = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                    )
                    Text("Has Ramp", color = Color(0xFF1F1F1F))
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(
                        checked = hasStairs,
                        onCheckedChange = { hasStairs = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                    )
                    Text("Has Stairs", color = Color(0xFF1F1F1F))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Number Inputs
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = distanceStr,
                        onValueChange = { distanceStr = it },
                        label = { Text("Distance (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF791A9),
                            focusedLabelColor = Color(0xFFF791A9)
                        )
                    )
                    OutlinedTextField(
                        value = doorWidthStr,
                        onValueChange = { doorWidthStr = it },
                        label = { Text("Door width (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF791A9),
                            focusedLabelColor = Color(0xFFF791A9)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Save Button
                Button(
                    onClick = {
                        if (selectedToRoomId.isNotEmpty()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                val entity = RoomConnectionEntity(
                                    id = UUID.randomUUID().toString(),
                                    fromRoomId = currentRoomId,
                                    toRoomId = selectedToRoomId,
                                    connectionType = selectedType,
                                    hasRamp = hasRamp,
                                    hasStairs = hasStairs,
                                    distanceMeters = distanceStr.toFloatOrNull() ?: 5f,
                                    doorWidthCm = doorWidthStr.toFloatOrNull() ?: 80f,
                                    timestamp = System.currentTimeMillis()
                                )
                                roomConnectionDao?.insertConnection(entity)
                                Log.d("LIMITLESS_TRACE", "RoomConnectionScreen: Saved connection edge between $currentRoomId and $selectedToRoomId")
                            }
                        }
                    },
                    enabled = selectedToRoomId.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Connection Edge", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Saved Connections", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Connections List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(connections) { connection ->
                val otherRoom = if (connection.fromRoomId == currentRoomId) connection.toRoomId else connection.fromRoomId
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("To: $otherRoom", color = Color(0xFF1F1F1F), fontWeight = FontWeight.Bold)
                            Text("Type: ${connection.connectionType}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                roomConnectionDao?.deleteConnection(connection)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF791A9))
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Navigation Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onMapAnotherRoom,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF791A9)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF791A9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Map Another Room", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onDoneMapping,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done Mapping", color = Color(0xFFF7F1EE), fontWeight = FontWeight.Bold)
            }
        }
    }
}
