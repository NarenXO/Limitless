package com.teamdexters.limitless.ui.deaf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamdexters.limitless.core.audio.SoundCategory
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.TextPrimary

@Composable
fun DeafHomeScreen(
    onBack: () -> Unit = {},
    viewModel: DeafViewModel = hiltViewModel()
) {
    val liveCaptions by viewModel.liveCaptions.collectAsState()
    val detectedAlerts by viewModel.detectedAlerts.collectAsState()
    val currentCategory by viewModel.currentSoundCategory.collectAsState()
    val decibelLevel by viewModel.decibelLevel.collectAsState()
    val sosCountdown by viewModel.sosCountdown.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopListening()
        }
    }

    BackHandler(enabled = true) {
        android.util.Log.e("NAV_DEBUG", "System BackHandler triggered in DeafHomeScreen")
        onBack()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(100f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            android.util.Log.e("NAV_DEBUG", "TopBar Back Button Clicked")
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back to persona selection",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Deaf & Hard-of-Hearing",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        },
        containerColor = Color(0xFFF7F1EE)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Environmental Indicator Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    SoundCategory.EMERGENCY to "Emergency",
                    SoundCategory.HOME to "Home",
                    SoundCategory.HUMAN to "Human"
                )
                items(categories) { (cat, label) ->
                    val isLit = currentCategory == cat
                    val bgColor = if (isLit) Color(0xFFBAD6DA) else Color.LightGray
                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (isLit) Color.Black else Color.DarkGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top: "Sound Status" card & Dynamic Waveform
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFBAD6DA)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Dynamic Visualizer (replaces static mic icon)
                    Canvas(modifier = Modifier.size(48.dp, 32.dp)) {
                        val barCount = 5
                        val spacing = 4.dp.toPx()
                        val barWidth = (size.width - spacing * (barCount - 1)) / barCount
                        
                        // Scale height based on decibel level (which is RMS in dB, usually varies wildly, let's normalize roughly)
                        // This is a simple visual representation
                        for (i in 0 until barCount) {
                            val baseHeight = size.height * 0.2f
                            val dynamicHeight = (baseHeight + (decibelLevel * (i + 1) * 0.1f)).coerceIn(baseHeight, size.height)
                            
                            val startX = i * (barWidth + spacing)
                            val startY = (size.height - dynamicHeight) / 2
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset(startX, startY),
                                size = Size(barWidth, dynamicHeight),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Monitoring Environment...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Center: Live Caption Area
            Text(
                text = "Live Captions",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFE0F2F4)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = liveCaptions.ifEmpty { "Listening for speech..." },
                        color = Color(0xFF1F1F1F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom: Detected Alerts
            Text(
                text = "Detected Alerts",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(detectedAlerts) { alert ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = alert,
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF1F1F1F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Emergency Section: SOS Button
            androidx.compose.material3.Button(
                onClick = { viewModel.triggerSOS() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDBDF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "EMERGENCY SOS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1F1F1F)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Full-screen overlay for SOS
        sosCountdown?.let { count ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFBAD6DA).copy(alpha = 0.9f))
                    .zIndex(200f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SOS in...",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "$count",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    androidx.compose.material3.Button(
                        onClick = { viewModel.cancelSOS() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 24.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
