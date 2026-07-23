package com.journal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.journal.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE entryId = :entryId ORDER BY createdAt ASC")
    fun getTags(entryId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE entryId = :entryId")
    suspend fun deleteByEntry(entryId: String)

    @Query("DELETE FROM tags WHERE entryId = :entryId AND name = :name")
    suspend fun deleteOne(entryId: String, name: String)
}
