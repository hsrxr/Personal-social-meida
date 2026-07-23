package com.journal.cxrcore.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Utility for building WAV files from raw PCM data.
 *
 * PCM format: 16kHz, mono, 16-bit.
 * Extracted from Sample's [AudioUsageViewModel.buildWavFromPcm].
 */
object WavBuilder {

    private const val SAMPLE_RATE = 16_000
    private const val CHANNELS: Short = 1
    private const val BITS_PER_SAMPLE: Short = 16

    /**
     * Converts PCM bytes to a WAV byte array.
     *
     * @param pcmBytes raw PCM data (16kHz, mono, 16-bit little-endian).
     * @return complete WAV file bytes (44-byte header + PCM data).
     */
    fun pcmToWavBytes(pcmBytes: ByteArray): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36

        val header = buildWavHeader(totalAudioLen, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, byteRate)
        val output = ByteArrayOutputStream((44 + pcmBytes.size).toInt())
        output.write(header)
        output.write(pcmBytes)
        return output.toByteArray()
    }

    /**
     * Converts a PCM file to a WAV file on disk.
     *
     * @param pcmFile source PCM file.
     * @param wavFile destination WAV file.
     * @param pcmSize number of PCM bytes to read (use file length if known to be pure PCM).
     */
    fun pcmFileToWavFile(pcmFile: File, wavFile: File, pcmSize: Long = pcmFile.length()) {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        FileOutputStream(wavFile, false).use { wavOut ->
            writeWavHeader(
                out = wavOut,
                totalAudioLen = pcmSize,
                sampleRate = SAMPLE_RATE,
                channels = CHANNELS,
                bitsPerSample = BITS_PER_SAMPLE,
                byteRate = byteRate,
            )
            FileInputStream(pcmFile).use { pcmIn ->
                val buffer = ByteArray(4096)
                var read = pcmIn.read(buffer)
                while (read > 0) {
                    wavOut.write(buffer, 0, read)
                    read = pcmIn.read(buffer)
                }
            }
        }
    }

    private fun buildWavHeader(
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short,
        byteRate: Int,
    ): ByteArray {
        val header = ByteArray(44)
        writeWavHeaderBytes(header, totalAudioLen, sampleRate, channels, bitsPerSample, byteRate)
        return header
    }

    private fun writeWavHeader(
        out: OutputStream,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short,
        byteRate: Int,
    ) {
        val header = buildWavHeader(totalAudioLen, sampleRate, channels, bitsPerSample, byteRate)
        out.write(header, 0, 44)
    }

    private fun writeWavHeaderBytes(
        header: ByteArray,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short,
        byteRate: Int,
    ) {
        val totalDataLen = totalAudioLen + 36
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE fmt
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // PCM sub-chunk size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = ((channels * bitsPerSample / 8) and 0xff).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // data
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
    }
}
