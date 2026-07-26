package com.nebula.vpn.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.nebula.vpn.R
import com.nebula.vpn.core.IpEndpoint
import com.nebula.vpn.core.NetProbe

/**
 * Shows, under the main button:
 *   - the IP + country flag (exit server when connected, operator IP when not),
 *   - a live HH:MM:SS uptime counter while connected.
 */
@Composable
fun ConnectionMeta(
    connected: Boolean,
    connectedSince: Long?,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IpBadge(connected = connected, ipInfo = ipInfo, ipLoading = ipLoading)

        if (connectedSince != null) {
            ConnectionTimer(connectedSince = connectedSince)
        }
    }
}

@Composable
private fun IpBadge(
    connected: Boolean,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
) {
    val label = if (connected) {
        stringResourceSafe(R.string.ip_server_label)
    } else {
        stringResourceSafe(R.string.ip_your_label)
    }

    val flag = NetProbe.flagEmoji(ipInfo?.countryCode)
    val value = when {
        ipLoading && ipInfo == null -> stringResourceSafe(R.string.ip_checking)
        ipInfo != null -> "$flag  ${ipInfo.ip}"
        else -> stringResourceSafe(R.string.ip_unavailable)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ip",
            ) { shown ->
                Text(
                    text = shown,
                    // BiDi fix: "104.28.197.15" + country flag is LTR technical
                    // text; in the Persian (RTL) locale the BiDi algorithm
                    // reordered the digits/dots. Pin the direction to LTR.
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ConnectionTimer(connectedSince: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSince) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsed = (now - connectedSince).coerceAtLeast(0L) / 1000L
    val h = elapsed / 3600
    val m = (elapsed % 3600) / 60
    val s = elapsed % 60
    val text = "%02d:%02d:%02d".format(h, m, s)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResourceSafe(R.string.connected_for),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun stringResourceSafe(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
