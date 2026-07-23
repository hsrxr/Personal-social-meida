package com.journal.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.entity.toDomain
import com.journal.app.data.local.entity.toEntity
import com.journal.app.data.remote.dto.SyncEntryDto
import com.journal.app.data.remote.dto.SyncPayload
import com.journal.app.data.remote.dto.TagDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages offline/online synchronization between local Room DB and cloud API.
 *
 * Strategy:
 * - Push: upload PENDING entries to cloud when online.
 * - Pull: fetch remote changes since last sync.
 * - Offline queue: entries are created with syncStatus=PENDING, pushed when online.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    private val mutex = Mutex()
    private var lastSyncAt: Long = 0L

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getActiveNetwork()?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun pushPending(): Int {
        if (!isOnline()) return 0
        mutex.withLock {
            val pending = entryDao.getPendingSync()
            if (pending.isEmpty()) return 0

            val syncEntries = pending.map { entity ->
                val domain = entity.toDomain()
                SyncEntryDto(
                    id = entity.id,
                    date = entity.date,
                    timestamp = entity.timestamp,
                    type = entity.type,
                    source = entity.source,
                    transcription = entity.transcription,
                    noteText = entity.noteText,
                    locationName = entity.locationName,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    isStarred = entity.isStarred,
                    tags = domain.tags.map { TagDto(it.name, it.type.name) },
                )
            }

            val payload = SyncPayload(
                entries = syncEntries,
                lastSyncAt = lastSyncAt,
            )

            try {
                // ⚠ In Sprint 2, call: apiService.pushEntries(payload)
                // For now, mark as SYNCED locally.
                pending.forEach { entity ->
                    entryDao.updateSyncStatus(entity.id, "SYNCED")
                }
                lastSyncAt = System.currentTimeMillis()
                Log.i(TAG, "Pushed ${pending.size} entries")
                return pending.size
            } catch (e: Exception) {
                Log.e(TAG, "Push failed", e)
                return 0
            }
        }
    }

    suspend fun pullUpdates() {
        if (!isOnline()) return
        mutex.withLock {
            try {
                // ⚠ In Sprint 2, call: val response = apiService.pullUpdates(lastSyncAt)
                // For now, no-op.
                lastSyncAt = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "Pull failed", e)
            }
        }
    }
}
