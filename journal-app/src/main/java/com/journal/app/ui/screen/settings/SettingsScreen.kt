package com.journal.app.ui.screen.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journal.cxrcore.link.LinkState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onProfileClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // ── 眼镜管理 ──
            item { SettingsSection("眼镜管理") }
            item { SettingsItem(Icons.Default.Bluetooth, "状态", subtitle = uiState.statusMessage) }
            if (uiState.deviceName.isNotEmpty()) {
                item { SettingsItem(Icons.Default.Bluetooth, "设备", subtitle = uiState.deviceName) }
            }
            if (uiState.battery > 0) {
                item { SettingsItem(Icons.Default.Bluetooth, "电量", subtitle = "${uiState.battery}%") }
            }

            // Connection action buttons — show only relevant ones based on state
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    when {
                        uiState.linkState == LinkState.Idle || uiState.linkState == LinkState.Disconnected -> {
                            ActionButton("1. 检测Rokid AI安装", enabled = true) {
                                viewModel.checkInstallation(activity)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.rokidAppInstalled) "已安装 ✅" else "未检测",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.rokidAppInstalled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            )

                            ActionButton("2. 发起鉴权", enabled = uiState.rokidAppInstalled) {
                                viewModel.requestAuth(activity)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.authDone) {
                                Text("鉴权完成 ✅", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                            }

                            ActionButton(
                                text = if (uiState.isConnecting) "连接中..." else "3. 连接眼镜",
                                enabled = uiState.authDone && !uiState.isConnecting,
                            ) { viewModel.connectGlasses() }
                        }

                        uiState.linkState == LinkState.Connecting -> {
                            Text("正在连接眼镜...", style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }

                        uiState.linkState == LinkState.LinkReady && !uiState.appInstalled -> {
                            ActionButton("安装眼镜App", enabled = !uiState.installing) {
                                viewModel.installGlassesApp("/data/local/tmp/glasses-journal-debug.apk")
                            }
                            if (uiState.installing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }

                        uiState.linkState == LinkState.LinkReady && uiState.appInstalled -> {
                            ActionButton("启动眼镜App", enabled = true) { viewModel.startGlassesApp() }
                        }

                        uiState.linkState == LinkState.SessionBuilt -> {
                            Text("🎉 眼镜App运行中", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Button(onClick = { viewModel.stopGlassesApp() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("停止")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.disconnectGlasses() },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("断开重连")
                                }
                            }
                        }
                    }
                }
            }

            // ── 个人 ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("个人") }
            item { SettingsItem(Icons.Default.Person, "个人资料") { onProfileClick() } }

            // ── 同步 ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("同步") }
            item { SettingsItem(Icons.Default.Download, "自动同步", subtitle = "WiFi-Only") }
            item { SettingsItem(Icons.Default.Notifications, "推送通知", subtitle = "已开启") }

            // ── 隐私 ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("隐私") }
            item { SettingsItem(Icons.Default.Lock, "位置共享", subtitle = "已关闭") }

            // ── 数据 ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("数据") }
            item { SettingsItem(Icons.Default.Delete, "清除缓存", subtitle = "素材保留30天") }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) { Text(text) }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
