package com.journal.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journal.cxrcore.auth.AuthService
import com.journal.cxrcore.link.LinkState
import com.journal.cxrcore.pipeline.audio.AudioChunk
import com.journal.cxrcore.pipeline.photo.PhotoCapture
import com.journal.cxrcore.session.SessionManager
import com.journal.cxrcore.util.WavBuilder
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<DebugViewModel>()

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onAuthResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Detect if we came back from Rokid AI auth
        if (savedInstanceState == null) {
            intent.getStringExtra("token")?.let { viewModel.onTokenFromHistory(it) }
        }

        setContent {
            MaterialTheme {
                DebugScreen(
                    viewModel = viewModel,
                    onCheckInstalled = { viewModel.checkRokidAppInstalled(this@MainActivity) },
                    onRequestAuth = { viewModel.requestAuth(this@MainActivity, authLauncher) },
                    onConnect = { viewModel.connect(this@MainActivity) },
                    onInstallApk = { viewModel.installApk() },
                    onStartApp = { viewModel.startApp() },
                    onStopApp = { viewModel.stopApp() },
                    onTakePhoto = { viewModel.takePhoto() },
                    onStartAudio = { viewModel.startAudio() },
                    onStopAudio = { viewModel.stopAudio() },
                    onSendTestCmd = { viewModel.sendTestCommand() },
                    onRefreshDevice = { viewModel.refreshDeviceInfo() },
                    onSaveLastWav = { viewModel.saveLastWav(this@MainActivity) },
                )
            }
        }
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            viewModel.onAuthResult(resultCode, data)
        }
    }
}

// ── Compose Debug UI ──────────────────────────────────────────────────────────

@Composable
fun DebugScreen(
    viewModel: DebugViewModel,
    onCheckInstalled: () -> Unit,
    onRequestAuth: () -> Unit,
    onConnect: () -> Unit,
    onInstallApk: () -> Unit,
    onStartApp: () -> Unit,
    onStopApp: () -> Unit,
    onTakePhoto: () -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSendTestCmd: () -> Unit,
    onRefreshDevice: () -> Unit,
    onSaveLastWav: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Journal Debug Console", style = MaterialTheme.typography.titleLarge)

        // Status log
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.log,
                modifier = Modifier.padding(8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        // Step 1: Check Rokid App
        SectionHeader("1. Companion App")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCheckInstalled, enabled = !state.busy) {
                Text("检测安装")
            }
            Text(
                "Rokid AI: ${if (state.rokidInstalled) "✅" else "❌"}  " +
                    "Auth: ${if (state.authenticated) "✅" else "❌"}",
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
            )
        }

        // Step 2: Auth
        SectionHeader("2. 鉴权")
        Button(onClick = onRequestAuth, enabled = state.rokidInstalled && !state.authenticated) {
            Text("发起鉴权")
        }

        // Step 3: Connect
        SectionHeader("3. 连接")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect, enabled = state.authenticated && state.linkState == LinkState.Idle) {
                Text("建立连接")
            }
            Text(
                "Link: ${state.linkState.name}",
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
            )
            Button(onClick = onRefreshDevice, enabled = state.linkState >= LinkState.LinkReady) {
                Text("设备信息")
            }
        }

        // Step 4: Install glasses APK
        SectionHeader("4. 眼镜 App")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInstallApk, enabled = state.linkState >= LinkState.LinkReady && !state.appInstalled && !state.installing) {
                Text(if (state.installing) "安装中..." else "安装眼镜App")
            }
            Button(onClick = onStartApp, enabled = state.appInstalled && !state.appOpened) {
                Text("启动眼镜App")
            }
            Button(onClick = onStopApp, enabled = state.appOpened) {
                Text("停止")
            }
        }
        Text("已安装: ${if (state.appInstalled) "✅" else "❌"}  已启动: ${if (state.appOpened) "✅" else "❌"}")

        // Step 5: Capabilities (only when session built)
        val capsReady = state.linkState == LinkState.SessionBuilt
        SectionHeader("5. 能力测试 (${if (capsReady) "就绪" else "等待会话构建"})")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTakePhoto, enabled = capsReady && !state.photoTaking) {
                Text(if (state.photoTaking) "拍照中..." else "📷 拍照")
            }
            Text(
                if (state.lastPhotoInfo != null) "照片: ${state.lastPhotoInfo}" else "",
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
                fontSize = 11.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartAudio, enabled = capsReady && !state.audioActive) {
                Text("🎤 开始录音")
            }
            Button(onClick = onStopAudio, enabled = capsReady && state.audioActive) {
                Text("⏹ 停止")
            }
        }
        Text(
            "录音: ${if (state.audioActive) "🔴 收集中..." else "空闲"}  " +
                "收到片段: ${state.audioChunkCount}",
            fontSize = 11.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSendTestCmd, enabled = capsReady) {
                Text("📨 发送测试指令")
            }
            Button(onClick = onSaveLastWav, enabled = state.lastWavPath != null) {
                Text("💾 保存WAV")
            }
        }

        // Device info
        if (state.deviceInfo.isNotEmpty()) {
            SectionHeader("设备信息")
            Text(state.deviceInfo, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
