package com.journal.app.ui.screen.home

import android.app.Application
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.local.dao.EntryDao
import com.journal.app.data.local.dao.JournalDao
import com.journal.app.data.local.DbSeeder
import com.journal.app.data.local.entity.TimelineEntryEntity
import com.journal.app.data.pipeline.GlassesSetupManager
import com.journal.app.data.pipeline.MaterialIngestion
import com.journal.app.data.repository.ProfileRepository
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
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val timelineRepository: TimelineRepository,
    private val profileRepository: ProfileRepository,
    private val dbSeeder: DbSeeder,
    private val sessionManager: SessionManager,
    private val materialIngestion: MaterialIngestion,
    private val commandChannel: CommandChannel,
    private val audioPipeline: AudioPipeline,
    private val photoPipeline: PhotoPipeline,
    private val entryDao: EntryDao,
    private val journalDao: JournalDao,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HomeVM"
        private const val TIMELINE_WINDOW_DAYS = 30L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val setupManager = GlassesSetupManager(sessionManager)
    private val connectionLog = StringBuilder()
    private var pipelinesReady = false
    private var autoConnectStarted = false

    // Phone-side audio recording
    private var phoneMediaRecorder: MediaRecorder? = null
    private var phoneAudioFile: File? = null

    init {
        sessionManager.init(application)
        setupManager.init(application)
        observeGlassesState()
        observeRecordingState()
        seedThenObserve()

        setupManager.onStatus = { step, msg ->
            Log.i(TAG, "[$step] $msg")
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
                _uiState.update {
                    it.copy(
                        date = today,
                        entries = journal.entries,
                        summaryPreview = journal.summary,
                        mood = journal.mood,
                    )
                }
            }
        }
    }

    /** Seed sample data on first run, then keep the profile and recent-day timeline live. */
    private fun seedThenObserve() {
        viewModelScope.launch {
            dbSeeder.seedIfEmpty()
            observeProfile()
            observeRecentJournals()
            loadToday()
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            profileRepository.getProfile().collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
    }

    private fun observeRecentJournals() {
        val today = LocalDate.now()
        val range = today.minusDays(TIMELINE_WINDOW_DAYS)..today
        viewModelScope.launch {
            timelineRepository.getJournalsWithEntries(range).collect { journals ->
                _uiState.update {
                    it.copy(isLoading = false, recentJournals = journals)
                }
            }
        }
    }

    /** Saves a free-text note as a NOTE entry for today (FAB → Text). */
    fun addTextNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val date = LocalDate.now().toString()
            val entity = TimelineEntryEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                timestamp = now,
                type = "NOTE",
                source = "PHONE",
                noteText = trimmed,
                createdAt = now,
            )
            entryDao.insert(entity)
            journalDao.refreshEntryCount(date)
        }
    }

    fun toggleStar(entryId: String) {
        viewModelScope.launch { timelineRepository.toggleStar(entryId) }
    }

    // ── Phone-side camera capture (uses system camera via ActivityResultLauncher in HomeScreen) ──

    /** Creates a temp file for the system camera to write into. */
    fun preparePhonePhotoFile(): File {
        val dir = File(application.cacheDir, "journal_photos")
        dir.mkdirs()
        return File(dir, "phone_${UUID.randomUUID()}.jpg")
    }

    /** Called after the system camera saves a photo to [filePath]. */
    fun savePhonePhoto(filePath: String) {
        Log.i(TAG, "savePhonePhoto: path=$filePath")
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val date = LocalDate.now().toString()
            val entity = TimelineEntryEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                timestamp = now,
                type = "PHOTO",
                source = "PHONE",
                localPath = filePath,
                createdAt = now,
            )
            entryDao.insert(entity)
            journalDao.refreshEntryCount(date)
        }
    }

    // ── Phone-side audio recording (uses phone mic directly) ──

    fun startPhoneRecording() {
        Log.i(TAG, "startPhoneRecording: using phone mic")
        try {
            val dir = File(application.cacheDir, "journal_audio")
            dir.mkdirs()
            phoneAudioFile = File(dir, "phone_${UUID.randomUUID()}.mp4")

            phoneMediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(phoneAudioFile!!.absolutePath)
                prepare()
                start()
            }
            _uiState.update { it.copy(isRecording = true) }
        } catch (e: Exception) {
            Log.e(TAG, "startPhoneRecording failed", e)
            phoneMediaRecorder = null
            phoneAudioFile = null
        }
    }

    fun stopPhoneRecording() {
        Log.i(TAG, "stopPhoneRecording")
        phoneMediaRecorder?.apply {
            try { stop() } catch (e: Exception) { Log.e(TAG, "stopPhoneRecording: stop failed", e) }
            release()
        }
        phoneMediaRecorder = null

        val file = phoneAudioFile
        if (file != null && file.exists()) {
            val durationMs = (file.length() * 1000L / (16_000 * 2)).toInt()
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val date = LocalDate.now().toString()
                val entity = TimelineEntryEntity(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    timestamp = now,
                    type = "AUDIO",
                    source = "PHONE",
                    localPath = file.absolutePath,
                    durationMs = if (durationMs > 0) durationMs else null,
                    createdAt = now,
                )
                entryDao.insert(entity)
                journalDao.refreshEntryCount(date)
            }
        } else {
            Log.w(TAG, "stopPhoneRecording: no audio file saved")
        }
        phoneAudioFile = null
        _uiState.update { it.copy(isRecording = false) }
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
        Log.i(TAG, "Pipelines initialized (photo/audio/command), ingestion started")
    }

    override fun onCleared() {
        super.onCleared()
        materialIngestion.stop()
        // Clean up phone recorder if active
        phoneMediaRecorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        phoneMediaRecorder = null
    }
}
