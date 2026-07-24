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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // ── Glasses management ──
            item { SettingsSection("Glasses") }
            item { SettingsItem(Icons.Default.Bluetooth, "Status", subtitle = uiState.statusMessage) }
            if (uiState.deviceName.isNotEmpty()) {
                item { SettingsItem(Icons.Default.Bluetooth, "Device", subtitle = uiState.deviceName) }
            }
            if (uiState.battery > 0) {
                item { SettingsItem(Icons.Default.Bluetooth, "Battery", subtitle = "${uiState.battery}%") }
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
                            ActionButton("1. Check Rokid AI installation", enabled = true) {
                                viewModel.checkInstallation(activity)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.rokidAppInstalled) "Installed ✅" else "Not detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.rokidAppInstalled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            )

                            ActionButton("2. Authorize", enabled = uiState.rokidAppInstalled) {
                                viewModel.requestAuth(activity)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.authDone) {
                                Text("Authorization complete ✅", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                            }

                            ActionButton(
                                text = if (uiState.isConnecting) "Connecting…" else "3. Connect glasses",
                                enabled = uiState.authDone && !uiState.isConnecting,
                            ) { viewModel.connectGlasses() }
                        }

                        uiState.linkState == LinkState.Connecting -> {
                            Text("Connecting to glasses…", style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }

                        uiState.linkState == LinkState.LinkReady && !uiState.appInstalled -> {
                            ActionButton("Install glasses app", enabled = !uiState.installing) {
                                viewModel.installGlassesApp("/data/local/tmp/glasses-journal-debug.apk")
                            }
                            if (uiState.installing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }

                        uiState.linkState == LinkState.LinkReady && uiState.appInstalled -> {
                            ActionButton("Launch glasses app", enabled = true) { viewModel.startGlassesApp() }
                        }

                        uiState.linkState == LinkState.SessionBuilt -> {
                            Text("🎉 Glasses app running", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Button(onClick = { viewModel.stopGlassesApp() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.disconnectGlasses() },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reconnect")
                                }
                            }
                        }
                    }
                }
            }

            // ── Profile ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("Profile") }
            item { SettingsItem(Icons.Default.Person, "My Profile") { onProfileClick() } }

            // ── Sync ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("Sync") }
            item { SettingsItem(Icons.Default.Download, "Auto-sync", subtitle = "Wi-Fi only") }
            item { SettingsItem(Icons.Default.Notifications, "Push notifications", subtitle = "On") }

            // ── Privacy ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("Privacy") }
            item { SettingsItem(Icons.Default.Lock, "Location sharing", subtitle = "Off") }

            // ── Data ──
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SettingsSection("Data") }
            item { SettingsItem(Icons.Default.Delete, "Clear cache", subtitle = "Media kept for 30 days") }

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
