package studio.cluvex.aether.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.cluvex.aether.model.ConnectionProfile
import studio.cluvex.aether.model.ConnectionState
import studio.cluvex.aether.model.IpVersion
import studio.cluvex.aether.model.Protocol
import studio.cluvex.aether.model.ScanMode
import studio.cluvex.aether.vpn.AetherVpnService

/**
 * App-wide singleton that (a) publishes the live [ConnectionState] to the UI and
 * (b) sends connect/disconnect intents to [AetherVpnService].
 */
object AetherController {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Epoch millis of when the current session became Connected, or null. */
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    /** IP + country shown in the UI (exit server when connected, operator when not). */
    private val _ipInfo = MutableStateFlow<IpEndpoint?>(null)
    val ipInfo: StateFlow<IpEndpoint?> = _ipInfo.asStateFlow()

    /** True while an IP lookup is in flight (drives the “…” placeholder). */
    private val _ipLoading = MutableStateFlow(false)
    val ipLoading: StateFlow<Boolean> = _ipLoading.asStateFlow()

    /** Called by the service to broadcast state changes. */
    fun setState(newState: ConnectionState) {
        _state.value = newState
        when (newState) {
            is ConnectionState.Connected ->
                if (_connectedSince.value == null) _connectedSince.value = System.currentTimeMillis()
            is ConnectionState.Reconnecting -> {
                // Keep the running timer during a transient reconnect.
            }
            else -> _connectedSince.value = null
        }
    }

    fun setIpInfo(info: IpEndpoint?) {
        _ipInfo.value = info
    }

    /**
     * Sets the tunnel exit IP only when the badge doesn't already show a
     * tunnel IP for this session.
     *
     * FLAG-FLICKER FIX: two lookups used to race (the automatic self-test and
     * the UI phase watcher) and each could win with a DIFFERENT geo provider;
     * providers can disagree about the exit country, so the flag sometimes
     * showed the wrong country or suddenly changed mid-session. First tunnel
     * result now wins and stays stable until the connection phase changes.
     */
    fun offerTunnelIpInfo(info: IpEndpoint) {
        if (_ipInfo.value?.viaTunnel == true) return
        _ipInfo.value = info
    }

    fun setIpLoading(loading: Boolean) {
        _ipLoading.value = loading
    }

    /**
     * Returns a consent Intent if the user must still grant VPN permission,
     * or null if permission was already granted.
     */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    fun connect(context: Context, profile: ConnectionProfile) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_CONNECT
            putExtra(AetherVpnService.EXTRA_PROFILE, ProfileCodec.encode(profile))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

/** Serialises a [ConnectionProfile] into a compact pipe-delimited string for Intent transport. */
object ProfileCodec {
    fun encode(p: ConnectionProfile): String = listOf(
        p.protocol.name,
        p.scanMode.name,
        p.ipVersion.name,
        p.quickReconnect.toString(),
        p.masqueHttp2.toString(),
    ).joinToString("|")

    fun decode(raw: String?): ConnectionProfile {
        if (raw.isNullOrBlank()) return ConnectionProfile()
        val parts = raw.split("|")
        if (parts.size < 5) return ConnectionProfile()
        return runCatching {
            ConnectionProfile(
                protocol = Protocol.valueOf(parts[0]),
                scanMode = ScanMode.valueOf(parts[1]),
                ipVersion = IpVersion.valueOf(parts[2]),
                quickReconnect = parts[3].toBoolean(),
                masqueHttp2 = parts[4].toBoolean(),
            )
        }.getOrDefault(ConnectionProfile())
    }
}
