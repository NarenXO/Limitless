package com.teamdexters.limitless.blind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.teamdexters.limitless.ui.blind.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Continuous voice orchestrator for the hands-free Blind Assistant.
 *
 * Flow:
 *   startListening() → SpeechRecognizer → onResults → route intent → run vision pipeline
 *                    → speak result → (cooldown) → auto re-arm listening
 *
 * Handles:
 *   - DESCRIBE_SURROUNDINGS  → VisionNarrationClient / OfflineObjectDetector
 *   - OBJECT_DETECTION       → OfflineObjectDetector.detectObjects()
 *   - COLOR_DETECTION        → OfflineColorDetector.detectColor()
 *   - READ_TEXT              → OfflineTextReader.readText() with rotation
 *   - STOP_LISTENING         → stop loop
 *   - REPEAT                 → re-speak last response
 *   - HELP / UNKNOWN         → speak help message, then re-arm
 *
 * Log tag: LIMITLESS_TRACE
 */
class BlindVoiceOrchestrator(
    private val context: Context,
    private val ttsManager: TTSManager,
    private val scope: CoroutineScope,
    /** Called on every state change so UI can reflect Listening / Thinking / Speaking. */
    private val onStateChanged: (OrchestratorState) -> Unit,
    /** Called with user command and banner text so UI can show result + deaf-safe backup. */
    private val onResult: (String, String) -> Unit
) {

    companion object {
        private const val TAG = "LIMITLESS_TRACE"
        /** Cooldown between end of TTS response and next listen cycle (ms). */
        private const val REARM_DELAY_MS = 1200L
        /** Max TTS wait time before forcing re-arm (ms). */
        private const val MAX_SPEAK_WAIT_MS = 8000L
    }

    // ----------- State --------------------------------------------------------

    enum class OrchestratorState {
        IDLE, LISTENING, THINKING, SPEAKING
    }

    private var isRunning = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastResponse: String = ""
    private var reArmJob: Job? = null
    private var listeningJob: Job? = null

    // ----------- Public API ---------------------------------------------------

    /**
     * Start the continuous hands-free listen loop.
     * Safe to call multiple times — no-op if already running.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "BlindVoiceOrchestrator: Starting continuous listen loop")
        armListening()
    }

    /**
     * Stop the loop cleanly — stops SpeechRecognizer, cancels jobs, emits IDLE.
     */
    fun stop() {
        isRunning = false
        reArmJob?.cancel()
        listeningJob?.cancel()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onStateChanged(OrchestratorState.IDLE)
        Log.d(TAG, "BlindVoiceOrchestrator: Stopped")
    }

    /**
     * Manually trigger a single listen cycle (Mic FAB tap).
     * Cancels any pending re-arm and starts immediately.
     */
    fun triggerManualListen() {
        reArmJob?.cancel()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        armListening()
    }

    // ----------- Core listen cycle --------------------------------------------

    private fun armListening() {
        if (!isRunning) return

        // Guard: SpeechRecognizer must be created on main thread
        listeningJob = scope.launch(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "BlindVoiceOrchestrator: SpeechRecognizer not available")
                ttsManager.speak("Voice recognition is not available on this device.")
                onStateChanged(OrchestratorState.IDLE)
                return@launch
            }

            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "BlindVoiceOrchestrator: Ready for speech")
                    onStateChanged(OrchestratorState.LISTENING)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "BlindVoiceOrchestrator: End of speech")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim() ?: ""
                    Log.d(TAG, "BlindVoiceOrchestrator: Recognized='$spoken'")
                    if (spoken.isNotBlank()) {
                        scope.launch { handleRecognizedText(spoken) }
                    } else {
                        scheduleReArm()
                    }
                }

                override fun onError(error: Int) {
                    val msg = speechErrorMessage(error)
                    Log.w(TAG, "BlindVoiceOrchestrator: SpeechRecognizer error=$error ($msg)")
                    // On error just re-arm silently — don't spam TTS for network/timeout errors
                    scheduleReArm()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "BlindVoiceOrchestrator: startListening failed", e)
                scheduleReArm()
            }
        }
    }

    // ----------- Intent dispatch ----------------------------------------------

    private suspend fun handleRecognizedText(spoken: String) {
        onStateChanged(OrchestratorState.THINKING)

        val intent = BlindVoiceIntentRouter.route(spoken)
        Log.d(TAG, "BlindVoiceOrchestrator: Dispatching intent=$intent")

        when (intent) {
            BlindVoiceIntentRouter.VoiceIntent.STOP_LISTENING -> {
                ttsManager.speakAndWait("Stopping. Tap the mic button to talk again.")
                isRunning = false
                onStateChanged(OrchestratorState.IDLE)
                return // Do NOT re-arm
            }

            BlindVoiceIntentRouter.VoiceIntent.REPEAT -> {
                val toRepeat = if (lastResponse.isNotBlank()) lastResponse
                               else "No previous response to repeat."
                deliverResponse(toRepeat)
            }

            BlindVoiceIntentRouter.VoiceIntent.HELP,
            BlindVoiceIntentRouter.VoiceIntent.UNKNOWN -> {
                deliverResponse(BlindVoiceIntentRouter.helpMessage, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.READ_TEXT -> {
                val response = runReadText()
                deliverResponse(response, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.COLOR_DETECTION -> {
                val response = runColorDetection()
                deliverResponse(response, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.OBJECT_DETECTION -> {
                val response = runObjectDetection()
                deliverResponse(response, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.DESCRIBE_SURROUNDINGS -> {
                val response = runDescribeSurroundings()
                deliverResponse(response, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.START_NAVIGATION -> {
                val destination = BlindNavigationVoice.extractDestination(spoken) ?: "the destination"
                val response = BlindNavigationVoice.startNavigation(destination)
                deliverResponse(response, spoken)
            }

            BlindVoiceIntentRouter.VoiceIntent.STOP_NAVIGATION -> {
                val response = BlindNavigationVoice.stopNavigation()
                deliverResponse(response, spoken)
            }
        }

        scheduleReArm()
    }

    // ----------- Vision pipelines --------------------------------------------

    private suspend fun runReadText(): String {
        val (frame, rotation) = CameraFrameManager.getLatestFrame()
        return if (frame.width == 640 && frame.height == 480) {
            // Fallback bitmap — camera not ready yet
            "Camera is getting ready. Please hold steady."
        } else {
            Log.d(TAG, "BlindVoiceOrchestrator: ReadText frame=${frame.width}x${frame.height} rot=$rotation")
            OfflineTextReader.readText(frame, rotation)
        }
    }

    private suspend fun runColorDetection(): String {
        val (frame, _) = CameraFrameManager.getLatestFrame()
        return if (frame.width == 640 && frame.height == 480) {
            "Camera is getting ready. Please hold steady."
        } else {
            Log.d(TAG, "BlindVoiceOrchestrator: ColorDetect frame=${frame.width}x${frame.height}")
            OfflineColorDetector.detectColor(frame)
        }
    }

    private fun runObjectDetection(): String {
        val (frame, _) = CameraFrameManager.getLatestFrame()
        return if (frame.width == 640 && frame.height == 480) {
            "Camera is getting ready. Please hold steady."
        } else {
            Log.d(TAG, "BlindVoiceOrchestrator: ObjectDetect frame=${frame.width}x${frame.height}")
            OfflineObjectDetector.detectObjects(frame, frame.width, frame.height)
        }
    }

    private suspend fun runDescribeSurroundings(): String {
        val (frame, rotation) = CameraFrameManager.getLatestFrame()
        return if (frame.width == 640 && frame.height == 480) {
            "Camera is getting ready. Please hold steady."
        } else {
            Log.d(TAG, "BlindVoiceOrchestrator: Describe frame=${frame.width}x${frame.height}")
            // Try Gemini online enrichment via BlindAIInvoker, fall back to offline objects
            try {
                BlindAIInvoker.invokeVisionAI(context, "describe surroundings")
            } catch (e: Exception) {
                Log.e(TAG, "BlindVoiceOrchestrator: Describe fallback to offline", e)
                OfflineObjectDetector.detectObjects(frame, frame.width, frame.height)
            }
        }
    }

    // ----------- Response delivery -------------------------------------------

    private suspend fun deliverResponse(text: String, spokenCommand: String = "") {
        lastResponse = text
        onResult(spokenCommand, text)
        onStateChanged(OrchestratorState.SPEAKING)
        Log.d(TAG, "BlindVoiceOrchestrator: Speaking response='${text.take(60)}...'")
        // speakAndWait with a max-wait guard so a long TTS never blocks re-arm forever
        kotlinx.coroutines.withTimeoutOrNull(MAX_SPEAK_WAIT_MS) {
            ttsManager.speakAndWait(text)
        }
        onStateChanged(OrchestratorState.LISTENING)
    }

    // ----------- Re-arm logic -------------------------------------------------

    private fun scheduleReArm() {
        if (!isRunning) return
        reArmJob?.cancel()
        reArmJob = scope.launch {
            delay(REARM_DELAY_MS)
            if (isRunning) {
                Log.d(TAG, "BlindVoiceOrchestrator: Re-arming listen cycle")
                armListening()
            }
        }
    }

    // ----------- Helpers ------------------------------------------------------

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO              -> "audio recording error"
        SpeechRecognizer.ERROR_CLIENT             -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic permission missing"
        SpeechRecognizer.ERROR_NETWORK            -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT    -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH           -> "no speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER             -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "speech timeout"
        else                                      -> "unknown ($error)"
    }
}
