package com.journal.app.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.journal.app.util.DateFormatter
import kotlin.math.abs
import kotlin.math.sin

/**
 * Audio row: a play/stop toggle, a decorative waveform, and a duration label.
 *
 * Playback is best-effort — it starts a [MediaPlayer] on [audioUrl] when present and
 * releases it on stop/dispose. The waveform is purely decorative (no real amplitude
 * data is available for these entries); it exists to match the mockup.
 */
@Composable
fun AudioPlayerBar(
    durationMs: Long,
    audioUrl: String?,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var player by remember(audioUrl) { mutableStateOf<MediaPlayer?>(null) }

    fun stop() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
        isPlaying = false
    }

    DisposableEffect(audioUrl) {
        onDispose { stop() }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (isPlaying) {
                            stop()
                        } else if (audioUrl != null) {
                            val mp = MediaPlayer()
                            runCatching {
                                mp.setDataSource(audioUrl)
                                mp.setOnCompletionListener { stop() }
                                mp.prepare()
                                mp.start()
                            }.onSuccess {
                                player = mp
                                isPlaying = true
                            }.onFailure {
                                mp.release()
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Waveform(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            )

            Text(
                text = DateFormatter.formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Static decorative bars — not tied to real audio amplitude. */
@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barCount = 28
        val gap = size.width / barCount
        val midY = size.height / 2f
        for (i in 0 until barCount) {
            // Deterministic pseudo-waveform via a couple of sine terms.
            val amp = (abs(sin(i * 0.7f)) * 0.6f + abs(sin(i * 1.9f)) * 0.4f)
            val half = (amp * midY).coerceAtLeast(2f)
            val x = i * gap + gap / 2f
            drawLine(
                color = color,
                start = Offset(x, midY - half),
                end = Offset(x, midY + half),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}
