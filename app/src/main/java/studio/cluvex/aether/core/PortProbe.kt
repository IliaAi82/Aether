package studio.cluvex.aether.core

import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The ground-truth "are we connected?" check — identical in spirit to the
 * desktop app: a successful TCP connect to the local SOCKS5 port means the
 * engine is up and tunnelling.
 */
object PortProbe {
    fun isOpen(host: String, port: Int, timeoutMs: Int = 800): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (e: Exception) {
            false
        }

    /**
     * Polls until the port opens or [totalTimeoutMs] elapses. Aborts early if
     * [isEngineAlive] returns false, so a dead engine fails fast with a clear
     * error instead of the caller hanging for the entire (possibly 5-minute)
     * timeout window.
     */
    suspend fun awaitOpen(
        host: String,
        port: Int,
        totalTimeoutMs: Long,
        // SPEED FIX: 300 ms polling detects the engine's port up to ~700 ms
        // sooner than the old 1 s poll; a localhost TCP connect is ~free.
        intervalMs: Long = 300,
        isEngineAlive: () -> Boolean = { true },
    ): Boolean {
        val deadline = System.currentTimeMillis() + totalTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isOpen(host, port)) return true
            if (!isEngineAlive()) return false
            delay(intervalMs)
        }
        return false
    }
}
