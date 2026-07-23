package com.journal.cxrcore.pipeline.audio

import android.util.Log
import com.rokid.cxr.Caps
import com.journal.cxrcore.command.CustomCmdRouter
import com.journal.cxrcore.command.CapsProtocol
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream

/**
 * Receives audio PCM frames from glasses via the "audio_stream" custom command channel.
 * Glasses side sends: Caps(seq: Int, pcmChunk: byte[], isFinal: Boolean).
 *
 * On isFinal=true (VAD end-of-speech detected on glasses), the accumulated PCM
 * buffer is emitted as a complete [AudioChunk].
 *
 * Usage:
 * - Call [init] after CustomCmdRouter.init() and session is built.
 * - Collect [audioChunkFlow] for complete utterances.
 * - Call [release] when done.
 */
class AudioReceiver {

    companion object {
        private const val TAG = "AudioReceiver"
        private const val SAMPLE_RATE = 16_000
    }

    private val _audioChunkFlow = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 2)
    val audioChunkFlow: SharedFlow<AudioChunk> = _audioChunkFlow.asSharedFlow()

    private val pcmBuffer = ByteArrayOutputStream()
    private var chunkStartTimeMs: Long = 0L
    private var subscribed = false

    /**
     * Subscribes to the audio_stream channel via CustomCmdRouter.
     * CustomCmdRouter.init() must have been called first.
     */
    fun init() {
        if (subscribed) return
        CustomCmdRouter.subscribe(CapsProtocol.CHANNEL_AUDIO_STREAM) { payload ->
            handleAudioFrame(payload)
        }
        subscribed = true
        Log.d(TAG, "AudioReceiver subscribed to ${CapsProtocol.CHANNEL_AUDIO_STREAM}")
    }

    /**
     * Unsubscribes from the audio channel.
     */
    fun release() {
        if (subscribed) {
            CustomCmdRouter.unsubscribe(CapsProtocol.CHANNEL_AUDIO_STREAM)
            subscribed = false
        }
        synchronized(pcmBuffer) { pcmBuffer.reset() }
    }

    // --- Internal ---

    /**
     * Parses an audio stream Caps message:
     * Fields: [0] seq (Int), [1] pcmChunk (ByteArray), [2] isFinal (Boolean)
     */
    private fun handleAudioFrame(payload: ByteArray) {
        val caps = Caps.fromBytes(payload)
        if (caps.size() < 3) {
            Log.w(TAG, "handleAudioFrame: invalid Caps size=${caps.size()}")
            return
        }

        val seq = caps.at(0).int
        val pcmChunk = caps.at(1).binary
        val isFinal = caps.at(2).int != 0  // uint32 boolean

        if (pcmChunk != null && pcmChunk.data != null && pcmChunk.length > 0) {
            synchronized(pcmBuffer) {
                if (chunkStartTimeMs == 0L) {
                    chunkStartTimeMs = System.currentTimeMillis()
                }
                pcmBuffer.write(pcmChunk.data, 0, pcmChunk.length)
            }
        }

        Log.v(TAG, "Audio frame: seq=$seq, pcmSize=${pcmChunk?.length ?: 0}, isFinal=$isFinal")

        if (isFinal) {
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
                    Log.d(TAG, "Emitted AudioChunk: ${pcmBytes.size} bytes, ${durationMs}ms")
                }
            }
        }
    }
}
