package com.journal.app.ui.components

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journal.app.data.model.CommonDetail
import com.journal.app.data.model.MatchCard as MatchCardModel

@Composable
fun MatchCardView(
    match: MatchCardModel,
    onGreet: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nickname
            Text(
                text = "🧑 @${match.matchedUserNickname}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Common details
            match.commonDetails.forEach { detail ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = detail.type.toIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = detail.toDisplayText(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ice break preview
            Text(
                text = match.iceBreakMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Skip")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onGreet,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Say Hi")
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun String.toIcon() = when (this) {
    "location" -> androidx.compose.material.icons.Icons.Default.ChevronRight // placeholder
    "mood" -> androidx.compose.material.icons.Icons.Default.ChevronRight
    "tag" -> androidx.compose.material.icons.Icons.Default.ChevronRight
    "activity" -> androidx.compose.material.icons.Icons.Default.ChevronRight
    else -> androidx.compose.material.icons.Icons.Default.ChevronRight
}

private fun CommonDetail.toDisplayText(): String = when (type) {
    "location" -> "You were both at $value today"
    "mood" -> "You both logged \"$value\" mood"
    "tag" -> "Shared tag: #$value"
    "activity" -> "You both did: $value"
    else -> value
}
