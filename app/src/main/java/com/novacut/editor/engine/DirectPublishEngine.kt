package com.novacut.editor.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct publish facade for YouTube / TikTok / Instagram / Threads.
 *
 * Strategy:
 *   1. When the OAuth creds are configured AND a native SDK/library is present,
 *      perform a resumable upload through the platform API.
 *   2. Otherwise fall back to a platform-branded share intent targeting the
 *      installed client app (requires the user to have it installed). This
 *      works offline and preserves user privacy with no NovaCut-side upload.
 *
 * Today only the share-intent fallback is wired. The OAuth resumable-upload
 * path requires platform partner approval (TikTok) or API-key setup (YT Data
 * API v3). Leaving the hook in the engine so the UI surface is stable and
 * we can ship the API integration incrementally without UI churn.
 */
@Singleton
class DirectPublishEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class Target(val displayName: String, val packageName: String?) {
        YOUTUBE("YouTube", "com.google.android.youtube"),
        TIKTOK("TikTok", "com.zhiliaoapp.musically"),
        INSTAGRAM("Instagram Reels", "com.instagram.android"),
        THREADS("Threads", "com.instagram.barcelona"),
        TWITTER_X("X", "com.twitter.android"),
        LINKEDIN("LinkedIn", "com.linkedin.android")
    }

    data class PublishMeta(
        val title: String,
        val description: String,
        val tags: List<String>,
        val chapters: String = "",
        val visibility: Visibility = Visibility.PRIVATE
    )

    enum class Visibility { PUBLIC, UNLISTED, PRIVATE }

    data class Result(val intent: Intent?, val used: Method, val message: String)
    enum class Method { API_UPLOAD, SHARE_INTENT, NONE }

    suspend fun publish(
        filePath: String,
        target: Target,
        meta: PublishMeta
    ): Result = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext Result(null, Method.NONE, "Export file not found")
        }
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider failed for $filePath", e)
            return@withContext Result(null, Method.NONE, "Share provider unavailable")
        }
        val intent = buildShareIntent(uri, target, meta)
        if (target.packageName != null && isInstalled(target.packageName)) {
            intent.setPackage(target.packageName)
        }
        Result(intent, Method.SHARE_INTENT, "Opening ${target.displayName}…")
    }

    private fun buildShareIntent(uri: Uri, target: Target, meta: PublishMeta): Intent {
        val body = buildString {
            append(meta.title)
            if (meta.description.isNotBlank()) append("\n\n").append(meta.description)
            if (meta.chapters.isNotBlank()) append("\n\n").append(meta.chapters)
            if (meta.tags.isNotEmpty()) {
                append("\n\n")
                append(meta.tags.joinToString(" ") { "#${it.replace(Regex("[^A-Za-z0-9_]"), "")}" })
            }
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, meta.title)
            putExtra(Intent.EXTRA_SUBJECT, meta.title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    companion object { private const val TAG = "DirectPublishEngine" }
}
