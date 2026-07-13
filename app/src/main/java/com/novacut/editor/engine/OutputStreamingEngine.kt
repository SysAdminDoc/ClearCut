package com.novacut.editor.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.novacut.editor.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R6.17 — Live streaming output engine for the future Live Studio mode (R4.6).
 *
 * Composes against [CameraCaptureEngine] (already stubbed) and the planned
 * scene/source graph: a configured `OutputDestination` consumes encoded
 * H.264 / HEVC packets and pushes them to an RTMP / SRT / WebRTC sink.
 *
 * **Today this is a stub** — no native streaming library is wired. The class
 * exists so the rest of the system has a stable shape to compose against and
 * the Settings / Live Studio panel can render the protocol list without
 * waiting for the library decision. The activation candidates documented
 * below have been evaluated; pick one at integration time based on the
 * specific creator workflow we're chasing.
 *
 * ## Activation candidates
 *
 *  - **Larix RTMP/SRT Android SDK** (Softvelum): proven on mobile, covers
 *    RTMP/RTMPS/SRT/RIST/WebRTC/RTSP/NDI|HX2 with adaptive bitrate and
 *    Talkback audio return. Reference for the protocol surface — see
 *    https://softvelum.com/larix/ — but SDK terms must be reviewed.
 *  - **Stream-Pack** (https://github.com/ThibaultBee/StreamPack): OSS
 *    Apache-2.0 Android RTMP / SRT streamer. Smaller scope, no proprietary
 *    SDK terms.
 *  - **LibSRT-Android** + custom RTMP muxer: build our own; max control,
 *    max maintenance cost.
 *
 * Whichever path lands, it must:
 *  - Probe network adaptive-bitrate down (resolution + framerate) on
 *    congestion, never block the encoder thread.
 *  - Expose the active protocol + bitrate as a `StateFlow` so the Live
 *    Studio panel shows a connection chip.
 *  - Persist credentials per stream key (RTMP key, SRT passphrase) in
 *    `EncryptedSharedPreferences`, never plain DataStore — these are
 *    creator monetization credentials.
 *
 * ## License + bundle size
 *
 * Stream-Pack is Apache-2.0. Larix SDK is proprietary and may require a
 * license fee. LibSRT is MPL-2.0 and pairs cleanly with ClearCut's MIT
 * license. Whichever native blob lands must pass the R6.1 16 KB alignment
 * gate.
 */
@Singleton
class OutputStreamingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    enum class Protocol(val displayName: String, val urlScheme: String) {
        RTMP("RTMP", "rtmp://"),
        RTMPS("RTMPS", "rtmps://"),
        SRT("SRT", "srt://"),
        RIST("RIST", "rist://"),
        WEBRTC("WebRTC", "https://"),
        RTSP("RTSP", "rtsp://"),
    }

    /**
     * A destination the engine can push to. The credentials field is the
     * stream key / passphrase / token; how it's stored is the caller's
     * problem (the UI is responsible for routing through
     * EncryptedSharedPreferences before construction).
     */
    data class OutputDestination(
        val id: String,
        val displayName: String,
        val protocol: Protocol,
        val url: String,
        val credentials: String? = null,
        val targetBitrateBps: Int = 6_000_000,
        val targetFps: Int = 30,
        val width: Int = 1920,
        val height: Int = 1080,
    )

    enum class StreamState {
        IDLE, CONNECTING, STREAMING, RECONNECTING, ENDED, ERROR
    }

    data class StreamStatus(
        val state: StreamState,
        val activeBitrateBps: Int = 0,
        val activeFps: Int = 0,
        val droppedFrames: Long = 0L,
        val errorMessage: String? = null,
    )

    /** Whether a streaming library is wired into the build. */
    fun isAvailable(): Boolean {
        if (!BuildConfig.LOCAL_NETWORK_STREAMING_ENABLED) return false
        cachedAvailability?.let { return it }
        // Probe the documented activation candidates in priority order. The
        // first class that resolves is enough to flip the gate; the actual
        // class names below match the well-known entry points so any of the
        // candidates can be dropped in without changing this engine.
        val classes = arrayOf(
            "io.github.thibaultbee.streampack.streamers.SingleStreamer", // Stream-Pack
            "com.wmspanel.libstream.Streamer",                            // Larix SDK
            "com.haivision.srtkit.Srt",                                   // LibSRT-Android
        )
        val present = classes.any { name ->
            try {
                Class.forName(name); true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
        cachedAvailability = present
        if (!present) Log.d(TAG, "isAvailable: no live-streaming library on classpath")
        return present
    }

    @Volatile private var cachedAvailability: Boolean? = null

    /**
     * Validate a stream destination URL string against the chosen protocol.
     * Pure function — runs without any streaming library present so the UI
     * can pre-validate creator input. Returns an error message or null when
     * the URL is well-formed for the protocol.
     */
    fun validateDestination(protocol: Protocol, url: String): String? {
        if (url.isBlank()) return "Destination URL is required"
        val trimmed = url.trim()
        if (!trimmed.startsWith(protocol.urlScheme)) {
            return "${protocol.displayName} URLs must start with ${protocol.urlScheme}"
        }
        // Reject control chars + whitespace inside the URL — they indicate
        // a paste error and would break the native muxer's URL parser.
        if (trimmed.any { it.isISOControl() || it == ' ' }) {
            return "URL must not contain whitespace or control characters"
        }
        // Minimum: scheme + at least one character of host.
        if (trimmed.length <= protocol.urlScheme.length) {
            return "URL is missing the host segment"
        }
        return null
    }

    /**
     * R8.15 — Android 16 Local Network Protection (LNP) scope classification.
     *
     * Android 16 Developer Preview 1 introduced LNP: apps cannot reach
     * internal-network hosts unless explicitly authorized. RTMP / SRT / RIST
     * / WebRTC / RTSP / NDI servers on the same Wi-Fi (OBS box, hardware
     * encoder, NDI source) all live in that LAN scope. Outbound streaming
     * to a public RTMP ingest (Twitch, YouTube Live) is **not** gated by
     * LNP — that's plain internet egress.
     *
     * This is a pure URL inspection so it can run before any streaming
     * library is wired. The UI uses it to decide whether to show the
     * one-time "ClearCut needs Local Network access" consent sheet before
     * attempting the connection.
     */
    enum class LocalNetworkScope {
        /** Public internet host — no LNP gate. */
        PUBLIC_INTERNET,
        /** RFC1918 LAN host — LNP gate applies on Android 16+. */
        LOCAL_LAN,
        /** Multicast group — LNP gate applies on Android 16+. */
        MULTICAST,
        /** Loopback / localhost — gated only on hardened profiles. */
        LOOPBACK
    }

    /**
     * Classify a destination URL by network scope. Returns
     * [LocalNetworkScope.PUBLIC_INTERNET] when the host segment is missing
     * or cannot be parsed; callers should already have run
     * [validateDestination] before reaching this.
     */
    fun classifyNetworkScope(url: String): LocalNetworkScope {
        val host = extractHost(url) ?: return LocalNetworkScope.PUBLIC_INTERNET
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "ip6-localhost") return LocalNetworkScope.LOOPBACK
        val octets = lower.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it in 0..255 }) {
            val a = octets[0]
            val b = octets[1]
            return when {
                a == 127 -> LocalNetworkScope.LOOPBACK
                a == 10 -> LocalNetworkScope.LOCAL_LAN                      // 10.0.0.0/8
                a == 172 && b in 16..31 -> LocalNetworkScope.LOCAL_LAN      // 172.16.0.0/12
                a == 192 && b == 168 -> LocalNetworkScope.LOCAL_LAN         // 192.168.0.0/16
                a == 169 && b == 254 -> LocalNetworkScope.LOCAL_LAN         // 169.254.0.0/16 link-local
                a in 224..239 -> LocalNetworkScope.MULTICAST                // 224.0.0.0/4
                else -> LocalNetworkScope.PUBLIC_INTERNET
            }
        }
        // IPv6 loopback / link-local / multicast (no full v6 parser — cheap heuristics).
        if (lower == "::1" || lower == "[::1]") return LocalNetworkScope.LOOPBACK
        if (lower.startsWith("fe80:") || lower.startsWith("[fe80:")) return LocalNetworkScope.LOCAL_LAN
        if (lower.startsWith("fc") || lower.startsWith("fd") ||
            lower.startsWith("[fc") || lower.startsWith("[fd")) {
            return LocalNetworkScope.LOCAL_LAN                              // fc00::/7 unique local
        }
        if (lower.startsWith("ff") || lower.startsWith("[ff")) return LocalNetworkScope.MULTICAST
        if (lower.endsWith(".local")) return LocalNetworkScope.LOCAL_LAN    // mDNS
        return LocalNetworkScope.PUBLIC_INTERNET
    }

    /**
     * Whether the URL targets a host inside the user's LAN (or multicast
     * group) and therefore needs Android 16 LNP authorization. Loopback is
     * **not** included — it's an in-process connection.
     */
    fun requiresLocalNetworkPermission(url: String): Boolean {
        return when (classifyNetworkScope(url)) {
            LocalNetworkScope.LOCAL_LAN, LocalNetworkScope.MULTICAST -> true
            LocalNetworkScope.PUBLIC_INTERNET, LocalNetworkScope.LOOPBACK -> false
        }
    }

    fun localNetworkPermissionDecision(
        url: String,
        runtimeSdkInt: Int = Build.VERSION.SDK_INT,
        targetSdkInt: Int = context.applicationInfo?.targetSdkVersion ?: 0,
        permissionGranted: Boolean = false,
        permissionDenied: Boolean = false,
        streamingEnabled: Boolean = BuildConfig.LOCAL_NETWORK_STREAMING_ENABLED,
    ): LocalNetworkPermissionPolicy.Decision =
        LocalNetworkPermissionPolicy.evaluate(
            scope = classifyNetworkScope(url),
            runtimeSdkInt = runtimeSdkInt,
            targetSdkInt = targetSdkInt,
            permissionGranted = permissionGranted,
            permissionDenied = permissionDenied,
            featureEnabled = streamingEnabled,
        )

    fun permissionAwareLocalNetworkFailureMessage(
        url: String,
        runtimeSdkInt: Int = Build.VERSION.SDK_INT,
        targetSdkInt: Int = context.applicationInfo?.targetSdkVersion ?: 0,
        permissionGranted: Boolean = false,
        permissionDenied: Boolean = false,
        throwable: Throwable? = null,
        streamingEnabled: Boolean = BuildConfig.LOCAL_NETWORK_STREAMING_ENABLED,
    ): String? =
        LocalNetworkPermissionPolicy.permissionFailureMessage(
            decision = localNetworkPermissionDecision(
                url = url,
                runtimeSdkInt = runtimeSdkInt,
                targetSdkInt = targetSdkInt,
                permissionGranted = permissionGranted,
                permissionDenied = permissionDenied,
                streamingEnabled = streamingEnabled,
            ),
            throwable = throwable,
        )

    /**
     * Extract the host segment from a URL of the shape
     * `scheme://[user[:pass]@]host[:port]/path`. IPv6 hosts wrapped in
     * brackets are preserved with brackets so the IPv6 heuristics in
     * [classifyNetworkScope] still trigger. Returns null when the URL is
     * malformed enough that the host cannot be located.
     */
    private fun extractHost(url: String): String? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val rest = trimmed.substring(schemeEnd + 3)
        if (rest.isEmpty()) return null
        // Cut off the path / query.
        val pathStart = rest.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (pathStart >= 0) rest.substring(0, pathStart) else rest
        if (authority.isEmpty()) return null
        // Drop optional userinfo.
        val at = authority.lastIndexOf('@')
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        if (hostPort.isEmpty()) return null
        // Strip optional :port — but keep IPv6 brackets intact.
        return if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) hostPort else hostPort.substring(0, close + 1)
        } else {
            val colon = hostPort.indexOf(':')
            if (colon < 0) hostPort else hostPort.substring(0, colon)
        }
    }

    /**
     * Pick a target bitrate for a destination from a curated table of
     * platform defaults. Stays a pure function so the caller can preview
     * the value before constructing an [OutputDestination].
     *
     * Defaults follow the YouTube / Twitch / TikTok / Instagram Live
     * recommended ranges (mid of the band, vertical aware).
     */
    fun recommendedBitrateBps(
        width: Int,
        height: Int,
        fps: Int,
    ): Int {
        require(width > 0 && height > 0 && fps > 0) {
            "Width/height/fps must be positive: ${width}x$height@$fps"
        }
        val pixels = width.toLong() * height.toLong()
        val basePer60Fps = when {
            pixels >= 3840L * 2160L -> 25_000_000  // 4K
            pixels >= 2560L * 1440L -> 13_000_000  // 1440p
            pixels >= 1920L * 1080L -> 6_500_000   // 1080p
            pixels >= 1280L * 720L -> 3_500_000    // 720p
            else -> 1_500_000                       // 540p and below
        }
        val fpsAdjusted = (basePer60Fps.toLong() * fps / 60L).toInt()
        return fpsAdjusted.coerceAtLeast(500_000) // hard floor at 500 kbps
    }

    /**
     * Start streaming. Today this returns ERROR with an explanation; will
     * become the real start once a library is wired (see class docstring).
     */
    suspend fun start(destination: OutputDestination): StreamStatus {
        if (!BuildConfig.LOCAL_NETWORK_STREAMING_ENABLED) {
            Log.d(TAG, "start: local-network streaming is compiled out")
            return StreamStatus(
                state = StreamState.ERROR,
                errorMessage = "Live streaming is disabled in this build",
            )
        }
        Log.d(TAG, "start: stub — no live-streaming library wired (target=${destination.protocol})")
        return StreamStatus(
            state = StreamState.ERROR,
            errorMessage = "Live streaming is not yet enabled in this build",
        )
    }

    /** Stop streaming. Idempotent. */
    suspend fun stop() {
        Log.d(TAG, "stop: stub — no live-streaming library wired")
    }

    companion object {
        private const val TAG = "OutputStreamingEngine"
    }
}
