package com.journal.glasses.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.CXRServiceBridge
import com.journal.glasses.protocol.CapsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Glasses-side audio capture using AudioRecord (16kHz, mono, 16-bit).
 * Performs simple VAD (voice activity detection) and sends PCM frames to the phone
 * via CXRServiceBridge.sendMessage on the [CapsProtocol.CHANNEL_AUDIO_STREAM] channel.
 *
 * VAD: silence for ~1.5s triggers end-of-speech (isFinal=true).
 */
class GlassesAudioCapture(
    private val cxrBridge: CXRServiceBridge,
) {
    companion object {
        private const val TAG = "GlassesAudioCapture"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** 10ms frame at 16kHz/mono/16bit = 320 bytes. */
        private const val FRAME_SIZE_BYTES = 320

        /** Silence threshold for VAD (RMS). */
        private const val SILENCE_THRESHOLD = 500

        /** Consecutive silent frames to trigger end-of-speech (~1.5s = 150 frames). */
        private const val SILENCE_FRAME_COUNT = 150
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecordingActive = false

    private var frameSeq = 0
    private var silenceCounter = 0

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecordingActive) return
        frameSeq = 0
        silenceCounter = 0

        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .build()

        recorder?.startRecording()
        isRecordingActive = true
        _isRecording.value = true

        recordingThread = Thread { captureLoop() }
        recordingThread?.start()
        Log.d(TAG, "startRecording: started")
    }

    fun stopRecording() {
        if (!isRecordingActive) return
        isRecordingActive = false
        recordingThread?.join(2000)
        recorder?.stop()
        recorder?.release()
        recorder = null
        _isRecording.value = false

        // Send final frame if any silence was building
        sendAudioFrame(ByteArray(0), isFinal = true)
        Log.d(TAG, "stopRecording: stopped, total frames=$frameSeq")
    }

    private fun captureLoop() {
        val buffer = ByteArray(FRAME_SIZE_BYTES)
        try {
            while (isRecordingActive) {
                val read = recorder?.read(buffer, 0, FRAME_SIZE_BYTES) ?: -1
                if (read <= 0) continue

                val frameData = if (read == FRAME_SIZE_BYTES) buffer.clone() else buffer.copyOf(read)
                val isSilent = isSilent(frameData)

                if (isSilent) {
                    silenceCounter++
                    if (silenceCounter >= SILENCE_FRAME_COUNT) {
                        // End of speech detected
                        sendAudioFrame(ByteArray(0), isFinal = true)
                        isRecordingActive = false
                        _isRecording.value = false
                        Log.d(TAG, "VAD: end-of-speech detected")
                        break
                    }
                    // Don't send silent frames
                    continue
                }

                silenceCounter = 0
                sendAudioFrame(frameData, isFinal = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureLoop error", e)
        }
    }

    /**
     * Simple RMS-based silence detection.
     */
    private fun isSilent(data: ByteArray): Boolean {
        var sum = 0L
        for (i in 0 until data.size - 1 step 2) {
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toLong()
        }
        val rms = kotlin.math.sqrt(sum.toDouble() / (data.size / 2))
        return rms < SILENCE_THRESHOLD
    }

    private fun sendAudioFrame(pcmChunk: ByteArray, isFinal: Boolean) {
        try {
            val caps = Caps().apply {
                write(frameSeq++)
                write(pcmChunk)
                write(isFinal)
            }
            cxrBridge.sendMessage(CapsProtocol.CHANNEL_AUDIO_STREAM, caps)
        } catch (e: Exception) {
            Log.e(TAG, "sendAudioFrame failed", e)
        }
    }

    fun release() {
        if (isRecordingActive) stopRecording()
    }
}
