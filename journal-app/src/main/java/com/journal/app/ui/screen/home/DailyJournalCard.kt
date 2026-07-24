package com.journal.app.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journal.app.data.model.DailyJournal
import com.journal.app.data.model.EntryType
import com.journal.app.ui.components.AudioPlayerBar
import com.journal.app.ui.components.PhotoRow
import com.journal.app.util.DateFormatter

/**
 * One day's card on the Home timeline: date, the day's text (summary or first note),
 * a photo row, and an audio player when the day has a voice entry.
 */
@Composable
fun DailyJournalCard(
    journal: DailyJournal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoUrls = journal.entries
        .filter { it.type == EntryType.PHOTO }
        .mapNotNull { it.imageUrl }
    val audioEntry = journal.entries.firstOrNull { it.type == EntryType.AUDIO }
    val bodyText = journal.summary
        ?: journal.entries.firstOrNull { it.type == EntryType.NOTE }?.noteText

    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = DateFormatter.formatEnglishDate(journal.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (bodyText != null) {
                Text(text = bodyText, style = MaterialTheme.typography.bodyMedium)
            }
            if (photoUrls.isNotEmpty()) {
                PhotoRow(imageUrls = photoUrls)
            }
            if (audioEntry != null) {
                AudioPlayerBar(
                    durationMs = (audioEntry.durationMs ?: 0).toLong(),
                    audioUrl = audioEntry.imageUrl,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
