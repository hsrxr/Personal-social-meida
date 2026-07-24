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
            _uiState.update { it.copy(statusMessage = "Please authorize in the popup…") }
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
                        statusMessage = "Authorization successful",
                    )
                }
            }
            is AuthResult.AuthFail -> {
                _uiState.update {
                    it.copy(statusMessage = "Authorization failed")
                }
            }
            is AuthResult.AuthCancel -> {
                _uiState.update {
                    it.copy(statusMessage = "Authorization cancelled")
                }
            }
        }
    }

    // ── Step 2: Connect ──

    fun connectGlasses() {
        val token = _uiState.value.authToken
        if (token == null) {
            _uiState.update { it.copy(statusMessage = "Please complete authorization first") }
            return
        }
        _uiState.update { it.copy(isConnecting = true, statusMessage = "Connecting…") }
        viewModelScope.launch {
            sessionManager.connect(token)
                .onSuccess {
                    _uiState.update { it.copy(isConnecting = false, statusMessage = "Connected, waiting for link…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isConnecting = false, statusMessage = "Connection failed: ${e.message}") }
                }
        }
    }

    fun disconnectGlasses() {
        sessionManager.disconnect()
        _uiState.update { it.copy(statusMessage = "Disconnected") }
    }

    // ── Step 3: Install glasses app ──

    fun installGlassesApp(apkPath: String) {
        if (_uiState.value.linkState != LinkState.LinkReady) {
            _uiState.update { it.copy(statusMessage = "Please establish a link first (LinkReady)") }
            return
        }
        _uiState.update { it.copy(installing = true, statusMessage = "Installing…") }
        sessionManager.installGlassesApp(apkPath)
    }

    // ── Step 4: Start glasses app ──

    fun startGlassesApp() {
        if (_uiState.value.linkState != LinkState.LinkReady) {
            _uiState.update { it.copy(statusMessage = "Please establish a link first (LinkReady)") }
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
                        LinkState.Idle -> "Idle"
                        LinkState.Connecting -> "Connecting…"
                        LinkState.LinkReady -> if (installed) "Link ready — launch glasses app" else "Link ready — install glasses app"
                        LinkState.SessionBuilt -> "Glasses app running ✅"
                        LinkState.Disconnected -> "Disconnected"
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
