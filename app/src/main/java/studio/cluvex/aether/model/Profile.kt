package studio.cluvex.aether.model

/** Transport protocol, mapped 1:1 to the desktop app's CLI flags. */
enum class Protocol { AUTO, MASQUE, WIREGUARD, GOOL }

/** Endpoint scanning strategy. */
enum class ScanMode { TURBO, BALANCED, THOROUGH, STEALTH }

/** IP family preference. */
enum class IpVersion { V4, V6, BOTH }

/**
 * User-tunable connection profile. Mirrors exactly the options the tested
 * Windows build exposes, and knows how to turn itself into the engine's CLI
 * arguments and environment variables.
 */
data class ConnectionProfile(
    val protocol: Protocol = Protocol.AUTO,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val ipVersion: IpVersion = IpVersion.V4,
    val quickReconnect: Boolean = true,
    val masqueHttp2: Boolean = false,
) {
    /** Command-line arguments passed to the `aether` engine binary. */
    fun toArgs(): List<String> {
        val args = mutableListOf<String>()

        when (protocol) {
            Protocol.AUTO -> { /* engine default (MASQUE) */ }
            Protocol.MASQUE -> args += "--masque"
            Protocol.WIREGUARD -> args += "--wg"
            Protocol.GOOL -> args += "--gool"
        }

        when (scanMode) {
            ScanMode.TURBO -> args += "--turbo"
            ScanMode.BALANCED -> args += "--balanced"
            ScanMode.THOROUGH -> args += "--thorough"
            ScanMode.STEALTH -> args += "--stealth"
        }

        when (ipVersion) {
            IpVersion.V4 -> args += "-4"
            IpVersion.V6 -> args += "-6"
            IpVersion.BOTH -> args += "--dual"
        }

        args += if (quickReconnect) "--quick-reconnect" else "--no-quick-reconnect"
        return args
    }

    /** Environment variables for the engine process. */
    fun toEnv(): Map<String, String> = buildMap {
        put("AETHER_MASQUE_HTTP2", if (masqueHttp2) "1" else "0")
    }

    /**
     * How long to wait for the engine to open the local SOCKS5 port before
     * giving up. This MUST comfortably exceed the engine's own endpoint-scan
     * budget for the chosen mode; otherwise we abort the connection while the
     * engine is still legitimately scanning. The THOROUGH scan alone reports a
     * ~250s budget (1500+ candidates), which is exactly why a fixed 150s limit
     * made THOROUGH connections always fail. Values below add a safety margin.
     */
    fun connectTimeoutMs(): Long = when (scanMode) {
        ScanMode.TURBO -> 60_000L
        ScanMode.BALANCED -> 150_000L
        ScanMode.STEALTH -> 240_000L
        ScanMode.THOROUGH -> 300_000L
    }
}
