package com.journal.cxrcore.pipeline.photo

import android.graphics.BitmapFactory
import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.IImageStreamCbk
import com.journal.cxrcore.app.JournalApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Encapsulates glass photo capture with JPEG byte delivery via [SharedFlow].
 *
 * Usage:
 * - Call [capture] to trigger a photo.
 * - Collect [photoFlow] for JPEG results.
 * - Call [release] when done (clears the SDK callback).
 */
class PhotoPipeline {

    companion object {
        private const val TAG = "PhotoPipeline"
        private const val DEFAULT_WIDTH = 2048
        private const val DEFAULT_HEIGHT = 1536
        private const val DEFAULT_QUALITY = 85
    }

    private val _photoFlow = MutableSharedFlow<PhotoCapture>(extraBufferCapacity = 1)
    val photoFlow: SharedFlow<PhotoCapture> = _photoFlow.asSharedFlow()

    private val imageCallback = object : IImageStreamCbk {
        override fun onImageReceived(data: ByteArray?) {
            if (data == null || data.isEmpty()) {
                Log.w(TAG, "onImageReceived: empty data")
                return
            }
            val capture = PhotoCapture(
                jpegBytes = data,
                timestamp = System.currentTimeMillis(),
            )
            _photoFlow.tryEmit(capture)
        }

        override fun onImageError(code: Int, msg: String?) {
            Log.e(TAG, "onImageError: code=$code msg=${msg ?: ""}")
        }
    }

    private var initialized = false

    /**
     * Registers the image callback with the shared CXRLink.
     * Call once after session is built.
     */
    fun init() {
        if (initialized) return
        val link = readyLink() ?: return
        link.setCXRImageCbk(imageCallback)
        initialized = true
    }

    /**
     * Triggers a photo capture on the glasses.
     * @param width  image width (default 2048 for AI analysis).
     * @param height image height (default 1536).
     * @param quality JPEG quality 0-100 (default 85).
     */
    fun capture(
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        quality: Int = DEFAULT_QUALITY,
    ) {
        val link = readyLink() ?: return
        link.takePhoto(width, height, quality)
    }

    /**
     * Clears the SDK image callback. Call when pipeline is no longer needed.
     */
    fun release() {
        if (!initialized) return
        val link = readyLink() ?: return
        runCatching {
            link.setCXRImageCbk(object : IImageStreamCbk {
                override fun onImageReceived(data: ByteArray?) {}
                override fun onImageError(code: Int, msg: String?) {}
            })
        }
        initialized = false
    }

    private fun readyLink(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()
}

/**
 * One captured photo as JPEG bytes with timestamp.
 */
data class PhotoCapture(
    val jpegBytes: ByteArray,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoCapture) return false
        return jpegBytes.contentEquals(other.jpegBytes) && timestamp == other.timestamp
    }

    override fun hashCode(): Int = jpegBytes.contentHashCode() * 31 + timestamp.hashCode()
}
