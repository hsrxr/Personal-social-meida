package com.journal.glasses.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal on-glasses Compose UI: shows connection status and display text.
 *
 * This is intentionally sparse — glasses screen real estate is tiny.
 * Agent-C controls what text displays via the phone_cmd channel.
 */
@Composable
fun StatusScreen(
    isConnected: Boolean,
    displayText: String,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            // Connection indicator
            Text(
                text = if (isConnected) "●" else "○",
                color = if (isConnected) Color.Green else Color.Red,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Recording indicator
            if (isRecording) {
                Text(
                    text = "REC",
                    color = Color.Red,
                    fontSize = 10.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main display text (set by phone_cmd)
            Text(
                text = displayText.ifEmpty { " " },
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
