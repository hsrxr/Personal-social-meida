package com.journal.app.data.pipeline

import android.content.Context
import android.util.Log
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.entity.TimelineEntryEntity
import com.journal.app.data.local.entity.toDomain
import com.journal.cxrcore.command.CommandChannel
import com.journal.cxrcore.command.JournalEvent
import com.journal.cxrcore.pipeline.audio.AudioChunk
import com.journal.cxrcore.pipeline.audio.AudioReceiver
import com.journal.cxrcore.pipeline.photo.PhotoCapture
import com.journal.cxrcore.pipeline.photo.PhotoPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialIngestion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioReceiver: AudioReceiver,
    private val photoPipeline: PhotoPipeline,
    private val commandChannel: CommandChannel,
    private val entryDao: EntryDao,
    private val journalDao: JournalDao,
    private val mediaUploader: MediaUploader,
) {
    companion object {
        private const val TAG = "MaterialIngestion"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        collectAudioChunks()
        collectPhotos()
        collectKeyEvents()
        Log.i(TAG, "MaterialIngestion started: listening to 3 data sources")
    }

    fun stop() {
        started = false
        Log.i(TAG, "MaterialIngestion stopped")
    }

    // ── Audio source ──

    private fun collectAudioChunks() {
        scope.launch {
            audioReceiver.audioChunkFlow
                .catch { Log.e(TAG, "Audio flow error", it) }
                .collect { chunk ->
                    processAudioChunk(chunk)
                }
        }
    }

    private suspend fun processAudioChunk(chunk: AudioChunk) {
        val now = System.currentTimeMillis()
        val date = dateFromEpoch(chunk.timestamp)
        val entryId = UUID.randomUUID().toString()
        val localPath = savePcmToFile(entryId, chunk.pcmData)

        val entity = TimelineEntryEntity(
            id = entryId,
            date = date,
            timestamp = chunk.timestamp,
            type = "AUDIO",
            source = "GLASSES",
            localPath = localPath,
            durationMs = chunk.durationMs,
            createdAt = now,
        )

        val deduped = MaterialNormalizer.deduplicate(entity, entryDao)
        if (!deduped) {
            entryDao.insert(entity)
            journalDao.refreshEntryCount(date)
            mediaUploader.enqueue(entity.toDomain())
        }
    }

    // ── Photo source ──

    private fun collectPhotos() {
        scope.launch {
            photoPipeline.photoFlow
                .catch { Log.e(TAG, "Photo flow error", it) }
                .collect { capture ->
                    processPhoto(capture)
                }
        }
    }

    private suspend fun processPhoto(capture: PhotoCapture) {
        val now = System.currentTimeMillis()
        val date = dateFromEpoch(capture.timestamp)
        val entryId = UUID.randomUUID().toString()
        val localPath = saveJpegToFile(entryId, capture.jpegBytes)

        val entity = TimelineEntryEntity(
            id = entryId,
            date = date,
            timestamp = capture.timestamp,
            type = "PHOTO",
            source = "GLASSES",
            localPath = localPath,
            createdAt = now,
        )

        val deduped = MaterialNormalizer.deduplicate(entity, entryDao)
        if (!deduped) {
            entryDao.insert(entity)
            journalDao.refreshEntryCount(date)
            mediaUploader.enqueue(entity.toDomain())
        }
    }

    // ── Key events source ──

    private fun collectKeyEvents() {
        scope.launch {
            commandChannel.inboundFlow
                .catch { Log.e(TAG, "Key event flow error", it) }
                .collect { event ->
                    processKeyEvent(event)
                }
        }
    }

    private suspend fun processKeyEvent(event: JournalEvent) {
        val now = System.currentTimeMillis()
        val date = dateFromEpoch(event.timestamp)

        val entryType = when (event.eventType) {
            "moment_mark" -> "MOMENT_MARK"
            "agent_talk_start", "agent_talk_stop" -> "AGENT_DIALOG"
            else -> {
                Log.d(TAG, "Unhandled event type: ${event.eventType}")
                return
            }
        }

        val entity = TimelineEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            timestamp = event.timestamp,
            type = entryType,
            source = "GLASSES",
            noteText = event.metadata.takeIf { it.isNotEmpty() },
            createdAt = now,
        )

        entryDao.insert(entity)
        journalDao.refreshEntryCount(date)
    }

    // ── File I/O helpers ──

    private fun savePcmToFile(entryId: String, pcmData: ByteArray): String? {
        return try {
            val dir = File(context.cacheDir, "journal_audio")
            dir.mkdirs()
            val file = File(dir, "$entryId.pcm")
            FileOutputStream(file).use { it.write(pcmData) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PCM", e)
            null
        }
    }

    private fun saveJpegToFile(entryId: String, jpegBytes: ByteArray): String? {
        return try {
            val dir = File(context.cacheDir, "journal_photos")
            dir.mkdirs()
            val file = File(dir, "$entryId.jpg")
            FileOutputStream(file).use { it.write(jpegBytes) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save JPEG", e)
            null
        }
    }

    private fun dateFromEpoch(epochMs: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            .toLocalDate()
            .toString()
}
