package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Builds natural language scene descriptions from detected objects.
 * Maps object positions to spatial descriptors and generates rule-based sentences.
 */
class SceneDescriptionBuilder {

    /**
     * Position bucket for objects based on their horizontal location.
     */
    enum class Position {
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * Build a natural language description from detected objects.
     * @param objects List of detected objects with their bounding boxes
     * @param frameWidth Width of the camera frame for position calculation
     * @param frameBitmap Optional bitmap for brightness/contrast fallback analysis
     * @return Natural language description of the scene
     */
    fun buildDescription(objects: List<DetectedObject>, frameWidth: Int, frameBitmap: Bitmap? = null): String {
        if (objects.isEmpty()) {
            // Run brightness/contrast fallback check if bitmap is available
            return frameBitmap?.let { analyzePathFallback(it) } 
                ?: "No clear objects detected. Try better lighting or move closer."
        }

        // Group objects by position
        val leftObjects = mutableListOf<String>()
        val centerObjects = mutableListOf<String>()
        val rightObjects = mutableListOf<String>()

        for (obj in objects) {
            val position = getPosition(obj.boundingBox, frameWidth)
            val label = obj.label

            when (position) {
                Position.LEFT -> leftObjects.add(label)
                Position.CENTER -> centerObjects.add(label)
                Position.RIGHT -> rightObjects.add(label)
            }
        }

        // Build the description
        val parts = mutableListOf<String>()

        if (leftObjects.isNotEmpty()) {
            val leftText = formatObjectList(leftObjects, "on your left")
            parts.add(leftText)
        }

        if (centerObjects.isNotEmpty()) {
            val centerText = formatObjectList(centerObjects, "ahead")
            parts.add(centerText)
        }

        if (rightObjects.isNotEmpty()) {
            val rightText = formatObjectList(rightObjects, "on your right")
            parts.add(rightText)
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "No clear objects detected. Try better lighting or move closer."
        }
    }

    /**
     * Analyze path fallback using brightness and contrast heuristics.
     * Analyzes the lower 50% of the frame (floor area) for path guidance.
     * @param bitmap The camera frame to analyze
     * @return Actionable path guidance message
     */
    private fun analyzePathFallback(bitmap: Bitmap): String {
        // Analyze lower 50% of frame (floor area)
        val floorY = (bitmap.height * 0.5).toInt()
        val floorHeight = bitmap.height - floorY
        
        // Sample pixels from floor area (every 20th pixel for performance)
        var totalBrightness = 0
        var pixelCount = 0
        val brightnessValues = mutableListOf<Int>()
        
        for (y in floorY until bitmap.height step 20) {
            for (x in 0 until bitmap.width step 20) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) * 0.299 + 
                                   Color.green(pixel) * 0.587 + 
                                   Color.blue(pixel) * 0.114).toInt()
                totalBrightness += brightness
                brightnessValues.add(brightness)
                pixelCount++
            }
        }
        
        if (pixelCount == 0) {
            return "Path ahead visible, no distinct objects identified."
        }
        
        val avgBrightness = totalBrightness / pixelCount
        val brightnessPercent = (avgBrightness / 255.0 * 100).toInt()
        
        // Calculate contrast variation (standard deviation)
        val variance = brightnessValues.map { (it - avgBrightness).toFloat() * (it - avgBrightness).toFloat() }.average()
        val contrastScore = if (variance > 0) {
            (Math.sqrt(variance) / 255.0 * 100).toInt()
        } else {
            0
        }
        
        // Provide actionable guidance based on analysis
        return when {
            brightnessPercent < 30 -> "Low lighting on path ahead. Caution advised."
            contrastScore > 25 -> "Possible surface change or step detected ahead."
            brightnessPercent >= 60 && contrastScore < 15 -> "Path ahead appears clear and well lit."
            else -> "Path ahead visible, no distinct objects identified."
        }
    }

    /**
     * Determine the position of an object based on its bounding box center.
     * Uses stable position bucketing: x < 0.35 -> left, 0.35 <= x <= 0.65 -> center, x > 0.65 -> right
     * @param boundingBox The object's bounding box
     * @param frameWidth Width of the camera frame
     * @return Position bucket (LEFT, CENTER, or RIGHT)
     */
    private fun getPosition(boundingBox: android.graphics.Rect, frameWidth: Int): Position {
        val centerX = boundingBox.centerX().toFloat()
        val relativeX = centerX / frameWidth

        return when {
            relativeX < 0.35f -> Position.LEFT
            relativeX <= 0.65f -> Position.CENTER
            else -> Position.RIGHT
        }
    }

    /**
     * Format a list of objects with their position.
     * @param objects List of object labels
     * @param position Position descriptor (e.g., "on your left", "ahead", "on your right")
     * @return Formatted string like "chair on your left" or "person and door ahead"
     */
    private fun formatObjectList(objects: List<String>, position: String): String {
        return when (objects.size) {
            1 -> "${objects[0]} $position"
            2 -> "${objects[0]} and ${objects[1]} $position"
            else -> {
                val allButLast = objects.dropLast(1).joinToString(", ")
                val last = objects.last()
                "$allButLast, and $last $position"
            }
        }
    }
}
