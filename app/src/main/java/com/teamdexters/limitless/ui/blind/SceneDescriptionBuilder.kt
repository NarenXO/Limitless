package com.teamdexters.limitless.ui.blind

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
     * @return Natural language description of the scene
     */
    fun buildDescription(objects: List<DetectedObject>, frameWidth: Int): String {
        if (objects.isEmpty()) {
            return "No objects detected in view."
        }

        // Group objects by position
        val leftObjects = mutableListOf<String>()
        val centerObjects = mutableListOf<String>()
        val rightObjects = mutableListOf<String>()

        for (obj in objects) {
            val position = getPosition(obj.boundingBox, frameWidth)
            val label = obj.label.lowercase()

            when (position) {
                Position.LEFT -> leftObjects.add(label)
                Position.CENTER -> centerObjects.add(label)
                Position.RIGHT -> rightObjects.add(label)
            }
        }

        // Build the description
        val parts = mutableListOf<String>()

        if (leftObjects.isNotEmpty()) {
            val leftText = formatObjectList(leftObjects, "left")
            parts.add(leftText)
        }

        if (centerObjects.isNotEmpty()) {
            val centerText = formatObjectList(centerObjects, "ahead")
            parts.add(centerText)
        }

        if (rightObjects.isNotEmpty()) {
            val rightText = formatObjectList(rightObjects, "right")
            parts.add(rightText)
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "No objects detected in view."
        }
    }

    /**
     * Determine the position of an object based on its bounding box center.
     * @param boundingBox The object's bounding box
     * @param frameWidth Width of the camera frame
     * @return Position bucket (LEFT, CENTER, or RIGHT)
     */
    private fun getPosition(boundingBox: android.graphics.Rect, frameWidth: Int): Position {
        val centerX = boundingBox.centerX().toFloat()
        val relativeX = centerX / frameWidth

        return when {
            relativeX < 0.33f -> Position.LEFT
            relativeX <= 0.66f -> Position.CENTER
            else -> Position.RIGHT
        }
    }

    /**
     * Format a list of objects with their position.
     * @param objects List of object labels
     * @param position Position descriptor (e.g., "left", "ahead", "right")
     * @return Formatted string like "chair on your left" or "person and door ahead"
     */
    private fun formatObjectList(objects: List<String>, position: String): String {
        return when (objects.size) {
            1 -> "${objects[0]} on your $position"
            2 -> "${objects[0]} and ${objects[1]} on your $position"
            else -> {
                val allButLast = objects.dropLast(1).joinToString(", ")
                val last = objects.last()
                "$allButLast, and $last on your $position"
            }
        }
    }

    /**
     * Normalize object labels for better speech output.
     * Converts technical labels to more natural language.
     * @param label Original label from the detector
     * @return Normalized label
     */
    private fun normalizeLabel(label: String): String {
        return when (label.lowercase()) {
            "person" -> "person"
            "cell phone" -> "phone"
            "laptop" -> "laptop"
            "chair" -> "chair"
            "cup" -> "cup"
            "bottle" -> "bottle"
            "book" -> "book"
            else -> label
        }
    }
}
