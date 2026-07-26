package com.nebula.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.nebula.vpn.core.DiagnosticsLog
import java.io.File

class AetherApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Wire the persistent diagnostics log FIRST, so anything logged during
        // startup (and any crash) is written to disk and survives process death.
        DiagnosticsLog.init(File(filesDir, "diagnostics.log"))
        installCrashHandler()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Captures otherwise-fatal JVM exceptions and flushes them to the on-disk
     * diagnostics log BEFORE the process dies. This is why "after a crash the
     * log was empty": the log lived only in memory. Now the crash cause is
     * persisted and reloaded into the panel on the next launch. (Native faults
     * inside the in-process tunnel can't be caught here, but every line logged
     * up to that instant is already on disk because we flush on every write.)
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                DiagnosticsLog.e(
                    "crash",
                    "FATAL on thread '${thread.name}': $throwable\n" +
                        Log.getStackTraceString(throwable),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_ID = "aether_vpn"
    }
}
