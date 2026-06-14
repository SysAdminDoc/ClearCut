package com.novacut.editor.engine

import com.novacut.editor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passive, opt-in update check for sideload / GitHub-release installs.
 *
 * GitHub-release users are NovaCut's only current distribution channel and
 * otherwise have no way to learn about fixes. This makes a single, TLS-only
 * request to the public GitHub releases API and compares the latest tag with
 * the installed version. It never downloads or installs an APK — the UI only
 * links to the release page — which keeps it clear of the F-Droid
 * self-updater anti-feature.
 *
 * The gate lives in [UpdateCheckPolicy]: with the Settings toggle off (the
 * default) or the build flavor opting out via
 * `BuildConfig.UPDATE_CHECK_AVAILABLE`, [check] returns [Result.Unavailable]
 * without making any network call. No identifiers beyond the bare HTTPS GET
 * are sent.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    sealed interface Result {
        /** Feature compiled out, or the user has not opted in. No call made. */
        data object Unavailable : Result

        /** The installed version is current. */
        data object UpToDate : Result

        /** A newer release exists; [releaseUrl] points at its GitHub page. */
        data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : Result

        /** The check ran but failed (offline, rate limited, parse error). */
        data class Failed(val reason: String) : Result
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Run the check. Returns [Result.Unavailable] immediately — before any
     * network access — when the policy gate is closed.
     */
    suspend fun check(
        userEnabled: Boolean,
        currentVersion: String = BuildConfig.VERSION_NAME,
        buildSupportsUpdateCheck: Boolean = BuildConfig.UPDATE_CHECK_AVAILABLE,
    ): Result {
        if (!UpdateCheckPolicy.mayCheckNetwork(buildSupportsUpdateCheck, userEnabled)) {
            return Result.Unavailable
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_ENDPOINT)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.Failed("HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use Result.Failed("empty response")
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                    val htmlUrl = json.optString("html_url").ifBlank { RELEASES_PAGE }
                    if (UpdateCheckPolicy.updateAvailable(tag, currentVersion)) {
                        Result.UpdateAvailable(latestVersion = tag.trim(), releaseUrl = htmlUrl)
                    } else {
                        Result.UpToDate
                    }
                }
            } catch (e: Exception) {
                Result.Failed(e.message ?: "network error")
            }
        }
    }

    companion object {
        const val RELEASES_PAGE = "https://github.com/SysAdminDoc/NovaCut/releases"
        const val LATEST_RELEASE_ENDPOINT =
            "https://api.github.com/repos/SysAdminDoc/NovaCut/releases/latest"

        private const val CALL_TIMEOUT_SECONDS = 15L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
    }
}
