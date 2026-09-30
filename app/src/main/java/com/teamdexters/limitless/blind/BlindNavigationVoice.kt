package com.teamdexters.limitless.blind

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.teamdexters.limitless.ui.blind.TTSManager
import com.teamdexters.limitless.ui.blind.nav.MockRoute
import com.teamdexters.limitless.ui.blind.nav.MockRouter
import com.teamdexters.limitless.ui.blind.nav.TurnType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Voice-driven navigation command handler for blind assist.
 * Parses navigation voice commands into intent enums and manages navigation state.
 * Provides turn-by-turn guidance with distinct haptic direction cues.
 */
object BlindNavigationVoice {

    private const val TAG = "LIMITLESS_TRACE"

    /**
     * Navigation intent enum for voice commands.
     */
    enum class NavigationIntent {
        START_NAVIGATION,
        TELL_ROUTE,
        STOP_NAVIGATION,
        UNKNOWN
    }

    /**
     * Navigation state management.
     */
    private var isNavigating = false
    private var currentStepIndex = 0
    private var destination: String? = null
    private var currentRoute: MockRoute? = null
    private var ttsManager: TTSManager? = null
    private var vibrator: Vibrator? = null

    /**
     * Initialize the navigation voice handler.
     * @param context Android context
     */
    fun initialize(context: Context) {
        ttsManager = TTSManager(context)
        ttsManager?.initialize { success ->
            Log.d(TAG, "BlindNav: TTS initialized=$success")
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        Log.d(TAG, "BlindNav: Initialized")
    }

    /**
     * Parse navigation voice command into intent enum.
     * @param query The spoken text from user
     * @return The detected navigation intent or UNKNOWN if no match
     */
    fun parseNavigationQuery(query: String): NavigationIntent {
        val normalizedQuery = query.lowercase().trim()

        val intent = when {
            // Start navigation
            normalizedQuery.contains("start navigation") ||
            normalizedQuery.contains("navigate") ||
            normalizedQuery.contains("guide me") ||
            normalizedQuery.contains("take me to") -> NavigationIntent.START_NAVIGATION

            // Tell route
            normalizedQuery.contains("tell me the route") ||
            normalizedQuery.contains("current route") ||
            normalizedQuery.contains("next step") ||
            normalizedQuery.contains("where to next") -> NavigationIntent.TELL_ROUTE

            // Stop navigation
            normalizedQuery.contains("stop navigation") ||
            normalizedQuery.contains("cancel route") ||
            normalizedQuery.contains("end route") -> NavigationIntent.STOP_NAVIGATION

            // Unknown intent
            else -> NavigationIntent.UNKNOWN
        }

        Log.d(TAG, "BlindNav: Parsed query='$query' -> intent=$intent")
        return intent
    }

    /**
     * Extract destination from navigation query.
     * @param query The spoken text from user
     * @return Extracted destination string or null
     */
    fun extractDestination(query: String): String? {
        val normalizedQuery = query.lowercase().trim()

        // Simple extraction: look for "to [destination]" pattern
        val toIndex = normalizedQuery.indexOf(" to ")
        if (toIndex != -1) {
            val afterTo = normalizedQuery.substring(toIndex + 4).trim()
            if (afterTo.isNotEmpty()) {
                return afterTo
            }
        }

        // Fallback: return entire query if it looks like a destination
        if (normalizedQuery.length > 3) {
            return normalizedQuery
        }

        return null
    }

    /**
     * Start navigation to the given destination.
     * @param destination The destination to navigate to
     * @return Success message or error
     */
    suspend fun startNavigation(destination: String): String {
        Log.d(TAG, "BlindNav: Starting navigation to destination=$destination")

        // TODO(Naren): Connect BlindNavigationVoice to Salman's AccessibleRouter API

        // For now, use mock router
        currentRoute = MockRouter.getRoute(destination)
        this.destination = destination
        currentStepIndex = 0
        isNavigating = true

        // Speak initial guidance
        val firstStep = currentRoute?.steps?.getOrNull(0)
        val instruction = if (firstStep != null) {
            "Starting navigation to $destination. ${firstStep.instruction}"
        } else {
            "Starting navigation to $destination."
        }

        ttsManager?.speakAndWait(instruction)

        // Trigger haptic for first step
        firstStep?.let { step ->
            triggerHapticForTurn(step.turnType)
            Log.d(TAG, "BlindNav: Triggered step=$currentStepIndex haptic=${step.turnType} destination=$destination")
        }

        return instruction
    }

    /**
     * Tell the current route or next step.
     * @return Current route information
     */
    fun tellRoute(): String {
        if (!isNavigating || currentRoute == null) {
            return "No active navigation."
        }

        val currentStep = currentRoute?.steps?.getOrNull(currentStepIndex)
        return if (currentStep != null) {
            if (currentStep.turnType == TurnType.ARRIVE) {
                "You have arrived at $destination."
            } else {
                "Next: ${currentStep.instruction}"
            }
        } else {
            "No active navigation."
        }
    }

    /**
     * Stop current navigation.
     * @return Confirmation message
     */
    fun stopNavigation(): String {
        Log.d(TAG, "BlindNav: Stopping navigation")

        isNavigating = false
        currentStepIndex = 0
        destination = null
        currentRoute = null

        ttsManager?.stop()
        vibrator?.cancel()

        return "Navigation stopped."
    }

    /**
     * Advance to the next navigation step.
     * @return Next step instruction or arrival message
     */
    suspend fun advanceToNextStep(): String {
        if (!isNavigating || currentRoute == null) {
            return "No active navigation."
        }

        currentStepIndex++
        val nextStep = currentRoute?.steps?.getOrNull(currentStepIndex)

        return if (nextStep != null) {
            if (nextStep.turnType == TurnType.ARRIVE) {
                val instruction = "You have arrived at $destination."
                ttsManager?.speakAndWait(instruction)
                triggerHapticForTurn(TurnType.ARRIVE)
                Log.d(TAG, "BlindNav: Triggered step=$currentStepIndex haptic=ARRIVE destination=$destination")
                isNavigating = false
                instruction
            } else {
                ttsManager?.speakAndWait(nextStep.instruction)
                triggerHapticForTurn(nextStep.turnType)
                Log.d(TAG, "BlindNav: Triggered step=$currentStepIndex haptic=${nextStep.turnType} destination=$destination")
                nextStep.instruction
            }
        } else {
            isNavigating = false
            "Route complete."
        }
    }

    /**
     * Trigger distinct haptic pattern based on turn type.
     * @param turnType The type of turn
     */
    private fun triggerHapticForTurn(turnType: TurnType) {
        if (vibrator?.hasVibrator() != true) return

        val pattern = when (turnType) {
            TurnType.LEFT -> {
                // LEFT: 2 short pulses
                longArrayOf(0, 100, 200, 100)
            }
            TurnType.RIGHT -> {
                // RIGHT: 3 short pulses
                longArrayOf(0, 100, 150, 100, 150, 100)
            }
            TurnType.STRAIGHT -> {
                // STRAIGHT: 1 long pulse (300ms)
                longArrayOf(0, 300)
            }
            TurnType.ARRIVE -> {
                // ARRIVE: 1 long + 2 short
                longArrayOf(0, 500, 100, 100, 100, 100)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    /**
     * Trigger obstacle warning haptic (rapid triple pulse).
     */
    fun triggerObstacleWarning() {
        if (vibrator?.hasVibrator() != true) return

        val pattern = longArrayOf(0, 80, 60, 80, 60, 80)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }

        Log.d(TAG, "BlindNav: Triggered obstacle warning haptic")
    }

    /**
     * Check if navigation is currently active.
     */
    fun isActive(): Boolean = isNavigating

    /**
     * Handle obstacle detection during navigation.
     * If an obstacle is detected < 1 meter away, interrupt TTS and speak warning.
     * @param frame The camera frame to analyze
     * @return Obstacle warning message if obstacle detected, null otherwise
     */
    suspend fun handleObstacleDetection(frame: Bitmap): String? {
        if (!isNavigating) {
            return null
        }

        val obstacleResult = OfflineObjectDetector.detectObstacles(frame)

        // Check if obstacle was detected (result contains obstacle names)
        if (obstacleResult.contains("obstacle") || obstacleResult.contains("chair") ||
            obstacleResult.contains("table") || obstacleResult.contains("wall")) {

            Log.d(TAG, "BlindNav: Obstacle detected during navigation - $obstacleResult")

            // Interrupt current TTS
            ttsManager?.stop()

            // Speak obstacle warning
            val warning = "Obstacle ahead. Please stop."
            ttsManager?.speakAndWait(warning)

            // Trigger rapid triple pulse warning haptic
            triggerObstacleWarning()

            return warning
        }

        return null
    }

    /**
     * Release resources.
     */
    fun release() {
        ttsManager?.release()
        ttsManager = null
        vibrator?.cancel()
        vibrator = null
        isNavigating = false
        currentStepIndex = 0
        destination = null
        currentRoute = null
        Log.d(TAG, "BlindNav: Released")
    }
}
