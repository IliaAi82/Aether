package studio.cluvex.aether.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import studio.cluvex.aether.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telegram-style in-app updates, backed by GitHub Releases.
 *
 * Flow:
 *  1. [check] asks the GitHub API for the latest release of [BuildConfig.GITHUB_REPO]
 *     (baked in at build time from the CI environment) and compares its version
 *     against the running build.
 *  2. [download] streams the matching APK asset (exact ABI first, universal as
 *     fallback) into the app cache with progress reporting.
 *  3. [install] hands the file to the system package installer via FileProvider.
 *     Because every release is signed with the same persisted key, the update
 *     installs on top of the old version -- no uninstall needed.
 *
 * Everything fails silently: a broken network or a blocked GitHub API must
 * never disturb the user.
 */
object UpdateChecker {

    data class UpdateInfo(
        val versionName: String,
        val apkName: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    private const val PREFS = "updater"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

    /** "owner/repo" injected by CI (GITHUB_REPOSITORY); empty in unconfigured local builds. */
    private val repo: String get() = BuildConfig.GITHUB_REPO

    val isConfigured: Boolean get() = repo.isNotBlank()

    /** Throttles automatic checks so we ping GitHub at most every few hours. */
    fun shouldAutoCheck(context: Context): Boolean {
        if (!isConfigured) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS
    }

    private fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Blocking -- call from Dispatchers.IO.
     * Returns null when already up to date, unconfigured, or on any failure.
     */
    fun check(context: Context): UpdateInfo? {
        if (!isConfigured) return null
        return try {
            val json = httpGetText("https://api.github.com/repos/$repo/releases/latest")
                ?: return null
            markChecked(context)
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            // CI tags look like "v1.2.2-build.7" -> version is "1.2.2".
            val remote = release.optString("tag_name").removePrefix("v").substringBefore("-").trim()
            if (remote.isEmpty() || !isNewer(remote, BuildConfig.VERSION_NAME)) return null
            val assets = release.optJSONArray("assets") ?: return null
            // Prefer the APK matching this device's primary ABI, then any
            // supported ABI, then the universal build.
            val abis = Build.SUPPORTED_ABIS.orEmpty().toList()
            var best: JSONObject? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk")) continue
                val rank = abis.indexOfFirst { name.contains("-$it.apk") }.let { idx ->
                    when {
                        idx >= 0 -> idx
                        name.contains("universal") -> abis.size
                        else -> abis.size + 1
                    }
                }
                if (rank < bestRank) {
                    bestRank = rank
                    best = asset
                }
            }
            val asset = best ?: return null
            UpdateInfo(
                versionName = remote,
                apkName = asset.optString("name"),
                apkUrl = asset.optString("browser_download_url"),
                sizeBytes = asset.optLong("size"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Blocking download into the app cache -- call from Dispatchers.IO.
     * [onProgress] receives 0..100.
     */
    fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // Drop stale downloads.
        val out = File(dir, info.apkName)
        var url = URL(info.apkUrl)
        var redirects = 0
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Aether/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = conn.responseCode
                // GitHub redirects asset downloads to a CDN host; follow manually
                // as a safety net (automatic following can skip cross-host hops).
                if (code in 301..308 && redirects < 5) {
                    url = URL(conn.getHeaderField("Location"))
                    redirects++
                    continue
                }
                check(code == 200) { "HTTP $code" }
                val total = if (info.sizeBytes > 0) info.sizeBytes else conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                return out
            } finally {
                conn.disconnect()
            }
        }
    }

    /** True while Android still needs the one-time "install unknown apps" approval. */
    fun needsInstallPermission(context: Context): Boolean =
        !context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user grants Aether the install permission. */
    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the downloaded APK to the system package installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun httpGetText(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Aether/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
