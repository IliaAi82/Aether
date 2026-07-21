package studio.cluvex.aether.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * LAN sharing bridge: lets OTHER devices on the same Wi-Fi / hotspot use this
 * phone's Aether tunnel as a normal proxy.
 *
 * Two listeners are exposed while sharing is on:
 *  - SOCKS5  0.0.0.0:[SOCKS_SHARE_PORT] — a transparent TCP relay into the
 *    engine's local SOCKS5 (127.0.0.1:1819, loopback-only), so the full SOCKS5
 *    protocol (including remote DNS) is served by the engine itself.
 *  - HTTP    0.0.0.0:[HTTP_SHARE_PORT] — a minimal HTTP/1.1 proxy (CONNECT for
 *    HTTPS + absolute-form for plain HTTP) that dials upstream THROUGH that
 *    SOCKS5 proxy. This is what the "system proxy" settings on Windows/macOS
 *    (and most phones) expect, so laptops work out of the box.
 *
 * Loop safety: this code runs inside the app process, which is excluded from
 * the TUN via addDisallowedApplication(), so proxied traffic always leaves via
 * the engine and never re-enters the VPN.
 *
 * Security note: both listeners accept connections from ANY device on the
 * local network while sharing is enabled. The UI warns the user accordingly
 * and sharing is OFF by default.
 */
object ShareBridge {

    const val SOCKS_SHARE_PORT = 1080
    const val HTTP_SHARE_PORT = 8118

    private const val TAG = "share"
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val DIAL_TIMEOUT_MS = 10_000

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var socksServer: ServerSocket? = null
    private var httpServer: ServerSocket? = null

    @Volatile
    private var starting = false

    /**
     * Turn sharing on. Safe to call from ANY thread — including the UI thread:
     * binding sockets is a network operation and Android throws
     * NetworkOnMainThreadException when it happens on the main thread, so the
     * actual work runs on a short-lived background thread and [active] flips
     * to true once both listeners are ready.
     */
    fun start() {
        synchronized(this) {
            if (_active.value || starting) return
            starting = true
        }
        thread(name = "share-start", isDaemon = true) { doStart() }
    }

    /** Turn sharing off. Safe to call from any thread. */
    fun stop() {
        _active.value = false
        thread(name = "share-stop", isDaemon = true) {
            synchronized(this) { closeServers() }
            DiagnosticsLog.i(TAG, "Sharing OFF")
        }
    }

    private fun doStart() {
        try {
            synchronized(this) {
                if (_active.value) return
                try {
                    socksServer = bind(SOCKS_SHARE_PORT)
                    httpServer = bind(HTTP_SHARE_PORT)
                } catch (e: Exception) {
                    DiagnosticsLog.e(TAG, "Could not open sharing ports: $e")
                    closeServers()
                    return
                }
                acceptLoop("share-socks", socksServer!!) { relayToLocalSocks(it) }
                acceptLoop("share-http", httpServer!!) { serveHttpClient(it) }
                _active.value = true
            }
            DiagnosticsLog.i(TAG, "Sharing ON — SOCKS5 :$SOCKS_SHARE_PORT + HTTP :$HTTP_SHARE_PORT on all interfaces")
        } finally {
            starting = false
        }
    }

    /** Best local (site-local IPv4) address other devices can reach us on. */
    fun lanAddress(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .filterNot { it.name.startsWith("tun") || it.name.startsWith("ppp") }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

    // ------------------------------------------------------------- internals

    private fun bind(port: Int): ServerSocket =
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", port), 32)
        }

    private fun closeServers() {
        runCatching { socksServer?.close() }
        socksServer = null
        runCatching { httpServer?.close() }
        httpServer = null
    }

    private fun acceptLoop(name: String, server: ServerSocket, handler: (Socket) -> Unit) {
        thread(name = name, isDaemon = true) {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break // server closed -> sharing stopped
                }
                thread(name = "$name-conn", isDaemon = true) {
                    try {
                        client.tcpNoDelay = true
                        handler(client)
                    } catch (_: Exception) {
                        // Per-connection errors are non-fatal by design.
                    } finally {
                        runCatching { client.close() }
                    }
                }
            }
        }
    }

    /** SOCKS5 share = byte-for-byte relay into the engine's loopback SOCKS5. */
    private fun relayToLocalSocks(client: Socket) {
        val upstream = Socket()
        try {
            upstream.tcpNoDelay = true
            upstream.connect(
                InetSocketAddress(TunnelConfig.SOCKS_HOST, TunnelConfig.SOCKS_PORT),
                DIAL_TIMEOUT_MS,
            )
            relay(client, upstream)
        } finally {
            runCatching { upstream.close() }
        }
    }

    /** Minimal HTTP proxy: CONNECT tunnels + absolute-form plain requests. */
    private fun serveHttpClient(client: Socket) {
        val input = client.getInputStream()
        val header = readHeaderBlock(input) ?: return
        val lines = header.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty()
        val parts = requestLine.split(" ")
        if (parts.size < 3) return

        val method = parts[0]
        val target = parts[1]

        if (method.equals("CONNECT", ignoreCase = true)) {
            val host = target.substringBeforeLast(':')
            val port = target.substringAfterLast(':').toIntOrNull() ?: 443
            val upstream = socksOpen(host, port) ?: run {
                client.getOutputStream().writeAscii("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n")
                return
            }
            try {
                client.getOutputStream().writeAscii("HTTP/1.1 200 Connection Established\r\n\r\n")
                relay(client, upstream)
            } finally {
                runCatching { upstream.close() }
            }
            return
        }

        // Plain HTTP with an absolute URI, e.g. "GET http://example.com/x HTTP/1.1".
        val url = target.removePrefix("http://")
        if (url == target) { // https:// or malformed — TLS must use CONNECT
            client.getOutputStream().writeAscii("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
            return
        }
        val hostPort = url.substringBefore('/')
        val path = "/" + url.substringAfter('/', "")
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "80").toIntOrNull() ?: 80

        val upstream = socksOpen(host, port) ?: run {
            client.getOutputStream().writeAscii("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n")
            return
        }
        try {
            val rebuilt = buildString {
                append("$method $path ${parts[2]}\r\n")
                lines.drop(1).forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val lower = line.lowercase()
                    if (lower.startsWith("proxy-connection:") ||
                        lower.startsWith("proxy-authorization:") ||
                        lower.startsWith("connection:")
                    ) {
                        return@forEach
                    }
                    append(line).append("\r\n")
                }
                append("Connection: close\r\n\r\n")
            }
            upstream.getOutputStream().writeAscii(rebuilt)
            relay(client, upstream)
        } finally {
            runCatching { upstream.close() }
        }
    }

    /** Opens a TCP stream to host:port THROUGH the engine's SOCKS5 proxy. */
    private fun socksOpen(host: String, port: Int): Socket? {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            socket.connect(
                InetSocketAddress(TunnelConfig.SOCKS_HOST, TunnelConfig.SOCKS_PORT),
                DIAL_TIMEOUT_MS,
            )
            socket.soTimeout = 30_000
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            // Greeting: version 5, one method, no-auth.
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val greet = inp.readExact(2)
            if (greet == null || greet[0] != 5.toByte() || greet[1] != 0.toByte()) {
                throw IllegalStateException("SOCKS5 greeting failed")
            }

            // CONNECT with a DOMAIN address -> DNS resolves inside the tunnel.
            val hostBytes = host.toByteArray(Charsets.ISO_8859_1)
            val request = ByteArrayOutputStream().apply {
                write(byteArrayOf(0x05, 0x01, 0x00, 0x03))
                write(hostBytes.size)
                write(hostBytes)
                write((port shr 8) and 0xFF)
                write(port and 0xFF)
            }
            out.write(request.toByteArray())
            out.flush()

            val reply = inp.readExact(4) ?: throw IllegalStateException("SOCKS5 reply truncated")
            if (reply[1] != 0.toByte()) throw IllegalStateException("SOCKS5 connect refused (${reply[1]})")
            val remaining = when (reply[3].toInt()) {
                0x01 -> 4 + 2
                0x03 -> (inp.readExact(1)?.get(0)?.toInt()?.and(0xFF)
                    ?: throw IllegalStateException("SOCKS5 reply truncated")) + 2
                0x04 -> 16 + 2
                else -> throw IllegalStateException("Bad SOCKS5 address type")
            }
            inp.readExact(remaining) ?: throw IllegalStateException("SOCKS5 reply truncated")

            socket.soTimeout = 0
            socket
        } catch (e: Exception) {
            DiagnosticsLog.e(TAG, "Upstream dial failed for $host:$port — $e")
            runCatching { socket.close() }
            null
        }
    }

    /** Reads raw bytes up to and including the CRLFCRLF header terminator. */
    private fun readHeaderBlock(input: InputStream): ByteArrayOutputStream? {
        val buf = ByteArrayOutputStream()
        var run = 0
        while (buf.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            run = when {
                b == '\r'.code && (run == 0 || run == 2) -> run + 1
                b == '\n'.code && (run == 1 || run == 3) -> run + 1
                else -> 0
            }
            if (run == 4) return buf
        }
        return null
    }

    /** Full-duplex pipe between two sockets; returns when either side ends. */
    private fun relay(a: Socket, b: Socket) {
        val reverse = thread(isDaemon = true) { pipe(b, a) }
        pipe(a, b)
        runCatching { reverse.join(1_000) }
    }

    private fun pipe(from: Socket, to: Socket) {
        val buffer = ByteArray(16 * 1024)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { to.shutdownOutput() }
            runCatching { from.shutdownInput() }
        }
    }

    private fun InputStream.readExact(n: Int): ByteArray? {
        val out = ByteArray(n)
        var done = 0
        while (done < n) {
            val r = read(out, done, n - done)
            if (r < 0) return null
            done += r
        }
        return out
    }

    private fun OutputStream.writeAscii(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        flush()
    }
}
