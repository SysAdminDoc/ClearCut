package com.novacut.editor.engine

import com.novacut.editor.model.*
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Timeline interchange engine supporting OTIO, FCPXML, EDL, and AAF formats.
 *
 * Enables round-tripping NovaCut projects to/from desktop NLEs:
 * - OpenTimelineIO (OTIO): Universal interchange format by Pixar/ASWF
 * - FCPXML: Final Cut Pro XML (also importable by DaVinci Resolve)
 * - EDL CMX 3600: Legacy edit decision list (Avid, Premiere, Resolve)
 * - AAF: Advanced Authoring Format (Avid Media Composer)
 *
 * OTIO Java bindings: github.com/OpenTimelineIO/OpenTimelineIO-Java-Bindings
 * The JNI library provides arm64-v8a .so for Android.
 * Until native bindings are integrated, this engine uses a pure-Kotlin JSON
 * serializer that produces valid OTIO JSON (schema version 0.15).
 */
@Singleton
class TimelineExchangeEngine @Inject constructor(
    private val videoEngine: VideoEngine
) {

    /**
     * Supported timeline interchange formats.
     */
    enum class TimelineExchangeFormat(
        val displayName: String,
        val extension: String,
        val canImport: Boolean,
        val canExport: Boolean
    ) {
        OTIO("OpenTimelineIO", ".otio", canImport = true, canExport = true),
        FCPXML("Final Cut Pro XML", ".fcpxml", canImport = false, canExport = true),
        EDL_CMX3600("EDL (CMX 3600)", ".edl", canImport = false, canExport = true),
        AAF("Advanced Authoring Format", ".aaf", canImport = false, canExport = false)
    }

    /**
     * Result of an import operation.
     *
     * @param tracks Imported tracks with clips.
     * @param textOverlays Imported text overlays (if format supports them).
     * @param warnings Non-fatal issues encountered during import (unsupported effects, etc.).
     */
    data class ExchangeResult(
        val tracks: List<Track>,
        val textOverlays: List<TextOverlay>,
        val warnings: List<String>
    )

    /**
     * Get all formats and their import/export support status.
     */
    fun getSupportedFormats(): List<TimelineExchangeFormat> {
        return TimelineExchangeFormat.entries.toList()
    }

    // ──────────────────────────────────────────────
    // OTIO Export
    // ──────────────────────────────────────────────

    /**
     * Export tracks and text overlays to OpenTimelineIO JSON format.
     *
     * Produces a valid OTIO JSON document following schema version 0.15.
     * Maps NovaCut's Track/Clip model to OTIO's Timeline → Stack → Track → Clip hierarchy.
     *
     * @param tracks List of NovaCut tracks to export.
     * @param textOverlays Text overlays to include (exported as OTIO markers on a separate track).
     * @param projectName Name for the timeline.
     * @param frameRate Frame rate for time conversions (default 30).
     * @return OTIO JSON string.
     */
    fun exportToOtio(
        tracks: List<Track>,
        textOverlays: List<TextOverlay> = emptyList(),
        projectName: String = "NovaCut Project",
        frameRate: Int = 30
    ): String {
        val safeFrameRate = normalizedFrameRate(frameRate)
        val timeline = JSONObject().apply {
            put("OTIO_SCHEMA", "Timeline.1")
            put("name", projectName)
            put("metadata", JSONObject().apply {
                put("novacut_version", "3.0.0")
                put("export_format", "otio")
            })
            put("tracks", buildOtioStack(tracks, textOverlays, safeFrameRate))
        }
        return timeline.toString(2)
    }

    private fun buildOtioStack(
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        frameRate: Int
    ): JSONObject {
        val children = JSONArray()

        // Video tracks
        tracks.filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
            .forEach { track ->
                children.put(buildOtioTrack(track, "Video", frameRate))
            }

        // Audio tracks
        tracks.filter { it.type == TrackType.AUDIO }
            .forEach { track ->
                children.put(buildOtioTrack(track, "Audio", frameRate))
            }

        // Text overlays as a separate track with markers
        if (textOverlays.isNotEmpty()) {
            children.put(buildTextOverlayTrack(textOverlays, frameRate))
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Stack.1")
            put("name", "tracks")
            put("children", children)
        }
    }

    private fun buildOtioTrack(track: Track, kind: String, frameRate: Int): JSONObject {
        val children = JSONArray()
        val sortedClips = track.clips.sortedBy { it.timelineStartMs }

        var currentTimeMs = 0L
        for (clip in sortedClips) {
            // Insert gap if there's space between clips
            if (clip.timelineStartMs > currentTimeMs) {
                val gapDurationMs = clip.timelineStartMs - currentTimeMs
                children.put(JSONObject().apply {
                    put("OTIO_SCHEMA", "Gap.1")
                    put("source_range", buildTimeRange(0, gapDurationMs, frameRate))
                })
            }

            children.put(buildOtioClip(clip, frameRate))
            currentTimeMs = clip.timelineStartMs + clip.durationMs
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Track.1")
            put("name", "Track ${track.index + 1}")
            put("kind", kind)
            put("children", children)
            put("metadata", JSONObject().apply {
                put("novacut_track_id", track.id)
                put("locked", track.isLocked)
                put("visible", track.isVisible)
                put("muted", track.isMuted)
            })
        }
    }

    private fun buildOtioClip(clip: Clip, frameRate: Int): JSONObject {
        val effects = JSONArray()
        val exportSpeed = safeJsonFloat(clip.speed, default = 1f)
        if (exportSpeed != 1.0f) {
            effects.put(JSONObject().apply {
                put("OTIO_SCHEMA", "LinearTimeWarp.1")
                put("name", "Speed ${exportSpeed}x")
                put("time_scalar", exportSpeed.toDouble())
            })
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Clip.1")
            put("name", clipDisplayName(clip))
            put("source_range", buildTimeRange(clip.trimStartMs, clip.trimEndMs - clip.trimStartMs, frameRate))
            put("media_reference", JSONObject().apply {
                put("OTIO_SCHEMA", "ExternalReference.1")
                put("target_url", clip.sourceUri.toString())
                put("available_range", buildTimeRange(0, clip.sourceDurationMs, frameRate))
            })
            if (effects.length() > 0) {
                put("effects", effects)
            }
            put("metadata", JSONObject().apply {
                put("novacut_clip_id", clip.id)
                putSafeFloat("opacity", clip.opacity, default = 1f)
                putSafeFloat("volume", clip.volume, default = 1f)
            })
        }
    }

    private fun buildTextOverlayTrack(overlays: List<TextOverlay>, frameRate: Int): JSONObject {
        val children = JSONArray()
        val sorted = overlays
            .filter { it.text.isNotBlank() && it.endTimeMs > it.startTimeMs }
            .sortedBy { it.startTimeMs }

        var currentTimeMs = 0L
        for (overlay in sorted) {
            if (overlay.startTimeMs > currentTimeMs) {
                children.put(JSONObject().apply {
                    put("OTIO_SCHEMA", "Gap.1")
                    put("source_range", buildTimeRange(0, overlay.startTimeMs - currentTimeMs, frameRate))
                })
            }

            children.put(JSONObject().apply {
                put("OTIO_SCHEMA", "Clip.1")
                put("name", overlay.text.take(30))
                put("source_range", buildTimeRange(
                    0,
                    overlay.endTimeMs - overlay.startTimeMs,
                    frameRate
                ))
                put("media_reference", JSONObject().apply {
                    put("OTIO_SCHEMA", "GeneratorReference.1")
                    put("generator_kind", "TextOverlay")
                    put("parameters", JSONObject().apply {
                        put("text", overlay.text)
                        put("font_family", overlay.fontFamily)
                        putSafeFloat("font_size", overlay.fontSize, default = 48f)
                        put("color", overlay.color)
                        putSafeFloat("position_x", overlay.positionX, default = 0.5f)
                        putSafeFloat("position_y", overlay.positionY, default = 0.5f)
                    })
                })
            })
            currentTimeMs = maxOf(currentTimeMs, overlay.endTimeMs)
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Track.1")
            put("name", "Text Overlays")
            put("kind", "Video")
            put("children", children)
            put("metadata", JSONObject().apply {
                put("novacut_track_type", "TEXT")
            })
        }
    }

    private fun buildTimeRange(startMs: Long, durationMs: Long, frameRate: Int): JSONObject {
        return JSONObject().apply {
            put("start_time", JSONObject().apply {
                put("value", msToFrames(startMs, frameRate))
                put("rate", frameRate)
            })
            put("duration", JSONObject().apply {
                put("value", msToFrames(durationMs, frameRate))
                put("rate", frameRate)
            })
        }
    }

    private fun msToFrames(ms: Long, frameRate: Int): Long {
        // Round-to-nearest instead of truncating, otherwise small ms values (e.g. 1ms at
        // 30fps = 0.03 frames) silently round down to 0 frames and cumulative drift on a
        // long timeline misaligns OTIO/FCPXML round-trips.
        val safeFrameRate = normalizedFrameRate(frameRate)
        val frames = ms.coerceAtLeast(0L).toDouble() * safeFrameRate.toDouble() / 1000.0
        if (!frames.isFinite()) return Long.MAX_VALUE
        if (frames >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return frames.roundToLong().coerceAtLeast(0L)
    }

    private fun framesToMs(frames: Long, frameRate: Int): Long {
        val safeFrameRate = normalizedFrameRate(frameRate)
        val ms = frames.coerceAtLeast(0L).toDouble() * 1000.0 / safeFrameRate.toDouble()
        if (!ms.isFinite()) return Long.MAX_VALUE
        if (ms >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return ms.roundToLong().coerceAtLeast(0L)
    }

    private fun clipDisplayName(clip: Clip): String {
        val path = clip.sourceUri.lastPathSegment ?: clip.sourceUri.toString()
        return path.substringAfterLast("/").substringBeforeLast(".")
    }

    private fun JSONObject.putSafeFloat(name: String, value: Float, default: Float = 0f): JSONObject {
        return put(name, safeJsonFloat(value, default).toDouble())
    }

    private fun safeJsonFloat(value: Float, default: Float = 0f): Float {
        val fallback = if (default.isFinite()) default else 0f
        return if (value.isFinite()) value else fallback
    }

    /**
     * Escape a string for safe inclusion as XML element text or attribute value.
     * Without this, a clip name like `"M&M's <draft>"` produces malformed FCPXML
     * that downstream tools (Final Cut Pro, DaVinci Resolve via FCPXML import) reject.
     */
    private fun xmlEscape(value: String): String {
        if (value.isEmpty()) return value
        val needsEscape = value.any { it == '&' || it == '<' || it == '>' || it == '"' || it == '\'' }
        if (!needsEscape) return value
        val sb = StringBuilder(value.length + 16)
        for (c in value) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ──────────────────────────────────────────────
    // OTIO Import
    // ──────────────────────────────────────────────

    /**
     * Import an OpenTimelineIO JSON document into NovaCut tracks and text overlays.
     *
     * @param json OTIO JSON string.
     * @return ExchangeResult with imported tracks, text overlays, and any warnings.
     */
    fun importFromOtio(json: String): ExchangeResult {
        val warnings = mutableListOf<String>()
        val tracks = mutableListOf<Track>()
        val textOverlays = mutableListOf<TextOverlay>()

        try {
            val root = JSONObject(json)
            val schema = root.optString("OTIO_SCHEMA", "")
            if (!schema.startsWith("Timeline")) {
                warnings.add("Unexpected root schema: $schema (expected Timeline)")
            }

            val stack = root.optJSONObject("tracks") ?: run {
                warnings.add("No tracks found in OTIO document")
                return ExchangeResult(emptyList(), emptyList(), warnings)
            }

            val children = stack.optJSONArray("children") ?: JSONArray()
            var trackIndex = 0

            for (i in 0 until children.length()) {
                val trackJson = children.optJSONObject(i) ?: continue
                val kind = trackJson.optString("kind", "Video")
                val trackType = when (kind) {
                    "Audio" -> TrackType.AUDIO
                    else -> TrackType.VIDEO
                }

                // Check if this is a text overlay track
                val metadata = trackJson.optJSONObject("metadata")
                if (metadata?.optString("novacut_track_type") == "TEXT") {
                    parseTextOverlayTrack(trackJson, textOverlays, warnings)
                    continue
                }

                val clips = parseOtioClips(trackJson, warnings)
                tracks.add(Track(
                    type = trackType,
                    index = trackIndex,
                    clips = clips,
                    isLocked = metadata?.optBoolean("locked", false) ?: false,
                    isVisible = metadata?.optBoolean("visible", true) ?: true,
                    isMuted = metadata?.optBoolean("muted", false) ?: false
                ))
                trackIndex++
            }
        } catch (e: Exception) {
            warnings.add("Failed to parse OTIO JSON: ${e.message}")
        }

        return ExchangeResult(tracks, textOverlays, warnings)
    }

    private fun parseOtioClips(trackJson: JSONObject, warnings: MutableList<String>): List<Clip> {
        val clips = mutableListOf<Clip>()
        val children = trackJson.optJSONArray("children") ?: return clips
        var timelinePositionMs = 0L

        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            val childSchema = child.optString("OTIO_SCHEMA", "")

            when {
                childSchema.startsWith("Gap") -> {
                    val sourceRange = child.optJSONObject("source_range")
                    if (sourceRange != null) {
                        val duration = sourceRange.optJSONObject("duration")
                        val rate = duration?.optInt("rate", 30) ?: 30
                        val frames = duration?.optLong("value", 0) ?: 0
                        timelinePositionMs += framesToMs(frames, rate)
                    }
                }
                childSchema.startsWith("Clip") -> {
                    val clip = parseOtioClip(child, timelinePositionMs, warnings)
                    if (clip != null) {
                        clips.add(clip)
                        timelinePositionMs = clip.timelineStartMs + clip.durationMs
                    }
                }
                else -> {
                    warnings.add("Unsupported OTIO schema in track: $childSchema")
                }
            }
        }

        return clips
    }

    private fun parseOtioClip(
        clipJson: JSONObject,
        timelinePositionMs: Long,
        warnings: MutableList<String>
    ): Clip? {
        val sourceRange = clipJson.optJSONObject("source_range") ?: return null
        val startTime = sourceRange.optJSONObject("start_time") ?: return null
        val duration = sourceRange.optJSONObject("duration") ?: return null
        val rate = normalizedFrameRate(startTime.optInt("rate", 30))
        val durationRate = normalizedFrameRate(duration.optInt("rate", rate))

        val trimStartMs = framesToMs(startTime.optLong("value", 0), rate)
        val durationMs = framesToMs(duration.optLong("value", 0), durationRate)
        if (durationMs <= 0L) {
            warnings.add("Clip '${clipJson.optString("name")}' has non-positive duration — skipped")
            return null
        }

        val mediaRef = clipJson.optJSONObject("media_reference")
        val targetUrl = mediaRef?.optString("target_url", "") ?: ""

        if (targetUrl.isEmpty()) {
            warnings.add("Clip '${clipJson.optString("name")}' has no media reference — skipped")
            return null // Skip clips with no media reference to prevent playback crashes
        }

        // Parse available range for source duration
        val availableRange = mediaRef?.optJSONObject("available_range")
        val importedSourceDurationMs = if (availableRange != null) {
            val avDuration = availableRange.optJSONObject("duration")
            val avRate = avDuration?.optInt("rate", rate) ?: rate
            framesToMs(avDuration?.optLong("value", 0) ?: 0, avRate)
        } else {
            trimStartMs + durationMs // Best guess
        }
        val trimEndMs = trimStartMs + durationMs
        val sourceDurationMs = importedSourceDurationMs.coerceAtLeast(trimEndMs)

        // Parse speed from effects
        var speed = 1.0f
        val effects = clipJson.optJSONArray("effects")
        if (effects != null) {
            for (j in 0 until effects.length()) {
                val effect = effects.optJSONObject(j) ?: continue
                if (effect.optString("OTIO_SCHEMA").startsWith("LinearTimeWarp")) {
                    speed = safeFloat(effect.optDouble("time_scalar", 1.0), default = 1f)
                        .coerceIn(0.01f, 100f)
                } else {
                    warnings.add("Unsupported effect: ${effect.optString("OTIO_SCHEMA")}")
                }
            }
        }

        return Clip(
            sourceUri = android.net.Uri.parse(targetUrl),
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = timelinePositionMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            speed = speed
        )
    }

    private fun parseTextOverlayTrack(
        trackJson: JSONObject,
        overlays: MutableList<TextOverlay>,
        warnings: MutableList<String>
    ) {
        val children = trackJson.optJSONArray("children") ?: return
        var timelinePositionMs = 0L

        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            val childSchema = child.optString("OTIO_SCHEMA", "")

            val sourceRange = child.optJSONObject("source_range") ?: continue
            val startTime = sourceRange.optJSONObject("start_time") ?: continue
            val duration = sourceRange.optJSONObject("duration") ?: continue
            val rate = normalizedFrameRate(startTime.optInt("rate", 30))
            val durationRate = normalizedFrameRate(duration.optInt("rate", rate))

            when {
                childSchema.startsWith("Gap") -> {
                    timelinePositionMs += framesToMs(duration.optLong("value", 0), durationRate)
                }
                childSchema.startsWith("Clip") -> {
                    val mediaRef = child.optJSONObject("media_reference") ?: continue
                    val params = mediaRef.optJSONObject("parameters") ?: continue
                    val sourceStartMs = framesToMs(startTime.optLong("value", 0), rate)
                    val durationMs = framesToMs(duration.optLong("value", 0), durationRate)
                    val startMs = if (sourceStartMs > timelinePositionMs) sourceStartMs else timelinePositionMs
                    val text = params.optString("text", "")
                    if (text.isBlank()) {
                        warnings.add("Skipped blank text overlay")
                        timelinePositionMs = startMs + durationMs
                        continue
                    }
                    if (durationMs <= 0L) {
                        warnings.add("Skipped text overlay '$text' with non-positive duration")
                        continue
                    }

                    overlays.add(TextOverlay(
                        text = text,
                        fontFamily = params.optString("font_family", "sans-serif"),
                        fontSize = safeFloat(params.optDouble("font_size", 48.0), default = 48f).coerceIn(1f, 512f),
                        color = params.optLong("color", 0xFFFFFFFF),
                        positionX = safeFloat(params.optDouble("position_x", 0.5), default = 0.5f).coerceIn(-5f, 5f),
                        positionY = safeFloat(params.optDouble("position_y", 0.5), default = 0.5f).coerceIn(-5f, 5f),
                        startTimeMs = startMs,
                        endTimeMs = startMs + durationMs
                    ))
                    timelinePositionMs = startMs + durationMs
                }
                else -> warnings.add("Unsupported OTIO schema in text overlay track: $childSchema")
            }
        }
    }

    // ──────────────────────────────────────────────
    // FCPXML Export
    // ──────────────────────────────────────────────

    /**
     * Export tracks to Final Cut Pro XML format (FCPXML v1.11).
     *
     * FCPXML is widely supported by DaVinci Resolve, Final Cut Pro, and other NLEs.
     * This improves on the existing EdlExporter by supporting multiple tracks,
     * transitions, and richer metadata.
     *
     * @param tracks List of NovaCut tracks.
     * @param projectName Project name.
     * @param frameRate Frame rate (e.g., 24, 30, 60).
     * @return FCPXML string.
     */
    fun exportToFcpxml(
        tracks: List<Track>,
        projectName: String = "NovaCut Project",
        frameRate: Int = 30
    ): String {
        val safeFrameRate = normalizedFrameRate(frameRate)
        val frameDuration = "1/${safeFrameRate}s"
        val totalDurationMs = tracks.flatMap { it.clips }.maxOfOrNull {
            it.timelineStartMs + it.durationMs
        } ?: 0L
        val totalDurationFcpxml = msToFcpxmlTime(totalDurationMs, safeFrameRate)

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE fcpxml>""")
        sb.appendLine("""<fcpxml version="1.11">""")
        sb.appendLine("""  <resources>""")
        sb.appendLine("""    <format id="r0" name="NovaCut ${safeFrameRate}p" frameDuration="$frameDuration" width="1920" height="1080"/>""")

        // Collect media references
        val mediaRefs = mutableMapOf<String, Clip>()
        tracks.flatMap { it.clips }.forEach { clip ->
            val key = clip.sourceUri.toString()
            if (key !in mediaRefs) mediaRefs[key] = clip
        }

        mediaRefs.entries.forEachIndexed { index, (uri, clip) ->
            val assetId = "r${index + 1}"
            val hasVideo = if (videoEngine.hasVisualTrack(clip.sourceUri)) "1" else "0"
            val hasAudio = if (videoEngine.hasAudioTrack(clip.sourceUri)) "1" else "0"
            sb.appendLine("""    <asset id="$assetId" name="${xmlEscape(clipDisplayName(clip))}" src="${xmlEscape(uri)}" start="0s" duration="${msToFcpxmlTime(clip.sourceDurationMs, safeFrameRate)}" hasVideo="$hasVideo" hasAudio="$hasAudio">""")
            sb.appendLine("""      <media-rep kind="original-media" src="${xmlEscape(uri)}"/>""")
            sb.appendLine("""    </asset>""")
        }

        sb.appendLine("""  </resources>""")
        sb.appendLine("""  <library>""")
        sb.appendLine("""    <event name="${xmlEscape(projectName)}">""")
        sb.appendLine("""      <project name="${xmlEscape(projectName)}">""")
        sb.appendLine("""        <sequence format="r0" duration="$totalDurationFcpxml" tcStart="0s" tcFormat="NDF">""")
        sb.appendLine("""          <spine>""")

        // Primary storyline (first video track)
        val primaryTrack = tracks.firstOrNull { it.type == TrackType.VIDEO }
        primaryTrack?.clips?.sortedBy { it.timelineStartMs }?.forEach { clip ->
            val assetIndex = mediaRefs.keys.indexOf(clip.sourceUri.toString())
            val assetId = "r${assetIndex + 1}"
            val offset = msToFcpxmlTime(clip.timelineStartMs, safeFrameRate)
            val start = msToFcpxmlTime(clip.trimStartMs, safeFrameRate)
            val duration = msToFcpxmlTime(clip.trimEndMs - clip.trimStartMs, safeFrameRate)

            sb.appendLine("""            <asset-clip ref="$assetId" name="${xmlEscape(clipDisplayName(clip))}" offset="$offset" start="$start" duration="$duration"/>""")
        }

        sb.appendLine("""          </spine>""")
        sb.appendLine("""        </sequence>""")
        sb.appendLine("""      </project>""")
        sb.appendLine("""    </event>""")
        sb.appendLine("""  </library>""")
        sb.appendLine("""</fcpxml>""")

        return sb.toString()
    }

    private fun msToFcpxmlTime(ms: Long, frameRate: Int): String {
        // Round-to-nearest so a 33 ms offset at 30 fps lands on frame 1, not frame 0.
        // Truncation accumulates into visible drift on long exports round-tripped through
        // Final Cut Pro / DaVinci Resolve — symmetric with msToFrames above.
        val safeFrameRate = normalizedFrameRate(frameRate)
        return "${msToFrames(ms, safeFrameRate)}/${safeFrameRate}s"
    }

    private fun normalizedFrameRate(frameRate: Int): Int {
        return frameRate.coerceIn(1, 240)
    }

    private fun safeFloat(value: Double, default: Float): Float {
        val asFloat = value.toFloat()
        return if (asFloat.isFinite()) asFloat else default
    }
}
