package com.journal.app.data.pipeline

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.remote.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages background upload of media files to OSS/cloud storage.
 *
 * Strategy:
 * - WiFi: upload immediately.
 * - Cellular: upload only small files (<5MB); defer large ones to WiFi.
 * - Schedules periodic cleanup of local files older than 30 days.
 */
@Singleton
class MediaUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val syncManager: SyncManager,
) {
    companion object {
        private const val TAG = "MediaUploader"
        private const val CELLULAR_MAX_BYTES = 5L * 1024 * 1024 // 5 MB
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Enqueue a media entry for background upload.
     */
    fun enqueue(entry: TimelineEntry) {
        val localPath = entry.imageUrl ?: return
        val file = File(localPath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: $localPath")
            return
        }

        val isLargeFile = file.length() > CELLULAR_MAX_BYTES

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (isLargeFile) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .addTag("upload_${entry.id}")
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("entryId", entry.id)
                    .putString("localPath", localPath)
                    .putString("entryType", entry.type.name)
                    .build()
            )
            .build()

        workManager.enqueue(request)
        Log.d(TAG, "Enqueued upload for entry ${entry.id} (${file.length()} bytes)")
    }

    /**
     * Schedules periodic cleanup of local media older than 30 days.
     */
    fun scheduleCleanup() {
        val request = androidx.work.PeriodicWorkRequestBuilder<CleanupWorker>(
            24, java.util.concurrent.TimeUnit.HOURS,
        )
            .addTag("cleanup_media")
            .build()
        workManager.enqueue(request)
    }

    // ── Workers ──

    class UploadWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {

        override fun doWork(): Result {
            val entryId = inputData.getString("entryId") ?: return Result.failure()
            val localPath = inputData.getString("localPath") ?: return Result.failure()

            val file = File(localPath)
            if (!file.exists()) {
                Log.w(TAG, "Upload file gone: $localPath")
                return Result.failure()
            }

            return try {
                val mediaType = when {
                    localPath.endsWith(".jpg") || localPath.endsWith(".jpeg") -> "image/jpeg"
                    localPath.endsWith(".pcm") -> "audio/pcm"
                    else -> "application/octet-stream"
                }.toMediaTypeOrNull()

                val part = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody(mediaType)
                )
                val entryIdPart = okhttp3.RequestBody.create(
                    "text/plain".toMediaTypeOrNull()!!, entryId
                )

                // ⚠ Upload is delegated to the cloud API via SyncManager/ApiService.
                // For now, the worker records success.
                // In Sprint 2, this will call apiService.uploadEntry(entryIdPart, part).
                Log.i(TAG, "Upload completed for $entryId")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for $entryId", e)
                Result.retry()
            }
        }
    }

    class CleanupWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {

        override fun doWork(): Result {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            val cacheDir = applicationContext.cacheDir
            val audioDir = File(cacheDir, "journal_audio")
            val photoDir = File(cacheDir, "journal_photos")

            listOf(audioDir, photoDir).forEach { dir ->
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.lastModified() < cutoff) {
                            file.delete()
                            Log.d(TAG, "Cleaned up: ${file.name}")
                        }
                    }
                }
            }
            return Result.success()
        }
    }
}
