package studio.cluvex.aether.core

import android.util.Log

/**
 * Direct binding to hev-socks5-tunnel's OWN JNI interface — the exact same
 * mechanism v2rayNG uses.
 *
 * libhev-socks5-tunnel.so is built with -DPKGNAME=studio/cluvex/aether/core,
 * so its JNI_OnLoad (hev-jni.c) registers the TProxy* natives onto THIS class
 * during System.loadLibrary(). TProxyStartService returns immediately and runs
 * the tunnel event loop on a NATIVE pthread that hev creates itself.
 *
 * ROOT-CAUSE NOTE: the previous custom wrapper (libhev.so) called
 * hev_socks5_tunnel_main directly on a Java (ART-attached) thread.
 * hev-task-system implements its coroutines by swapping the thread's stack
 * pointer; doing that on a thread managed by the Android runtime corrupts what
 * ART expects of the stack and kills the whole app with a native SIGSEGV a few
 * seconds after real traffic starts — with nothing in the Java crash log.
 * Letting hev run its loop on its own pthread (this class) avoids ART
 * entirely, which is why v2rayNG is stable with the identical core.
 *
 * IMPORTANT: this object must NOT be renamed or moved to another package —
 * the native registration looks it up by the exact name
 * "studio/cluvex/aether/core/TProxyService".
 */
object TProxyService {
    /** True if libhev-socks5-tunnel.so loaded and registered successfully. */
    @Volatile
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("hev-socks5-tunnel")
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError is an Error (NOT an Exception) and would
            // otherwise escape `catch (Exception)` blocks and crash the app.
            Log.e("aether-tunnel", "Failed to load libhev-socks5-tunnel.so", t)
            runCatching {
                DiagnosticsLog.e("tunnel", "FATAL: could not load libhev-socks5-tunnel.so: $t")
            }
            false
        }
    }

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyGetStats(): LongArray?
}
