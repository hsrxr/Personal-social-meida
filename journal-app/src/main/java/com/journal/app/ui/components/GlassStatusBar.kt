package com.journal.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journal.cxrcore.link.LinkState

/**
 * Displays glasses connection status from [LinkState].
 */
@Composable
fun GlassStatusBar(
    linkState: LinkState,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    val connected = linkState == LinkState.SessionBuilt
    val tint by animateColorAsState(
        targetValue = if (connected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val statusText = when (linkState) {
        LinkState.Idle -> "眼镜未连接"
        LinkState.Connecting -> "连接中..."
        LinkState.LinkReady -> "链路就绪"
        LinkState.SessionBuilt -> "眼镜已连接"
        LinkState.Disconnected -> "已断开"
    }

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                connected -> Icons.Default.BluetoothConnected
                linkState == LinkState.Connecting -> Icons.Default.Bluetooth
                else -> Icons.Default.BluetoothDisabled
            },
            contentDescription = statusText,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        if (connected && batteryLevel > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "电量",
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$batteryLevel%",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}
