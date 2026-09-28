// NOTE: This module uses QR codes as a placeholder for the long-term 
// NFC waypoint design. NFC tap-based positioning is the intended 
// production approach (no visual aiming required, better for blind 
// users). QR is used here because NFC tags are not available for 
// this hackathon demo. See project docx Section 5.9 for full rationale.

package com.teamdexters.limitless.routing.indoor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Main Indoor Navigation Screen for KCG Main Building.
 *
 * Integrates QR code scanning (camera anchor) with PDR dead-reckoning sensor tracking
 * and 2D canvas visualization.
 */
@Composable
fun IndoorNavScreen() {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activityPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    // -- Sensor PDR Engine Lifecycle -------------------------------------------
    val pdrEngine = remember { PdrEngine(context) }
    DisposableEffect(Unit) {
        pdrEngine.start()
        onDispose { pdrEngine.stop() }
    }

    // -- Shared TextToSpeech Engine -------------------------------------------
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                ttsReady = true
            }
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    // -- Selected Target Waypoint State ---------------------------------------
    var targetWaypoint by remember { mutableStateOf<IndoorWaypoint?>(kcgIndoorWaypoints.find { it.id == "kcg-restroom" }) }
    var hasAnnouncedArrival by remember { mutableStateOf(false) }

    // Distance & Direction Calculation
    val target = targetWaypoint
    val dx = (target?.xMeters ?: 0f) - pdrEngine.xMeters
    val dy = (target?.yMeters ?: 0f) - pdrEngine.yMeters
    val distanceToTargetMeters = hypot(dx.toDouble(), dy.toDouble()).toFloat()

    val relativeAngleDeg = calculateRelativeAngle(dx, dy, pdrEngine.headingDegrees)
    val clockDirectionText = getRelativeDirectionPrompt(relativeAngleDeg)

    // Arrival Notification Check (within 3 meters)
    LaunchedEffect(distanceToTargetMeters, target) {
        if (target != null && distanceToTargetMeters <= 3.0f && !hasAnnouncedArrival) {
            hasAnnouncedArrival = true
            if (ttsReady) {
                tts.value?.speak(
                    "You have arrived at ${target.name}.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "arrival_${target.id}"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .semantics { contentDescription = "Indoor Navigation Screen for KCG Main Building." }
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Header -----------------------------------------------------------
        Text(
            text = "Indoor Navigation — KCG Main Building",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PersonaMobility,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Indoor Navigation header" }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Target Waypoint Selector ("Navigate To") -------------------------
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Navigate To Target Waypoint:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(kcgIndoorWaypoints) { wp ->
                    val isSelected = wp.id == targetWaypoint?.id
                    val bgColor = if (isSelected) PersonaMobility else SurfaceTint
                    val borderColor = if (isSelected) TextPrimary else PersonaMobility.copy(alpha = 0.40f)

                    Box(
                        modifier = Modifier
                            .background(bgColor, RoundedCornerShape(12.dp))
                            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                targetWaypoint = wp
                                hasAnnouncedArrival = false
                                if (ttsReady) {
                                    val prompt = "${wp.name} is ${distanceToTargetMeters.toInt()} meters away, $clockDirectionText"
                                    tts.value?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, wp.id)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = wp.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. QR Camera Scanner Section (Top Half) -----------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            QrScannerSection(
                pdrEngine = pdrEngine,
                tts = tts.value,
                onWaypointScanned = { scannedWp ->
                    if (targetWaypoint?.id == scannedWp.id) {
                        hasAnnouncedArrival = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. 2D Canvas Indoor Map Section (Bottom Half) -----------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            IndoorMapView(
                userX = pdrEngine.xMeters,
                userY = pdrEngine.yMeters,
                headingDegrees = pdrEngine.headingDegrees,
                waypoints = kcgIndoorWaypoints,
                targetWaypoint = targetWaypoint,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Bottom Navigation Telemetry Status Bar ---------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PersonaMobility, RoundedCornerShape(12.dp))
                .border(1.dp, TextPrimary.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = target?.let { "${it.name}: ${distanceToTargetMeters.toInt()}m" } ?: "No target selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { pdrEngine.advanceStep() } // Tap status bar to simulate step
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Simulate step",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = clockDirectionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

private fun calculateRelativeAngle(dx: Float, dy: Float, headingDeg: Float): Float {
    val bearingRad = atan2(dx.toDouble(), dy.toDouble())
    var bearingDeg = Math.toDegrees(bearingRad).toFloat()
    if (bearingDeg < 0f) bearingDeg += 360f

    var relAngle = bearingDeg - headingDeg
    if (relAngle > 180f) relAngle -= 360f
    if (relAngle < -180f) relAngle += 360f
    return relAngle
}

private fun getRelativeDirectionPrompt(relAngleDeg: Float): String {
    return when {
        relAngleDeg in -22.5f..22.5f -> "straight ahead"
        relAngleDeg in 22.5f..67.5f -> "slightly to your right"
        relAngleDeg in 67.5f..112.5f -> "to your right"
        relAngleDeg in 112.5f..157.5f -> "behind to your right"
        relAngleDeg in -67.5f..-22.5f -> "slightly to your left"
        relAngleDeg in -112.5f..-67.5f -> "to your left"
        relAngleDeg in -157.5f..-112.5f -> "behind to your left"
        else -> "behind you"
    }
}
