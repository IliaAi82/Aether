package studio.cluvex.aether.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs the ordered connectivity self-test against the local SOCKS5 proxy and
 * records every step in [DiagnosticsLog]. The order is deliberate so a reader
 * can pinpoint WHERE the pipeline breaks:
 *
 *   port      -> is the engine even listening?
 *   handshake -> does it speak SOCKS5?
 *   tcp       -> can it open an outbound TCP connection (to an IP, no DNS)?
 *   dns_http  -> can it resolve a domain AND fetch over HTTP end-to-end?
 *
 * Example: port+handshake+tcp PASS but dns_http FAIL => the tunnel works but
 * DNS (SOCKS5 UDP ASSOCIATE / remote resolution) is broken — the usual reason a
 * WARP-style tunnel "connects but no site loads".
 */
object Diagnostics {
    const val C_PORT = "socks_port"
    const val C_HANDSHAKE = "socks_handshake"
    const val C_TCP = "tcp_via_proxy"
    const val C_DNS = "dns_http_via_tunnel"

    private const val TAG = "diag"

    // How long we keep retrying the outbound checks after connect. Warp-in-warp
    // (GOOL) keeps building its INNER tunnel for a while after the SOCKS5 port
    // is already open; during that window every CONNECT is rejected with rep=1.
    // That is a COLD START, not a failure, so give the engine a grace window
    // instead of failing on the very first attempt.
    private const val OUTBOUND_GRACE_MS = 90_000L
    private const val OUTBOUND_RETRY_DELAY_MS = 3_000L

    fun resetChecks() {
        DiagnosticsLog.setChecks(
            listOf(
                ComponentCheck(C_PORT, "SOCKS5 port ${TunnelConfig.SOCKS_HOST}:${TunnelConfig.SOCKS_PORT}"),
                ComponentCheck(C_HANDSHAKE, "SOCKS5 handshake"),
                ComponentCheck(C_TCP, "TCP via proxy (1.1.1.1:80)"),
                ComponentCheck(C_DNS, "DNS + HTTP via tunnel"),
            )
        )
    }

    /** Runs all checks sequentially. Safe to call from any coroutine. */
    suspend fun run(
        host: String = TunnelConfig.SOCKS_HOST,
        port: Int = TunnelConfig.SOCKS_PORT,
    ): Boolean = withContext(Dispatchers.IO) {
        resetChecks()
        DiagnosticsLog.i(TAG, "Starting connectivity self-test…")

        // 1. Port open
        DiagnosticsLog.updateCheck(C_PORT, CheckState.RUNNING)
        val portOpen = PortProbe.isOpen(host, port, 1500)
        DiagnosticsLog.updateCheck(
            C_PORT,
            if (portOpen) CheckState.PASS else CheckState.FAIL,
            if (portOpen) "listening" else "no listener",
        )
        DiagnosticsLog.log(TAG, if (portOpen) LogLevel.INFO else LogLevel.ERROR, "port open = $portOpen")
        if (!portOpen) {
            failRemaining(C_HANDSHAKE, C_TCP, C_DNS)
            return@withContext false
        }

        // 2. SOCKS5 handshake
        DiagnosticsLog.updateCheck(C_HANDSHAKE, CheckState.RUNNING)
        val handshake = NetProbe.checkSocksHandshake(host, port)
        DiagnosticsLog.updateCheck(C_HANDSHAKE, if (handshake) CheckState.PASS else CheckState.FAIL)
        DiagnosticsLog.log(TAG, if (handshake) LogLevel.INFO else LogLevel.ERROR, "socks5 handshake = $handshake")
        if (!handshake) {
            failRemaining(C_TCP, C_DNS)
            return@withContext false
        }

        // 3. TCP via proxy (IP literal, no DNS) — retried over a grace window
        // so the engine's cold start (inner tunnel still handshaking) does not
        // show up as a scary false FAIL in the panel.
        DiagnosticsLog.updateCheck(C_TCP, CheckState.RUNNING)
        var tcp = NetProbe.checkTcpViaProxy(host, port, "1.1.1.1", 80)
        val deadline = System.currentTimeMillis() + OUTBOUND_GRACE_MS
        while (!tcp && System.currentTimeMillis() < deadline) {
            DiagnosticsLog.i(TAG, "tcp via proxy not ready yet (engine warming up), retrying…")
            delay(OUTBOUND_RETRY_DELAY_MS)
            tcp = NetProbe.checkTcpViaProxy(host, port, "1.1.1.1", 80)
        }
        DiagnosticsLog.updateCheck(C_TCP, if (tcp) CheckState.PASS else CheckState.FAIL)
        DiagnosticsLog.log(TAG, if (tcp) LogLevel.INFO else LogLevel.ERROR, "tcp via proxy = $tcp")

        // 4. DNS + HTTP end-to-end
        DiagnosticsLog.updateCheck(C_DNS, CheckState.RUNNING)
        // Retried as well — remote DNS/HTTP needs the same warm-up window.
        val info = NetProbe.fetchIpInfoViaSocksWithRetry(host, port)
        val dnsOk = info != null
        DiagnosticsLog.updateCheck(
            C_DNS,
            if (dnsOk) CheckState.PASS else CheckState.FAIL,
            if (dnsOk) "exit ${info!!.ip} ${info.countryCode ?: "?"}" else "no response",
        )
        DiagnosticsLog.log(
            TAG,
            if (dnsOk) LogLevel.INFO else LogLevel.ERROR,
            if (dnsOk) "dns+http OK, exit ip=${info!!.ip} cc=${info.countryCode}" else "dns+http FAILED",
        )

        // The self-test already discovered the real exit IP through the tunnel.
        // Feed it straight into the badge so the UI never has to race a second,
        // independent lookup right after connect.
        if (dnsOk) {
            AetherController.offerTunnelIpInfo(IpEndpoint(info!!.ip, info.countryCode, true))
            AetherController.setIpLoading(false)
        }

        if (!dnsOk) {
            DiagnosticsLog.w(
                TAG,
                if (tcp) "TCP works but DNS/HTTP fails → likely broken remote DNS (SOCKS5 UDP ASSOCIATE)."
                else "Proxy cannot open outbound connections → engine has no upstream route.",
            )
        }
        dnsOk
    }

    private fun failRemaining(vararg ids: String) {
        ids.forEach { DiagnosticsLog.updateCheck(it, CheckState.FAIL, "skipped") }
    }
}
