package com.journal.app.ui.screen.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.journal.cxrcore.auth.AuthService
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.session.SessionManager
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val rokidAppInstalled: Boolean = false,
    val authToken: String? = null,
    val authDone: Boolean = false,
    val linkState: LinkState = LinkState.Idle,
    val appInstalled: Boolean = false,
    val installing: Boolean = false,
    val isConnecting: Boolean = false,
    val statusMessage: String = "",
    val battery: Int = 0,
    val deviceName: String = "",
    val wearing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val sessionManager: SessionManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        sessionManager.init(application)
        observeSessionState()
    }

    // ── Step 1: Auth ──

    fun checkInstallation(activity: Activity) {
        val installed = AuthService.isRokidAppInstalled(activity)
        _uiState.update { it.copy(rokidAppInstalled = installed) }
    }

    fun requestAuth(activity: Activity) {
        val result = AuthService.requestAuth(
            activity,
            permissions = arrayOf(
                GlassPermission.MICROPHONE,
                GlassPermission.CAMERA,
                GlassPermission.MEDIA,
            ),
        )
        if (result != null) {
            // Immediate result (pre-authorized)
            applyAuthResult(AuthService.parseAuthResult(result.first, result.second))
        } else {
            // Auth UI launched; result will come via onActivityResult
            _uiState.update { it.copy(statusMessage = "请在弹窗中授权...") }
        }
    }

    /** Call from Activity.onActivityResult after auth UI returns. */
    fun onAuthResult(resultCode: Int, data: android.content.Intent?) {
        applyAuthResult(AuthService.parseAuthResult(resultCode, data))
    }

    private fun applyAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.AuthSuccess -> {
                _uiState.update {
                    it.copy(
                        authDone = result.token.isNotBlank(),
                        authToken = result.token,
                        statusMessage = "鉴权成功",
                    )
                }
            }
            is AuthResult.AuthFail -> {
                _uiState.update {
                    it.copy(statusMessage = "鉴权失败")
                }
            }
            is AuthResult.AuthCancel -> {
                _uiState.update {
                    it.copy(statusMessage = "鉴权取消")
                }
            }
        }
    }

    // ── Step 2: Connect ──

    fun connectGlasses() {
        val token = _uiState.value.authToken
        if (token == null) {
            _uiState.update { it.copy(statusMessage = "请先完成鉴权") }
            return
        }
        _uiState.update { it.copy(isConnecting = true, statusMessage = "连接中...") }
        viewModelScope.launch {
            sessionManager.connect(token)
                .onSuccess {
                    _uiState.update { it.copy(isConnecting = false, statusMessage = "连接成功，等待链路就绪...") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isConnecting = false, statusMessage = "连接失败: ${e.message}") }
                }
        }
    }

    fun disconnectGlasses() {
        sessionManager.disconnect()
        _uiState.update { it.copy(statusMessage = "已断开") }
    }

    // ── Step 3: Install glasses app ──

    fun installGlassesApp(apkPath: String) {
        if (_uiState.value.linkState != LinkState.LinkReady) {
            _uiState.update { it.copy(statusMessage = "请先建立链路 (LinkReady)") }
            return
        }
        _uiState.update { it.copy(installing = true, statusMessage = "安装中...") }
        sessionManager.installGlassesApp(apkPath)
    }

    // ── Step 4: Start glasses app ──

    fun startGlassesApp() {
        if (_uiState.value.linkState != LinkState.LinkReady) {
            _uiState.update { it.copy(statusMessage = "请先建立链路 (LinkReady)") }
            return
        }
        sessionManager.startGlassesApp()
    }

    fun stopGlassesApp() {
        sessionManager.stopGlassesApp()
    }

    // ── State observation ──

    private fun observeSessionState() {
        viewModelScope.launch {
            combine(
                sessionManager.linkState,
                sessionManager.glassState,
                sessionManager.appInstalled,
                sessionManager.installing,
            ) { link, glass, installed, installing ->
                SettingsUiState(
                    linkState = link,
                    appInstalled = installed,
                    installing = installing,
                    battery = glass.batteryLevel,
                    deviceName = glass.deviceName,
                    wearing = glass.wearing,
                    statusMessage = when (link) {
                        LinkState.Idle -> "待连接"
                        LinkState.Connecting -> "连接中..."
                        LinkState.LinkReady -> if (installed) "链路就绪，可启动眼镜App" else "链路就绪，请安装眼镜App"
                        LinkState.SessionBuilt -> "眼镜App运行中 ✅"
                        LinkState.Disconnected -> "已断开"
                    },
                )
            }.collect { state ->
                _uiState.value = _uiState.value.copy(
                    linkState = state.linkState,
                    appInstalled = state.appInstalled,
                    installing = state.installing,
                    battery = state.battery,
                    deviceName = state.deviceName,
                    wearing = state.wearing,
                    statusMessage = state.statusMessage,
                )
            }
        }
    }
}
