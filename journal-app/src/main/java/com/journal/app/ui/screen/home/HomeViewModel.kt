package com.journal.app.ui.screen.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.pipeline.GlassesSetupManager
import com.journal.app.data.pipeline.MaterialIngestion
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.ui.states.HomeUiState
import com.journal.cxrcore.command.CommandChannel
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.pipeline.audio.AudioPipeline
import com.journal.cxrcore.pipeline.photo.PhotoPipeline
import com.journal.cxrcore.session.GlassState
import com.journal.cxrcore.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val timelineRepository: TimelineRepository,
    private val sessionManager: SessionManager,
    private val materialIngestion: MaterialIngestion,
    private val commandChannel: CommandChannel,
    private val audioPipeline: AudioPipeline,
    private val photoPipeline: PhotoPipeline,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val setupManager = GlassesSetupManager(sessionManager)
    private val connectionLog = StringBuilder()
    private var pipelinesReady = false
    private var autoConnectStarted = false

    init {
        sessionManager.init(application)
        setupManager.init(application)
        observeGlassesState()
        observeRecordingState()
        loadToday()

        setupManager.onStatus = { step, msg ->
            Log.i("HomeVM", "[$step] $msg")
            connectionLog.append("[$step] $msg\n")
            val lines = connectionLog.lines().takeLast(6)
            _uiState.update { it.copy(connectionStatus = lines.joinToString("\n")) }
        }
    }

    /**
     * Call from HomeScreen LaunchedEffect to auto-connect.
     * Safe to call multiple times (runs once).
     */
    fun startAutoConnect(activity: android.app.Activity) {
        if (autoConnectStarted) return
        autoConnectStarted = true
        viewModelScope.launch {
            val ok = setupManager.start(activity)
            if (ok && !pipelinesReady) {
                initPipelines()
            }
        }
    }

    // ── Public Actions ──

    fun onAuthResult(resultCode: Int, data: android.content.Intent?) {
        setupManager.onAuthResult(resultCode, data)
    }

    fun disconnectGlasses() {
        sessionManager.disconnect()
    }

    fun loadToday() {
        val today = LocalDate.now()
        viewModelScope.launch {
            timelineRepository.getJournal(today).collect { journal ->
                Log.i("HomeVM", "Journal updated: entries=${journal.entries.size} summary=${journal.summary?.take(30)}")
                journal.entries.forEach { e ->
                    Log.i("HomeVM", "  entry: id=${e.id} type=${e.type} imageUrl=${e.imageUrl?.take(60)}")
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        date = today,
                        entries = journal.entries,
                        summaryPreview = journal.summary,
                        mood = journal.mood,
                    )
                }
            }
        }
    }

    fun toggleStar(entryId: String) {
        viewModelScope.launch { timelineRepository.toggleStar(entryId) }
    }

    // ── Manual capture controls (for on-phone trigger, bypassing glasses key events) ──

    /** Take a photo through the glasses. Requires SessionBuilt. */
    fun capturePhoto() {
        Log.i("HomeVM", "capturePhoto: triggered from phone UI")
        photoPipeline.capture()
    }

    fun startRecording() {
        Log.i("HomeVM", "startRecording: triggered from phone UI")
        audioPipeline.start()
    }

    fun stopRecording() {
        Log.i("HomeVM", "stopRecording: triggered from phone UI")
        audioPipeline.stop()
    }

    // ── Internal ──

    private fun observeRecordingState() {
        viewModelScope.launch {
            audioPipeline.isActive.collect { active ->
                _uiState.update { it.copy(isRecording = active) }
            }
        }
    }

    private fun observeGlassesState() {
        viewModelScope.launch {
            combine(
                sessionManager.linkState,
                sessionManager.glassState,
            ) { link, glass -> link to glass }
                .collect { (link, glass) ->
                    onGlassesChanged(link, glass)
                }
        }
    }

    private fun onGlassesChanged(link: LinkState, glass: GlassState) {
        _uiState.update {
            it.copy(
                glassLinkState = link,
                glassConnected = link == LinkState.SessionBuilt,
                glassBattery = glass.batteryLevel,
                glassDeviceName = glass.deviceName,
                glassWearing = glass.wearing,
            )
        }
        if (link == LinkState.SessionBuilt && !pipelinesReady) {
            initPipelines()
        }
    }

    private fun initPipelines() {
        pipelinesReady = true
        photoPipeline.init()
        commandChannel.init()
        audioPipeline.init()
        materialIngestion.start()
        Log.i("HomeVM", "Pipelines initialized (photo/audio/command), ingestion started")
    }

    override fun onCleared() {
        super.onCleared()
        materialIngestion.stop()
    }
}
