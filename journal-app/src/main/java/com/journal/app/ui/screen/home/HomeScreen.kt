package com.journal.app.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.TimelineEntry
import com.journal.app.ui.components.GlassStatusBar
import com.journal.app.ui.states.HomeUiState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEntryClick: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onSummaryClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onMatchClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as android.app.Activity

    // Auto-connect on first composition
    LaunchedEffect(Unit) {
        viewModel.startAutoConnect(activity)
    }

    // ── Phone camera (system camera intent) ──
    var photoFile by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            viewModel.savePhonePhoto(photoFile!!.absolutePath)
        }
    }

    // ── Phone audio recording permission ──
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startPhoneRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "日志",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = uiState.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMatchClick) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "匹配",
                        )
                    }
                    IconButton(onClick = onCalendarClick) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "日历",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
            ) {
                // Capture photo button — uses system camera
                SmallFloatingActionButton(
                    onClick = {
                        val file = viewModel.preparePhonePhotoFile()
                        photoFile = file
                        val uri = FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileprovider",
                            file,
                        )
                        photoLauncher.launch(uri)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "拍照",
                    )
                }
                // Record / Stop button — uses phone mic
                FloatingActionButton(
                    onClick = {
                        if (uiState.isRecording) {
                            viewModel.stopPhoneRecording()
                        } else {
                            if (ContextCompat.checkSelfPermission(
                                    activity,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.startPhoneRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    containerColor = if (uiState.isRecording)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        if (uiState.isRecording) Icons.Default.Stop
                        else Icons.Default.FiberManualRecord,
                        contentDescription = if (uiState.isRecording) "停止录音" else "开始录音",
                    )
                }
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Glass connection status
                item {
                    GlassStatusBar(
                        linkState = uiState.glassLinkState,
                        batteryLevel = uiState.glassBattery,
                    )
                }

                // Timeline entries
                items(uiState.entries, key = { it.id }) { entry ->
                    TimelineEntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry.id) },
                    )
                }

                // Empty state
                if (uiState.entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今天的素材还在路上...\n戴上眼镜开始记录吧",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // AI summary preview
                if (uiState.summaryPreview != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSummaryClick() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "AI 每日总结",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.summaryPreview!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Bottom spacer
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun TimelineEntryCard(
    entry: TimelineEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Time column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp),
            ) {
                Text(
                    text = formatTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Type icon
                val icon = when (entry.type) {
                    EntryType.PHOTO -> Icons.Default.CameraAlt
                    EntryType.AUDIO -> Icons.Default.Mic
                    EntryType.MOMENT_MARK -> Icons.Default.Star
                    EntryType.AGENT_DIALOG -> Icons.Default.Chat
                    EntryType.NOTE -> Icons.Default.Star
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when (entry.type) {
                                EntryType.MOMENT_MARK -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                EntryType.AGENT_DIALOG -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = entry.type.name,
                        modifier = Modifier.size(16.dp),
                        tint = when (entry.type) {
                            EntryType.MOMENT_MARK -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content column
            Column(modifier = Modifier.weight(1f)) {
                // Entry type label
                val typeLabel = when (entry.type) {
                    EntryType.PHOTO -> "照片"
                    EntryType.AUDIO -> "语音笔记"
                    EntryType.MOMENT_MARK -> "重要时刻"
                    EntryType.AGENT_DIALOG -> "AI 对话"
                    EntryType.NOTE -> "笔记"
                }
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Photo thumbnail
                val imageUrl = entry.thumbnailUrl ?: entry.imageUrl
                if (entry.type == EntryType.PHOTO && imageUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "照片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                // Transcription / note text
                val text = entry.transcription ?: entry.noteText
                if (text != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Location + tags row
                if (entry.locationName != null || entry.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (entry.locationName != null) {
                            Text(
                                text = entry.locationName!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        entry.tags.take(3).forEach { tag ->
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Audio duration
                if (entry.type == EntryType.AUDIO && entry.durationMs != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${entry.durationMs!! / 1000}秒",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(epochMs)
        val localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}
