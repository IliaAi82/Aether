package studio.cluvex.aether.core

import android.util.Log
import studio.cluvex.aether.BuildConfig
import studio.cluvex.aether.model.ConnectionProfile
import java.io.File

/**
 * Runs the native `aether` engine (shipped as libaether.so) as a child
 * process. On Android an executable packaged in jniLibs is extracted to
 * nativeLibraryDir with the exec bit set, which is exactly what we run.
 */
class AetherProcess(
    private val nativeLibDir: String,
    private val workingDir: File,
) {
    private var process: Process? = null

    fun start(profile: ConnectionProfile) {
        val bin = File(nativeLibDir, "libaether.so")
        if (!bin.exists()) {
            throw IllegalStateException("Engine binary missing: ${bin.absolutePath}")
        }

        val command = mutableListOf(bin.absolutePath).apply { addAll(profile.toArgs()) }
        val builder = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
        builder.environment().apply {
            putAll(profile.toEnv())
            put("HOME", workingDir.absolutePath)
            put("TMPDIR", workingDir.absolutePath)
        }

        val proc = builder.start()
        process = proc

        DiagnosticsLog.i("engine", "Spawned ${bin.name} ${profile.toArgs().joinToString(" ")}")

        // Drain stdout/stderr so a full pipe never blocks the engine, mirroring
        // every line into both logcat and the in-app diagnostics panel.
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach {
                        // SECURITY FIX: the engine's stdout (endpoints, exit
                        // IPs, config echo) must not be mirrored to Logcat in
                        // release builds — Logcat is world-readable via adb and
                        // ends up in bug reports. The in-app diagnostics panel
                        // (app-private file) still receives every line below.
                        if (BuildConfig.DEBUG) Log.i("aether-engine", it)
                        DiagnosticsLog.d("engine", it)
                    }
                }
            } catch (_: Exception) {
            } finally {
                DiagnosticsLog.w("engine", "Engine output stream closed.")
            }
        }, "aether-log").apply { isDaemon = true }.start()
    }

    fun isAlive(): Boolean = process?.isAlive == true

    fun stop() {
        process?.destroy()
        process = null
    }
}
