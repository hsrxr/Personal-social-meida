package com.journal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.journal.app.data.local.entity.TimelineEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM timeline_entries WHERE date = :date ORDER BY timestamp ASC")
    fun getEntries(date: String): Flow<List<TimelineEntryEntity>>

    @Query("SELECT * FROM timeline_entries WHERE date BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getEntriesInRange(from: String, to: String): Flow<List<TimelineEntryEntity>>

    @Query("SELECT * FROM timeline_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TimelineEntryEntity?

    @Query("SELECT * FROM timeline_entries WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSync(): List<TimelineEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimelineEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TimelineEntryEntity>)

    @Query("UPDATE timeline_entries SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("UPDATE timeline_entries SET aiAnnotation = :annotation WHERE id = :id")
    suspend fun updateAnnotation(id: String, annotation: String)

    @Query("UPDATE timeline_entries SET isStarred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)

    @Query("DELETE FROM timeline_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM timeline_entries WHERE timestamp = :ts AND type = :type AND source = :source LIMIT 1)")
    suspend fun existsByTimestamp(ts: Long, type: String, source: String): Boolean
}
