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
            report("完成", "眼镜已连接")
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

        report("完成", "🎉 眼镜已连接，开始记录")
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
        report("检测", "检测 Rokid AI 应用...")
        val ok = AuthService.isRokidAppInstalled(activity) || AuthService.isHiRokidInstalled(activity)
        report("检测", if (ok) "Rokid AI 应用已安装 ✅" else "未安装 Rokid AI 应用，请先安装")
        return ok
    }

    private suspend fun getOrRequestToken(activity: Activity): String? {
        val cached = prefs.getString(KEY_TOKEN, null)
        if (!cached.isNullOrBlank()) {
            report("鉴权", "使用已保存的 token ✅")
            return cached
        }

        report("鉴权", "请求授权...")
        val immediate = AuthService.requestAuth(
            activity,
            arrayOf(GlassPermission.MICROPHONE, GlassPermission.CAMERA, GlassPermission.MEDIA),
        )

        if (immediate != null) {
            val res = AuthService.parseAuthResult(immediate.first, immediate.second)
            if (res is AuthResult.AuthSuccess) {
                prefs.edit().putString(KEY_TOKEN, res.token).apply()
                report("鉴权", "鉴权成功 ✅")
                return res.token
            }
            report("鉴权", "鉴权失败")
            return null
        }

        // Auth UI launched — wait for user
        report("鉴权", "请在弹窗中授权...")
        onAuthRequired?.invoke()
        authDeferred = CompletableDeferred()
        val token = authDeferred!!.await()
        return token.ifBlank { null }
    }

    private suspend fun connectToGlasses(token: String): Boolean {
        val ls = sessionManager.linkState.value
        if (ls == LinkState.LinkReady || ls == LinkState.SessionBuilt) {
            report("连接", "已连接 ✅")
            return true
        }

        report("连接", "正在连接眼镜...")
        val res = sessionManager.connect(token)
        if (res.isFailure) {
            report("连接", "连接失败: ${res.exceptionOrNull()?.message}")
            return false
        }

        // Wait for LinkReady or SessionBuilt
        val state = withTimeoutOrNull(TIMEOUT_LINK_MS) {
            sessionManager.linkState.filter {
                it == LinkState.LinkReady || it == LinkState.SessionBuilt || it == LinkState.Disconnected
            }.first()
        }
        if (state == null || state == LinkState.Disconnected) {
            report("连接", "等待 LinkReady 超时")
            return false
        }
        report("连接", "链路就绪 ✅")
        return true
    }

    private suspend fun ensureGlassesAppInstalled(): Boolean {
        val apk = File(GLASSES_APK_PATH)
        if (!apk.exists() || !apk.canRead()) {
            report("安装", "找不到眼镜 APK: $GLASSES_APK_PATH")
            return false
        }

        val savedVersion = prefs.getLong(KEY_GLASSES_APK_VERSION, -1L)
        val currentVersion = apk.lastModified()

        if (savedVersion == currentVersion && sessionManager.appInstalled.value) {
            report("安装", "眼镜 App 已是最新版本，跳过安装 ✅")
            return true
        }

        report("安装", "正在安装眼镜 App (${apk.length() / 1024}KB)...")
        sessionManager.installGlassesApp(apk.absolutePath)

        // Wait for install callback
        val ok = withTimeoutOrNull(TIMEOUT_INSTALL_MS) {
            sessionManager.appInstalled.filter { it }.first()
        }
        if (ok != true) {
            report("安装", "安装超时")
            return false
        }

        prefs.edit().putLong(KEY_GLASSES_APK_VERSION, currentVersion).apply()
        report("安装", "安装完成 ✅")
        return true
    }

    private suspend fun startGlasses(): Boolean {
        if (sessionManager.appOpened.value) {
            report("启动", "眼镜 App 已在运行 ✅")
            return true
        }

        report("启动", "正在启动眼镜 App...")
        sessionManager.startGlassesApp()

        val state = withTimeoutOrNull(TIMEOUT_SESSION_MS) {
            sessionManager.linkState.filter {
                it == LinkState.SessionBuilt || it == LinkState.Disconnected
            }.first()
        }
        if (state != LinkState.SessionBuilt) {
            report("启动", "启动超时")
            return false
        }
        report("启动", "眼镜 App 运行中 ✅")
        return true
    }

    private fun report(step: String, msg: String) {
        Log.i(TAG, "[$step] $msg")
        onStatus?.invoke(step, msg)
    }
}
