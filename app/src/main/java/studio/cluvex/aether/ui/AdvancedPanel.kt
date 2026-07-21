package studio.cluvex.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import studio.cluvex.aether.R
import studio.cluvex.aether.model.ConnectionProfile
import studio.cluvex.aether.model.IpVersion
import studio.cluvex.aether.model.Protocol
import studio.cluvex.aether.model.ScanMode
import studio.cluvex.aether.ui.components.SegmentedSelector

/**
 * Collapsible "Advanced" card exposing exactly the same knobs as the desktop
 * build: protocol, scan mode, IP version, quick-reconnect and MASQUE HTTP/2.
 */
@Composable
fun AdvancedPanel(
    profile: ConnectionProfile,
    onProfileChange: (ConnectionProfile) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    // True when hosted in the home-screen bottom sheet, where the card should
    // open already expanded instead of requiring an extra tap.
    startExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "arrow")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  " + stringResource(R.string.advanced),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(16.dp))

                    SettingLabel(stringResource(R.string.protocol))
                    SegmentedSelector(
                        options = Protocol.entries,
                        selected = profile.protocol,
                        onSelect = { onProfileChange(profile.copy(protocol = it)) },
                        label = { protocolLabel(it) },
                        enabled = enabled,
                    )
                    Spacer(Modifier.height(16.dp))

                    SettingLabel(stringResource(R.string.scan_mode))
                    SegmentedSelector(
                        options = ScanMode.entries,
                        selected = profile.scanMode,
                        onSelect = { onProfileChange(profile.copy(scanMode = it)) },
                        label = { scanLabel(it) },
                        enabled = enabled,
                    )
                    Spacer(Modifier.height(16.dp))

                    SettingLabel(stringResource(R.string.ip_version))
                    SegmentedSelector(
                        options = IpVersion.entries,
                        selected = profile.ipVersion,
                        onSelect = { onProfileChange(profile.copy(ipVersion = it)) },
                        label = { ipLabel(it) },
                        enabled = enabled,
                    )
                    Spacer(Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    )

                    ToggleRow(
                        title = stringResource(R.string.quick_reconnect),
                        description = stringResource(R.string.quick_reconnect_desc),
                        checked = profile.quickReconnect,
                        enabled = enabled,
                        onChange = { onProfileChange(profile.copy(quickReconnect = it)) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.masque_http2),
                        description = stringResource(R.string.masque_http2_desc),
                        checked = profile.masqueHttp2,
                        enabled = enabled,
                        onChange = { onProfileChange(profile.copy(masqueHttp2 = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.AUTO -> stringResource(R.string.protocol_auto)
    Protocol.MASQUE -> stringResource(R.string.protocol_masque)
    Protocol.WIREGUARD -> stringResource(R.string.protocol_wireguard)
    Protocol.GOOL -> stringResource(R.string.protocol_gool)
}

@Composable
private fun scanLabel(mode: ScanMode): String = when (mode) {
    ScanMode.TURBO -> stringResource(R.string.scan_turbo)
    ScanMode.BALANCED -> stringResource(R.string.scan_balanced)
    ScanMode.THOROUGH -> stringResource(R.string.scan_thorough)
    ScanMode.STEALTH -> stringResource(R.string.scan_stealth)
}

@Composable
private fun ipLabel(ip: IpVersion): String = when (ip) {
    IpVersion.V4 -> stringResource(R.string.ip_v4)
    IpVersion.V6 -> stringResource(R.string.ip_v6)
    IpVersion.BOTH -> stringResource(R.string.ip_both)
}
