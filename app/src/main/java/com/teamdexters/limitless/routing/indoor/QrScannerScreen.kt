package com.teamdexters.limitless.routing.indoor

import android.annotation.SuppressLint
import android.speech.tts.TextToSpeech
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
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
    tts: TextToSpeech?,
    onWaypointScanned: (IndoorWaypoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var scannedMessage by remember { mutableStateOf<String?>(null) }
    var lastScannedTime by remember { mutableStateOf(0L) }

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
            .background(SurfaceTint)
            .border(1.dp, PersonaMobility, RoundedCornerShape(16.dp))
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
                                                val raw = barcode.rawValue ?: continue
                                                val now = System.currentTimeMillis()
                                                if (now - lastScannedTime > 3000) { // Throttle scans to 3s
                                                    lastScannedTime = now
                                                    val matchedWaypoint = kcgIndoorWaypoints.find { it.qrPayload == raw }
                                                    if (matchedWaypoint != null) {
                                                        pdrEngine.resetPosition(matchedWaypoint)
                                                        scannedMessage = "Verified: ${matchedWaypoint.name}"
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        tts?.speak(
                                                            matchedWaypoint.description,
                                                            TextToSpeech.QUEUE_FLUSH,
                                                            null,
                                                            matchedWaypoint.id
                                                        )
                                                        onWaypointScanned(matchedWaypoint)
                                                    } else {
                                                        scannedMessage = "Unknown QR code: $raw"
                                                    }
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
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentMsg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
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
                val cardinalHeading = getCardinalDirection(pdrEngine.headingDegrees)
                val lastWp = pdrEngine.lastScannedWaypoint

                val positionText = if (lastWp != null) {
                    val dx = (pdrEngine.xMeters - lastWp.xMeters).toInt()
                    val dy = (pdrEngine.yMeters - lastWp.yMeters).toInt()
                    "Position: ${dx}m East, ${dy}m North of ${lastWp.name}"
                } else {
                    "Position: (${pdrEngine.xMeters.toInt()}m, ${pdrEngine.yMeters.toInt()}m) Floor ${pdrEngine.currentFloor}"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = positionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Facing: $cardinalHeading (${pdrEngine.headingDegrees.toInt()}°)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
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
