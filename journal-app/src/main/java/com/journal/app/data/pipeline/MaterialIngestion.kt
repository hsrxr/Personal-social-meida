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
import com.journal.cxrcore.pipeline.audio.AudioPipeline
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
    private val audioPipeline: AudioPipeline,
    private val photoPipeline: PhotoPipeline,
    private val commandChannel: CommandChannel,
    private val entryDao: EntryDao,
    private val journalDao: JournalDao,
    private val mediaUploader: MediaUploader,
    // AudioPipeline is used for both receiving audio chunks AND triggering start/stop
    // PhotoPipeline is used for both receiving photo captures AND triggering capture
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
            audioPipeline.audioChunkFlow
                .catch { Log.e(TAG, "Audio flow error", it) }
                .collect { chunk ->
                    try {
                        processAudioChunk(chunk)
                    } catch (e: Exception) {
                        Log.e(TAG, "processAudioChunk failed", e)
                    }
                }
        }
    }

    private suspend fun processAudioChunk(chunk: AudioChunk) {
        val now = System.currentTimeMillis()
        val date = dateFromEpoch(chunk.timestamp)
        val entryId = UUID.randomUUID().toString()
        val localPath = savePcmToFile(entryId, chunk.pcmData)

        Log.i(TAG, "processAudioChunk: id=$entryId date=$date path=$localPath durationMs=${chunk.durationMs} bytes=${chunk.pcmData.size}")

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
            Log.i(TAG, "Audio entry inserted: id=$entryId date=$date")
            journalDao.refreshEntryCount(date)
            mediaUploader.enqueue(entity.toDomain())
        } else {
            Log.w(TAG, "Audio entry deduplicated (skipped): id=$entryId")
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

        Log.i(TAG, "processPhoto: id=$entryId date=$date path=$localPath jpegBytes=${capture.jpegBytes.size}")

        if (localPath == null) {
            Log.e(TAG, "Skipping photo insert: file save failed for id=$entryId")
            return
        }

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
            Log.i(TAG, "Photo entry inserted: id=$entryId date=$date path=$localPath")
            journalDao.refreshEntryCount(date)
            mediaUploader.enqueue(entity.toDomain())
        } else {
            Log.w(TAG, "Photo entry deduplicated (skipped): id=$entryId")
        }
    }

    // ── Key events source ──

    private fun collectKeyEvents() {
        scope.launch {
            commandChannel.inboundFlow
                .catch { Log.e(TAG, "Key event flow error", it) }
                .collect { event ->
                    try {
                        processKeyEvent(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "processKeyEvent failed", e)
                    }
                }
        }
    }

    private suspend fun processKeyEvent(event: JournalEvent) {
        val now = System.currentTimeMillis()
        val date = dateFromEpoch(event.timestamp)

        val entryType = when (event.eventType) {
            "take_photo" -> {
                // Auto-trigger photo capture from glasses camera button
                Log.i(TAG, "Auto-capturing photo from take_photo event")
                photoPipeline.capture()
                "PHOTO"
            }
            "moment_mark" -> "MOMENT_MARK"
            "agent_talk_start" -> {
                // Auto-start audio streaming via SDK API
                Log.i(TAG, "Auto-starting audio from agent_talk_start")
                audioPipeline.start()
                "AGENT_DIALOG"
            }
            "agent_talk_stop" -> {
                // Auto-stop audio streaming
                Log.i(TAG, "Auto-stopping audio from agent_talk_stop")
                audioPipeline.stop()
                "AGENT_DIALOG"
            }
            "quick_note_start" -> {
                Log.i(TAG, "Auto-starting audio from quick_note_start")
                audioPipeline.start()
                "AUDIO"
            }
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
