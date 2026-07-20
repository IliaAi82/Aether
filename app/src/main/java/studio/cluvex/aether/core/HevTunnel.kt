package studio.cluvex.aether.core

/**
 * Thin facade over [TProxyService] (hev-socks5-tunnel's own JNI interface).
 *
 * hev MUST run inside the *same process* that owns the VpnService TUN file
 * descriptor (fds are per-process), and its event loop MUST run on a native
 * pthread — not a Java thread. TProxyStartService satisfies both: it returns
 * immediately after spawning hev's own native thread.
 *
 * See TProxyService.kt for the full root-cause history (custom-wrapper SIGSEGV
 * on ART-attached threads).
 */
object HevTunnel {
    @Volatile
    private var running = false

    /** True if the native core is available to run. */
    fun isAvailable(): Boolean = TProxyService.available

    /** Starts hev on its own native thread. No-op if already running. */
    fun start(configPath: String, tunFd: Int) {
        if (!TProxyService.available) {
            DiagnosticsLog.e("tunnel", "libhev-socks5-tunnel.so not loaded — cannot start tunnel.")
            throw IllegalStateException("hev native library unavailable")
        }
        if (running) return
        DiagnosticsLog.i("tunnel", "hev-socks5-tunnel starting on native thread (fd=$tunFd)")
        TProxyService.TProxyStartService(configPath, tunFd)
        running = true
    }

    fun isAlive(): Boolean = running

    /**
     * Cumulative traffic counters since the tunnel core started, or null when
     * the core is not running. hev's own JNI returns
     * [tx_packets, tx_bytes, rx_packets, rx_bytes] where TX is what the core
     * WRITES to the TUN device (data arriving at apps = download) and RX is
     * what it READS from the TUN (data leaving apps = upload).
     */
    fun stats(): LongArray? {
        if (!TProxyService.available || !running) return null
        return runCatching { TProxyService.TProxyGetStats() }.getOrNull()
    }

    /** Requests hev to quit. Safe to call repeatedly. */
    fun stop() {
        if (!TProxyService.available) return
        if (!running) return
        try {
            TProxyService.TProxyStopService()
            DiagnosticsLog.i("tunnel", "hev-socks5-tunnel stop requested")
        } catch (t: Throwable) {
            DiagnosticsLog.w("tunnel", "TProxyStopService failed: ${t.message}")
        }
        running = false
    }
}
