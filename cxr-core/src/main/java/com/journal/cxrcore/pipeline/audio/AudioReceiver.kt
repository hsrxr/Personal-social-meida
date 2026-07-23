package com.journal.cxrcore.pipeline.audio

import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.IAudioStreamCbk
import com.journal.cxrcore.app.JournalApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream

/**
 * Receives audio stream from glasses and emits [AudioChunk] via [audioChunkFlow].
 *
 * Usage:
 * - Call [startListening] to begin receiving audio PCM data.
 * - Collect [audioChunkFlow] for VAD-trimmed audio chunks.
 * - Call [stopListening] to stop the stream.
 * - Call [release] when done.
 */
class AudioReceiver {

    companion object {
        private const val TAG = "AudioReceiver"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
    }

    private val _audioChunkFlow = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 2)
    val audioChunkFlow: SharedFlow<AudioChunk> = _audioChunkFlow.asSharedFlow()

    private var isActive = false
    private var initialized = false

    // Accumulated PCM buffer for the current utterance (VAD-gated on glasses side)
    private val pcmBuffer = ByteArrayOutputStream()
    private var chunkStartTimeMs: Long = 0L

    private val audioCallback = object : IAudioStreamCbk {
        override fun onAudioReceived(data: ByteArray?, offset: Int, length: Int) {
            if (data == null || length <= 0) return
            val safeOffset = if (offset in 0 until data.size) offset else 0
            val maxAvailable = data.size - safeOffset
            val safeLength = when {
                length in 1..maxAvailable -> length
                maxAvailable > 0 -> maxAvailable
                else -> data.size
            }
            if (safeLength <= 0) return

            synchronized(pcmBuffer) {
                if (chunkStartTimeMs == 0L) {
                    chunkStartTimeMs = System.currentTimeMillis()
                }
                pcmBuffer.write(data, safeOffset, safeLength)
            }
        }

        override fun onAudioError(errorCode: Int, errorInfo: String?) {
            Log.e(TAG, "onAudioError: code=$errorCode info=${errorInfo ?: ""}")
            isActive = false
        }

        override fun onAudioStreamStateChanged(started: Boolean) {
            Log.d(TAG, "onAudioStreamStateChanged: started=$started")
            isActive = started

            // When stream stops (VAD end-of-speech), emit accumulated chunk
            if (!started) {
                synchronized(pcmBuffer) {
                    val pcmBytes = pcmBuffer.toByteArray()
                    pcmBuffer.reset()
                    val startMs = chunkStartTimeMs
                    chunkStartTimeMs = 0L

                    if (pcmBytes.isNotEmpty()) {
                        val durationMs = (pcmBytes.size * 1000L / (SAMPLE_RATE * 2)).toInt()
                        _audioChunkFlow.tryEmit(
                            AudioChunk(
                                pcmData = pcmBytes,
                                timestamp = startMs,
                                durationMs = durationMs,
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Registers the audio callback and starts the audio stream.
     */
    fun startListening() {
        if (initialized && isActive) return
        val link = readyLink() ?: return
        if (!initialized) {
            link.setCXRAudioCbk(audioCallback)
            initialized = true
        }
        link.startAudioStream(1)
        isActive = true
    }

    /**
     * Stops the audio stream.
     */
    fun stopListening() {
        if (!initialized) return
        val link = readyLink() ?: return
        link.stopAudioStream()
        isActive = false
    }

    /**
     * Releases audio resources. Stops stream if active.
     */
    fun release() {
        if (initialized) {
            runCatching {
                val link = readyLink()
                link?.stopAudioStream()
            }
            initialized = false
        }
        isActive = false
    }

    private fun readyLink(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()
}
