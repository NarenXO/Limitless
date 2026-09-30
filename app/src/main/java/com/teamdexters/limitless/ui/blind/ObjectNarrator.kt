package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages real-time object narration with short-term memory.
 * Prevents repeating the same objects within a 5-second window.
 * Integrates haptic feedback for spatial awareness.
 */
class ObjectNarrator(
    private val ttsManager: TTSManager,
    private val scope: CoroutineScope,
    private val context: Context,
    private val onHapticTriggered: (() -> Unit)? = null
) {

    // Short-term memory of spoken objects to avoid repetition
    private val spokenObjects = mutableMapOf<String, Long>()
    private val SPEAK_DURATION_MS = 5000L // 5 seconds

    /**
     * Process detected objects and announce only new ones.
     * @param objects List of detected objects
     * @param frameWidth Width of the camera frame for position calculation
     * @param frameHeight Height of the camera frame for bbox area calculation
     */
    fun narrateObjects(objects: List<DetectedObject>, frameWidth: Int, frameHeight: Int = (frameWidth * 0.75f).toInt()) {
        if (objects.isEmpty()) {
            return
        }

        // Clean up old entries from spoken set (older than 5 seconds)
        val currentTime = System.currentTimeMillis()
        spokenObjects.entries.removeIf { (_, timestamp) ->
            currentTime - timestamp > SPEAK_DURATION_MS
        }

        // Determine which objects are new and should be announced
        val newObjects = objects.filter { obj ->
            val objectKey = generateObjectKey(obj, frameWidth)
            val lastSpoken = spokenObjects[objectKey]

            // Announce if never spoken or spoken more than 5 seconds ago
            lastSpoken == null || (currentTime - lastSpoken > SPEAK_DURATION_MS)
        }

        // Sort new objects by confidence (descending) for consistent ordering
        val sortedNewObjects = newObjects.sortedByDescending { it.confidence }

        // Announce each new object with haptic feedback
        for (obj in sortedNewObjects) {
            val phrase = generateObjectPhrase(obj, frameWidth)
            speakPhrase(phrase)

            // Trigger directional haptic feedback
            triggerDirectionalHaptic(obj, frameWidth)

            // Check if object is close (large bbox) and trigger obstacle warning
            if (isObjectClose(obj, frameWidth, frameHeight)) {
                scope.launch {
                    delay(300) // Short delay before obstacle warning
                    HapticVocabulary.trigger(context, HapticVocabulary.HapticEvent.OBSTACLE_NEAR)
                }
            }

            // Mark as spoken
            val objectKey = generateObjectKey(obj, frameWidth)
            spokenObjects[objectKey] = currentTime
        }
    }

    /**
     * Generate a unique key for an object based on label and position.
     * This helps distinguish between multiple instances of the same object type.
     */
    private fun generateObjectKey(obj: DetectedObject, frameWidth: Int): String {
        val position = getPosition(obj.boundingBox, frameWidth)
        return "${obj.label}_${position}"
    }

    /**
     * Determine the position of an object based on its bounding box center.
     * @param boundingBox The object's bounding box
     * @param frameWidth Width of the camera frame
     * @return Position descriptor (LEFT, CENTER, RIGHT)
     */
    private fun getPosition(boundingBox: Rect, frameWidth: Int): String {
        val centerX = boundingBox.centerX().toFloat()
        val relativeX = centerX / frameWidth

        return when {
            relativeX < 0.35f -> "left"
            relativeX <= 0.65f -> "center"
            else -> "right"
        }
    }

    /**
     * Generate a natural language phrase for an object.
     * @param obj The detected object
     * @param frameWidth Width of the camera frame
     * @return Human-readable phrase
     */
    private fun generateObjectPhrase(obj: DetectedObject, frameWidth: Int): String {
        val position = getPosition(obj.boundingBox, frameWidth)
        val positionPhrase = when (position) {
            "left" -> "on your left"
            "center" -> "ahead"
            "right" -> "on your right"
            else -> "ahead"
        }

        // Optional depth hint for large bounding boxes
        val frameArea = frameWidth * (frameWidth * 0.75f) // Approximate frame area (4:3 aspect ratio)
        val bboxArea = obj.boundingBox.width() * obj.boundingBox.height()
        val isLarge = bboxArea > (frameArea * 0.35f)
        
        val depthHint = if (isLarge) {
            ", close"
        } else {
            ""
        }

        val capitalizedLabel = obj.label.substring(0, 1).uppercase() + obj.label.substring(1)
        return "$capitalizedLabel $positionPhrase$depthHint."
    }

    /**
     * Speak a phrase using TTS.
     * @param phrase The phrase to speak
     */
    private fun speakPhrase(phrase: String) {
        scope.launch {
            ttsManager.speak(phrase)
        }
    }

    /**
     * Trigger directional haptic feedback based on object position.
     * @param obj The detected object
     * @param frameWidth Width of the camera frame
     */
    private fun triggerDirectionalHaptic(obj: DetectedObject, frameWidth: Int) {
        val position = getPosition(obj.boundingBox, frameWidth)
        val hapticEvent = when (position) {
            "left" -> HapticVocabulary.HapticEvent.OBJECT_LEFT
            "right" -> HapticVocabulary.HapticEvent.OBJECT_RIGHT
            "center" -> HapticVocabulary.HapticEvent.OBJECT_CENTER
            else -> HapticVocabulary.HapticEvent.OBJECT_CENTER
        }
        HapticVocabulary.trigger(context, hapticEvent)
        onHapticTriggered?.invoke()
    }

    /**
     * Check if an object is close based on bounding box area.
     * @param obj The detected object
     * @param frameWidth Width of the camera frame
     * @param frameHeight Height of the camera frame
     * @return true if object bounding box is > 35% of frame area
     */
    private fun isObjectClose(obj: DetectedObject, frameWidth: Int, frameHeight: Int): Boolean {
        val frameArea = frameWidth * frameHeight
        val bboxArea = obj.boundingBox.width() * obj.boundingBox.height()
        return bboxArea > (frameArea * 0.35f)
    }

    /**
     * Clear the spoken objects memory (useful when scene changes significantly).
     */
    fun clearMemory() {
        spokenObjects.clear()
    }
}
