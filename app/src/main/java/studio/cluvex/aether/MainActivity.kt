package studio.cluvex.aether

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.cluvex.aether.core.AetherController
import studio.cluvex.aether.core.IpEndpoint
import studio.cluvex.aether.core.NetProbe
import studio.cluvex.aether.core.TunnelConfig
import studio.cluvex.aether.data.ProfileStore
import studio.cluvex.aether.model.ConnectionProfile
import studio.cluvex.aether.model.ConnectionState
import studio.cluvex.aether.model.isBusy
import studio.cluvex.aether.model.isConnected
import studio.cluvex.aether.ui.HomeScreen
import studio.cluvex.aether.ui.theme.AetherTheme

class MainActivity : ComponentActivity() {

    private lateinit var profileStore: ProfileStore

    // Holds the profile to connect with once VPN consent is granted.
    private var pendingProfile: ConnectionProfile? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingProfile?.let { AetherController.connect(this, it) }
            }
            pendingProfile = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        profileStore = ProfileStore(applicationContext)

        maybeRequestNotificationPermission()

        // Launched from the Quick Settings tile while VPN consent was still
        // missing: run the normal connect flow, which shows the system's VPN
        // consent dialog and then connects.
        if (intent?.getBooleanExtra(EXTRA_CONNECT_ON_LAUNCH, false) == true) {
            intent.removeExtra(EXTRA_CONNECT_ON_LAUNCH)
            lifecycleScope.launch {
                val current = AetherController.state.value
                if (!current.isConnected && !current.isBusy) {
                    toggleConnection(current)
                }
            }
        }

        setContent {
            AetherTheme {
                val state by AetherController.state.collectAsState()
                val profile by profileStore.profile.collectAsState(initial = ConnectionProfile())
                val connectedSince by AetherController.connectedSince.collectAsState()
                val ipInfo by AetherController.ipInfo.collectAsState()
                val ipLoading by AetherController.ipLoading.collectAsState()

                // Refresh the shown IP whenever the connection phase flips:
                //  - connected  -> exit server IP (through the SOCKS proxy) + flag
                //  - idle       -> the user's real operator IP (direct)
                // NOTE: any resting, non-busy state (Idle OR Error/failed
                // connect) must show the real IP — previously Error fell into
                // "busy" and the operator IP was never fetched after a failure.
                val phase = when {
                    state.isConnected -> "connected"
                    state.isBusy -> "busy"
                    else -> "idle"
                }
                LaunchedEffect(phase) {
                    when (phase) {
                        "connected" -> {
                            // FLAG-FLICKER FIX: the automatic self-test
                            // (Diagnostics) is the single owner of the exit-IP
                            // lookup. This block used to fire its OWN parallel
                            // lookup; whichever finished last overwrote the
                            // badge, and because geo providers can disagree
                            // about the exit country, the flag flickered or
                            // suddenly changed. Now we only WAIT for the
                            // self-test's result and fetch ourselves purely as
                            // a last-resort fallback (guarded by
                            // offerTunnelIpInfo, so it can never overwrite).
                            AetherController.setIpLoading(true)
                            val deadline = System.currentTimeMillis() + 100_000L
                            while (AetherController.ipInfo.value?.viaTunnel != true &&
                                System.currentTimeMillis() < deadline
                            ) {
                                delay(1000L)
                            }
                            if (AetherController.ipInfo.value?.viaTunnel != true) {
                                val info = withContext(Dispatchers.IO) {
                                    NetProbe.fetchIpInfoViaSocksWithRetry(
                                        TunnelConfig.SOCKS_HOST,
                                        TunnelConfig.SOCKS_PORT,
                                    )
                                }
                                if (info != null) {
                                    AetherController.offerTunnelIpInfo(
                                        IpEndpoint(info.ip, info.countryCode, true),
                                    )
                                }
                            }
                            AetherController.setIpLoading(false)
                        }
                        "idle" -> {
                            AetherController.setIpInfo(null)
                            AetherController.setIpLoading(true)
                            val info = withContext(Dispatchers.IO) { NetProbe.fetchIpInfoDirectWithRetry() }
                            AetherController.setIpInfo(info?.let { IpEndpoint(it.ip, it.countryCode, false) })
                            AetherController.setIpLoading(false)
                        }
                        else -> {
                            AetherController.setIpInfo(null)
                            AetherController.setIpLoading(false)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        state = state,
                        profile = profile,
                        connectedSince = connectedSince,
                        ipInfo = ipInfo,
                        ipLoading = ipLoading,
                        onProfileChange = { updated ->
                            lifecycleScope.launch { profileStore.save(updated) }
                        },
                        onToggleConnection = { toggleConnection(state) },
                    )
                }
            }
        }
    }

    private fun toggleConnection(state: ConnectionState) {
        if (state.isConnected || state.isBusy) {
            AetherController.disconnect(this)
            return
        }
        lifecycleScope.launch {
            val profile = profileStore.profile.first()
            val consent = AetherController.prepare(this@MainActivity)
            if (consent != null) {
                pendingProfile = profile
                vpnPermissionLauncher.launch(consent)
            } else {
                AetherController.connect(this@MainActivity, profile)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Set by the Quick Settings tile when it needs the consent dialog. */
        const val EXTRA_CONNECT_ON_LAUNCH = "studio.cluvex.aether.CONNECT_ON_LAUNCH"
    }
}
