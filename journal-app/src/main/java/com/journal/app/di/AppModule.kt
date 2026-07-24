package com.journal.app.di

import android.content.Context
import androidx.room.Room
import com.journal.app.data.local.AppDatabase
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.dao.MatchDao
import com.journal.app.data.local.dao.TagDao
import com.journal.app.data.local.dao.UserDao
import com.journal.app.data.pipeline.MaterialIngestion
import com.journal.app.data.pipeline.MediaUploader
import com.journal.app.data.remote.ApiService
import com.journal.app.data.remote.SyncManager
import com.journal.app.data.repository.AiService
import com.journal.app.data.repository.MatchRepository
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.data.repository.TimelineRepositoryImpl
import com.journal.app.data.repository.mock.MockAiService
import com.journal.app.data.repository.mock.MockMatchRepository
import com.journal.cxrcore.command.CommandChannel
import com.journal.cxrcore.pipeline.audio.AudioPipeline
import com.journal.cxrcore.pipeline.photo.PhotoPipeline
import com.journal.cxrcore.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ══════════════════════════════════════════
    // Agent-A (cxr-core)
    // ══════════════════════════════════════════

    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager =
        SessionManager(
            glassesPackageName = "com.journal.glasses",
            glassesMainActivity = ".MainActivity",
        )

    @Provides
    @Singleton
    fun providePhotoPipeline(): PhotoPipeline = PhotoPipeline()

    @Provides
    @Singleton
    fun provideAudioPipeline(): AudioPipeline = AudioPipeline()

    @Provides
    @Singleton
    fun provideCommandChannel(): CommandChannel = CommandChannel()

    // ══════════════════════════════════════════
    // Room Database
    // ══════════════════════════════════════════

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "journal.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()

    @Provides
    @Singleton
    fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()

    @Provides
    @Singleton
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    @Singleton
    fun provideMatchDao(db: AppDatabase): MatchDao = db.matchDao()

    @Provides
    @Singleton
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    // ══════════════════════════════════════════
    // Remote
    // ══════════════════════════════════════════

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    @Provides
    @Singleton
    fun provideApiService(client: OkHttpClient): ApiService =
        Retrofit.Builder()
            .baseUrl("https://api.journal-app.example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        entryDao: EntryDao,
    ): SyncManager = SyncManager(context, entryDao)

    // ══════════════════════════════════════════
    // Pipeline
    // ══════════════════════════════════════════

    @Provides
    @Singleton
    fun provideMediaUploader(
        @ApplicationContext context: Context,
        entryDao: EntryDao,
        syncManager: SyncManager,
    ): MediaUploader = MediaUploader(context, entryDao, syncManager)

    @Provides
    @Singleton
    fun provideMaterialIngestion(
        @ApplicationContext context: Context,
        audioPipeline: AudioPipeline,
        photoPipeline: PhotoPipeline,
        commandChannel: CommandChannel,
        entryDao: EntryDao,
        journalDao: JournalDao,
        mediaUploader: MediaUploader,
    ): MaterialIngestion = MaterialIngestion(
        context, audioPipeline, photoPipeline, commandChannel,
        entryDao, journalDao, mediaUploader,
    )

    // ══════════════════════════════════════════
    // Repositories — Mock implementations for standalone dev
    // ══════════════════════════════════════════

    @Provides
    @Singleton
    fun provideTimelineRepository(impl: TimelineRepositoryImpl): TimelineRepository = impl

    @Provides
    @Singleton
    fun provideMatchRepository(impl: MockMatchRepository): MatchRepository = impl

    @Provides
    @Singleton
    fun provideAiService(service: MockAiService): AiService = service
}
