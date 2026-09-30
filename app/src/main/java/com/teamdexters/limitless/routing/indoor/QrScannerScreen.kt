package com.teamdexters.limitless.routing.indoor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.teamdexters.limitless.ui.theme.*
import java.util.concurrent.Executors

/**
 * CameraX QR Scanner component for indoor positioning.
 *
 * Scans physical QR code tags, matches payload against [kcgIndoorWaypoints],
 * and invokes [PdrEngine.resetPosition] for drift correction.
 */
@Composable
fun QrScannerSection(
    pdrEngine: PdrEngine,
    userX: Float,
    userY: Float,
    userHeading: Float,
    currentFloor: Int,
    lastScannedWaypoint: IndoorWaypoint?,
    tts: TextToSpeech?,
    onWaypointScanned: (IndoorWaypoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var scannedMessage by remember { mutableStateOf<String?>(null) }
    var currentScannedContent by remember { mutableStateOf("") }
    var isUrlContent by remember { mutableStateOf(false) }
    var lastScanTimestamp by remember { mutableStateOf(0L) }
    var lastScannedPayload by remember { mutableStateOf("") }

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = java.util.Locale.US
                isTtsReady = true
            }
        }
        ttsInstance = localTts
        onDispose {
            localTts.stop()
            localTts.shutdown()
        }
    }

    fun speakText(text: String) {
        if (isTtsReady && ttsInstance != null) {
            ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qr_speech_${System.currentTimeMillis()}")
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LightGray)
            .border(1.dp, SubtleDivider, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "QR code scanner for indoor navigation" }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top: Camera Preview (65% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                if (!hasCameraPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightGray)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = PureBlack,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera permission is required to scan indoor QR waypoints",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureBlack,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .background(PureBlack, RoundedCornerShape(12.dp))
                                .clickable { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Grant Camera Permission",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                @SuppressLint("UnsafeOptInUsageError")
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawText = barcode.rawValue?.trim() ?: continue
                                                    if (rawText.isBlank()) continue

                                                    // Debounce duplicate scans within 2 seconds
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastScanTimestamp < 2000L && rawText == lastScannedPayload) continue
                                                    lastScanTimestamp = now
                                                    lastScannedPayload = rawText

                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                    val upperText = rawText.uppercase()

                                                    // Check for waypoint keywords
                                                    val matchedWaypoint = when {
                                                        upperText.contains("ENTRANCE") -> kcgIndoorWaypoints.find { it.id.contains("entrance", ignoreCase = true) }
                                                        upperText.contains("LIFT") || upperText.contains("ELEVATOR") -> kcgIndoorWaypoints.find { it.id.contains("lift", ignoreCase = true) }
                                                        upperText.contains("RESTROOM") || upperText.contains("WASHROOM") || upperText.contains("TOILET") -> kcgIndoorWaypoints.find { it.id.contains("restroom", ignoreCase = true) }
                                                        upperText.contains("RAMP") -> kcgIndoorWaypoints.find { it.id.contains("ramp", ignoreCase = true) }
                                                        upperText.contains("WARD") || upperText.contains("ADMIN") -> kcgIndoorWaypoints.find { it.id.contains("ward", ignoreCase = true) }
                                                        upperText.contains("RECEPTION") -> kcgIndoorWaypoints.find { it.id.contains("reception", ignoreCase = true) }
                                                        else -> kcgIndoorWaypoints.find { it.qrPayload.equals(upperText, ignoreCase = true) || it.id.equals(upperText, ignoreCase = true) }
                                                    }

                                                    if (matchedWaypoint != null) {
                                                        // 1. WAYPOINT DETECTED
                                                        pdrEngine.resetPosition(matchedWaypoint)
                                                        currentScannedContent = "Waypoint: ${matchedWaypoint.name}\n${matchedWaypoint.description}"
                                                        isUrlContent = false
                                                        speakText("Location updated to ${matchedWaypoint.name}. ${matchedWaypoint.description}")
                                                        onWaypointScanned(matchedWaypoint)
                                                    } else if (rawText.startsWith("http://", ignoreCase = true) || rawText.startsWith("https://", ignoreCase = true)) {
                                                        // 2. WEBSITE URL DETECTED
                                                        currentScannedContent = rawText
                                                        isUrlContent = true
                                                        speakText("Scanned website link: $rawText")
                                                    } else {
                                                        // 3. GENERAL TEXT DETECTED
                                                        currentScannedContent = rawText
                                                        isUrlContent = false
                                                        speakText("Scanned QR content: $rawText")
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Confirmation Banner Overlay
                val currentMsg = scannedMessage
                if (currentMsg != null) {
                    Column(modifier = Modifier.align(Alignment.TopCenter)) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PersonaMobility)
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentMsg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            // Bottom: Position & Heading Telemetry (35% height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(12.dp)
            ) {
                if (currentScannedContent.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftWhite),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleDivider)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Scanned QR Content",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentScannedContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftBlack
                            )
                            if (isUrlContent) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentScannedContent))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack)
                                ) {
                                    Text("Open in Browser", color = PureWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                val cardinalHeading = getCardinalDirection(userHeading)
                val lastWp = lastScannedWaypoint

                val positionText = if (lastWp != null) {
                    val dx = (userX - lastWp.xMeters).toInt()
                    val dy = (userY - lastWp.yMeters).toInt()
                    "Position: ${dx}m East, ${dy}m North of ${lastWp.name}"
                } else {
                    "Position: (${userX.toInt()}m, ${userY.toInt()}m) Floor $currentFloor"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = positionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PureBlack
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Facing: $cardinalHeading (${userHeading.toInt()}°)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureBlack
                )
            }
        }
    }

}

private fun getCardinalDirection(degrees: Float): String {
    val normalized = (degrees % 360 + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "North"
        normalized in 22.5..67.5 -> "North-East"
        normalized in 67.5..112.5 -> "East"
        normalized in 112.5..157.5 -> "South-East"
        normalized in 157.5..202.5 -> "South"
        normalized in 202.5..247.5 -> "South-West"
        normalized in 247.5..292.5 -> "West"
        else -> "North-West"
    }
}
