package com.banner.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater: asks GitHub for the newest release of this public repository, and, when it is newer
 * than the running build, downloads its APK and opens the system installer. No account or token is
 * needed because the repository is public.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/The412Banner/BannerCloud/releases/latest"

    data class Release(val version: String, val tag: String, val apkUrl: String, val notes: String, val size: Long)

    sealed interface Result {
        data class Available(val release: Release) : Result
        object UpToDate : Result
        data class Failed(val message: String) : Result
    }

    /** Queries GitHub and compares the latest stable release to this build. Safe to call off the main thread. */
    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject(get(LATEST))
            // Only stable releases are offered (prereleases and drafts are skipped).
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return@withContext Result.UpToDate
            val tag = obj.getString("tag_name")
            val version = tag.removePrefix("v")
            val assets = obj.optJSONArray("assets")
            var apkUrl = ""
            var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url"); size = a.optLong("size"); break
                }
            }
            if (apkUrl.isEmpty()) return@withContext Result.Failed("The latest release has no APK.")
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext Result.UpToDate
            Result.Available(Release(version, tag, apkUrl, obj.optString("body"), size))
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Downloads the release APK to the cache and returns the file. Safe to call off the main thread. */
    suspend fun download(ctx: Context, release: Release): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk")
        if (out.exists()) out.delete()
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "EvenBetterXcloud")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("download HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        out
    }

    /**
     * On Android O and newer the app needs the user's permission to install packages. Returns true when
     * it is already granted; otherwise opens the right settings screen and returns false.
     */
    fun ensureInstallPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    /** Opens the system installer for a downloaded APK. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "EvenBetterXcloud")
        }
        return try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("GitHub HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Compares dotted numeric versions ("0.1.10" > "0.1.2"); non-numeric parts are ignored. */
    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
