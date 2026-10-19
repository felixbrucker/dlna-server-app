package com.felixbrucker.dlnaserver.feature.dashboard.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.dlnaserver.core.designsystem.theme.StatusGreen
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus

@Composable
fun ServerStatusCard(
    serverInfo: DlnaServerInfo,
    onToggleServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ServerStatusHeader(serverInfo = serverInfo, onToggleServer = onToggleServer)
            Spacer(modifier = Modifier.height(12.dp))
            ServerNetworkDetails(serverInfo = serverInfo)
        }
    }
}

@Composable
private fun ServerStatusHeader(
    serverInfo: DlnaServerInfo,
    onToggleServer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerStatusBadge(status = serverInfo.status)
        ServerPowerButton(isRunning = serverInfo.status == ServerStatus.RUNNING, onToggleServer = onToggleServer)
    }
}

@Composable
private fun ServerStatusBadge(status: ServerStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsatingStatusDot(status = status)
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedContent(targetState = status, label = "status_text") { targetStatus ->
            Text(
                text = targetStatus.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = getStatusColor(targetStatus)
            )
        }
    }
}

@Composable
private fun PulsatingStatusDot(status: ServerStatus) {
    val isRunning = status == ServerStatus.RUNNING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    val color by animateColorAsState(targetValue = getStatusColor(status), label = "dot_color")

    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(scale)
            .background(color, CircleShape)
    )
}

@Composable
private fun ServerPowerButton(
    isRunning: Boolean,
    onToggleServer: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) StatusGreen else MaterialTheme.colorScheme.primary,
        label = "power_color"
    )
    Surface(
        onClick = onToggleServer,
        shape = CircleShape,
        color = buttonColor
    ) {
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "Toggle Server",
            tint = Color.White,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ServerNetworkDetails(serverInfo: DlnaServerInfo) {
    Column {
        NetworkInterfaceRow(boundInterface = serverInfo.boundInterface, ipAddress = serverInfo.ipAddress)
        Spacer(modifier = Modifier.height(4.dp))
        ConnectedDevicesRow(count = serverInfo.connectedDeviceCount)
    }
}

@Composable
private fun NetworkInterfaceRow(boundInterface: String, ipAddress: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$boundInterface ($ipAddress)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectedDevicesRow(count: Int) {
    Text(
        text = "Connected devices: $count",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

private fun getStatusColor(status: ServerStatus): Color {
    return when (status) {
        ServerStatus.RUNNING -> StatusGreen
        ServerStatus.STARTING -> Color(0xFFF57C00)
        ServerStatus.ERROR -> Color(0xFFD32F2F)
        ServerStatus.STOPPED -> Color.Gray
    }
}
