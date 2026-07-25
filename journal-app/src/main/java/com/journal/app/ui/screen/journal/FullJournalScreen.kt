package com.journal.app.ui.screen.journal

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.EntryType
import com.journal.app.ui.components.AudioPlayerBar
import com.journal.app.ui.components.HashtaggedText
import com.journal.app.ui.components.PhotoRow
import com.journal.app.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullJournalScreen(
    onBack: () -> Unit = {},
    onEditEntry: (String) -> Unit = {},
    viewModel: FullJournalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Journey", fontWeight = FontWeight.SemiBold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search your journal") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val days = uiState.filteredJournals
                if (days.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.query.isBlank()) "No journal entries yet."
                            else "No entries match \"${uiState.query}\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(days, key = { it.date.toString() }) { journal ->
                            JournalRailRow(journal = journal, onEditEntry = onEditEntry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalRailRow(journal: DailyJournal, onEditEntry: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Date rail
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = DateFormatter.formatEnglishShortDate(journal.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        JournalDayContent(journal = journal, onEditEntry = onEditEntry, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun JournalDayContent(
    journal: DailyJournal,
    onEditEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoEntries = journal.entries.filter { it.type == EntryType.PHOTO }
    val audioEntries = journal.entries.filter { it.type == EntryType.AUDIO }
    val noteEntries = journal.entries.filter { it.type == EntryType.NOTE }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary
            journal.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            // Photos — clickable to edit
            photoEntries.forEach { entry ->
                val url = entry.imageUrl
                if (url != null) {
                    Box(modifier = Modifier.fillMaxWidth().clickable { onEditEntry(entry.id) }) {
                        coil3.compose.AsyncImage(
                            model = url,
                            contentDescription = "Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }

            // Audio — clickable to edit
            audioEntries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEditEntry(entry.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioPlayerBar(
                        durationMs = (entry.durationMs ?: 0).toLong(),
                        audioUrl = entry.imageUrl,
                        modifier = Modifier.weight(1f),
                    )
                }
                entry.transcription?.let {
                    HashtaggedText(text = it)
                }
            }

            // Notes — clickable to edit
            noteEntries.forEach { entry ->
                entry.noteText?.let { text ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onEditEntry(entry.id) }) {
                        HashtaggedText(text = text)
                    }
                }
            }

            // Collect non-content entries (MOMENT_MARK, AGENT_DIALOG) as plain text
            val otherTexts = journal.entries
                .filter { it.type != EntryType.PHOTO && it.type != EntryType.AUDIO && it.type != EntryType.NOTE }
                .mapNotNull { it.noteText ?: it.transcription }
            if (otherTexts.isNotEmpty()) {
                otherTexts.forEach { text ->
                    HashtaggedText(text = text)
                }
            }
        }
    }
}
