package com.example.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Handles automatic app updates via GitHub Releases.
 *
 * Flow:
 * 1. A periodic WorkManager job checks the GitHub Releases API every 6 hours.
 * 2. If a release with a higher versionCode is found, the APK asset is downloaded
 *    silently in the background using Android's DownloadManager.
 * 3. Once the download completes, the system package installer is invoked
 *    to install the update. On Android, this requires a single user tap on "Install".
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val WORK_NAME = "prismcast_auto_update"

    // ── GitHub configuration ───────────────────────────────────────────
    // Change these if the repo is forked or moved.
    private const val GITHUB_OWNER = "mr-ceo7"
    private const val GITHUB_REPO = "prism-cast-desk"
    private const val RELEASES_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // ── Moshi models for the GitHub API response ───────────────────────

    @JsonClass(generateAdapter = true)
    data class GitHubRelease(
        @Json(name = "tag_name") val tagName: String,
        val assets: List<GitHubAsset>
    )

    @JsonClass(generateAdapter = true)
    data class GitHubAsset(
        val name: String,
        @Json(name = "browser_download_url") val downloadUrl: String,
        @Json(name = "content_type") val contentType: String?
    )

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Schedules periodic update checks. Safe to call multiple times —
     * WorkManager de-duplicates by [WORK_NAME].
     */
    fun scheduleUpdateChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Scheduled periodic update checks (every 6 h)")
    }

    /**
     * Runs an immediate, one-shot update check. Call from the UI thread — work
     * is dispatched to IO internally.
     */
    suspend fun checkNow(context: Context) {
        withContext(Dispatchers.IO) {
            performUpdateCheck(context)
        }
    }

    // ── Internal logic ─────────────────────────────────────────────────

    private fun performUpdateCheck(context: Context) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned ${response.code}")
                return
            }

            val body = response.body?.string() ?: return
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(GitHubRelease::class.java)
            val release = adapter.fromJson(body) ?: return

            val remoteVersion = parseVersionCode(release.tagName)
            val localVersion = getLocalVersionCode(context)

            Log.i(TAG, "Local versionCode=$localVersion, remote versionCode=$remoteVersion")

            if (remoteVersion > localVersion) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    Log.i(TAG, "New version found! Downloading ${apkAsset.name}...")
                    downloadAndInstall(context, apkAsset.downloadUrl, apkAsset.name)
                } else {
                    Log.w(TAG, "Release ${release.tagName} has no APK asset")
                }
            } else {
                Log.i(TAG, "App is up to date")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
        }
    }

    /**
     * Extracts a numeric versionCode from the release tag.
     *
     * Supports formats:
     *   - `v1.2.3`   → versionCode derived as `1*10000 + 2*100 + 3` = 10203
     *   - `v5`       → 50000
     *   - `1.0`      → 10000
     *
     * If parsing fails, returns 0 so the update is skipped safely.
     */
    private fun parseVersionCode(tag: String): Long {
        return try {
            val cleaned = tag.removePrefix("v").removePrefix("V")
            val parts = cleaned.split(".")
            when (parts.size) {
                1 -> parts[0].toLong() * 10000
                2 -> parts[0].toLong() * 10000 + parts[1].toLong() * 100
                else -> parts[0].toLong() * 10000 + parts[1].toLong() * 100 + parts[2].toLong()
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Could not parse version from tag: $tag", e)
            0L
        }
    }

    private fun getLocalVersionCode(context: Context): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read local versionCode", e)
            0L
        }
    }

    /**
     * Downloads the APK via [DownloadManager] (background, with progress in the
     * notification shade), then kicks off the package installer when done.
     */
    private fun downloadAndInstall(context: Context, url: String, fileName: String) {
        // Clean up previous downloads
        val updatesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "updates"
        )
        if (updatesDir.exists()) updatesDir.listFiles()?.forEach { it.delete() }
        updatesDir.mkdirs()

        val destination = File(updatesDir, fileName)

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Prism Cast Update")
            setDescription("Downloading new version…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(destination))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)

        // Register a receiver to trigger install when the download finishes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    installApk(ctx, destination)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Launches the system package installer for the given APK file.
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
        }
    }

    // ── WorkManager Worker ─────────────────────────────────────────────

    class UpdateCheckWorker(
        appContext: Context,
        params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return try {
                withContext(Dispatchers.IO) {
                    performUpdateCheck(applicationContext)
                }
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "UpdateCheckWorker failed", e)
                Result.retry()
            }
        }
    }
}
