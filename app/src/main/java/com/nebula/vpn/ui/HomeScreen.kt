package com.nebula.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.nebula.vpn.R
import com.nebula.vpn.core.IpEndpoint
import com.nebula.vpn.model.ConnectionProfile
import com.nebula.vpn.model.ConnectionState
import com.nebula.vpn.model.isBusy
import com.nebula.vpn.model.isConnected
import com.nebula.vpn.ui.components.AmbientBackground
import com.nebula.vpn.ui.components.ButtonMode
import com.nebula.vpn.ui.components.ConnectButton
import com.nebula.vpn.ui.components.ConnectionMeta
import com.nebula.vpn.ui.components.DiagnosticsPanel
import com.nebula.vpn.ui.components.StatusLine
import com.nebula.vpn.ui.components.TrafficPanel
import com.nebula.vpn.ui.theme.NebulaAccent
import com.nebula.vpn.ui.theme.NebulaError
import com.nebula.vpn.ui.theme.NebulaOnDark
import com.nebula.vpn.ui.theme.NebulaOnDarkMuted
import com.nebula.vpn.ui.theme.NebulaPrimary
import com.nebula.vpn.ui.theme.NebulaPrimaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: ConnectionState,
    profile: ConnectionProfile,
    connectedSince: Long?,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
    onProfileChange: (ConnectionProfile) -> Unit,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = when {
        state.isConnected -> ButtonMode.CONNECTED
        state.isBusy -> ButtonMode.BUSY
        state is ConnectionState.Error -> ButtonMode.ERROR
        else -> ButtonMode.IDLE
    }

    val accent = when (mode) {
        ButtonMode.CONNECTED -> NebulaAccent
        ButtonMode.ERROR -> NebulaError
        ButtonMode.BUSY -> NebulaOnDark
        ButtonMode.IDLE -> NebulaPrimary
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val drawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsEnabled = state is ConnectionState.Idle || state is ConnectionState.Error

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    // App branding
                    Text(
                        text = "NEBULA",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                        ),
                        color = NebulaPrimary,
                    )
                    Text(
                        text = "VPN",
                        style = MaterialTheme.typography.titleSmall,
                        color = NebulaOnDarkMuted,
                    )

                    Spacer(Modifier.height(24.dp))

                    if (drawerVisible) {
                        DiagnosticsPanel()
                        Spacer(Modifier.height(16.dp))

                        SharePanel(
                            state = state,
                            profile = profile,
                            onProfileChange = onProfileChange,
                        )
                        Spacer(Modifier.height(16.dp))

                        AdvancedPanel(
                            profile = profile,
                            onProfileChange = onProfileChange,
                            enabled = settingsEnabled,
                        )
                        Spacer(Modifier.height(16.dp))

                        AboutPanel()
                    }
                }
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            AmbientBackground(accent = accent, active = state.isConnected)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // App title
                Text(
                    text = "NEBULA",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 8.sp,
                    ),
                    color = NebulaOnDark,
                )
                Text(
                    text = "VPN",
                    style = MaterialTheme.typography.titleMedium,
                    color = NebulaOnDarkMuted,
                    letterSpacing = 4.sp,
                )

                Spacer(Modifier.height(48.dp))

                // Connect button
                ConnectButton(mode = mode, onClick = onToggleConnection)

                Spacer(Modifier.height(40.dp))

                // Status
                StatusLine(
                    title = stateTitle(state),
                    subtitle = stateSubtitle(state),
                )

                // Traffic (animated visibility)
                AnimatedVisibility(
                    visible = state.isConnected,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(24.dp))
                        TrafficPanel(connectedSince = connectedSince)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Connection meta
                ConnectionMeta(
                    connected = state.isConnected,
                    connectedSince = connectedSince,
                    ipInfo = ipInfo,
                    ipLoading = ipLoading,
                )
            }

            // Menu button
            IconButton(
                onClick = { drawerScope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = NebulaOnDark,
                )
            }

            // Settings button
            IconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = NebulaOnDark,
                )
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
                var sheetReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { sheetReady = true }
                if (sheetReady) {
                    AdvancedPanel(
                        profile = profile,
                        onProfileChange = onProfileChange,
                        enabled = settingsEnabled,
                        startExpanded = true,
                    )
                } else {
                    Spacer(Modifier.height(320.dp))
                }
            }
        }
    }
}

@Composable
private fun stateTitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> stringResource(R.string.state_idle)
    is ConnectionState.Launching -> stringResource(R.string.state_launching)
    is ConnectionState.Connecting -> stringResource(R.string.state_connecting)
    is ConnectionState.Verifying -> stringResource(R.string.state_verifying)
    is ConnectionState.Connected -> stringResource(R.string.state_connected)
    is ConnectionState.Reconnecting -> stringResource(R.string.state_reconnecting)
    is ConnectionState.Disconnecting -> stringResource(R.string.state_disconnecting)
    is ConnectionState.Error -> stringResource(R.string.state_error)
}

@Composable
private fun stateSubtitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> "اتصال به سرور..."
    is ConnectionState.Connected -> "اتصال برقرار است"
    is ConnectionState.Reconnecting ->
        stringResource(R.string.reconnect_attempt, state.attempt, state.maxAttempts)
    is ConnectionState.Error -> state.message
    else -> "قطع اتصال..."
}
