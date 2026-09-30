package com.teamdexters.limitless.roommapping

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QRWaypointScanner(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (!rawValue.isNullOrBlank()) {
                                val formatId = if (!rawValue.startsWith("LIMITLESS_ROOM_")) "LIMITLESS_ROOM_$rawValue" else rawValue
                                Log.d("LIMITLESS_TRACE", "QRWaypointScanner: Detected roomId=$formatId")
                                onQRCodeDetected(formatId)
                                break
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("LIMITLESS_TRACE", "QRWaypointScanner: ML Kit barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "QRWaypointScanner: Exception processing image", e)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}
