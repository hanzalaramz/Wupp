package com.wa.beastclient.core.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.wa.beastclient.BuildConfig
import com.wa.beastclient.MainActivity
import com.wa.beastclient.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isPreRelease: Boolean = false
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val silent: Boolean) : UpdateUiState
    data class UpToDate(val currentVersion: String, val checkedAtMs: Long) : UpdateUiState
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateUiState
    data class Downloading(
        val release: AppReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class ReadyToInstall(val release: AppReleaseInfo, val apkPath: String) : UpdateUiState
    data class Error(
        val message: String,
        val silent: Boolean,
        val release: AppReleaseInfo? = null,
        val apkPath: String? = null
    ) : UpdateUiState
}

object WuppUpdater {
    private const val PREFS_NAME = "wupp_updater_prefs"
    private const val KEY_AUTO_CHECK = "auto_check_enabled"
    private const val KEY_INCLUDE_PRE_RELEASES = "include_pre_releases"
    private const val KEY_SKIPPED_TAG = "skipped_release_tag"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val AUTO_CHECK_INTERVAL_MS = 60_000L
    private const val UPDATE_CHANNEL_ID = "wupp_updates_channel"
    private const val UPDATE_NOTIFICATION_ID = 4209

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _autoCheckEnabled = MutableStateFlow(true)
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    private val _includePreReleases = MutableStateFlow(false)
    val includePreReleases: StateFlow<Boolean> = _includePreReleases.asStateFlow()

    private val _showPromptDialog = MutableStateFlow(false)
    val showPromptDialog: StateFlow<Boolean> = _showPromptDialog.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var dismissedInCurrentVisit = false

    @Volatile
    private var checkInFlight = false

    @Volatile
    private var activeInstallRelease: AppReleaseInfo? = null

    @Volatile
    private var activeInstallApkPath: String? = null

    fun onPackageInstallSucceeded() {
        val curRelease = activeInstallRelease
        activeInstallRelease = null
        activeInstallApkPath = null
        _showPromptDialog.value = false
        if (curRelease != null) {
            _state.value = UpdateUiState.UpToDate(curRelease.versionName, System.currentTimeMillis())
        }
    }

    fun onPackageInstallCancelled() {
        val rel = activeInstallRelease
        val path = activeInstallApkPath
        if (rel != null && !path.isNullOrBlank() && File(path).exists()) {
            _state.value = UpdateUiState.ReadyToInstall(rel, path)
            _showPromptDialog.value = true
        }
    }

    fun onPackageInstallFailed(message: String) {
        _state.value = UpdateUiState.Error(
            message = message,
            silent = false,
            release = activeInstallRelease,
            apkPath = activeInstallApkPath?.takeIf { File(it).exists() }
        )
        _showPromptDialog.value = true
    }

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AUTO_CHECK, true)) {
            prefs.edit().putBoolean(KEY_AUTO_CHECK, true).apply()
        }
        _autoCheckEnabled.value = true
        _includePreReleases.value = prefs.getBoolean(KEY_INCLUDE_PRE_RELEASES, false)
        initialized = true
    }

    fun setAutoCheckEnabled(context: Context, @Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        init(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHECK, true)
            .apply()
        _autoCheckEnabled.value = true
    }

    fun setIncludePreReleases(context: Context, enabled: Boolean) {
        init(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INCLUDE_PRE_RELEASES, enabled)
            .remove(KEY_LAST_CHECK_MS)
            .apply()
        _includePreReleases.value = enabled
        val cur = _state.value
        val curRelease = when (cur) {
            is UpdateUiState.UpdateAvailable -> cur.release
            is UpdateUiState.ReadyToInstall -> cur.release
            else -> null
        }
        if (cur is UpdateUiState.UpToDate ||
            cur is UpdateUiState.Error ||
            (!enabled && curRelease?.isPreRelease == true)
        ) {
            _state.value = UpdateUiState.Idle
            if (!enabled && curRelease?.isPreRelease == true) {
                _showPromptDialog.value = false
            }
        }
    }

    fun openPromptDialog() {
        dismissedInCurrentVisit = false
        _showPromptDialog.value = true
    }

    fun onAppBackgrounded() {
        dismissedInCurrentVisit = false
    }

    suspend fun onAppOpened(context: Context) {
        val appCtx = context.applicationContext
        init(appCtx)
        if (!_autoCheckEnabled.value) return
        val current = _state.value
        if (!dismissedInCurrentVisit &&
            (current is UpdateUiState.UpdateAvailable || current is UpdateUiState.ReadyToInstall)
        ) {
            _showPromptDialog.value = true
        }
        checkForUpdates(appCtx, silent = true)
    }

    fun dismissPromptDialog(context: Context, skipVersion: Boolean = false) {
        dismissedInCurrentVisit = true
        _showPromptDialog.value = false
        if (skipVersion) {
            val tag = when (val s = _state.value) {
                is UpdateUiState.UpdateAvailable -> s.release.tagName
                is UpdateUiState.ReadyToInstall -> s.release.tagName
                else -> null
            }
            if (!tag.isNullOrBlank()) {
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SKIPPED_TAG, tag)
                    .apply()
            }
        }
    }

    suspend fun checkForUpdates(context: Context, silent: Boolean): UpdateUiState {
        val appCtx = context.applicationContext
        init(appCtx)
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!silent) {
            dismissedInCurrentVisit = false
        }

        if (_state.value is UpdateUiState.Downloading) {
            if (!silent || !dismissedInCurrentVisit) _showPromptDialog.value = true
            return _state.value
        }

        if (silent) {
            if (!_autoCheckEnabled.value) return _state.value
            val now = System.currentTimeMillis()
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            if (now - lastCheck < AUTO_CHECK_INTERVAL_MS && _state.value !is UpdateUiState.Idle) {
                val cur = _state.value
                if (!dismissedInCurrentVisit &&
                    (cur is UpdateUiState.UpdateAvailable || cur is UpdateUiState.ReadyToInstall)
                ) {
                    _showPromptDialog.value = true
                }
                return cur
            }
        }

        if (checkInFlight) {
            return _state.value
        }
        checkInFlight = true
        try {
            _state.value = UpdateUiState.Checking(silent = silent)
            val repoSlug = BuildConfig.GITHUB_REPO.trim().trim('/')
            val includePre = _includePreReleases.value

            val result = withContext(Dispatchers.IO) {
                fetchLatestRelease(repoSlug, includePreReleases = includePre)
            }

            prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()

            return result.fold(
                onSuccess = { release ->
                    val current = BuildConfig.VERSION_NAME
                    if (release != null && isNewerVersion(release.versionName, current)) {
                        val existingReady = findExistingDownloadedApk(appCtx, release)
                        val nextState = if (existingReady != null) {
                            UpdateUiState.ReadyToInstall(release, existingReady.absolutePath)
                        } else {
                            UpdateUiState.UpdateAvailable(release)
                        }
                        _state.value = nextState
                        if (!silent || !dismissedInCurrentVisit) {
                            _showPromptDialog.value = true
                            postUpdateNotification(appCtx, release)
                        }
                        nextState
                    } else {
                        val upToDate = UpdateUiState.UpToDate(current, System.currentTimeMillis())
                        _state.value = upToDate
                        upToDate
                    }
                },
                onFailure = { err ->
                    val errorState = UpdateUiState.Error(
                        message = err.message ?: "Unable to check GitHub releases",
                        silent = silent
                    )
                    _state.value = errorState
                    errorState
                }
            )
        } finally {
            checkInFlight = false
        }
    }

    private fun updatesDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun findExistingDownloadedApk(context: Context, release: AppReleaseInfo): File? {
        val dir = updatesDir(context)
        val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val internalFile = File(dir, "Wupp-$safeTag.apk")
        if (internalFile.exists() && internalFile.length() > 1_000_000L &&
            (release.apkSizeBytes <= 0L || internalFile.length() == release.apkSizeBytes)
        ) {
            return internalFile
        }
        return null
    }

    suspend fun downloadUpdate(context: Context, release: AppReleaseInfo) {
        val appCtx = context.applicationContext
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            _state.value = UpdateUiState.Error(
                message = "No APK asset attached to ${release.tagName}. Open the release page in your browser to download.",
                silent = false,
                release = release
            )
            return
        }

        _state.value = UpdateUiState.Downloading(
            release = release,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes
        )

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = updatesDir(appCtx)
                val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val outFile = File(dir, "Wupp-$safeTag.apk")
                val tmpFile = File(dir, "Wupp-$safeTag.apk.part")

                // Remove old cached APKs / leftover partial files so internal storage stays clean
                dir.listFiles()?.forEach { child ->
                    if (child.name != outFile.name && (child.name.endsWith(".apk") || child.name.endsWith(".part"))) {
                        runCatching { child.delete() }
                    }
                }

                var lastErr: Throwable? = null
                var downloadedFile: File? = null
                for (attempt in 1..2) {
                    try {
                        if (tmpFile.exists()) tmpFile.delete()
                        val conn = openFollowingRedirects(downloadUrl)
                        try {
                            val code = conn.responseCode
                            if (code !in 200..299) {
                                throw IllegalStateException("Download error (HTTP $code)")
                            }
                            val total = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                            conn.inputStream.use { input ->
                                FileOutputStream(tmpFile).use { output ->
                                    val buf = ByteArray(16 * 1024)
                                    var downloaded = 0L
                                    var lastEmitMs = 0L
                                    while (true) {
                                        val read = input.read(buf)
                                        if (read <= 0) break
                                        output.write(buf, 0, read)
                                        downloaded += read
                                        val now = System.currentTimeMillis()
                                        if (now - lastEmitMs >= 120L || (total > 0 && downloaded >= total)) {
                                            lastEmitMs = now
                                            val pct = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else -1
                                            _state.value = UpdateUiState.Downloading(
                                                release = release,
                                                progressPercent = pct,
                                                downloadedBytes = downloaded,
                                                totalBytes = total
                                            )
                                        }
                                    }
                                    output.flush()
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }

                        if (!tmpFile.exists() || tmpFile.length() < 100_000L) {
                            throw IllegalStateException("Downloaded APK is incomplete")
                        }
                        if (outFile.exists()) outFile.delete()
                        if (!tmpFile.renameTo(outFile)) {
                            tmpFile.copyTo(outFile, overwrite = true)
                            tmpFile.delete()
                        }
                        downloadedFile = outFile
                        break
                    } catch (err: Throwable) {
                        lastErr = err
                        if (tmpFile.exists()) tmpFile.delete()
                        if (attempt < 2) delay(900L)
                    }
                }
                downloadedFile ?: throw (lastErr ?: IllegalStateException("APK download failed"))
            }
        }

        result.fold(
            onSuccess = { apkFile ->
                activeInstallRelease = release
                activeInstallApkPath = apkFile.absolutePath
                _state.value = UpdateUiState.ReadyToInstall(release, apkFile.absolutePath)
                installApk(appCtx, apkFile).onFailure { err ->
                    _state.value = UpdateUiState.Error(
                        message = err.message ?: "Unable to start update installer",
                        silent = false,
                        release = release,
                        apkPath = apkFile.absolutePath
                    )
                }
            },
            onFailure = { err ->
                _state.value = UpdateUiState.Error(
                    message = err.message ?: "Download error — tap Retry or Open the web.",
                    silent = false,
                    release = release
                )
            }
        )
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        val appCtx = context.applicationContext
        if (!apkFile.exists()) {
            throw IllegalStateException("APK file not found: ${apkFile.name}")
        }
        val cur = _state.value
        val curRel = when (cur) {
            is UpdateUiState.ReadyToInstall -> cur.release
            is UpdateUiState.Error -> cur.release
            else -> null
        }
        if (curRel != null) activeInstallRelease = curRel
        activeInstallApkPath = apkFile.absolutePath

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appCtx.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appCtx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appCtx.startActivity(permissionIntent)
            throw IllegalStateException("Allow \"Install unknown apps\" for Wupp, then tap Install now again.")
        }

        val installer = appCtx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apkFile.length())
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("wupp_update.apk", 0L, apkFile.length()).use { out ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(out, bufferSize = 32 * 1024)
                }
                session.fsync(out)
            }
            val callbackIntent = Intent(appCtx, UpdateInstallReceiver::class.java)
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                appCtx,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            session.commit(pendingIntent.intentSender)
        } catch (err: Throwable) {
            runCatching { session.abandon() }
            throw err
        } finally {
            runCatching { session.close() }
        }
    }

    fun installViaExternalChooser(context: Context, apkFile: File): Result<Unit> = runCatching {
        val appCtx = context.applicationContext
        if (!apkFile.exists()) {
            throw IllegalStateException("APK file not found: ${apkFile.name}")
        }
        val apkUri = FileProvider.getUriForFile(
            appCtx,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appCtx.startActivity(installIntent)
    }

    private fun fetchLatestRelease(
        repoSlug: String,
        includePreReleases: Boolean
    ): Result<AppReleaseInfo?> = runCatching {
        if (!repoSlug.contains("/")) {
            throw IllegalStateException("Invalid GitHub repository: $repoSlug")
        }
        if (includePreReleases) {
            return@runCatching fetchLatestFromReleasesList(repoSlug, includePreReleases = true)
        }
        val latestUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        val conn = (URL(latestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Wupp-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            when (code) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parseReleaseJson(JSONObject(body), allowPreRelease = false)
                }
                404 -> {
                    // No stable /releases/latest found; check list for non-prerelease published releases.
                    fetchLatestFromReleasesList(repoSlug, includePreReleases = false)
                }
                else -> {
                    val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val apiMsg = runCatching { JSONObject(errText).optString("message") }.getOrDefault("")
                    throw IllegalStateException(
                        if (apiMsg.isNotBlank()) "GitHub API ($code): $apiMsg" else "GitHub API returned HTTP $code"
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchLatestFromReleasesList(
        repoSlug: String,
        includePreReleases: Boolean
    ): AppReleaseInfo? {
        val listUrl = "https://api.github.com/repos/$repoSlug/releases?per_page=10"
        val conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Wupp-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val apiMsg = runCatching { JSONObject(errText).optString("message") }.getOrDefault("")
                throw IllegalStateException(
                    if (apiMsg.isNotBlank()) "GitHub API ($code): $apiMsg" else "GitHub API returned HTTP $code"
                )
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            var best: AppReleaseInfo? = null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val candidate = parseReleaseJson(obj, allowPreRelease = includePreReleases) ?: continue
                if (best == null || isNewerVersion(candidate.versionName, best.versionName)) {
                    best = candidate
                }
            }
            return best
        } finally {
            conn.disconnect()
        }
    }

    private fun parseReleaseJson(obj: JSONObject, allowPreRelease: Boolean = true): AppReleaseInfo? {
        if (obj.optBoolean("draft", false)) return null
        val isPreRelease = obj.optBoolean("prerelease", false)
        if (!allowPreRelease && isPreRelease) return null
        val tagName = obj.optString("tag_name").trim()
        if (tagName.isBlank()) return null
        val versionName = tagName.removePrefix("v").removePrefix("V").trim()
        val title = obj.optString("name").trim().ifBlank { tagName }
        val changelog = obj.optString("body").trim().ifBlank { "No changelog notes provided for $tagName." }
        val htmlUrl = obj.optString("html_url").trim()
        val publishedAt = obj.optString("published_at").trim()

        val assets = obj.optJSONArray("assets") ?: JSONArray()
        var bestApkUrl: String? = null
        var bestApkName: String? = null
        var bestApkSize = 0L
        var bestScore = -1
        val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
        val primaryAbi = supportedAbis.firstOrNull().orEmpty()

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val lower = name.lowercase()
            val abiScore = when {
                (lower.contains("arm64-v8a") || lower.contains("arm64") || lower.contains("armv8")) ->
                    if (primaryAbi.contains("arm64")) 100 else if (supportedAbis.any { it.contains("arm64") }) 70 else -50
                (lower.contains("armeabi-v7a") || lower.contains("armeabi") || lower.contains("armv7")) ->
                    if (primaryAbi.contains("armeabi-v7a")) 100 else if (supportedAbis.any { it.contains("armeabi") }) 60 else -50
                (lower.contains("x86_64") || lower.contains("amd64")) ->
                    if (primaryAbi == "x86_64") 100 else if (supportedAbis.contains("x86_64")) 60 else -50
                lower.contains("x86") ->
                    if (primaryAbi == "x86") 100 else if (supportedAbis.contains("x86")) 55 else -50
                lower.contains("universal") -> 40
                else -> 25
            }
            if (abiScore < 0) continue
            val releaseBoost = if (lower.contains("release")) 5 else 0
            val score = abiScore + releaseBoost
            if (score > bestScore) {
                bestScore = score
                bestApkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                bestApkName = name
                bestApkSize = asset.optLong("size", 0L)
            }
        }

        return AppReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            changelog = changelog,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            apkDownloadUrl = bestApkUrl,
            apkFileName = bestApkName,
            apkSizeBytes = bestApkSize,
            isPreRelease = isPreRelease
        )
    }

    private fun openFollowingRedirects(urlStr: String): HttpURLConnection {
        var currentUrl = urlStr
        repeat(8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("Accept", "application/octet-stream, */*")
                setRequestProperty("User-Agent", "Wupp-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect missing Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects while downloading update")
    }

    private fun postUpdateNotification(context: Context, release: AppReleaseInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Wupp Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new Wupp release is published on GitHub"
            }
            nm.createNotificationChannel(channel)
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_update_prompt", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wupp)
            .setContentTitle("Wupp ${release.tagName}${if (release.isPreRelease) " (Pre-release)" else ""} available")
            .setContentText("Tap to review changelog and download the update.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseNumericVersionParts(remote)
        val currentParts = parseNumericVersionParts(current)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        val remotePre = parsePreRelease(remote)
        val currentPre = parsePreRelease(current)
        if (remotePre == null && currentPre != null) return true
        if (remotePre != null && currentPre == null) return false
        if (remotePre == null || currentPre == null) return false

        val preLen = maxOf(remotePre.size, currentPre.size)
        for (i in 0 until preLen) {
            val rSeg = remotePre.getOrNull(i)
            val cSeg = currentPre.getOrNull(i)
            if (rSeg == null) return false
            if (cSeg == null) return true
            val rInt = rSeg.toIntOrNull()
            val cInt = cSeg.toIntOrNull()
            when {
                rInt != null && cInt != null -> if (rInt != cInt) return rInt > cInt
                rInt != null -> return false
                cInt != null -> return true
                else -> {
                    val cmp = rSeg.compareTo(cSeg, ignoreCase = true)
                    if (cmp != 0) return cmp > 0
                }
            }
        }
        return false
    }

    private fun parsePreRelease(raw: String): List<String>? {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V").substringBefore("+")
        if (!trimmed.contains("-")) return null
        val suffix = trimmed.substringAfter("-").trim()
        return suffix.takeIf { it.isNotBlank() }?.split(".")
    }

    private fun parseNumericVersionParts(raw: String): List<Int> {
        val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
        return core.split(".").map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
    }
}
