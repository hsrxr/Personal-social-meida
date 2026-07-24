package com.journal.app.data.pipeline

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.journal.cxrcore.auth.AuthService
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.session.SessionManager
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Orchestrates the full glasses connection flow automatically.
 *
 * Call [start] from the Activity — it runs the full pipeline:
 * check Rokid app → auth (cached or new) → connect → install glasses app → start glasses app.
 *
 * Each step is idempotent: if already connected / already installed with correct version, skip.
 */
class GlassesSetupManager(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "GlassesSetupManager"
        private const val PREFS_NAME = "glasses_setup"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_GLASSES_APK_VERSION = "glasses_apk_version"
        private const val GLASSES_APK_PATH = "/data/local/tmp/glasses-journal-debug.apk"
        private const val TIMEOUT_LINK_MS = 20_000L
        private const val TIMEOUT_SESSION_MS = 15_000L
        private const val TIMEOUT_INSTALL_MS = 30_000L
    }

    private lateinit var prefs: SharedPreferences

    /** Call once to init SharedPreferences. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var onStatus: ((step: String, message: String) -> Unit)? = null

    /** Called when auth UI launches; Activity must call [onAuthResult] afterwards. */
    var onAuthRequired: (() -> Unit)? = null

    private var authDeferred: CompletableDeferred<String>? = null

    // ── Public API ──

    /**
     * Start the auto-connect flow. Returns true if SessionBuilt, false if any step failed.
     */
    suspend fun start(activity: Activity): Boolean = withContext(Dispatchers.Main) {
        if (sessionManager.linkState.value == LinkState.SessionBuilt) {
            report("done", "Glasses connected")
            return@withContext true
        }

        // Step 1
        if (!checkRokidApp(activity)) return@withContext false

        // Step 2
        val token = getOrRequestToken(activity) ?: return@withContext false

        // Step 3
        if (!connectToGlasses(token)) return@withContext false

        // Step 4
        if (!ensureGlassesAppInstalled()) return@withContext false

        // Step 5
        if (!startGlasses()) return@withContext false

        report("done", "🎉 Glasses connected, starting capture")
        true
    }

    /** Call from Activity.onActivityResult(requestCode, resultCode, data). */
    fun onAuthResult(resultCode: Int, data: Intent?) {
        val result = AuthService.parseAuthResult(resultCode, data)
        when (result) {
            is AuthResult.AuthSuccess -> {
                prefs.edit().putString(KEY_TOKEN, result.token).apply()
                authDeferred?.complete(result.token)
            }
            is AuthResult.AuthFail, is AuthResult.AuthCancel -> {
                authDeferred?.complete("")
            }
        }
        authDeferred = null
    }

    // ── Steps ──

    private fun checkRokidApp(activity: Activity): Boolean {
        report("check", "Checking for Rokid AI app…")
        val ok = AuthService.isRokidAppInstalled(activity) || AuthService.isHiRokidInstalled(activity)
        report("check", if (ok) "Rokid AI app installed ✅" else "Rokid AI app not found — please install it first")
        return ok
    }

    private suspend fun getOrRequestToken(activity: Activity): String? {
        val cached = prefs.getString(KEY_TOKEN, null)
        if (!cached.isNullOrBlank()) {
            report("auth", "Using saved token ✅")
            return cached
        }

        report("auth", "Requesting authorization…")
        val immediate = AuthService.requestAuth(
            activity,
            arrayOf(GlassPermission.MICROPHONE, GlassPermission.CAMERA, GlassPermission.MEDIA),
        )

        if (immediate != null) {
            val res = AuthService.parseAuthResult(immediate.first, immediate.second)
            if (res is AuthResult.AuthSuccess) {
                prefs.edit().putString(KEY_TOKEN, res.token).apply()
                report("auth", "Authorization successful ✅")
                return res.token
            }
            report("auth", "Authorization failed")
            return null
        }

        // Auth UI launched — wait for user
        report("auth", "Please authorize in the popup…")
        onAuthRequired?.invoke()
        authDeferred = CompletableDeferred()
        val token = authDeferred!!.await()
        return token.ifBlank { null }
    }

    private suspend fun connectToGlasses(token: String): Boolean {
        val ls = sessionManager.linkState.value
        if (ls == LinkState.LinkReady || ls == LinkState.SessionBuilt) {
            report("connect", "Already connected ✅")
            return true
        }

        report("connect", "Connecting to glasses…")
        val res = sessionManager.connect(token)
        if (res.isFailure) {
            report("connect", "Connection failed: ${res.exceptionOrNull()?.message}")
            return false
        }

        // Wait for LinkReady or SessionBuilt
        val state = withTimeoutOrNull(TIMEOUT_LINK_MS) {
            sessionManager.linkState.filter {
                it == LinkState.LinkReady || it == LinkState.SessionBuilt || it == LinkState.Disconnected
            }.first()
        }
        if (state == null || state == LinkState.Disconnected) {
            report("connect", "Timed out waiting for LinkReady")
            return false
        }
        report("connect", "Link ready ✅")
        return true
    }

    private suspend fun ensureGlassesAppInstalled(): Boolean {
        val apk = File(GLASSES_APK_PATH)
        if (!apk.exists() || !apk.canRead()) {
            report("install", "Glasses APK not found: $GLASSES_APK_PATH")
            return false
        }

        val savedVersion = prefs.getLong(KEY_GLASSES_APK_VERSION, -1L)
        val currentVersion = apk.lastModified()

        if (savedVersion == currentVersion && sessionManager.appInstalled.value) {
            report("install", "Glasses app is up to date, skipping install ✅")
            return true
        }

        report("install", "Installing glasses app (${apk.length() / 1024}KB)…")
        sessionManager.installGlassesApp(apk.absolutePath)

        // Wait for install callback
        val ok = withTimeoutOrNull(TIMEOUT_INSTALL_MS) {
            sessionManager.appInstalled.filter { it }.first()
        }
        if (ok != true) {
            report("install", "Install timed out")
            return false
        }

        prefs.edit().putLong(KEY_GLASSES_APK_VERSION, currentVersion).apply()
        report("install", "Install complete ✅")
        return true
    }

    private suspend fun startGlasses(): Boolean {
        if (sessionManager.appOpened.value) {
            report("launch", "Glasses app already running ✅")
            return true
        }

        report("launch", "Launching glasses app…")
        sessionManager.startGlassesApp()

        val state = withTimeoutOrNull(TIMEOUT_SESSION_MS) {
            sessionManager.linkState.filter {
                it == LinkState.SessionBuilt || it == LinkState.Disconnected
            }.first()
        }
        if (state != LinkState.SessionBuilt) {
            report("launch", "Launch timed out")
            return false
        }
        report("launch", "Glasses app running ✅")
        return true
    }

    private fun report(step: String, msg: String) {
        Log.i(TAG, "[$step] $msg")
        onStatus?.invoke(step, msg)
    }
}
