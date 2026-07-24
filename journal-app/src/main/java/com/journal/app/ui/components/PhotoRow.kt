package com.journal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A row of up to three photo thumbnails, sized to fill the width evenly.
 * Extra photos beyond the third are not shown (matches the mockup's 1–3 grid).
 */
@Composable
fun PhotoRow(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.isEmpty()) return
    val shown = imageUrls.take(3)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (shown.size == 1) Modifier.height(200.dp)
                        else Modifier.aspectRatio(1f)
                    )
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
