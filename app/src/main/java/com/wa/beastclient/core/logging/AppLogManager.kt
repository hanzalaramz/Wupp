package com.wa.beastclient.core.logging

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.wa.beastclient.BuildConfig
import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Captures rolling runtime/bridge logs and uncaught crash traces locally, and lets users
 * inspect, save to Downloads / Files, or share diagnostic reports when troubleshooting bugs.
 */
object AppLogManager {
    private const val TAG = "AppLogManager"
    private const val PREFS_NAME = "wupp_diagnostics_prefs"
    private const val KEY_PENDING_CRASH_NOTICE = "pending_crash_notice"
    private const val KEY_LAST_CRASH_EPOCH_MS = "last_crash_epoch_ms"
    private const val LOGS_DIR_NAME = "logs"
    private const val SESSION_LOG_FILE = "wupp_session.log"
    private const val PREV_LOG_FILE = "wupp_session_prev.log"
    private const val CRASH_LOG_FILE = "crash_latest.log"
    private const val MAX_SESSION_BYTES = 512L * 1024L

    private val writeLock = Any()
    private val pendingLines = ArrayList<String>(32)
    private val logFlushExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "wupp-log-flush").apply { isDaemon = true }
    }
    private var scheduledFlush: ScheduledFuture<*>? = null
    private val lineTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var crashHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        ensureLogDir(appCtx)
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordUncaughtCrash(appCtx, thread, throwable)
            } catch (_: Throwable) {
                // Never mask original crash propagation
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
        log("INFO", "App", "Wupp v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val ctx = appContext ?: return
        val cleanLevel = level.trim().uppercase(Locale.US).ifBlank { "INFO" }
        val cleanTag = tag.trim().ifBlank { "Wupp" }
        val ts = lineTimeFormat.get()?.format(Date()).orEmpty()
        val stack = throwable?.let { "\n${stackTraceToString(it)}" }.orEmpty()
        val entry = "$ts [$cleanLevel/$cleanTag] $message$stack"
        val urgent = cleanLevel == "ERROR" || cleanLevel == "CRASH" || throwable != null

        synchronized(writeLock) {
            pendingLines.add(entry)
            if (urgent || pendingLines.size >= 32) {
                scheduledFlush?.cancel(false)
                scheduledFlush = null
                flushPendingLocked(ctx)
            } else if (scheduledFlush == null || scheduledFlush?.isDone == true) {
                scheduledFlush = logFlushExecutor.schedule(
                    {
                        val currentCtx = appContext ?: return@schedule
                        synchronized(writeLock) {
                            scheduledFlush = null
                            flushPendingLocked(currentCtx)
                        }
                    },
                    3L,
                    TimeUnit.SECONDS
                )
            }
        }
    }

    private fun flushPendingLocked(ctx: Context) {
        if (pendingLines.isEmpty()) return
        val batchText = buildString {
            pendingLines.forEach { appendLine(it) }
        }
        pendingLines.clear()
        runCatching {
            val dir = ensureLogDir(ctx)
            val file = File(dir, SESSION_LOG_FILE)
            rotateIfOversizedLocked(dir, file)
            file.appendText(batchText)
        }
    }

    internal fun formatDiagnosticReportHeader(
        versionName: String,
        versionCode: Int,
        manufacturer: String,
        model: String,
        androidRelease: String,
        sdkInt: Int,
        abisCsv: String,
        generatedAtFormatted: String
    ): String = buildString {
        appendLine("=== Wupp Diagnostic Report ===")
        appendLine("Generated: $generatedAtFormatted")
        appendLine("App Version: $versionName ($versionCode)")
        appendLine("Device: $manufacturer $model")
        appendLine("Android: $androidRelease (API $sdkInt)")
        appendLine("ABIs: $abisCsv")
        appendLine("==============================")
    }

    private fun recordUncaughtCrash(context: Context, thread: Thread, throwable: Throwable) {
        val nowMs = System.currentTimeMillis()
        val formattedTime = lineTimeFormat.get()?.format(Date(nowMs)).orEmpty()
        val header = formatDiagnosticReportHeader(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            abisCsv = Build.SUPPORTED_ABIS.joinToString(", "),
            generatedAtFormatted = formattedTime
        )
        val stack = stackTraceToString(throwable)
        val tailLines = synchronized(writeLock) {
            flushPendingLocked(context)
            val session = File(ensureLogDir(context), SESSION_LOG_FILE)
            if (session.exists()) {
                runCatching { session.readLines().takeLast(200).joinToString("\n") }.getOrDefault("")
            } else ""
        }

        val crashBody = buildString {
            append(header)
            appendLine()
            appendLine("FATAL CRASH on thread '${thread.name}' (id=${thread.id})")
            appendLine(stack)
            if (tailLines.isNotBlank()) {
                appendLine()
                appendLine("--- Recent Logs Before Crash ---")
                appendLine(tailLines)
            }
        }

        synchronized(writeLock) {
            val dir = ensureLogDir(context)
            File(dir, CRASH_LOG_FILE).writeText(crashBody)
            File(dir, SESSION_LOG_FILE).appendText(
                "$formattedTime [CRASH/${thread.name}] ${throwable.javaClass.simpleName}: ${throwable.message}\n$stack\n"
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_CRASH_NOTICE, true)
            .putLong(KEY_LAST_CRASH_EPOCH_MS, nowMs)
            .apply()
    }

    fun hasCrashLog(context: Context): Boolean {
        val file = File(ensureLogDir(context.applicationContext), CRASH_LOG_FILE)
        return file.exists() && file.length() > 0L
    }

    fun hasPendingCrashNotice(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_CRASH_NOTICE, false) && hasCrashLog(context)

    fun dismissCrashNotice(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_CRASH_NOTICE, false)
            .apply()
    }

    fun buildCombinedReportText(context: Context, maxSessionLines: Int = 600): String {
        val ctx = context.applicationContext
        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val header = formatDiagnosticReportHeader(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            abisCsv = Build.SUPPORTED_ABIS.joinToString(", "),
            generatedAtFormatted = nowFormatted
        )
        return synchronized(writeLock) {
            flushPendingLocked(ctx)
            val dir = ensureLogDir(ctx)
            val crashFile = File(dir, CRASH_LOG_FILE)
            val prevFile = File(dir, PREV_LOG_FILE)
            val currFile = File(dir, SESSION_LOG_FILE)

            buildString {
                appendLine(header)
                if (crashFile.exists() && crashFile.length() > 0L) {
                    appendLine()
                    appendLine("--- Latest Crash Report ---")
                    appendLine(runCatching { crashFile.readText() }.getOrDefault(""))
                }
                val sessionLines = buildList {
                    if (prevFile.exists()) {
                        runCatching { addAll(prevFile.readLines().takeLast(maxSessionLines / 2)) }
                    }
                    if (currFile.exists()) {
                        runCatching { addAll(currFile.readLines().takeLast(maxSessionLines)) }
                    }
                }.takeLast(maxSessionLines)
                appendLine()
                appendLine("--- Session Logs (${sessionLines.size} lines) ---")
                if (sessionLines.isEmpty()) {
                    appendLine("(No runtime logs recorded yet in this session.)")
                } else {
                    sessionLines.forEach { appendLine(it) }
                }
            }
        }
    }

    fun readLogPreview(context: Context, maxLines: Int = 120): String =
        buildCombinedReportText(context, maxSessionLines = maxLines)

    fun suggestedLogFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "wupp-logs-$stamp.txt"
    }

    fun buildShareLogsIntent(context: Context): Intent? {
        val ctx = context.applicationContext
        return runCatching {
            val shareDir = File(ctx.cacheDir, "shared_logs").apply { mkdirs() }
            // Keep cache tidy by pruning old temporary exports
            shareDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }
                ?.forEach { runCatching { it.delete() } }

            val outFile = File(shareDir, suggestedLogFileName())
            outFile.writeText(buildCombinedReportText(ctx))
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Wupp Diagnostic Logs (${BuildConfig.VERSION_NAME})")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.onFailure {
            Log.w(TAG, "Failed to prepare shared log file: ${it.message}")
        }.getOrNull()
    }

    fun writeReportToStream(context: Context, out: OutputStream): Result<Unit> = runCatching {
        val report = buildCombinedReportText(context)
        out.bufferedWriter().use { writer -> writer.write(report) }
    }

    fun saveLogsToDownloads(context: Context): Result<String> = runCatching {
        val ctx = context.applicationContext
        val fileName = suggestedLogFileName()
        val content = buildCombinedReportText(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Wupp")
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create file in Downloads")
            resolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { it.write(content) }
            } ?: error("Unable to write to Downloads")
            "Downloads/Wupp/$fileName"
        } else {
            val baseDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ensureLogDir(ctx)
            val targetDir = File(baseDir, "Wupp").apply { mkdirs() }
            val outFile = File(targetDir, fileName)
            outFile.writeText(content)
            outFile.absolutePath
        }
    }

    fun clearLogs(context: Context) {
        val ctx = context.applicationContext
        synchronized(writeLock) {
            pendingLines.clear()
            scheduledFlush?.cancel(false)
            scheduledFlush = null
            val dir = ensureLogDir(ctx)
            runCatching { File(dir, SESSION_LOG_FILE).delete() }
            runCatching { File(dir, PREV_LOG_FILE).delete() }
            runCatching { File(dir, CRASH_LOG_FILE).delete() }
        }
        dismissCrashNotice(ctx)
    }

    private fun ensureLogDir(context: Context): File =
        File(context.filesDir, LOGS_DIR_NAME).apply { mkdirs() }

    private fun rotateIfOversizedLocked(dir: File, current: File) {
        if (current.exists() && current.length() >= MAX_SESSION_BYTES) {
            val prev = File(dir, PREV_LOG_FILE)
            if (prev.exists()) runCatching { prev.delete() }
            runCatching { current.renameTo(prev) }
        }
    }

    private fun stackTraceToString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
