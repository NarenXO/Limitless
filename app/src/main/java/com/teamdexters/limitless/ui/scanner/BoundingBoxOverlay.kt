package com.teamdexters.limitless.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoundingBoxOverlay(
    detections: List<DetectedObject>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryAccent = Color(0xFFF791A9)
    val highlightBox = Color(0xFFFFDBDF)
    val textColor = Color(0xFF1F1F1F)

    Canvas(modifier = modifier) {
        if (imageWidth == 0 || imageHeight == 0) return@Canvas
        
        // Use center crop scaling since the CameraX preview usually fills the box
        val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (size.width - scaledWidth) / 2
        val offsetY = (size.height - scaledHeight) / 2

        detections.forEach { detection ->
            val rect = detection.boundingBox
            val left = rect.left * scale + offsetX
            val top = rect.top * scale + offsetY
            val right = rect.right * scale + offsetX
            val bottom = rect.bottom * scale + offsetY

            drawRect(
                color = primaryAccent,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )

            val labelText = "${detection.label}: ${(detection.confidence * 100).toInt()}%"
            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(color = textColor, fontSize = 14.sp)
            )

            val textWidth = textLayoutResult.size.width.toFloat()
            val textHeight = textLayoutResult.size.height.toFloat()
            
            // Draw background chip
            drawRect(
                color = highlightBox,
                topLeft = Offset(left, top - textHeight - 8.dp.toPx()),
                size = Size(textWidth + 16.dp.toPx(), textHeight + 8.dp.toPx())
            )
            
            // Draw text
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(left + 8.dp.toPx(), top - textHeight - 4.dp.toPx())
            )
        }
    }
}
