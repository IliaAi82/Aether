package studio.cluvex.aether.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.cluvex.aether.AetherApp
import studio.cluvex.aether.MainActivity
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherController
import studio.cluvex.aether.core.AetherProcess
import studio.cluvex.aether.core.Diagnostics
import studio.cluvex.aether.core.DiagnosticsLog
import studio.cluvex.aether.core.PortProbe
import studio.cluvex.aether.core.ProfileCodec
import studio.cluvex.aether.core.HevTunnel
import studio.cluvex.aether.core.ShareBridge
import studio.cluvex.aether.core.TunnelConfig
import studio.cluvex.aether.model.ConnectionProfile
import studio.cluvex.aether.model.ConnectionState
import java.io.File

/**
 * The heart of the app. On connect it:
 *   1. launches the bundled `aether` engine (opens SOCKS5 on 127.0.0.1:1819),
 *   2. waits until that port is actually reachable (ground-truth check),
 *   3. builds the VPN TUN interface,
 *   4. starts the embedded hev-socks5-tunnel core (libhev-socks5-tunnel.so) to forward all
 *      traffic through the proxy — replacing the need for v2rayNG entirely,
 *   5. supervises both processes and auto-reconnects on failure.
 */
class AetherVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engine: AetherProcess? = null
    private var tunnelStarted: Boolean = false
    private var runJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                val profile = ProfileCodec.decode(intent?.getStringExtra(EXTRA_PROFILE))
                startForeground(NOTIF_ID, buildNotification(getString(R.string.state_launching)))
                startTunnel(profile)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(profile: ConnectionProfile) {
        if (runJob?.isActive == true) return
        runJob = scope.launch {
            try {
                connectFlow(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AetherController.setState(
                    ConnectionState.Error(e.message ?: getString(R.string.state_error)),
                )
                updateNotification(getString(R.string.state_error))
                cleanupNativeOnly()
            }
        }
    }

    private suspend fun connectFlow(profile: ConnectionProfile) {
        DiagnosticsLog.clear()
        DiagnosticsLog.i(TAG, "Connect requested — protocol=${profile.protocol} scan=${profile.scanMode} ip=${profile.ipVersion}")

        AetherController.setState(ConnectionState.Launching)
        updateNotification(getString(R.string.state_launching))
        DiagnosticsLog.i(TAG, "Launching engine (libaether.so)…")
        engine = AetherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }

        AetherController.setState(ConnectionState.Connecting)
        updateNotification(getString(R.string.state_connecting))
        // Timeout MUST match the scan mode: a THOROUGH scan legitimately needs
        // ~250s to select an endpoint before the engine opens SOCKS, so a fixed
        // 150s limit aborted every THOROUGH connection mid-scan.
        val timeoutMs = profile.connectTimeoutMs()
        DiagnosticsLog.i(
            TAG,
            "Waiting for SOCKS5 on $SOCKS_HOST:$SOCKS_PORT… (scan=${profile.scanMode}, timeout=${timeoutMs / 1000}s)",
        )
        val opened = PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, timeoutMs) { engine?.isAlive() == true }
        if (!opened) {
            val engineDied = engine?.isAlive() != true
            if (engineDied) {
                DiagnosticsLog.e(TAG, "Engine exited before it opened the SOCKS5 port.")
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            DiagnosticsLog.e(TAG, "Engine still scanning after ${timeoutMs / 1000}s — SOCKS5 port never opened.")
            throw IllegalStateException(getString(R.string.err_engine_timeout))
        }
        DiagnosticsLog.i(TAG, "SOCKS5 port is up.")

        establishTun()
        startTun2Socks()

        AetherController.setState(ConnectionState.Connected("$SOCKS_HOST:$SOCKS_PORT"))
        updateNotification(getString(R.string.state_connected))
        DiagnosticsLog.i(TAG, "TUN + hev tunnel started. Running end-to-end self-test…")

        // LAN sharing: if the user enabled it, expose the tunnel to other
        // devices on the same Wi-Fi/hotspot (HTTP + SOCKS5 bridge).
        if (profile.lanShare) ShareBridge.start()

        // Auto-run the connectivity self-test so the log panel immediately shows
        // whether traffic actually flows (and if not, exactly which stage fails).
        scope.launch { runCatching { Diagnostics.run() } }

        superviseEngine(profile)
    }

    /** Keeps the engine alive; retries with backoff if it dies. */
    private suspend fun superviseEngine(profile: ConnectionProfile) {
        var attempt = 0
        while (currentScopeActive()) {
            if (engine?.isAlive() == true) {
                attempt = 0
                delay(2000)
                continue
            }

            if (attempt >= MAX_RETRIES) {
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            val backoff = BACKOFF[attempt.coerceAtMost(BACKOFF.size - 1)]
            attempt++
            AetherController.setState(ConnectionState.Reconnecting(attempt, MAX_RETRIES))
            updateNotification(getString(R.string.state_reconnecting))
            delay(backoff)

            engine = AetherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }
            if (PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, profile.connectTimeoutMs()) { engine?.isAlive() == true }) {
                attempt = 0
                AetherController.setState(ConnectionState.Connected("$SOCKS_HOST:$SOCKS_PORT"))
                updateNotification(getString(R.string.state_connected))
            }
        }
    }

    private fun currentScopeActive(): Boolean = runJob?.isActive ?: false

    private fun establishTun() {
        val builder = Builder()
            .setSession("Aether")
            .setMtu(MTU)
            // The TUN address MUST match hev's tunnel.ipv4/ipv6 (see writeHevConfig).
            .addAddress(TunnelConfig.TUN_IPV4, TunnelConfig.TUN_IPV4_PREFIX)
            .addAddress(TunnelConfig.TUN_IPV6, TunnelConfig.TUN_IPV6_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        TunnelConfig.DNS_SERVERS.forEach { builder.addDnsServer(it) }

        // Never route the app's own (engine) traffic back into the tunnel.
        // This is our loop-prevention mechanism and is equivalent to v2rayNG's
        // in-process protect(): the spawned engine shares the app UID, so
        // excluding the package keeps its upstream traffic off the TUN.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tun = builder.establish()
            ?: throw IllegalStateException("Failed to establish the VPN interface")
        DiagnosticsLog.i(
            TAG,
            "TUN established: ipv4=${TunnelConfig.TUN_IPV4}/${TunnelConfig.TUN_IPV4_PREFIX} " +
                "ipv6=${TunnelConfig.TUN_IPV6}/${TunnelConfig.TUN_IPV6_PREFIX} mtu=$MTU dns=${TunnelConfig.DNS_SERVERS}",
        )
    }

    private fun startTun2Socks() {
        val config = writeHevConfig()
        // Use the LIVE fd of the ParcelFileDescriptor (do NOT detach): hev uses it
        // while running and we close the pfd ourselves on teardown. The fd is only
        // valid inside THIS process, which is exactly why hev must run in-process.
        val fd = tun?.fd ?: throw IllegalStateException("TUN descriptor is null")
        DiagnosticsLog.i(TAG, "Starting hev-socks5-tunnel in-process (fd=$fd)")
        HevTunnel.start(config.absolutePath, fd)
        tunnelStarted = true
    }

    /**
     * Writes the hev-socks5-tunnel config in the exact shape v2rayNG uses.
     *
     * The critical difference from the previous (broken) version is the
     * `tunnel.ipv4` / `tunnel.ipv6` fields. hev configures its internal lwIP
     * netif from these; without them packets are pulled off the TUN fd but have
     * nowhere to be routed, so the tunnel "connects" but no site ever loads.
     * These MUST equal the VpnService addAddress values.
     */
    private fun writeHevConfig(): File {
        val file = File(filesDir, "hev.yaml")
        val yaml = """
            tunnel:
              mtu: $MTU
              ipv4: ${TunnelConfig.TUN_IPV4}
              ipv6: '${TunnelConfig.TUN_IPV6}'
            socks5:
              address: $SOCKS_HOST
              port: $SOCKS_PORT
              udp: 'udp'
            misc:
              task-stack-size: 86016
              connect-timeout: 5000
              tcp-read-write-timeout: 60000
              udp-read-write-timeout: 60000
              log-level: warn
        """.trimIndent()
        file.writeText(yaml)
        DiagnosticsLog.i(TAG, "hev.yaml written:\n$yaml")
        return file
    }

    private fun stopEverything() {
        AetherController.setState(ConnectionState.Disconnecting)
        updateNotification(getString(R.string.state_disconnecting))
        runJob?.cancel()
        scope.launch {
            cleanupNativeOnly()
            AetherController.setState(ConnectionState.Idle)
            AetherTileService.requestUpdate(this@AetherVpnService)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun cleanupNativeOnly() {
        // Stop sharing first: without the tunnel the bridge would leak direct.
        try {
            ShareBridge.stop()
        } catch (_: Throwable) {
        }
        if (tunnelStarted) {
            try {
                HevTunnel.stop()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        engine = null
        try {
            tun?.close()
        } catch (_: Throwable) {
        }
        tun = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        runJob?.cancel()
        cleanupNativeOnly()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AetherApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.state_disconnecting), disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
        // Keep the Quick Settings tile in sync with every state transition.
        AetherTileService.requestUpdate(this)
    }

    companion object {
        const val ACTION_CONNECT = "studio.cluvex.aether.CONNECT"
        const val ACTION_DISCONNECT = "studio.cluvex.aether.DISCONNECT"
        const val EXTRA_PROFILE = "profile"

        private const val NOTIF_ID = 0x4145
        private const val TAG = "vpn"
        private const val SOCKS_HOST = TunnelConfig.SOCKS_HOST
        private const val SOCKS_PORT = TunnelConfig.SOCKS_PORT
        private const val MTU = TunnelConfig.MTU
        private const val MAX_RETRIES = 3
        private val BACKOFF = longArrayOf(2000L, 5000L, 10000L)
    }
}
