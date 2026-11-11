package com.quarry.congregationscheduler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdate(
    val version: String,
    val tag: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String?
)

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/charledave2/jw-task-scheduler-android/releases/latest"
    private const val APK_PREFIX = "jw-task-scheduler-"
    private const val APK_SUFFIX = ".apk"
    private const val MAX_APK_BYTES = 150L * 1024L * 1024L

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        if (BuildConfig.NAV_PREVIEW) return@withContext null
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "JWTaskScheduler-Android")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
            val tag = release.optString("tag_name").trim()
            val version = tag.removePrefix("v").takeIf { it.isNotBlank() } ?: return@withContext null
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
            val expectedName = "$APK_PREFIX$version$APK_SUFFIX"
            val assets = release.optJSONArray("assets") ?: return@withContext null
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == expectedName } ?: return@withContext null
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/charledave2/jw-task-scheduler-android/releases/download/")) return@withContext null
            val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            AppUpdate(version, tag, release.optString("body").trim(), url, digest)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(context: Context, update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        check(!BuildConfig.NAV_PREVIEW) { "Production updates are disabled in this preview." }
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "JWTaskScheduler-Android")
        }
        val target = File(File(context.cacheDir, "updates"), "$APK_PREFIX${update.version}$APK_SUFFIX")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("GitHub returned download error $status.")
            val finalHost = connection.url.host.lowercase()
            if (finalHost != "github.com" && !finalHost.endsWith(".githubusercontent.com")) throw IllegalStateException("The APK download came from an unexpected host.")
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES) throw IllegalStateException("The update file is unexpectedly large.")
            target.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > MAX_APK_BYTES) throw IllegalStateException("The update file is unexpectedly large.")
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (length > 0) onProgress((copied.toFloat() / length).coerceIn(0f, 1f))
                }
            } }
            if (copied == 0L) throw IllegalStateException("GitHub returned an empty APK.")
            update.sha256?.let { expected ->
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) throw IllegalStateException("The downloaded APK failed its integrity check.")
            }
            target
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun install(context: Context, apk: File) {
        check(!BuildConfig.NAV_PREVIEW) { "Production updates are disabled in this preview." }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canInstallPackages(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun isNewer(remote: String, current: String): Boolean {
        val left = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
