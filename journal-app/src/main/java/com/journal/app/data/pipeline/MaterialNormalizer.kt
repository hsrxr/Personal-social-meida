package com.journal.app.data.pipeline

import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.entity.TimelineEntryEntity

/**
 * Timestamp normalization and deduplication logic for ingested materials.
 */
object MaterialNormalizer {

    /** Tolerance window for duplicate detection (ms). */
    private const val DEDUP_WINDOW_MS = 2_000L

    /**
     * Returns true if a duplicate entry already exists within the dedup window.
     * A duplicate is defined as: same type + same source + timestamp within [DEDUP_WINDOW_MS].
     */
    suspend fun deduplicate(entity: TimelineEntryEntity, entryDao: EntryDao): Boolean {
        return entryDao.existsByTimestamp(
            ts = entity.timestamp,
            type = entity.type,
            source = entity.source,
        )
    }

    /**
     * Normalizes a timestamp to the nearest reasonable bin.
     * Currently a pass-through; can be extended to handle clock drift correction.
     */
    fun normalizeTimestamp(rawMs: Long): Long = rawMs
}
