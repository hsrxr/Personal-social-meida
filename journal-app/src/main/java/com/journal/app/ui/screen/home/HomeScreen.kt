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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.journal.app.data.model.DailyJournal
import com.journal.app.ui.components.SpeedDialAction
import com.journal.app.ui.components.SpeedDialFab
import com.journal.app.ui.states.HomeUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDayClick: (String) -> Unit,
    onViewFullJournalClick: () -> Unit,
    onGenerateClick: () -> Unit,
    onManualEditClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as android.app.Activity

    LaunchedEffect(Unit) {
        viewModel.startAutoConnect(activity)
    }

    // ── Phone camera (system camera intent) ──
    var photoFile by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && photoFile != null) {
            viewModel.savePhonePhoto(photoFile!!.absolutePath)
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startPhoneRecording()
    }

    fun launchCamera() {
        val file = viewModel.preparePhonePhotoFile()
        photoFile = file
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        photoLauncher.launch(uri)
    }
    fun toggleRecording() {
        if (uiState.isRecording) {
            viewModel.stopPhoneRecording()
        } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startPhoneRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Which speed-dial (if any) is open: "add", "ai", or null.
    var openDial by remember { mutableStateOf<String?>(null) }
    var showTextComposer by remember { mutableStateOf(false) }

    val addActions = listOf(
        SpeedDialAction("Text", Icons.Default.Notes) { showTextComposer = true },
        SpeedDialAction("Voice", Icons.Default.Mic) { toggleRecording() },
        SpeedDialAction("Photo", Icons.Default.PhotoCamera) { launchCamera() },
    )
    val aiActions = listOf(
        SpeedDialAction("Select Records", Icons.Default.AutoAwesome) { onGenerateClick() },
        SpeedDialAction("Time Period", Icons.Default.Schedule) { onGenerateClick() },
        SpeedDialAction("Keywords", Icons.Default.Tag) { onGenerateClick() },
        SpeedDialAction("Manual Edit", Icons.Default.Edit) { onManualEditClick() },
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ProfileHeader(uiState = uiState, onSettingsClick = onSettingsClick)
                }
                item {
                    Button(
                        onClick = onViewFullJournalClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("View Full Journal", fontWeight = FontWeight.SemiBold)
                    }
                }
                items(uiState.recentJournals, key = { it.date.toString() }) { journal ->
                    DailyJournalCard(
                        journal = journal,
                        onClick = { onDayClick(journal.date.toString()) },
                    )
                }
                if (uiState.recentJournals.isEmpty()) {
                    item { EmptyTimeline() }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }

        // Two stacked speed-dial FABs, AI above ＋.
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpeedDialFab(
                expanded = openDial == "ai",
                onExpandedChange = { openDial = if (it) "ai" else null },
                mainIcon = Icons.Default.AutoAwesome,
                mainContentDescription = "Generate with AI",
                actions = aiActions,
            )
            SpeedDialFab(
                expanded = openDial == "add",
                onExpandedChange = { openDial = if (it) "add" else null },
                mainIcon = Icons.Default.Add,
                mainContentDescription = "Add entry",
                actions = addActions,
            )
        }
    }

    if (showTextComposer) {
        TextComposerSheet(
            onDismiss = { showTextComposer = false },
            onSave = { text ->
                viewModel.addTextNote(text)
                showTextComposer = false
            },
        )
    }
}

@Composable
private fun ProfileHeader(uiState: HomeUiState, onSettingsClick: () -> Unit) {
    val profile = uiState.profile
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (profile?.avatarUrl != null) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.handle ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = profile?.tagline ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        if (profile != null) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("Entries", profile.entriesCount, Modifier.weight(1f))
                StatCell("Notes", profile.notesCount, Modifier.weight(1f))
                StatCell("Matched Friends", profile.matchedFriendsCount, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTimeline() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No entries yet.\nTap ＋ to capture your first moment.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextComposerSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("New note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("What's on your mind?") },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
