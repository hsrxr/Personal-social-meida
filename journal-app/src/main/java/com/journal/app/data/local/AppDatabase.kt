package com.journal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.journal.app.data.local.converter.Converters
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.dao.MatchDao
import com.journal.app.data.local.dao.TagDao
import com.journal.app.data.local.dao.UserDao
import com.journal.app.data.local.entity.DailyJournalEntity
import com.journal.app.data.local.entity.MatchEntity
import com.journal.app.data.local.entity.TagEntity
import com.journal.app.data.local.entity.TimelineEntryEntity
import com.journal.app.data.local.entity.UserEntity

@Database(
    entities = [
        DailyJournalEntity::class,
        TimelineEntryEntity::class,
        TagEntity::class,
        MatchEntity::class,
        UserEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun entryDao(): EntryDao
    abstract fun tagDao(): TagDao
    abstract fun matchDao(): MatchDao
    abstract fun userDao(): UserDao
}
