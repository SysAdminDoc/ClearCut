package com.novacut.editor.engine

import android.content.Context
import android.util.Log
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
 * license fee. LibSRT is MPL-2.0 and pairs cleanly with NovaCut's MIT
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
