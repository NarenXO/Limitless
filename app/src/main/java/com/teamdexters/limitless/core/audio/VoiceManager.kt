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
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.assistant.cloud.GroqWhisperClient
import com.teamdexters.limitless.config.SecureKeyProvider
import com.teamdexters.limitless.haptics.HapticManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
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
    DEAF_CAPTION,
    BLIND_COMMAND,
    VOICE_ASSISTANT,
    NAME_CAPTURE,
    PERSONA_SELECTION,
    TTS_PLAYING
}

sealed class VoiceResult {
    data class Partial(val text: String) : VoiceResult()
    data class Final(val text: String)   : VoiceResult()
    data class Error(val code: Int, val message: String) : VoiceResult()
}

private val STATE_PRIORITY = listOf(
    VoiceState.TTS_PLAYING,
    VoiceState.VOICE_ASSISTANT,
    VoiceState.BLIND_COMMAND,
    VoiceState.DEAF_CAPTION,
    VoiceState.NAME_CAPTURE,
    VoiceState.PERSONA_SELECTION,
    VoiceState.WAKEWORD,
    VoiceState.IDLE
)

private fun VoiceState.priority() = STATE_PRIORITY.indexOf(this)

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groqWhisperClient: GroqWhisperClient,
    private val secureKeyProvider: SecureKeyProvider,
    private val hapticManager: HapticManager
) {
    companion object {
        private const val TAG = "VoiceManager"
        private const val SAMPLE_RATE = 16000
    }

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<VoiceResult>(extraBufferCapacity = 64)
    val results: SharedFlow<VoiceResult> = _results.asSharedFlow()

    private val _liveCaptions = MutableStateFlow("")
    val liveCaptions: StateFlow<String> = _liveCaptions.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recognizer: SpeechRecognizer? = null

    // AudioRecord elements
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Tracks the currently active transition coroutine so force requests can cancel it
    private var transitionJob: Job? = null

    private var isContinuous = false
    private var transitionInProgress = false
    private var previousState: VoiceState = VoiceState.IDLE

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null

    // --- Dynamic name from SecureKeyProvider ---
    private val currentUserName: String
        get() = secureKeyProvider.getUserName()?.trim() ?: ""

    /**
     * Checks if the transcript contains the user's saved name (case-insensitive).
     * Fires [HapticManager.playNameRhythm] and logs a LIMITLESS_TRACE entry.
     * Runs across ALL states: WAKEWORD, DEAF_CAPTION, VOICE_ASSISTANT, etc.
     */
    private fun checkForNameAndVibrate(transcript: String) {
        val name = currentUserName
        Log.d(TAG, "Checking speech '$transcript' against target name '$name'")
        if (name.isNotEmpty() && transcript.isNotEmpty()) {
            if (transcript.lowercase().contains(name.lowercase())) {
                Log.d("LIMITLESS_TRACE", "Haptic name match found for '$name' in speech: '$transcript'")
                hapticManager.playNameRhythm(name)
            }
        }
    }

    // --- State Machine ---

    fun requestState(newState: VoiceState, force: Boolean = false): Boolean {
        val current = _state.value

        // ── Re-entrancy guard for VOICE_ASSISTANT ────────────────────────────
        // If HazelListeningOverlay mounts while VOICE_ASSISTANT is already active
        // and Groq Whisper is recording, silently return true to avoid cancelling
        // the in-flight recordingJob.
        if (newState == VoiceState.VOICE_ASSISTANT
            && current == VoiceState.VOICE_ASSISTANT
            && recordingJob?.isActive == true) {
            Log.d(TAG, "requestState(VOICE_ASSISTANT): already recording — no-op to protect Whisper job")
            return true
        }

        if (current == newState) return true
        if (!force && newState.priority() > current.priority()) return false
        if (transitionInProgress && !force) return false

        // Force-preempt any in-progress transition (e.g. shutdown from onStop)
        if (transitionInProgress && force) {
            Log.w(TAG, "Force-preempting transition to ${_state.value} for $newState")
            transitionJob?.cancel()
            stopAudioRecord()
            stopAndDestroyRecognizer()
            transitionInProgress = false
        }

        Log.i(TAG, "STATE CHANGE: $current → $newState")
        transitionJob = scope.launch { doTransition(current, newState) }
        return true
    }

    fun onTTSStarted() {
        Log.d(TAG, "TTS START")
        previousState = _state.value
        requestState(VoiceState.TTS_PLAYING, force = true)
    }

    fun onTTSFinished() {
        Log.d(TAG, "TTS END")
        // After TTS completes, always return to WAKEWORD state
        requestState(VoiceState.WAKEWORD, force = true)
    }

    fun shutdown() {
        Log.d(TAG, "SHUTDOWN called — forcing IDLE")
        requestState(VoiceState.IDLE, force = true)
    }

    private suspend fun doTransition(from: VoiceState, to: VoiceState) {
        transitionInProgress = true
        try {
            // Special case: WAKEWORD -> VOICE_ASSISTANT transition doesn't need teardown
            // since they both use AudioRecord and we want to preserve the recording
            if (from == VoiceState.WAKEWORD && to == VoiceState.VOICE_ASSISTANT) {
                Log.d(TAG, "Direct WAKEWORD -> VOICE_ASSISTANT transition (no teardown)")
                _state.value = to
                Log.i(TAG, "MIC ACQUIRED for $to")
                activate(to)
            } else {
                teardown(from)
                delay(400) // Hardware settling
                Log.d(TAG, "MIC RELEASED")
                _state.value = to
                Log.i(TAG, "MIC ACQUIRED for $to")
                activate(to)
            }
        } finally {
            transitionInProgress = false
        }
    }

    private fun teardown(state: VoiceState) {
        when (state) {
            VoiceState.WAKEWORD, VoiceState.VOICE_ASSISTANT -> {
                Log.d(TAG, "WAKE WORD STOP / ASSISTANT STOP (releasing AudioRecord)")
                stopAudioRecord()
            }
            VoiceState.TTS_PLAYING -> releaseAudioFocus()
            else -> stopAndDestroyRecognizer()
        }
        if (state == VoiceState.DEAF_CAPTION) {
            _liveCaptions.value = ""
        }
    }

    private fun activate(state: VoiceState) {
        when (state) {
            VoiceState.IDLE -> {}
            VoiceState.WAKEWORD -> {
                Log.d(TAG, "WAKE WORD START")
                startWakeWordRecord()
            }
            VoiceState.VOICE_ASSISTANT -> {
                acquireAudioFocus()
                startAssistantRecord()
            }
            VoiceState.DEAF_CAPTION -> {
                isContinuous = true
                startRecognizer(continuous = true)
            }
            VoiceState.BLIND_COMMAND, VoiceState.NAME_CAPTURE, VoiceState.PERSONA_SELECTION -> {
                isContinuous = false
                startRecognizer(continuous = false)
            }
            VoiceState.TTS_PLAYING -> {}
        }
    }

    // --- AudioRecord for WakeWord & Assistant ---

    private fun startWakeWordRecord() {
        if (!hasMicPermission()) return
        stopAudioRecord()
        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
                )
                audioRecord?.startRecording()
                var ambientRms = 100.0f
                var consecutiveSpeechFrames = 0
                var postSpeechSilenceFrames = 0
                var shouldExit = false
                val buffer = ShortArray(1024)
                val startTime = System.currentTimeMillis()

                while (isActive && _state.value == VoiceState.WAKEWORD && !shouldExit) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val rms = calculateRMS(buffer)
                        val zcr = calculateZCR(buffer, read)
                        val durationMs = System.currentTimeMillis() - startTime

                        // Dynamically adapt to ambient noise floor
                        ambientRms = (0.95f * ambientRms + 0.05f * rms).coerceIn(50.0f, 1000.0f)
                        val dynamicThreshold = (ambientRms * 2.2f).coerceIn(350.0f, 1200.0f)
                        
                        // Multi-parameter wake word validation: RMS >= 450.0, ZCR in 20..450, duration in 300..2500ms
                        val isSpeechFrame = rms >= 450.0f && zcr in 20..450 && durationMs in 300..2500

                        if (isSpeechFrame) {
                            consecutiveSpeechFrames++
                            postSpeechSilenceFrames = 0
                            // Limit max utterance duration: >2.5s is continuous conversation/noise, not a wake phrase
                            if (consecutiveSpeechFrames > 40) {
                                consecutiveSpeechFrames = 0
                            }
                        } else {
                            // Phrase completed if speech burst was between 320ms and 1600ms
                            if (consecutiveSpeechFrames in 5..25) {
                                postSpeechSilenceFrames++
                                if (postSpeechSilenceFrames >= 2) {
                                    Log.d(TAG, "Valid wake word speech detected (RMS=$rms, ZCR=$zcr, Duration=${durationMs}ms). Requesting VOICE_ASSISTANT.")
                                    withContext(Dispatchers.Main) { 
                                        requestState(VoiceState.VOICE_ASSISTANT)
                                        shouldExit = true
                                    }
                                }
                            } else {
                                consecutiveSpeechFrames = 0
                                postSpeechSilenceFrames = 0
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal lifecycle event - log at debug level
                Log.d(TAG, "Wake word recording cancelled (normal lifecycle event)")
            } catch (e: Exception) {
                Log.e(TAG, "WAKEWORD exception: ${e.message}")
            } finally {
                stopAudioRecord()
            }
        }
    }

    private fun startAssistantRecord() {
        if (!hasMicPermission()) {
            _results.tryEmit(VoiceResult.Error(-1, "Permission denied"))
            return
        }
        stopAudioRecord()
        recordingJob = scope.launch(Dispatchers.IO) {
            val audioFile = File(context.cacheDir, "assistant_audio.wav")
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
                )
                audioRecord?.startRecording()

                val pcmData = mutableListOf<ShortArray>()
                var silenceFrames = 0
                val buffer = ShortArray(1024)
                val maxRecordingFrames = 250 // ~4 seconds at 16kHz

                while (isActive && _state.value == VoiceState.VOICE_ASSISTANT && pcmData.size < maxRecordingFrames) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        pcmData.add(chunk)

                        // Silence detection (~1 second)
                        val rms = calculateRMS(chunk)
                        if (rms < 350f) silenceFrames++ else silenceFrames = 0

                        if (silenceFrames > 30) {
                            Log.d(TAG, "Silence detected, stopping recording")
                            break
                        }
                    }
                }
                stopAudioRecord()

                // Execute Whisper STT -> Groq LLM -> TTS pipeline with NonCancellable to prevent cancellation
                withContext(NonCancellable) {
                    Log.d(TAG, "WHISPER_START")
                    saveAsWav(pcmData, audioFile)
                    val result = groqWhisperClient.transcribeAudio(audioFile)

                    if (result.isSuccess) {
                        val text = result.getOrNull() ?: ""
                        Log.d(TAG, "WHISPER_STOP success text_len=${text.length}")
                        // Check for user's name in assistant transcript too
                        if (text.isNotEmpty()) checkForNameAndVibrate(text)
                        withContext(Dispatchers.Main) {
                            _results.tryEmit(VoiceResult.Final(text))
                            // Request TTS to speak the response
                            onTTSStarted()
                        }
                    } else {
                        Log.e(TAG, "WHISPER_STOP error: ${result.exceptionOrNull()?.message}")
                        withContext(Dispatchers.Main) {
                            _results.tryEmit(VoiceResult.Error(-1, "Whisper failed"))
                            requestState(VoiceState.WAKEWORD, force = true)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal lifecycle event - log at debug level as specified
                Log.d(TAG, "Assistant recording cancelled (normal lifecycle event)")
                withContext(Dispatchers.Main) {
                    requestState(VoiceState.WAKEWORD, force = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Assistant recording exception: ${e.message}")
                withContext(Dispatchers.Main) { 
                    requestState(VoiceState.WAKEWORD, force = true)
                }
            } finally {
                stopAudioRecord()
                audioFile.delete()
            }
        }
    }

    private fun calculateRMS(buffer: ShortArray): Float {
        var sum = 0L
        for (s in buffer) sum += s.toLong() * s.toLong()
        return if (buffer.isNotEmpty()) Math.sqrt(sum.toDouble() / buffer.size).toFloat() else 0f
    }

    private fun calculateZCR(buffer: ShortArray, size: Int): Int {
        var zeroCrossings = 0
        if (size == 0) return 0
        var prevSign = buffer[0] > 0
        for (i in 1 until size) {
            val sign = buffer[i] > 0
            if (sign != prevSign) {
                zeroCrossings++
                prevSign = sign
            }
        }
        return zeroCrossings
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
    }

    private fun stopAudioRecord() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    // --- SpeechRecognizer ---

    private fun startRecognizer(continuous: Boolean) {
        if (!hasMicPermission()) {
            _results.tryEmit(VoiceResult.Error(-1, "RECORD_AUDIO not granted"))
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener(continuous))
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
        }
        try { recognizer?.startListening(intent) } catch (e: Exception) {
            _results.tryEmit(VoiceResult.Error(-1, "Failed to start: ${e.message}"))
        }
    }

    private fun buildListener(continuous: Boolean) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            val name = errorName(error)
            Log.e(TAG, "SpeechRecognizer: onError code=$error ($name) state=${_state.value}")
            _results.tryEmit(VoiceResult.Error(error, name))

            if (continuous && _state.value == VoiceState.DEAF_CAPTION) {
                scope.launch {
                    delay(500)
                    if (_state.value == VoiceState.DEAF_CAPTION) {
                        stopAndDestroyRecognizer()
                        delay(300)
                        startRecognizer(continuous = true)
                    }
                }
            } else {
                scope.launch {
                    delay(300)
                    stopAndDestroyRecognizer()
                    if (_state.value != VoiceState.IDLE && _state.value != VoiceState.TTS_PLAYING) {
                        requestState(VoiceState.WAKEWORD, force = true)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "SpeechRecognizer: onResults → \"$text\"")

            // Always-on name detection — fires across ALL recognizer states
            if (text.isNotEmpty()) checkForNameAndVibrate(text)

            if (_state.value == VoiceState.DEAF_CAPTION) {
                if (text.isNotEmpty()) _liveCaptions.value = text
                _results.tryEmit(VoiceResult.Final(text))
                scope.launch {
                    delay(200)
                    if (_state.value == VoiceState.DEAF_CAPTION) {
                        stopAndDestroyRecognizer()
                        delay(200)
                        startRecognizer(continuous = true)
                    }
                }
            } else {
                _results.tryEmit(VoiceResult.Final(text))
                scope.launch {
                    delay(300)
                    stopAndDestroyRecognizer()
                    if (_state.value != VoiceState.IDLE && _state.value != VoiceState.TTS_PLAYING) {
                        requestState(VoiceState.WAKEWORD, force = true)
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
            if (text.isNotEmpty()) {
                // Always-on name detection on partial results too
                checkForNameAndVibrate(text)
                if (_state.value == VoiceState.DEAF_CAPTION) _liveCaptions.value = text
                _results.tryEmit(VoiceResult.Partial(text))
            }
        }
    }

    private fun stopAndDestroyRecognizer() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    // --- Audio Focus ---

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "AUDIO_FOCUS change=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent audio focus loss (-1) -> Transition to IDLE
                if (_state.value != VoiceState.IDLE) {
                    Log.d(TAG, "Permanent audio focus loss (-1) -> Transitioning to IDLE")
                    requestState(VoiceState.IDLE, force = true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Transient focus loss (-2, -3) ignored to preserve active listening state
                // This prevents Live Captions from being killed on screen touches or audio notifications
                Log.d(TAG, "Transient focus loss ($focusChange) ignored to preserve active listening state")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained")
            }
        }
    }

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

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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
}
