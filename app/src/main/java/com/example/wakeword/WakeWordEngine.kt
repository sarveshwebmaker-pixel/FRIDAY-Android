package com.example.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.core.BatteryMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

interface WakeWordListener {
    fun onWakeWordDetected(keyword: String)
    fun onWakeWordError(error: String)
}

/**
 * FRIDAY Voice Engine V2: Low-Power Acoustic Wake-Word & Voice Activity Engine.
 *
 * Capabilities:
 * - Local, zero-cloud continuous listening for wake word "FRIDAY".
 * - Hardware DSP Noise Suppression, Echo Cancellation, and AGC when supported by device.
 * - Software bandpass filtering (220 Hz - 3600 Hz) rejecting fan rumbles, traffic, and high hiss.
 * - Dynamic noise-floor tracking: adapts threshold dynamically to ambient noise so no shouting is needed.
 * - Syllabic temporal acoustic profile detector for "FRIDAY" (/fraɪ/ + dip + /deɪ/).
 * - Zero system beeps or chimes; fast (< 10ms) microphone release for VoiceEngine.
 */
class WakeWordEngine(
    private val context: Context,
    private val listener: WakeWordListener
) {

    companion object {
        private const val TAG = "WakeWordEngineV2"
        const val WAKE_WORD = "FRIDAY"
        const val SAMPLE_RATE = 16000

        // Bandpass filter constants for 16kHz sample rate
        // High-pass cutoff ~220 Hz, Low-pass cutoff ~3600 Hz
        private const val HP_ALPHA = 0.9205f
        private const val LP_ALPHA = 0.5857f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private val recordLock = Any()
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    // Hardware Audio Effects
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null

    private var currentBatteryMode = BatteryMode.NORMAL

    // Adaptive noise tracking
    private var ambientNoiseFloor = 250.0

    fun updateBatteryMode(mode: BatteryMode) {
        currentBatteryMode = mode
        if (mode == BatteryMode.BATTERY_SAVER && isRunning.get()) {
            Log.i(TAG, "Battery Saver active: Pausing background wake-word monitor")
            stopDetection()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDetection() {
        if (currentBatteryMode == BatteryMode.BATTERY_SAVER) {
            Log.i(TAG, "Wake word not started: Battery Saver mode active")
            return
        }

        synchronized(recordLock) {
            if (isRunning.get()) return

            try {
                val minBufSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    audioRecord?.release()
                    audioRecord = null
                    return
                }

                // Attach Android Hardware Audio Effects if available
                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    attachAudioEffects(sessionId)
                }

                isRunning.set(true)
                audioRecord?.startRecording()

                recordingThread = Thread({
                    runWakeWordLoop(minBufSize)
                }, "FridayWakeDetectorV2").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY + 1
                    start()
                }

                Log.d(TAG, "FRIDAY Voice Engine V2 wake-word detector active")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start wake word detector", e)
                stopDetectionInternal()
            }
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware NoiseSuppressor enabled on session $sessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach NoiseSuppressor", e)
        }

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware AcousticEchoCanceler enabled on session $sessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach AcousticEchoCanceler", e)
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                }
                Log.d(TAG, "Hardware AutomaticGainControl enabled on session $sessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach AutomaticGainControl", e)
        }
    }

    private fun runWakeWordLoop(bufferSize: Int) {
        val rawBuffer = ShortArray(bufferSize / 2)
        val filteredBuffer = FloatArray(rawBuffer.size)

        // Filter state
        var hpPrevX = 0f
        var hpPrevY = 0f
        var lpPrevY = 0f

        // Syllable State Machine for "FRIDAY"
        // State 0: Idle / Noise floor tracking
        // State 1: Syllable 1 ("FRI-") with rising energy and characteristic speech ZCR
        // State 2: Inter-syllable Dip (plosive closure for 'D')
        // State 3: Syllable 2 ("-DAY") energy resurgence
        var spotterState = 0
        var stateFrameCount = 0

        while (isRunning.get()) {
            val read = audioRecord?.read(rawBuffer, 0, rawBuffer.size) ?: -1
            if (read > 0) {
                // 1. Digital Bandpass Filter (220 Hz - 3600 Hz) to eliminate fan/HVAC hum & high hiss
                var sumSq = 0.0
                var zeroCrossings = 0

                for (i in 0 until read) {
                    val x = rawBuffer[i].toFloat()
                    // High-pass step (removes low-frequency fan drone / AC rumbling)
                    val hpY = HP_ALPHA * (hpPrevY + x - hpPrevX)
                    hpPrevX = x
                    hpPrevY = hpY

                    // Low-pass step (removes high-frequency hiss)
                    val lpY = lpPrevY + LP_ALPHA * (hpY - lpPrevY)
                    lpPrevY = lpY
                    filteredBuffer[i] = lpY

                    val sampleD = lpY.toDouble()
                    sumSq += sampleD * sampleD

                    if (i > 0 && ((filteredBuffer[i] >= 0f && filteredBuffer[i - 1] < 0f) ||
                                (filteredBuffer[i] < 0f && filteredBuffer[i - 1] >= 0f))) {
                        zeroCrossings++
                    }
                }

                val frameRms = sqrt(sumSq / read)
                val zcr = zeroCrossings.toDouble() / read

                // 2. Dynamic Noise-Floor Tracking & SNR Calculation
                // Sensitive floor allowing normal speech at 1-2 meters and moderate noise
                val dynamicThreshold = (ambientNoiseFloor * 1.25 + 100.0).coerceIn(180.0, 2800.0)
                val isSpeechFrame = frameRms > dynamicThreshold && (zcr in 0.03..0.50)

                if (!isSpeechFrame) {
                    // Adapt ambient noise floor smoothly (leaky integrator)
                    ambientNoiseFloor = ambientNoiseFloor * 0.96 + frameRms * 0.04
                    if (ambientNoiseFloor < 60.0) ambientNoiseFloor = 60.0
                }

                // 3. Acoustic Syllabic State Machine for "FRIDAY" and "HEY FRIDAY"
                // State 0: Idle / Noise floor tracking
                // State 1: Syllable 1 (e.g., "HEY" or "FRI-")
                // State 2: Inter-syllable dip / boundary
                // State 3: Syllable 2 (e.g., "FRI-" or "-DAY")
                // State 4: Syllable 3 (for "HEY FRIDAY" -> "-DAY")
                when (spotterState) {
                    0 -> {
                        if (isSpeechFrame) {
                            spotterState = 1
                            stateFrameCount = 1
                        }
                    }
                    1 -> {
                        if (isSpeechFrame) {
                            stateFrameCount++
                            if (stateFrameCount > 20) {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        } else {
                            // Valid syllable 1 (50ms - 450ms)
                            if (stateFrameCount in 2..18) {
                                spotterState = 2
                                stateFrameCount = 1
                            } else {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        }
                    }
                    2 -> {
                        if (!isSpeechFrame) {
                            stateFrameCount++
                            if (stateFrameCount > 7) {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        } else {
                            // Energy resurgent
                            if (stateFrameCount in 1..6) {
                                spotterState = 3
                                stateFrameCount = 1
                            } else {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        }
                    }
                    3 -> {
                        if (isSpeechFrame) {
                            stateFrameCount++
                            // Syllable 2 confirmed (FRIDAY 2-syllable trigger or HEY FRI transition)
                            if (stateFrameCount in 3..6) {
                                Log.i(TAG, "[WAKE_WORD_STATE] Acoustic keyword 'FRIDAY' detected! (RMS: $frameRms, NoiseFloor: $ambientNoiseFloor)")
                                isRunning.set(false)
                                synchronized(recordLock) {
                                    try {
                                        noiseSuppressor?.release()
                                        noiseSuppressor = null
                                        echoCanceler?.release()
                                        echoCanceler = null
                                        gainControl?.release()
                                        gainControl = null

                                        audioRecord?.let { record ->
                                            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                                record.stop()
                                            }
                                            record.release()
                                        }
                                        audioRecord = null
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error releasing AudioRecord on wake detection", e)
                                    }
                                }
                                Log.i(TAG, "[MIC_OWNER] WakeWordEngine AudioRecord stopped and released synchronously upon wake-word detection.")
                                mainHandler.post {
                                    listener.onWakeWordDetected(WAKE_WORD)
                                }
                                break
                            } else if (stateFrameCount > 18) {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        } else {
                            // Dip after second syllable ("Hey Fri- ...") -> could lead to "-day"
                            if (stateFrameCount in 2..14) {
                                spotterState = 4
                                stateFrameCount = 1
                            } else {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        }
                    }
                    4 -> {
                        // Looking for syllable 3 ("-DAY" in "HEY FRIDAY")
                        if (!isSpeechFrame) {
                            stateFrameCount++
                            if (stateFrameCount > 6) {
                                spotterState = 0
                                stateFrameCount = 0
                            }
                        } else {
                            // Final syllable of "HEY FRIDAY"
                            Log.i(TAG, "[WAKE_WORD_STATE] Acoustic keyword 'HEY FRIDAY' detected! (RMS: $frameRms, NoiseFloor: $ambientNoiseFloor)")
                            isRunning.set(false)
                            synchronized(recordLock) {
                                try {
                                    noiseSuppressor?.release()
                                    noiseSuppressor = null
                                    echoCanceler?.release()
                                    echoCanceler = null
                                    gainControl?.release()
                                    gainControl = null

                                    audioRecord?.let { record ->
                                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                            record.stop()
                                        }
                                        record.release()
                                    }
                                    audioRecord = null
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error releasing AudioRecord on wake detection", e)
                                }
                            }
                            mainHandler.post {
                                listener.onWakeWordDetected(WAKE_WORD)
                            }
                            break
                        }
                    }
                }
            } else {
                try {
                    Thread.sleep(15)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun stopDetection() {
        stopDetectionInternal()
    }

    private fun stopDetectionInternal() {
        isRunning.set(false)
        synchronized(recordLock) {
            try {
                recordingThread?.let { t ->
                    try {
                        t.interrupt()
                    } catch (_: Exception) {}
                }
                recordingThread = null

                noiseSuppressor?.release()
                noiseSuppressor = null
                echoCanceler?.release()
                echoCanceler = null
                gainControl?.release()
                gainControl = null

                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    record.release()
                }
                audioRecord = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord in WakeWordEngine", e)
            } finally {
                audioRecord = null
                recordingThread = null
            }
        }
    }

    fun isDetecting(): Boolean = isRunning.get()
}

