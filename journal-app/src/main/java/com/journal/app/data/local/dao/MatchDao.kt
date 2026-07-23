package com.journal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.journal.app.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    @Query("SELECT * FROM matches WHERE matchDate = :date ORDER BY createdAt DESC")
    fun getMatches(date: String): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE matchDate = :date ORDER BY createdAt DESC")
    suspend fun getMatchesOnce(date: String): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<MatchEntity>)

    @Query("UPDATE matches SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM matches WHERE matchDate < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
