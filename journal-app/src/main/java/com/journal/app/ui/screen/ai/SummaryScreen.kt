package com.journal.app.ui.screen.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.Visibility
import com.journal.app.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(
    onSocialCopyClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.published) {
        if (uiState.published) {
            snackbarHostState.showSnackbar("Published successfully!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Summary") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Material selection ──
            item {
                Text("Select source materials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose which entries to use for AI summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.materials.isEmpty()) {
                item {
                    Text(
                        text = "No entries available for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.materials, key = { it.id }) { entry ->
                    val isSelected = entry.id in uiState.selectedMaterialIds
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface,
                        ),
                        onClick = { viewModel.toggleMaterial(entry.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleMaterial(entry.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (entry.type) {
                                        EntryType.PHOTO -> "📷 Photo"
                                        EntryType.AUDIO -> "🎤 Voice"
                                        EntryType.NOTE -> "📝 Note"
                                        EntryType.MOMENT_MARK -> "⭐ Moment"
                                        EntryType.AGENT_DIALOG -> "💬 AI Chat"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val preview = entry.noteText ?: entry.transcription?.take(80)
                                if (preview != null) {
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                if (entry.locationName != null) {
                                    Text(
                                        text = "📍 ${entry.locationName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Generate button ──
            item {
                Button(
                    onClick = viewModel::loadSummary,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedMaterialIds.isNotEmpty() && !uiState.isGenerating,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (uiState.isGenerating) "Generating…" else "Generate Summary")
                }
            }

            // ── Loading ──
            if (uiState.isGenerating) {
                item {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // ── Summary content or editing mode ──
            val summary = uiState.summary
            if (summary != null && !uiState.isGenerating) {
                if (uiState.isEditing) {
                    // ── Editing mode ──
                    item {
                        Text("Edit Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    item {
                        OutlinedTextField(
                            value = uiState.editedNarrative,
                            onValueChange = viewModel::onNarrativeChange,
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            label = { Text("Narrative") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    item {
                        Text("Keywords", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            uiState.editedKeywords.forEach { kw ->
                                AssistChip(
                                    onClick = { viewModel.onKeywordRemove(kw) },
                                    label = { Text(kw) },
                                    shape = RoundedCornerShape(20.dp),
                                )
                            }
                        }
                        // Add keyword input
                        var newKeyword by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newKeyword,
                                onValueChange = { newKeyword = it },
                                modifier = Modifier.weight(1f).height(56.dp),
                                placeholder = { Text("Add keyword") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            IconButton(onClick = {
                                viewModel.onKeywordAdd(newKeyword)
                                newKeyword = ""
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add keyword")
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = viewModel::cancelEditing, modifier = Modifier.weight(1f)) {
                                Text("Cancel")
                            }
                            Button(onClick = viewModel::saveEdits, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Save")
                            }
                        }
                    }
                } else {
                    // ── Display mode ──
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::startEditing) {
                                Text("Edit")
                            }
                        }
                    }
                    if (summary.keywords.isNotEmpty()) {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                summary.keywords.forEach { keyword ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(keyword) },
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (!summary.mood.isNullOrEmpty()) {
                        item {
                            Text("Mood: ${summary.mood}", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Text(
                                text = summary.narrative,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    if (!summary.highlight.isNullOrEmpty()) {
                        item {
                            Text("Highlight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(summary.highlight, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // ── Visibility toggle ──
                    item {
                        Text("Visibility", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Visibility.entries.forEach { vis ->
                                FilterChip(
                                    selected = uiState.visibility == vis,
                                    onClick = { viewModel.setVisibility(vis) },
                                    label = {
                                        Text(
                                            when (vis) {
                                                Visibility.PUBLIC -> "🌐 Public"
                                                Visibility.PRIVATE -> "🔒 Private"
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // ── Generate social copies + Publish ──
                    item {
                        Button(
                            onClick = onSocialCopyClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Generate social copy")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = viewModel::publish,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Publish to Feed")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
