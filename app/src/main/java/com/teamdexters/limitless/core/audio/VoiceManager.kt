package com.teamdexters.limitless.core.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.assistant.cloud.GroqWhisperClient
import com.teamdexters.limitless.assistant.cloud.GroqChatClient
import com.teamdexters.limitless.config.SecureKeyProvider
import com.teamdexters.limitless.haptics.HapticManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceState {
    IDLE,
    WAKEWORD,
    VOICE_ASSISTANT, // Legacy state for backward compatibility
    ASSISTANT_RECORDING,
    ASSISTANT_PROCESSING,
    DEAF_CAPTION,
    BLIND_COMMAND,
    NAME_CAPTURE,
    PERSONA_SELECTION,
    TTS_PLAYING
}

sealed class VoiceResult {
    data class Partial(val text: String) : VoiceResult()
    data class Final(val text: String) : VoiceResult()
    data class Error(val code: Int, val message: String) : VoiceResult()
}

interface TtsCallback {
    fun speak(text: String)
}

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groqWhisperClient: GroqWhisperClient,
    private val groqChatClient: GroqChatClient,
    private val secureKeyProvider: SecureKeyProvider,
    private val hapticManager: HapticManager
) {
    companion object {
        private const val TAG = "VoiceManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
        private const val CONVERSATION_TIMEOUT_MS = 8000L // 8 seconds of silence before returning to wake word
        private const val MIN_RECORDING_DURATION_MS = 500L
        private const val MIN_RECORDING_BYTES = 10000
        
        // Wake word phrases
        private val WAKE_WORD_PHRASES = listOf(
            "hazel",
            "hey hazel",
            "okay hazel",
            "hi hazel"
        )
    }

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<VoiceResult>(extraBufferCapacity = 64)
    val results: SharedFlow<VoiceResult> = _results.asSharedFlow()

    private val _liveCaptions = MutableStateFlow("")
    val liveCaptions: StateFlow<String> = _liveCaptions.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // AudioRecord elements - single instance
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var conversationTimeoutJob: Job? = null
    private var transitionJob: Job? = null
    
    // SpeechRecognizer for DEAF_CAPTION and other states
    private var recognizer: SpeechRecognizer? = null
    private var recognizerJob: Job? = null
    
    // Continuous name monitoring recognizer (runs during assistant recording)
    private var nameMonitoringRecognizer: SpeechRecognizer? = null
    
    // Wake word recognizer (single instance for continuous detection)
    private var wakeWordRecognizer: SpeechRecognizer? = null

    // Audio effects
    private var agc: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    // State tracking
    private var transitionInProgress = false
    private var isTtsSpeaking = false
    private var userName: String = "" // Cached name for haptic vocabulary
    private var muteWakeWordDuringTts = true // Mute wake word detection during TTS
    private var ttsCallback: TtsCallback? = null // Callback for TTS integration
    
    // Audio focus
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        // Load user name once at initialization
        loadUserName()
    }

    private fun loadUserName() {
        userName = secureKeyProvider.getUserName()?.trim().orEmpty()
        Log.d(TAG, "HAPTIC_INIT: Loaded user name: '$userName'")
    }

    /**
     * Public API for external components to request state changes.
     * Overlay UI should NEVER call this - it's UI-only.
     * Only MainActivity should call this for explicit user actions (FAB tap).
     */
    fun requestState(newState: VoiceState, force: Boolean = false): Boolean {
        val current = _state.value
        val timestamp = System.currentTimeMillis()
        
        // Legacy compatibility: map VOICE_ASSISTANT to ASSISTANT_RECORDING
        val actualNewState = if (newState == VoiceState.VOICE_ASSISTANT) {
            VoiceState.ASSISTANT_RECORDING
        } else {
            newState
        }
        
        Log.d(TAG, "STATE_REQUEST[$timestamp]: $current → $actualNewState (force=$force, original=$newState)")

        // Prevent duplicate state requests
        if (current == actualNewState) {
            Log.d(TAG, "STATE_REQUEST[$timestamp]: Already in $actualNewState, ignoring")
            return true
        }

        // Prevent concurrent transitions
        if (transitionInProgress && !force) {
            Log.w(TAG, "STATE_REQUEST[$timestamp]: Transition in progress, denying request")
            return false
        }

        // Force override
        if (transitionInProgress && force) {
            Log.w(TAG, "STATE_REQUEST[$timestamp]: Force-preempting transition")
            transitionJob?.cancel()
            stopAllAudio()
            transitionInProgress = false
        }

        transitionInProgress = true
        transitionJob = scope.launch {
            doTransition(current, actualNewState, timestamp)
        }
        return true
    }

    private suspend fun doTransition(from: VoiceState, to: VoiceState, timestamp: Long) {
        Log.d(TAG, "=== STATE_TRANSITION_START ===")
        Log.d(TAG, "STATE: $from → $to (timestamp=$timestamp)")
        val startTime = System.currentTimeMillis()
        
        try {
            // Teardown current state
            teardown(from)
            
            // Hardware settling delay
            if (from != VoiceState.IDLE && to != VoiceState.IDLE) {
                delay(200)
            }
            
            // Update state
            _state.value = to
            Log.d(TAG, "STATE: Updated to $to")
            
            // Activate new state
            activate(to)
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "=== STATE_TRANSITION_END === Duration=${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "=== STATE_TRANSITION_ERROR ===")
            Log.e(TAG, "STATE: ${e.message}", e)
            _state.value = VoiceState.IDLE
        } finally {
            transitionInProgress = false
        }
    }

    private fun teardown(state: VoiceState) {
        Log.d(TAG, "TEARDOWN: $state")
        when (state) {
            VoiceState.WAKEWORD -> stopWakeWordRecording()
            VoiceState.VOICE_ASSISTANT -> { // Legacy compatibility
                stopAssistantRecording()
                stopNameMonitoring()
            }
            VoiceState.ASSISTANT_RECORDING -> {
                stopAssistantRecording()
                stopNameMonitoring()
            }
            VoiceState.ASSISTANT_PROCESSING -> {
                // Processing state doesn't hold audio resources
                stopNameMonitoring()
            }
            VoiceState.TTS_PLAYING -> {
                releaseAudioFocus()
                cancelConversationTimeout()
                stopNameMonitoring()
            }
            VoiceState.DEAF_CAPTION -> stopRecognizer()
            VoiceState.BLIND_COMMAND, VoiceState.NAME_CAPTURE, VoiceState.PERSONA_SELECTION -> stopRecognizer()
            VoiceState.IDLE -> {}
        }
        
        if (state == VoiceState.DEAF_CAPTION) {
            _liveCaptions.value = ""
        }
    }

    private fun activate(state: VoiceState) {
        Log.d(TAG, "ACTIVATE: $state")
        when (state) {
            VoiceState.IDLE -> {}
            VoiceState.WAKEWORD -> startWakeWordRecording()
            VoiceState.VOICE_ASSISTANT -> { // Legacy compatibility
                acquireAudioFocus()
                startAssistantRecording()
            }
            VoiceState.ASSISTANT_RECORDING -> {
                acquireAudioFocus()
                startAssistantRecording()
            }
            VoiceState.DEAF_CAPTION -> startRecognizer(continuous = true)
            VoiceState.BLIND_COMMAND, VoiceState.NAME_CAPTURE, VoiceState.PERSONA_SELECTION -> {
                startRecognizer(continuous = false)
            }
            VoiceState.TTS_PLAYING -> {}
            VoiceState.ASSISTANT_PROCESSING -> {} // Processing state is passive
        }
    }

    // --- Wake Word Recording ---

    private fun startWakeWordRecording() {
        Log.d(TAG, "=== WAKEWORD_START ===")
        Log.d(TAG, "WAKEWORD: Starting wake word detection")
        if (!hasMicPermission()) {
            Log.e(TAG, "WAKEWORD: Permission denied")
            return
        }
        
        stopAllAudio()
        
        // Initialize wake word recognizer (single instance)
        initializeWakeWordRecognizer()
        
        recordingJob = scope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            try {
                initializeAudioRecord()
                enableAudioEffects()
                audioRecord?.startRecording()
                Log.d(TAG, "=== AUDIO_START ===")
                Log.d(TAG, "AUDIO: Wake word recording started at $timestamp")
                
                val buffer = ShortArray(1024)
                var lastSpeechTime = System.currentTimeMillis()
                
                while (isActive && _state.value == VoiceState.WAKEWORD) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        val rms = calculateRMS(buffer)
                        
                        // Speech detection - only when not muted by TTS
                        if (rms > 450f && !isTtsSpeaking && !muteWakeWordDuringTts) {
                            lastSpeechTime = System.currentTimeMillis()
                            
                            // Use SpeechRecognizer for actual wake word detection
                            // This is more accurate than audio analysis
                            withContext(Dispatchers.Main) {
                                if (_state.value == VoiceState.WAKEWORD && !isTtsSpeaking) {
                                    triggerWakeWordRecognition()
                                }
                            }
                        }
                        
                        // Return to wake word after 30 seconds of silence
                        if (System.currentTimeMillis() - lastSpeechTime > 30000) {
                            Log.d(TAG, "WAKEWORD[$timestamp]: Timeout - restarting")
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "WAKEWORD[$timestamp]: Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "WAKEWORD[$timestamp]: Error - ${e.message}", e)
            } finally {
                Log.d(TAG, "=== AUDIO_STOP ===")
                Log.d(TAG, "AUDIO: Wake word recording stopped")
                Log.d(TAG, "=== WAKEWORD_STOP ===")
                stopAudioRecord()
                stopWakeWordRecognizer()
            }
        }
    }
    
    private fun initializeWakeWordRecognizer() {
        wakeWordRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        if (wakeWordRecognizer == null) {
            Log.e(TAG, "WAKEWORD: Failed to create SpeechRecognizer")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        
        wakeWordRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "WAKEWORD: Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "WAKEWORD: Speech detected")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "WAKEWORD: Speech ended")
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                Log.d(TAG, "WAKEWORD: Recognized: '$text'")
                
                if (isWakeWord(text)) {
                    Log.d(TAG, "=== WAKEWORD_DETECTED ===")
                    Log.d(TAG, "WAKEWORD: Transitioning to ASSISTANT_RECORDING")
                    requestState(VoiceState.ASSISTANT_RECORDING)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                
                if (isWakeWord(text)) {
                    Log.d(TAG, "=== WAKEWORD_DETECTED (partial) ===")
                    requestState(VoiceState.ASSISTANT_RECORDING)
                }
            }
            
            override fun onError(error: Int) {
                Log.d(TAG, "WAKEWORD: Recognizer error: $error")
            }
        })
    }
    
    private fun triggerWakeWordRecognition() {
        if (wakeWordRecognizer == null) {
            Log.e(TAG, "WAKEWORD: Recognizer not initialized")
            return
        }
        
        // Mute wake word detection during TTS to avoid false positives
        if (muteWakeWordDuringTts) {
            Log.d(TAG, "WAKEWORD: Muted during TTS - skipping recognition")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        
        try {
            wakeWordRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "WAKEWORD: Failed to start recognizer - ${e.message}")
        }
    }
    
    private fun stopWakeWordRecognizer() {
        Log.d(TAG, "WAKEWORD_RECOGNIZER_STOP")
        try { wakeWordRecognizer?.stopListening() } catch (_: Exception) {}
        try { wakeWordRecognizer?.destroy() } catch (_: Exception) {}
        wakeWordRecognizer = null
    }

    private fun isWakeWord(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return WAKE_WORD_PHRASES.any { lowerText.contains(it) }
    }

    private fun stopWakeWordRecording() {
        Log.d(TAG, "WAKEWORD_STOP")
        recordingJob?.cancel()
        recordingJob = null
        stopAudioRecord()
        stopWakeWordRecognizer()
    }

    // --- Assistant Recording (Continuous Conversation Mode) ---

    private fun startAssistantRecording() {
        Log.d(TAG, "=== ASSISTANT_START ===")
        Log.d(TAG, "ASSISTANT: Starting assistant recording")
        if (!hasMicPermission()) {
            Log.e(TAG, "ASSISTANT: Permission denied")
            _results.tryEmit(VoiceResult.Error(-1, "Permission denied"))
            requestState(VoiceState.WAKEWORD)
            return
        }
        
        stopAllAudio()
        
        // NOTE: Do NOT start name monitoring during ASSISTANT_RECORDING
        // It causes microphone hardware contention with AudioRecord
        // Name monitoring is only for DEAF_CAPTION mode
        
        recordingJob = scope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val audioFile = File(context.cacheDir, "assistant_audio_${timestamp}.wav")
            var totalBytesRecorded = 0L
            var totalFrames = 0
            var silenceFrames = 0
            val buffer = ShortArray(1024)
            val pcmData = mutableListOf<ShortArray>()
            
            try {
                initializeAudioRecord()
                enableAudioEffects()
                audioRecord?.startRecording()
                Log.d(TAG, "=== AUDIO_START ===")
                Log.d(TAG, "AUDIO: Assistant recording started at $timestamp")
                
                // Reset conversation timeout
                resetConversationTimeout()
                
                while (isActive && _state.value == VoiceState.ASSISTANT_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        pcmData.add(chunk)
                        totalBytesRecorded += read * 2
                        totalFrames++
                        
                        val rms = calculateRMS(chunk)
                        
                        // VAD: Detect silence
                        if (rms < 350f) {
                            silenceFrames++
                        } else {
                            silenceFrames = 0
                            resetConversationTimeout()
                        }
                        
                        // End recording after sufficient silence
                        if (silenceFrames > 30 && totalFrames > 30) {
                            Log.d(TAG, "ASSISTANT[$timestamp]: VAD: Silence detected - processing")
                            break
                        }
                        
                        // Max recording duration (10 seconds)
                        if (totalFrames > 625) { // ~10 seconds at 16kHz
                            Log.d(TAG, "ASSISTANT[$timestamp]: Max duration reached - processing")
                            break
                        }
                    }
                }
                
                val duration = System.currentTimeMillis() - timestamp
                Log.d(TAG, "=== AUDIO_STOP ===")
                Log.d(TAG, "AUDIO: Assistant recording stopped - Duration=${duration}ms, Frames=$totalFrames, Bytes=$totalBytesRecorded")
                
                // Validate recording before processing
                if (totalBytesRecorded < MIN_RECORDING_BYTES || duration < MIN_RECORDING_DURATION_MS) {
                    Log.w(TAG, "ASSISTANT[$timestamp]: Recording too short - discarding (bytes=$totalBytesRecorded, duration=${duration}ms)")
                    audioFile.delete()
                    _results.tryEmit(VoiceResult.Error(-1, "Recording too short"))
                    requestState(VoiceState.ASSISTANT_RECORDING) // Restart recording
                    return@launch
                }
                
                // Transition to processing state
                requestState(VoiceState.ASSISTANT_PROCESSING)
                
                // Process audio
                processAssistantAudio(pcmData, audioFile, timestamp)
                
            } catch (e: CancellationException) {
                Log.d(TAG, "ASSISTANT[$timestamp]: Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "ASSISTANT[$timestamp]: Error - ${e.message}", e)
                _results.tryEmit(VoiceResult.Error(-1, e.message ?: "Unknown error"))
                requestState(VoiceState.WAKEWORD)
            } finally {
                stopAudioRecord()
                stopNameMonitoring()
                audioFile.delete()
            }
        }
    }

    private suspend fun processAssistantAudio(pcmData: List<ShortArray>, audioFile: File, timestamp: Long) {
        Log.d(TAG, "=== ASSISTANT_PROCESSING_START ===")
        Log.d(TAG, "ASSISTANT: Processing audio from $timestamp")
        val startTime = System.currentTimeMillis()
        
        try {
            // Normalize and trim audio
            Log.d(TAG, "ASSISTANT: Normalizing PCM data")
            val normalizedPcm = normalizePCM(pcmData)
            Log.d(TAG, "ASSISTANT: Trimming silence")
            val trimmedPcm = trimSilence(normalizedPcm)
            
            // Save as WAV
            Log.d(TAG, "ASSISTANT: Saving as WAV")
            saveAsWav(trimmedPcm, audioFile)
            Log.d(TAG, "ASSISTANT: WAV saved - ${audioFile.length()} bytes")
            
            // Transcribe with Whisper
            Log.d(TAG, "=== WHISPER_START ===")
            val whisperStart = System.currentTimeMillis()
            val whisperResult = groqWhisperClient.transcribeAudio(audioFile)
            val whisperDuration = System.currentTimeMillis() - whisperStart
            
            if (whisperResult.isSuccess) {
                Log.d(TAG, "=== WHISPER_SUCCESS ===")
                Log.d(TAG, "WHISPER: Completed in ${whisperDuration}ms")
                val transcription = whisperResult.getOrNull()?.trim() ?: ""
                Log.d(TAG, "WHISPER: Transcription: '$transcription'")
                
                if (transcription.isNotEmpty()) {
                    // Emit final result
                    _results.tryEmit(VoiceResult.Final(transcription))
                    
                    // Check for name in transcript
                    checkForNameAndVibrate(transcription)
                    
                    // Check for system commands via HazelActionDispatcher
                    // If it's a system command, don't send to LLM
                    val isSystemCommand = checkForSystemCommand(transcription)
                    
                    if (!isSystemCommand) {
                        // Send to Groq LLM for chat completion
                        Log.d(TAG, "=== GROQ_CHAT_START ===")
                        val chatStart = System.currentTimeMillis()
                        val chatResult = groqChatClient.chatCompletion(transcription)
                        val chatDuration = System.currentTimeMillis() - chatStart
                        
                        if (chatResult.isSuccess) {
                            Log.d(TAG, "=== GROQ_CHAT_SUCCESS ===")
                            Log.d(TAG, "CHAT: Completed in ${chatDuration}ms")
                            val response = chatResult.getOrNull()?.trim() ?: ""
                            Log.d(TAG, "CHAT: Response: '$response'")
                            
                            if (response.isNotEmpty()) {
                                // Speak the response via TTS
                                speakResponse(response)
                            } else {
                                Log.w(TAG, "CHAT: Empty response - returning to recording")
                                requestState(VoiceState.ASSISTANT_RECORDING)
                            }
                        } else {
                            Log.e(TAG, "=== GROQ_CHAT_FAILURE ===")
                            Log.e(TAG, "CHAT: Failed - returning to recording")
                            requestState(VoiceState.ASSISTANT_RECORDING)
                        }
                    } else {
                        // System command handled, return to recording
                        Log.d(TAG, "ASSISTANT: System command handled - returning to recording")
                        requestState(VoiceState.ASSISTANT_RECORDING)
                    }
                } else {
                    Log.w(TAG, "WHISPER: Empty transcription - restarting")
                    requestState(VoiceState.ASSISTANT_RECORDING)
                }
            } else {
                Log.e(TAG, "=== WHISPER_FAILURE ===")
                Log.e(TAG, "WHISPER: Failed - restarting")
                requestState(VoiceState.ASSISTANT_RECORDING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ASSISTANT: Processing error - ${e.message}", e)
            requestState(VoiceState.ASSISTANT_RECORDING)
        }
        
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "=== ASSISTANT_PROCESSING_END === Duration=${duration}ms")
    }

    private fun stopAssistantRecording() {
        Log.d(TAG, "ASSISTANT_STOP")
        recordingJob?.cancel()
        recordingJob = null
        stopAudioRecord()
    }

    // --- Conversation Timeout ---

    private fun resetConversationTimeout() {
        conversationTimeoutJob?.cancel()
        conversationTimeoutJob = scope.launch {
            delay(CONVERSATION_TIMEOUT_MS)
            if (_state.value == VoiceState.ASSISTANT_RECORDING) {
                Log.d(TAG, "=== CONVERSATION_TIMEOUT ===")
                Log.d(TAG, "TIMEOUT: No speech for ${CONVERSATION_TIMEOUT_MS}ms")
                Log.d(TAG, "=== RETURN_TO_WAKEWORD ===")
                requestState(VoiceState.WAKEWORD)
            }
        }
    }

    private fun cancelConversationTimeout() {
        conversationTimeoutJob?.cancel()
        conversationTimeoutJob = null
    }

    // --- Name Detection (Continuous) ---

    private fun startNameMonitoring() {
        if (userName.isEmpty()) {
            Log.d(TAG, "NAME_MONITORING: No user name configured, skipping")
            return
        }
        
        Log.d(TAG, "=== NAME_MONITORING_START ===")
        Log.d(TAG, "NAME_MONITORING: Monitoring for name '$userName'")
        
        nameMonitoringRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        if (nameMonitoringRecognizer == null) {
            Log.e(TAG, "NAME_MONITORING: Failed to create SpeechRecognizer")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        
        nameMonitoringRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "NAME_MONITORING: Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "NAME_MONITORING: Speech detected")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "NAME_MONITORING: Speech ended - restarting")
                // Restart listening for continuous monitoring
                try {
                    nameMonitoringRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "NAME_MONITORING: Failed to restart - ${e.message}")
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                Log.d(TAG, "NAME_MONITORING: Final result - '$text'")
                checkForNameAndVibrate(text)
                // Restart for continuous monitoring
                try {
                    nameMonitoringRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "NAME_MONITORING: Failed to restart - ${e.message}")
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    Log.d(TAG, "NAME_MONITORING: Partial result - '$text'")
                    checkForNameAndVibrate(text)
                }
            }
            
            override fun onError(error: Int) {
                val name = errorName(error)
                Log.e(TAG, "NAME_MONITORING: Error $error ($name)")
                // Restart on recoverable errors
                if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    try {
                        nameMonitoringRecognizer?.startListening(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "NAME_MONITORING: Failed to restart - ${e.message}")
                    }
                }
            }
        })
        
        try {
            nameMonitoringRecognizer?.startListening(intent)
            Log.d(TAG, "NAME_MONITORING: Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "NAME_MONITORING: Failed to start - ${e.message}")
            stopNameMonitoring()
        }
    }
    
    private fun stopNameMonitoring() {
        Log.d(TAG, "=== NAME_MONITORING_STOP ===")
        try { nameMonitoringRecognizer?.stopListening() } catch (_: Exception) {}
        try { nameMonitoringRecognizer?.destroy() } catch (_: Exception) {}
        nameMonitoringRecognizer = null
    }

    private fun checkForNameAndVibrate(transcript: String) {
        if (userName.isEmpty() || transcript.isEmpty()) return
        
        // Fuzzy matching: ignore case, punctuation
        val cleanTranscript = transcript.lowercase().replace(Regex("[^a-z\\s]"), "")
        val cleanName = userName.lowercase()
        
        if (cleanTranscript.contains(cleanName)) {
            Log.d(TAG, "=== HAPTIC_MATCH ===")
            Log.d(TAG, "HAPTIC: Name '$userName' detected in: '$transcript'")
            hapticManager.playNameRhythm(userName)
        }
    }

    private fun checkForSystemCommand(transcript: String): Boolean {
        // Check if the transcript matches a system command
        // This is a simplified check - in production, integrate with HazelActionDispatcher
        val lowerText = transcript.lowercase()
        val systemCommands = listOf(
            "sos", "help", "emergency",
            "open camera", "camera", "take photo",
            "scan text", "read text", "read label",
            "navigate", "navigation", "maps",
            "flashlight"
        )
        
        val isCommand = systemCommands.any { lowerText.contains(it) }
        if (isCommand) {
            Log.d(TAG, "ASSISTANT: System command detected: '$transcript'")
        }
        return isCommand
    }

    private fun speakResponse(text: String) {
        Log.d(TAG, "=== TTS_SPEAK ===")
        Log.d(TAG, "TTS: Speaking: '$text'")
        
        // Use the TTS callback if available
        ttsCallback?.speak(text)
        
        // If no callback is set, emit the text for external handling
        _results.tryEmit(VoiceResult.Final(text))
    }

    fun setTtsCallback(callback: TtsCallback?) {
        ttsCallback = callback
        Log.d(TAG, "TTS: Callback ${if (callback != null) "set" else "cleared"}")
    }

    // --- Audio Record Management ---

    private fun initializeAudioRecord() {
        Log.d(TAG, "AUDIO_RECORD: Initializing AudioRecord")
        // Try different audio sources to find the best one
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        
        for (source in sources) {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
                
                audioRecord = AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    val sourceName = when (source) {
                        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                        MediaRecorder.AudioSource.MIC -> "MIC"
                        else -> "UNKNOWN"
                    }
                    Log.d(TAG, "AUDIO_RECORD: Initialized with source=$sourceName, SampleRate=$SAMPLE_RATE, BufferSize=$bufferSize")
                    return
                } else {
                    audioRecord?.release()
                    audioRecord = null
                }
            } catch (e: Exception) {
                val sourceName = when (source) {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                    MediaRecorder.AudioSource.MIC -> "MIC"
                    else -> "UNKNOWN"
                }
                Log.w(TAG, "AUDIO_RECORD: Failed with source=$sourceName - ${e.message}")
                audioRecord?.release()
                audioRecord = null
            }
        }
        
        if (audioRecord == null) {
            Log.e(TAG, "AUDIO_RECORD: Failed to initialize with any source")
            throw Exception("Failed to initialize AudioRecord with any source")
        }
    }

    private fun enableAudioEffects() {
        Log.d(TAG, "AUDIO_EFFECTS: Attempting to enable audio effects")
        try {
            // Automatic Gain Control
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioRecord?.audioSessionId ?: 0)
                agc?.setEnabled(true)
                Log.d(TAG, "AUDIO_EFFECTS: AGC enabled successfully")
            } else {
                Log.d(TAG, "AUDIO_EFFECTS: AGC not available on this device")
            }
            
            // Noise Suppressor
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioRecord?.audioSessionId ?: 0)
                noiseSuppressor?.setEnabled(true)
                Log.d(TAG, "AUDIO_EFFECTS: NoiseSuppressor enabled successfully")
            } else {
                Log.d(TAG, "AUDIO_EFFECTS: NoiseSuppressor not available on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AUDIO_EFFECTS: Failed to enable effects - ${e.message}")
        }
    }

    private fun stopAudioRecord() {
        agc?.release()
        agc = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private fun stopAllAudio() {
        stopWakeWordRecording()
        stopAssistantRecording()
        stopRecognizer()
    }

    // --- SpeechRecognizer (for DEAF_CAPTION, etc.) ---

    private fun startRecognizer(continuous: Boolean) {
        if (!hasMicPermission()) {
            _results.tryEmit(VoiceResult.Error(-1, "Permission denied"))
            return
        }
        
        stopRecognizer()
        
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(buildRecognizerListener(continuous))
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (continuous) 8000L else 3000L)
        }
        
        try {
            recognizer?.startListening(intent)
            Log.d(TAG, "RECOGNIZER: Started (continuous=$continuous)")
        } catch (e: Exception) {
            Log.e(TAG, "RECOGNIZER: Failed to start - ${e.message}")
            _results.tryEmit(VoiceResult.Error(-1, e.message ?: "Failed to start"))
        }
    }

    private fun buildRecognizerListener(continuous: Boolean) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "RECOGNIZER: onResults → '$text'")
            
            checkForNameAndVibrate(text)
            
            if (_state.value == VoiceState.NAME_CAPTURE && text.isNotEmpty()) {
                Log.d(TAG, "NAME_CAPTURE: Saving '$text'")
                secureKeyProvider.saveUserName(text)
                userName = text
                Log.d(TAG, "NAME_CAPTURE: Name saved and cached")
            }
            
            if (_state.value == VoiceState.DEAF_CAPTION) {
                _liveCaptions.value = text
            }
            
            _results.tryEmit(VoiceResult.Final(text))
            
            if (continuous && _state.value == VoiceState.DEAF_CAPTION) {
                recognizerJob = scope.launch {
                    delay(200)
                    if (_state.value == VoiceState.DEAF_CAPTION) {
                        startRecognizer(continuous = true)
                    }
                }
            } else {
                recognizerJob = scope.launch {
                    delay(300)
                    stopRecognizer()
                    if (_state.value != VoiceState.IDLE && _state.value != VoiceState.TTS_PLAYING) {
                        requestState(VoiceState.WAKEWORD)
                    }
                }
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            
            checkForNameAndVibrate(text)
            
            if (_state.value == VoiceState.DEAF_CAPTION) {
                _liveCaptions.value = text
            }
            
            _results.tryEmit(VoiceResult.Partial(text))
        }
        
        override fun onError(error: Int) {
            val name = errorName(error)
            Log.e(TAG, "RECOGNIZER: onError code=$error ($name)")
            _results.tryEmit(VoiceResult.Error(error, name))
            
            recognizerJob = scope.launch {
                delay(300)
                stopRecognizer()
                if (_state.value != VoiceState.IDLE && _state.value != VoiceState.TTS_PLAYING) {
                    requestState(VoiceState.WAKEWORD)
                }
            }
        }
    }

    private fun stopRecognizer() {
        recognizerJob?.cancel()
        recognizerJob = null
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    // --- TTS Callbacks ---

    fun onTTSStarted() {
        Log.d(TAG, "=== TTS_START ===")
        isTtsSpeaking = true
        muteWakeWordDuringTts = true
        requestState(VoiceState.TTS_PLAYING, force = true)
    }

    fun onTTSFinished() {
        Log.d(TAG, "=== TTS_END ===")
        isTtsSpeaking = false
        muteWakeWordDuringTts = false
        // After TTS, return to WAKEWORD for wake-word detection
        Log.d(TAG, "TTS: Returning to WAKEWORD for wake-word detection")
        requestState(VoiceState.WAKEWORD, force = true)
    }
    
    /**
     * Interrupt TTS and immediately start assistant recording
     * Called when user taps FAB during TTS
     */
    fun interruptTTS() {
        Log.d(TAG, "=== TTS_INTERRUPT ===")
        isTtsSpeaking = false
        muteWakeWordDuringTts = false
        requestState(VoiceState.ASSISTANT_RECORDING, force = true)
    }

    /**
     * Stop TTS and reset flags
     * Called when TTS is stopped externally
     */
    fun stopTts() {
        Log.d(TAG, "=== TTS_STOP ===")
        isTtsSpeaking = false
        muteWakeWordDuringTts = false
    }

    // --- Audio Focus ---

    private fun acquireAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "AUDIO_FOCUS: change=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (_state.value != VoiceState.IDLE) {
                    Log.d(TAG, "AUDIO_FOCUS: Loss - returning to IDLE")
                    requestState(VoiceState.IDLE, force = true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "AUDIO_FOCUS: Transient loss - ignoring")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AUDIO_FOCUS: Regained")
            }
        }
    }

    // --- Audio Processing ---

    private fun calculateRMS(buffer: ShortArray): Float {
        var sum = 0L
        for (s in buffer) sum += s.toLong() * s.toLong()
        return if (buffer.isNotEmpty()) kotlin.math.sqrt(sum.toDouble() / buffer.size).toFloat() else 0f
    }

    private fun normalizePCM(pcmData: List<ShortArray>): List<ShortArray> {
        Log.d(TAG, "AUDIO_PROCESSING: Starting PCM normalization")
        var maxAmplitude = 0f
        for (chunk in pcmData) {
            for (sample in chunk) {
                val abs = kotlin.math.abs(sample.toFloat())
                if (abs > maxAmplitude) maxAmplitude = abs
            }
        }
        
        Log.d(TAG, "AUDIO_PROCESSING: Max amplitude = $maxAmplitude")
        
        // Skip normalization if already sufficient volume
        if (maxAmplitude > 20000f) {
            Log.d(TAG, "AUDIO_PROCESSING: Sufficient volume, skipping normalization")
            return pcmData
        }
        
        // Skip if too quiet (indicates no speech)
        if (maxAmplitude < 100f) {
            Log.w(TAG, "AUDIO_PROCESSING: Too quiet, skipping normalization (maxAmplitude=$maxAmplitude)")
            return pcmData
        }
        
        val targetAmplitude = Short.MAX_VALUE * 0.8f
        val normalizationFactor = targetAmplitude / maxAmplitude
        
        Log.d(TAG, "AUDIO_PROCESSING: Normalization factor = $normalizationFactor")
        
        if (normalizationFactor > 3.0f) {
            Log.w(TAG, "AUDIO_PROCESSING: Weak input detected - normalization factor=$normalizationFactor (may indicate poor mic placement)")
        }
        
        return pcmData.map { chunk ->
            chunk.map { sample ->
                val normalized = (sample * normalizationFactor).toInt()
                normalized.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }.toShortArray()
        }
    }

    private fun trimSilence(pcmData: List<ShortArray>): List<ShortArray> {
        if (pcmData.isEmpty()) return pcmData
        
        val chunkRms = pcmData.map { calculateRMS(it) }
        val avgRms = chunkRms.average().toFloat()
        val silenceThreshold = avgRms * 0.3f
        
        var startChunk = 0
        for (i in chunkRms.indices) {
            if (chunkRms[i] > silenceThreshold) {
                startChunk = i
                break
            }
        }
        
        var endChunk = pcmData.size - 1
        for (i in chunkRms.indices.reversed()) {
            if (chunkRms[i] > silenceThreshold) {
                endChunk = i
                break
            }
        }
        
        return pcmData.subList(startChunk, endChunk + 1)
    }

    private fun saveAsWav(pcmData: List<ShortArray>, file: File) {
        val totalShorts = pcmData.sumOf { it.size }
        val byteBuffer = ByteBuffer.allocate(totalShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (chunk in pcmData) {
            for (s in chunk) byteBuffer.putShort(s)
        }
        val audioBytes = byteBuffer.array()
        
        val header = ByteArray(44)
        val totalDataLen = audioBytes.size + 36
        val sampleRate = SAMPLE_RATE.toLong()
        val byteRate = sampleRate * 2
        
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (audioBytes.size and 0xff).toByte(); header[41] = ((audioBytes.size shr 8) and 0xff).toByte()
        header[42] = ((audioBytes.size shr 16) and 0xff).toByte(); header[43] = ((audioBytes.size shr 24) and 0xff).toByte()
        
        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(audioBytes)
        }
        
        Log.d(TAG, "SAVE_WAV: Saved ${file.length()} bytes")
    }

    // --- Utility ---

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun errorName(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER_DISCONNECTED"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        else -> "ERROR_UNKNOWN_$code"
    }

    fun shutdown() {
        Log.d(TAG, "=== SHUTDOWN ===")
        stopAllAudio()
        cancelConversationTimeout()
        releaseAudioFocus()
        requestState(VoiceState.IDLE, force = true)
    }
}
