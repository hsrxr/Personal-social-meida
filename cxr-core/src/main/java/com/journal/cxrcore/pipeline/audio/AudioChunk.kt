package com.journal.cxrcore.pipeline.audio

/**
 * Audio data chunk received from glasses.
 *
 * @param pcmData raw PCM 16-bit mono 16kHz data.
 * @param timestamp epoch ms when first sample was received.
 * @param durationMs approximate duration of this chunk in ms.
 */
data class AudioChunk(
    val pcmData: ByteArray,
    val timestamp: Long,
    val durationMs: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false
        return pcmData.contentEquals(other.pcmData) &&
            timestamp == other.timestamp &&
            durationMs == other.durationMs
    }

    override fun hashCode(): Int =
        pcmData.contentHashCode() * 31 + timestamp.hashCode()
}
