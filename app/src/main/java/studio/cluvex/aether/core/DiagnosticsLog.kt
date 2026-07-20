package studio.cluvex.aether.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogLine(
    val timeMs: Long,
    val tag: String,
    val level: LogLevel,
    val message: String,
    /** Restored-from-disk lines are already formatted; print them verbatim. */
    val raw: Boolean = false,
) {
    fun format(): String {
        if (raw) return message
        val ts = TS_FORMAT.get()?.format(Date(timeMs)) ?: timeMs.toString()
        val lvl = when (level) {
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        return "$ts $lvl/$tag: $message"
    }

    private companion object {
        val TS_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }
}

enum class CheckState { PENDING, RUNNING, PASS, FAIL }

data class ComponentCheck(
    val id: String,
    val label: String,
    val state: CheckState = CheckState.PENDING,
    val detail: String = "",
)

/**
 * In-app, professional-grade log + self-test store. Every moving part of the
 * tunnel (engine process, in-process hev tunnel, VpnService lifecycle and the
 * connectivity self-tests) writes here, so the UI can show exactly which stage
 * fails when "connected but no site loads" happens.
 *
 * CRASH-SURVIVAL (root-cause fix): now that hev runs IN-PROCESS, a native fault
 * or an [Error] such as UnsatisfiedLinkError can take the whole app down. A
 * memory-only log is wiped by that death, so the user "sees no log after a
 * crash". We therefore mirror every line to a file on disk as it is written and
 * reload it on next launch, so the crashing session is always inspectable.
 */
object DiagnosticsLog {
    private const val MAX_LINES = 800

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val _checks = MutableStateFlow<List<ComponentCheck>>(emptyList())
    val checks: StateFlow<List<ComponentCheck>> = _checks.asStateFlow()

    @Volatile
    private var logFile: File? = null

    /**
     * Wires the persistent log file (call once from Application.onCreate). If a
     * file from a previous run exists (e.g. it ended in a crash), its contents
     * are preserved to `<name>.prev` and loaded back into the panel so the
     * crash is visible after relaunch.
     */
    @Synchronized
    fun init(file: File) {
        logFile = file
        runCatching {
            if (file.exists() && file.length() > 0L) {
                val previous = file.readLines()
                runCatching { file.copyTo(File(file.parentFile, file.name + ".prev"), overwrite = true) }
                val restored = previous.takeLast(MAX_LINES).map {
                    LogLine(0L, "prev", LogLevel.DEBUG, it, raw = true)
                }
                _lines.value = restored + LogLine(
                    System.currentTimeMillis(),
                    "log",
                    LogLevel.INFO,
                    "—— previous session restored (${restored.size} lines) ——",
                )
            }
        }
    }

    @Synchronized
    fun log(tag: String, level: LogLevel, message: String) {
        val line = LogLine(System.currentTimeMillis(), tag, level, message)
        val next = _lines.value + line
        _lines.value = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
        // Flush to disk immediately so nothing is lost if the process dies.
        runCatching { logFile?.appendText(line.format() + "\n") }
    }

    fun d(tag: String, m: String) = log(tag, LogLevel.DEBUG, m)
    fun i(tag: String, m: String) = log(tag, LogLevel.INFO, m)
    fun w(tag: String, m: String) = log(tag, LogLevel.WARN, m)
    fun e(tag: String, m: String) = log(tag, LogLevel.ERROR, m)

    /**
     * Starts a fresh session. The prior on-disk log is rotated to `<name>.prev`
     * (never silently destroyed) so a crash log is always recoverable.
     */
    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        runCatching {
            logFile?.let { f ->
                if (f.exists() && f.length() > 0L) {
                    f.copyTo(File(f.parentFile, f.name + ".prev"), overwrite = true)
                }
                f.writeText("")
            }
        }
    }

    @Synchronized
    fun setChecks(checks: List<ComponentCheck>) {
        _checks.value = checks
    }

    @Synchronized
    fun updateCheck(id: String, state: CheckState, detail: String? = null) {
        _checks.value = _checks.value.map {
            if (it.id == id) it.copy(state = state, detail = detail ?: it.detail) else it
        }
    }

    fun exportText(): String = _lines.value.joinToString("\n") { it.format() }
}
