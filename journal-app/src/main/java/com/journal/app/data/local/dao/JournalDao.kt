package com.journal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.journal.app.data.local.entity.DailyJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM daily_journals WHERE date = :date LIMIT 1")
    fun getJournal(date: String): Flow<DailyJournalEntity?>

    @Query("SELECT * FROM daily_journals WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getJournals(from: String, to: String): Flow<List<DailyJournalEntity>>

    @Query("SELECT COUNT(*) FROM daily_journals")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(journal: DailyJournalEntity)

    @Query("UPDATE daily_journals SET summary = :summary, lastModified = :now WHERE date = :date")
    suspend fun updateSummary(date: String, summary: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE daily_journals SET mood = :mood, lastModified = :now WHERE date = :date")
    suspend fun updateMood(date: String, mood: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE daily_journals SET entryCount = (SELECT COUNT(*) FROM timeline_entries WHERE date = :date), lastModified = :now WHERE date = :date")
    suspend fun refreshEntryCount(date: String, now: Long = System.currentTimeMillis())
}
