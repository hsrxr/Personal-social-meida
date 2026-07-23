package com.journal.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.journal.app.data.model.EntryType
import com.journal.app.data.model.TimelineEntry
import com.journal.app.util.DateFormatter

@Composable
fun TimelineItem(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = if (entry.isStarred) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Type icon
            Icon(
                imageVector = entry.type.toIcon(),
                contentDescription = entry.type.name,
                tint = if (entry.isStarred) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Time
                Text(
                    text = DateFormatter.formatTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Location (if present)
                if (!entry.locationName.isNullOrBlank()) {
                    Text(
                        text = entry.locationName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Thumbnail for PHOTO / MOMENT_MARK
                if (entry.type == EntryType.PHOTO || entry.type == EntryType.MOMENT_MARK) {
                    entry.thumbnailUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "照片缩略图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                // Transcription / note text
                val displayText = entry.transcription ?: entry.noteText
                if (!displayText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tags
                if (entry.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        entry.tags.take(3).forEach { tag ->
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun EntryType.toIcon() = when (this) {
    EntryType.PHOTO -> Icons.Default.CameraAlt
    EntryType.AUDIO -> Icons.Default.Mic
    EntryType.NOTE -> Icons.Default.EditNote
    EntryType.AGENT_DIALOG -> Icons.Default.Chat
    EntryType.MOMENT_MARK -> Icons.Default.Star
}
