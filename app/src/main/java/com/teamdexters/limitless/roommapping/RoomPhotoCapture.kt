package com.teamdexters.limitless.roommapping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class RoomPhotoCapture(private val context: Context) {
    fun savePhoto(roomId: String, cornerIndex: Int, imageProxy: ImageProxy, rotationDegrees: Int): String? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        // Rotate if necessary
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Resize to max 1280x960
        val maxDim = max(rotatedBitmap.width, rotatedBitmap.height)
        val scale = if (maxDim > 1280) 1280f / maxDim else 1f
        val resizedBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotatedBitmap,
                (rotatedBitmap.width * scale).toInt(),
                (rotatedBitmap.height * scale).toInt(),
                true
            )
        } else {
            rotatedBitmap
        }

        val dir = File(context.filesDir, "mapped_rooms/$roomId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "photo_$cornerIndex.jpg")

        FileOutputStream(file).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        imageProxy.close()
        
        return file.absolutePath
    }
}
