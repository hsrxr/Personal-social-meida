package com.journal.cxrcore.pipeline.audio

import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.IAudioStreamCbk
import com.journal.cxrcore.app.JournalApplication
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * Uses the SDK's built-in audio streaming API (mirrors Sample's AudioUsageViewModel).
 *
 * Calls [CXRLink.startAudioStream] / [stopAudioStream], receives PCM via
 * [IAudioStreamCbk.onAudioReceived], accumulates into a buffer, and emits
 * a complete [AudioChunk] when stopped.
 */
class AudioPipeline {

    companion object {
        private const val TAG = "AudioPipeline"
        private const val SAMPLE_RATE = 16_000
    }

    private val _audioChunkFlow = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 2)
    val audioChunkFlow: SharedFlow<AudioChunk> = _audioChunkFlow.asSharedFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val pcmBuffer = ByteArrayOutputStream()
    private var chunkStartTimeMs: Long = 0L
    private var initialized = false

    private val audioCallback = object : IAudioStreamCbk {
        override fun onAudioReceived(data: ByteArray?, offset: Int, length: Int) {
            if (data == null || length <= 0) {
                Log.w(TAG, "onAudioReceived: empty data")
                return
            }
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
            _isActive.value = false
        }

        override fun onAudioStreamStateChanged(started: Boolean) {
            Log.d(TAG, "onAudioStreamStateChanged: started=$started")
            _isActive.value = started
        }
    }

    /**
     * Registers audio callback and checks glass microphone permission.
     * Must be called after session is built, before [start].
     */
    fun init() {
        if (initialized) return
        val link = readyLink() ?: run {
            Log.w(TAG, "init: link not ready")
            return
        }
        link.setCXRAudioCbk(audioCallback)
        _permissionGranted.value = AuthorizationHelper.hasGlassPermission(GlassPermission.MICROPHONE)
        initialized = true
        Log.d(TAG, "AudioPipeline initialized, micPermission=${_permissionGranted.value}")
    }

    /**
     * Starts audio capture from glasses. The audio data accumulates in the internal
     * buffer and is emitted as a complete [AudioChunk] when [stop] is called.
     */
    fun start() {
        val link = readyLink() ?: return
        if (!_permissionGranted.value) {
            Log.w(TAG, "start: microphone permission not granted on glasses")
            return
        }
        synchronized(pcmBuffer) {
            pcmBuffer.reset()
            chunkStartTimeMs = System.currentTimeMillis()
        }
        val result = link.startAudioStream(1) // codecType=1 for PCM
        Log.d(TAG, "startAudioStream: result=$result")
        _isActive.value = result
    }

    /**
     * Stops audio capture and emits the accumulated PCM as an [AudioChunk].
     */
    fun stop() {
        val link = readyLink() ?: return
        link.stopAudioStream()
        _isActive.value = false

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
            } else {
                Log.w(TAG, "stop: no audio data received")
            }
        }
    }

    /**
     * Releases the audio callback by setting a no-op callback.
     */
    fun release() {
        val link = readyLink() ?: return
        runCatching { link.stopAudioStream() }
        _isActive.value = false
        initialized = false
    }

    private fun readyLink(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()
}
