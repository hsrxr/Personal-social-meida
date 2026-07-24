package com.journal.sample

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.cxrcore.command.JournalEvent
import com.journal.cxrcore.app.JournalApplication
import com.journal.cxrcore.auth.AuthService
import com.journal.cxrcore.command.CapsProtocol
import com.journal.cxrcore.command.CommandChannel
import com.journal.cxrcore.device.DeviceController
import com.journal.cxrcore.link.LinkConnectionHub
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.pipeline.audio.AudioChunk
import com.journal.cxrcore.pipeline.audio.AudioPipeline
import com.journal.cxrcore.pipeline.photo.PhotoCapture
import com.journal.cxrcore.pipeline.photo.PhotoPipeline
import com.journal.cxrcore.session.SessionManager
import com.journal.cxrcore.util.WavBuilder
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugViewModel : ViewModel() {

    // ── Glasses target config (must match glasses-journal APK) ──
    private val glassesPackage = "com.journal.glasses"
    private val glassesActivity = ".MainActivity"

    private val sessionManager = SessionManager(glassesPackage, glassesActivity)
    private val photoPipeline = PhotoPipeline()
    private val audioPipeline = AudioPipeline()
    private val commandChannel = CommandChannel()
    private val deviceController = DeviceController()

    private var token: String = ""

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    private val logLines = mutableListOf<String>()

    init {
        // Observe link state
        viewModelScope.launch {
            sessionManager.linkState.collect { ls ->
                _state.update { it.copy(linkState = ls) }
                appendLog("LinkState → $ls")
                if (ls == LinkState.SessionBuilt) {
                    onSessionBuilt()
                }
            }
        }
        // Observe glass state
        viewModelScope.launch {
            sessionManager.glassState.collect { gs ->
                _state.update {
                    it.copy(
                        deviceInfo = "设备: ${gs.deviceName}\n电量: ${gs.batteryLevel}%\n" +
                            "佩戴: ${if (gs.wearing) "是" else "否"}\n亮度: ${gs.brightness}/15  音量: ${gs.volume}/15"
                    )
                }
            }
        }
        // Observe app installed/opened
        viewModelScope.launch {
            sessionManager.appInstalled.collect { installed ->
                _state.update { it.copy(appInstalled = installed) }
            }
        }
        viewModelScope.launch {
            sessionManager.appOpened.collect { opened ->
                _state.update { it.copy(appOpened = opened) }
            }
        }
        viewModelScope.launch {
            sessionManager.installing.collect { inst ->
                _state.update { it.copy(installing = inst) }
            }
        }
        // Observe photo flow
        viewModelScope.launch {
            photoPipeline.photoFlow.collectLatest { capture ->
                onPhotoReceived(capture)
            }
        }
        // Observe audio flow
        viewModelScope.launch {
            audioPipeline.audioChunkFlow.collectLatest { chunk ->
                onAudioChunkReceived(chunk)
            }
        }
        // Observe command inbound
        viewModelScope.launch {
            commandChannel.inboundFlow.collectLatest { event ->
                appendLog("📥 眼镜事件: ${event.eventType} @ ${event.timestamp}")
            }
        }
    }

    // ── Step 1: Check Rokid App ──

    fun checkRokidAppInstalled(activity: Activity) {
        val installed = AuthService.isRokidAppInstalled(activity) || AuthService.isHiRokidInstalled(activity)
        _state.update { it.copy(rokidInstalled = installed) }
        appendLog(if (installed) "✅ Rokid App 已安装" else "❌ Rokid App 未安装，请先安装")
    }

    // ── Step 2: Auth ──

    fun requestAuth(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        appendLog("发起鉴权...")
        val immediate = AuthService.requestAuth(activity)
        if (immediate != null) {
            // Already authorized, parse immediately
            onAuthResult(immediate.first, immediate.second)
        }
        // else: result comes via launcher callback
    }

    fun onTokenFromHistory(token: String) {
        if (token.isBlank()) return
        this.token = token
        _state.update { it.copy(authenticated = true) }
        appendLog("✅ 从历史记录恢复 token")
    }

    fun onAuthResult(resultCode: Int, data: Intent?) {
        val result = AuthService.parseAuthResult(resultCode, data)
        when (result) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                _state.update { it.copy(authenticated = result.token.isNotBlank()) }
                appendLog("✅ 鉴权成功, token 长度=${result.token.length}")
            }
            is AuthResult.AuthFail -> {
                _state.update { it.copy(authenticated = false) }
                appendLog("❌ 鉴权失败")
            }
            is AuthResult.AuthCancel -> {
                _state.update { it.copy(authenticated = false) }
                appendLog("⚠ 用户取消鉴权")
            }
        }
    }

    // ── Step 3: Connect ──

    fun connect(activity: Activity) {
        if (token.isBlank()) {
            appendLog("❌ 无 token, 请先鉴权")
            return
        }
        _state.update { it.copy(busy = true) }
        sessionManager.init(activity)
        viewModelScope.launch {
            val result = sessionManager.connect(token)
            _state.update { it.copy(busy = false) }
            result.fold(
                onSuccess = { appendLog("✅ connect() 发起成功, 等待回调...") },
                onFailure = { appendLog("❌ connect() 失败: ${it.message}") },
            )
        }
    }

    // ── Step 4: Install / Start glasses app ──

    fun installApk() {
        val apkPath = findApkPath()
        if (apkPath == null) {
            appendLog("❌ 找不到眼镜 APK，请将 glasses-journal.apk 放到手机 Download 目录")
            return
        }
        appendLog("安装眼镜 App: $apkPath")
        sessionManager.installGlassesApp(apkPath)
    }

    fun startApp() {
        appendLog("启动眼镜 App...")
        sessionManager.startGlassesApp()
    }

    fun stopApp() {
        appendLog("停止眼镜 App...")
        sessionManager.stopGlassesApp()
    }

    // ── Step 5: Capability tests ──

    private fun onSessionBuilt() {
        appendLog("🎉 会话构建完成, 初始化能力管道...")
        photoPipeline.init()
        commandChannel.init()
        audioPipeline.init()
    }

    fun takePhoto() {
        _state.update { it.copy(photoTaking = true) }
        photoPipeline.capture()
        appendLog("📷 触发拍照...")
    }

    fun startAudio() {
        _state.update { it.copy(audioActive = true) }
        if (!audioPipeline.isActive.value) {
            audioPipeline.start()
            appendLog("🎤 startAudioStream 已调用 (mic权限: ${audioPipeline.permissionGranted.value})")
        } else {
            appendLog("🎤 已在录音中")
        }
    }

    fun stopAudio() {
        _state.update { it.copy(audioActive = false) }
        audioPipeline.stop()
        appendLog("⏹ stopAudioStream 已调用")
    }

    fun sendTestCommand() {
        commandChannel.sendPhoneCmd(
            cmd = CapsProtocol.PhoneCmd.UPDATE_DISPLAY,
            text = "Hello from phone! ${System.currentTimeMillis() % 100000}",
        )
        appendLog("📨 已发送测试指令到眼镜")
    }

    fun refreshDeviceInfo() {
        deviceController.refreshDeviceInfo()
        appendLog("🔍 查询设备信息...")
    }

    fun saveLastWav(activity: Activity) {
        val lastChunk = lastAudioChunk ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val wavBytes = WavBuilder.pcmToWavBytes(lastChunk.pcmData)
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir
                dir.mkdirs()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "debug_$ts.wav")
                file.writeBytes(wavBytes)
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(lastWavPath = file.absolutePath) }
                    appendLog("💾 WAV 已保存: ${file.absolutePath} (${wavBytes.size} bytes)")
                }
            }
        }
    }

    // ── Internal ──

    private var lastAudioChunk: AudioChunk? = null

    private fun onPhotoReceived(capture: PhotoCapture) {
        _state.update {
            it.copy(
                photoTaking = false,
                lastPhotoInfo = "${capture.jpegBytes.size} bytes @ ${capture.timestamp}",
            )
        }
        appendLog("📷 收到照片: ${capture.jpegBytes.size} bytes")
    }

    private fun onAudioChunkReceived(chunk: AudioChunk) {
        lastAudioChunk = chunk
        _state.update { it.copy(audioChunkCount = it.audioChunkCount + 1) }
        appendLog("🎤 收到音频片段: ${chunk.pcmData.size} bytes, ${chunk.durationMs}ms")
    }

    private fun findApkPath(): String? {
        val candidates = listOf(
            File("/data/local/tmp/glasses-journal-debug.apk"),
            File("/sdcard/Download/glasses-journal-debug.apk"),
        )
        return candidates.firstOrNull { it.exists() && it.canRead() }?.absolutePath
    }

    private fun appendLog(msg: String) {
        Log.i("DebugVM", msg)
        logLines.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg")
        if (logLines.size > 50) logLines.removeAt(0)
        _state.update { it.copy(log = logLines.joinToString("\n")) }
    }
}

// ── State ──

data class DebugState(
    val log: String = "等待操作...",
    val busy: Boolean = false,

    // Step 1
    val rokidInstalled: Boolean = false,

    // Step 2
    val authenticated: Boolean = false,

    // Step 3
    val linkState: LinkState = LinkState.Idle,
    val deviceInfo: String = "",

    // Step 4
    val appInstalled: Boolean = false,
    val appOpened: Boolean = false,
    val installing: Boolean = false,

    // Step 5
    val photoTaking: Boolean = false,
    val lastPhotoInfo: String? = null,
    val audioActive: Boolean = false,
    val audioChunkCount: Int = 0,
    val lastWavPath: String? = null,
)
