package com.teamdexters.limitless.ui.roommapping

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.roommapping.RoomAIAnalyzer
import com.teamdexters.limitless.roommapping.RoomAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import com.teamdexters.limitless.data.local.entity.MappedRoomEntity
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomAnalysisScreen(
    roomId: String,
    photoPaths: List<String>,
    mappedRoomDao: MappedRoomDao?, 
    onRoomSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val analyzer = remember { RoomAIAnalyzer(context) }
    
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var analysisResult by remember { mutableStateOf<RoomAnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }
    
    var confirmedRamp by remember { mutableStateOf(false) }
    var confirmedStairs by remember { mutableStateOf(false) }
    var confirmedWideDoor by remember { mutableStateOf(false) }
    var confirmedObstacles by remember { mutableStateOf(false) }
    var userNotes by remember { mutableStateOf("") }
    
    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech.shutdown()
        }
    }
    
    LaunchedEffect(roomId, photoPaths) {
        isAnalyzing = true
        val result = analyzer.analyze(roomId, photoPaths)
        analysisResult = result
        
        // Pre-fill checkboxes based on AI analysis
        confirmedRamp = result.hasRamp
        confirmedStairs = result.hasStairs
        confirmedWideDoor = result.hasWideDoor
        confirmedObstacles = result.obstacleCount > 0
        
        isAnalyzing = false
    }
    
    if (isAnalyzing) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F1EE)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFF791A9))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing Room with AI...", color = Color(0xFF1F1F1F))
            }
        }
        return
    }
    
    val result = analysisResult ?: return
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F1EE))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = roomId,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF1F1F1F),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // AI Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF1F1F1F)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.aiDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF1F1F1F)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        // Grid of 4 captured corner thumbnails
        Text("Corner Captures", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (photoPaths.isNotEmpty()) {
                    AsyncImage(model = photoPaths[0], contentDescription = "North Corner", modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                if (photoPaths.size > 1) {
                    AsyncImage(model = photoPaths[1], contentDescription = "East Corner", modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (photoPaths.size > 2) {
                    AsyncImage(model = photoPaths[2], contentDescription = "South Corner", modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                if (photoPaths.size > 3) {
                    AsyncImage(model = photoPaths[3], contentDescription = "West Corner", modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        // Pre-Filled Checkboxes Section
        Text("Confirm Features", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = confirmedRamp,
                    onCheckedChange = { confirmedRamp = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                )
                Text("Has Ramp", color = Color(0xFF1F1F1F))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = confirmedStairs,
                    onCheckedChange = { confirmedStairs = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                )
                Text("Has Stairs", color = Color(0xFF1F1F1F))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = confirmedWideDoor,
                    onCheckedChange = { confirmedWideDoor = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                )
                Text("Has Wide Doorway (>90cm)", color = Color(0xFF1F1F1F))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = confirmedObstacles,
                    onCheckedChange = { confirmedObstacles = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF791A9))
                )
                Text("Has Obstacles", color = Color(0xFF1F1F1F))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Optional Notes Text Field
        OutlinedTextField(
            value = userNotes,
            onValueChange = { userNotes = it },
            label = { Text("Optional Notes") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFF791A9),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color(0xFFF791A9),
                focusedLabelColor = Color(0xFFF791A9)
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Save Button
        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val entity = MappedRoomEntity(
                            id = roomId,
                            roomName = roomId,
                            hasRamp = confirmedRamp,
                            hasStairs = confirmedStairs,
                            hasWideDoor = confirmedWideDoor,
                            hasObstacles = confirmedObstacles,
                            obstacleCount = result.obstacleCount,
                            notes = userNotes,
                            timestamp = System.currentTimeMillis()
                        )
                        mappedRoomDao?.insertRoom(entity)
                        
                        Log.d("LIMITLESS_TRACE", "RoomAnalysisScreen: Saved MappedRoomEntity for $roomId")
                        
                        launch(Dispatchers.Main) {
                            tts?.speak("Room $roomId saved successfully.", TextToSpeech.QUEUE_FLUSH, null, null)
                            onRoomSaved(roomId)
                        }
                    } catch (e: Exception) {
                        Log.e("LIMITLESS_TRACE", "Error saving room", e)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Room to Database", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}
