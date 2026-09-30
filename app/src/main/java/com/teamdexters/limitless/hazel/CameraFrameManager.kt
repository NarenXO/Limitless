package com.teamdexters.limitless.hazel

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFrameManager @Inject constructor() {
    private val latestFrame = AtomicReference<Bitmap?>(null)

    @Synchronized
    fun updateLatestFrame(bitmap: Bitmap) {
        val maxDimension = 640
        val width = bitmap.width
        val height = bitmap.height

        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
            val newHeight = if (ratio > 1) (maxDimension / ratio).toInt() else maxDimension
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        val oldFrame = latestFrame.getAndSet(scaledBitmap)
        // If we created a new scaled bitmap, we might want to recycle the old one if it's different.
        // We'll leave recycling to standard GC or caller for safety, or clear it if needed.
    }

    fun getLatestFrame(): Bitmap? {
        return latestFrame.get()
    }

    fun getLatestFrameAsBase64(): String? {
        val bitmap = latestFrame.get() ?: return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    @Synchronized
    fun clearFrame() {
        val oldFrame = latestFrame.getAndSet(null)
        oldFrame?.recycle()
    }
}
