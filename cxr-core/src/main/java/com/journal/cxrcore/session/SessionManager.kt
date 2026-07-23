package com.journal.cxrcore.session

import android.content.Context
import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.IGlassAppCbk
import com.journal.cxrcore.app.JournalApplication
import com.journal.cxrcore.link.LinkConnectionHub
import com.journal.cxrcore.link.LinkSessionGate
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.command.CustomCmdRouter
import com.journal.cxrcore.util.ApkInstallAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

/**
 * Public API for Agent-B/C: manages CUSTOMAPP session lifecycle.
 *
 * Single entry point for:
 * - Connecting to glasses with a token.
 * - Installing / starting / stopping the glasses-side app.
 * - Querying link and glass state.
 */
class SessionManager(
    private val glassesPackageName: String,
    private val glassesMainActivity: String,
) {
    companion object {
        private const val TAG = "SessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cxrLink: CXRLink? = null
    private var appContext: Context? = null

    // -- State --

    private val _linkState = MutableStateFlow(LinkState.Idle)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _glassState = MutableStateFlow(GlassState())
    val glassState: StateFlow<GlassState> = _glassState.asStateFlow()

    private val _appInstalled = MutableStateFlow(false)
    val appInstalled: StateFlow<Boolean> = _appInstalled.asStateFlow()

    private val _appOpened = MutableStateFlow(false)
    val appOpened: StateFlow<Boolean> = _appOpened.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    private val glassesEntry: String get() = "$glassesPackageName$glassesMainActivity"

    // -- Public API --

    /**
     * Connect to glasses with auth token.
     * Transitions: Idle → Connecting → LinkReady → SessionBuilt.
     */
    suspend fun connect(token: String): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("init() not called"))
        _linkState.value = LinkState.Connecting

        val link = LinkSessionGate.createCustomAppSession(ctx, token, glassesPackageName)
            ?: return Result.failure(IllegalStateException("Failed to create CXRLink session"))
        cxrLink = link
        appContext = ctx

        // Observe connection state from Hub to update LinkState
        observeHubState()
        return Result.success(Unit)
    }

    /**
     * Disconnect and release all session resources.
     */
    fun disconnect() {
        CustomCmdRouter.release()
        cxrLink?.let { link ->
            if (_appOpened.value) {
                runCatching { link.appStop(appCallback) }
            }
            runCatching { link.disconnect() }
        }
        _linkState.value = LinkState.Disconnected
        _appOpened.value = false
        JournalApplication.instance.resetSession()
    }

    /**
     * Initialize with a Context before calling [connect].
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Check if the glasses app is installed.
     */
    fun checkAppInstalled() {
        val link = readyLinkOrNull() ?: return
        runCatching { link.appIsInstalled(appCallback) }
    }

    /**
     * Install the glasses APK from a readable file path.
     */
    fun installGlassesApp(apkPath: String) {
        val link = readyLinkOrNull() ?: return
        val ctx = appContext ?: return
        val apkFile = File(apkPath)
        if (!ApkInstallAccess.isReadableApkFile(ctx, apkFile)) {
            Log.e(TAG, "installGlassesApp: APK not readable: $apkPath")
            return
        }
        _installing.value = true
        runCatching {
            link.appUploadAndInstall(apkPath, appCallback)
        }.onFailure {
            Log.e(TAG, "installGlassesApp: upload failed", it)
            _installing.value = false
        }
    }

    /**
     * Start the glasses-side app (completes session build).
     */
    fun startGlassesApp() {
        val link = readyLinkOrNull() ?: return
        Log.i(TAG, "startGlassesApp: entry=$glassesEntry")
        runCatching { link.appStart(glassesEntry, appCallback) }
    }

    /**
     * Stop the glasses-side app.
     */
    fun stopGlassesApp() {
        val link = readyLinkOrNull() ?: return
        Log.i(TAG, "stopGlassesApp")
        runCatching { link.appStop(appCallback) }
    }

    /**
     * Release session resources (stops app if running, does NOT disconnect).
     */
    fun release() {
        val link = cxrLink ?: return
        if (_appOpened.value) {
            runCatching { link.appStop(appCallback) }
            _appOpened.value = false
        }
    }

    /** Called when session is fully built. Initializes shared infrastructure. */
    private fun onSessionBuilt() {
        _linkState.value = LinkState.SessionBuilt
        val link = cxrLink ?: return
        CustomCmdRouter.init(link)
        Log.i(TAG, "Session built: CustomCmdRouter initialized")
    }

    // -- Internal --

    private fun readyLinkOrNull(): CXRLink? =
        runCatching { JournalApplication.instance.requireReadyLink() }.getOrNull()

    private fun observeHubState() {
        scope.launch {
            combine(
                LinkConnectionHub.cxrlConnected,
                LinkConnectionHub.btConnected,
            ) { cxr, bt -> cxr to bt }
                .collect { (cxr, bt) ->
                    val wasSessionBuilt = _linkState.value == LinkState.SessionBuilt
                    _linkState.value = when {
                        !cxr || !bt -> LinkState.Disconnected
                        !wasSessionBuilt && cxr && bt -> LinkState.LinkReady
                        else -> _linkState.value
                    }
                }
        }
        scope.launch {
            LinkConnectionHub.deviceInfo.collect { info ->
                _glassState.value = GlassState(
                    deviceName = info.deviceName,
                    batteryLevel = info.batteryLevel,
                    wearing = LinkConnectionHub.wearingStatus.value ?: false,
                    brightness = info.brightness,
                    volume = info.volume,
                )
            }
        }
    }

    // Glass app lifecycle callback
    private val appCallback = object : IGlassAppCbk {
        override fun onInstallAppResult(success: Boolean) {
            Log.i(TAG, "onInstallAppResult: success=$success")
            _installing.value = false
            if (success) checkAppInstalled() else {
                _appInstalled.value = false
            }
        }

        override fun onUnInstallAppResult(success: Boolean) {
            Log.i(TAG, "onUnInstallAppResult: success=$success")
            if (success) {
                _appInstalled.value = false
                _appOpened.value = false
            }
        }

        override fun onOpenAppResult(success: Boolean) {
            Log.i(TAG, "onOpenAppResult: success=$success")
            _appOpened.value = success
            if (success) onSessionBuilt()
        }

        override fun onStopAppResult(success: Boolean) {
            Log.i(TAG, "onStopAppResult: success=$success")
            _appOpened.value = !success
        }

        override fun onGlassAppResume(resumed: Boolean) {
            Log.i(TAG, "onGlassAppResume: resumed=$resumed")
            _appOpened.value = resumed
            if (resumed) onSessionBuilt()
        }

        override fun onQueryAppResult(installed: Boolean) {
            Log.i(TAG, "onQueryAppResult: installed=$installed")
            _appInstalled.value = installed
        }
    }
}
