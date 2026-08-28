package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val changelog: String? = null,
    @SerializedName("assets") val assets: List<GitHubAsset>? = null
)

data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val downloadUrl: String = "",
    @SerializedName("size") val sizeBytes: Long = 0
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val apkUrl: String
)

object AppUpdateManager {
    private const val GITHUB_REPO = "8arpon/Bachelors-Wallet"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Checks GitHub API for newer releases compared to current installed app version.
     */
    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Bachelors-Wallet-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val release = Gson().fromJson(json, GitHubRelease::class.java) ?: return@withContext null

                val latestTag = release.tagName.trim()
                val currentVersion = try {
                    val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                    pInfo.versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }

                val apkAsset = release.assets?.find { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: release.assets?.firstOrNull()

                val isNewer = isVersionNewer(latestTag, currentVersion)

                if (isNewer && apkAsset != null) {
                    return@withContext UpdateInfo(
                        hasUpdate = true,
                        latestVersion = release.tagName,
                        title = release.name ?: "New Update Available",
                        changelog = release.changelog?.ifBlank { null } ?: "• Performance improvements and bug fixes.",
                        apkUrl = apkAsset.downloadUrl
                    )
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Downloads APK from GitHub CDN with progress callback and triggers installation.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Bachelors-Wallet-Android")
            }

            val fileLength = connection.contentLength
            val apkFile = File(context.cacheDir, "update_latest.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalRead: Long = 0
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (fileLength > 0) {
                            val progress = totalRead.toFloat() / fileLength.toFloat()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Download failed. Please check internet connection.") }
        }
    }

    /**
     * Launches Android Package Installer using FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Semantic Version Comparison (e.g. 2.1.0 > 2.0.0, v2.1.0 > 2.0.0)
     */
    fun isVersionNewer(remote: String, local: String): Boolean {
        val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = local.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val localParts = cleanLocal.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
