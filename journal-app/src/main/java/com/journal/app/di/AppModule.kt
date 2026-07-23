package com.journal.app.di

import com.journal.app.data.repository.AiService
import com.journal.app.data.repository.MatchRepository
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.data.repository.mock.MockAiService
import com.journal.app.data.repository.mock.MockMatchRepository
import com.journal.app.data.repository.mock.MockTimelineRepository
import com.journal.cxrcore.command.CommandChannel
import com.journal.cxrcore.pipeline.audio.AudioReceiver
import com.journal.cxrcore.pipeline.photo.PhotoPipeline
import com.journal.cxrcore.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Agent-A (cxr-core) ──
    // SessionManager configured for glasses-journal APK
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
    fun provideAudioReceiver(): AudioReceiver = AudioReceiver()

    @Provides
    @Singleton
    fun provideCommandChannel(): CommandChannel = CommandChannel()

    // ── Repositories (mock phase → swap with real impls in Sprint 5) ──
    @Provides
    @Singleton
    fun provideTimelineRepository(repo: MockTimelineRepository): TimelineRepository = repo

    @Provides
    @Singleton
    fun provideMatchRepository(repo: MockMatchRepository): MatchRepository = repo

    @Provides
    @Singleton
    fun provideAiService(service: MockAiService): AiService = service
}
