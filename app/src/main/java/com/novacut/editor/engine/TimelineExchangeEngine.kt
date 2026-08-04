package com.novacut.editor.engine

import com.novacut.editor.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Timeline interchange engine for OTIO, FCPXML, EDL, portable edit-decision
 * JSON, and AAF formats.
 *
 * Enables export handoff from ClearCut projects to desktop NLEs:
 * - OpenTimelineIO (OTIO): Universal interchange format by Pixar/ASWF
 * - FCPXML: Final Cut Pro XML (also importable by DaVinci Resolve)
 * - EDL CMX 3600: Legacy edit decision list (Avid, Premiere, Resolve)
 * - AAF: Advanced Authoring Format (Avid Media Composer)
 *
 * OTIO Java bindings: github.com/OpenTimelineIO/OpenTimelineIO-Java-Bindings
 * The JNI library provides arm64-v8a .so for Android.
 * Until native bindings are integrated, this engine uses a pure-Kotlin JSON
 * serializer that produces OTIO JSON understood by the official OpenTimelineIO
 * adapter. Import is deliberately kept at the same canonical document boundary
 * as autosave and archive restore so a parsed timeline can be committed once,
 * after its fidelity and media reports have been reviewed.
 */
@Singleton
class TimelineExchangeEngine @Inject constructor(
    private val videoEngine: VideoEngine?
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
        FCPXML("Final Cut Pro XML", ".fcpxml", canImport = true, canExport = true),
        EDL_CMX3600("EDL (CMX 3600)", ".edl", canImport = true, canExport = true),
        EDIT_DECISION_JSON(
            "ClearCut edit-decision JSON",
            ".${EditDecisionJsonEngine.FILE_EXTENSION}",
            canImport = true,
            canExport = true,
        ),
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
        val warnings: List<String>,
        val unresolvedMediaUris: List<String> = emptyList(),
        val droppedEffects: Int = 0,
        val timelineMarkers: List<TimelineMarker> = emptyList(),
        val schemaVersion: Int? = null,
        val schemaTooNew: Boolean = false,
    ) {
        /** Convert the parsed interchange result into the canonical save boundary. */
        fun toProjectDocument(project: Project, playheadMs: Long = 0L): ProjectDocument =
            ProjectDocumentApplicator.fromTimelineExchange(
                project = project,
                tracks = tracks,
                textOverlays = textOverlays,
                timelineMarkers = timelineMarkers,
                playheadMs = playheadMs,
            )
    }

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
     * Maps ClearCut's Track/Clip model to OTIO's Timeline → Stack → Track → Clip hierarchy.
     *
     * @param tracks List of ClearCut tracks to export.
     * @param textOverlays Text overlays to include (exported as OTIO markers on a separate track).
     * @param projectName Name for the timeline.
     * @param frameRate Frame rate for time conversions (default 30).
     * @return OTIO JSON string.
     */
    fun exportToOtio(
        tracks: List<Track>,
        textOverlays: List<TextOverlay> = emptyList(),
        projectName: String = "ClearCut Project",
        frameRate: Int = 30
    ): String = exportToOtio(
        tracks = tracks,
        textOverlays = textOverlays,
        projectName = projectName,
        timebase = TimelineTimebase(normalizedFrameRate(frameRate))
    )

    /** Export using the project's exact rational timebase (for example 24000/1001). */
    fun exportToOtio(
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        projectName: String,
        timebase: TimelineTimebase
    ): String {
        val timeline = JSONObject().apply {
            put("OTIO_SCHEMA", "Timeline.1")
            put("name", projectName)
            put("metadata", JSONObject().apply {
                put("clearcut_version", "3.0.0")
                put("export_format", "otio")
                put("clearcut_timebase_numerator", timebase.numerator)
                put("clearcut_timebase_denominator", timebase.denominator)
            })
            put("tracks", buildOtioStack(tracks, textOverlays, timebase))
        }
        return timeline.toString(2)
    }

    private fun buildOtioStack(
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        timebase: TimelineTimebase
    ): JSONObject {
        val children = JSONArray()

        // Video tracks
        tracks.filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
            .forEach { track ->
                children.put(buildOtioTrack(track, "Video", timebase))
            }

        // Audio tracks
        tracks.filter { it.type == TrackType.AUDIO }
            .forEach { track ->
                children.put(buildOtioTrack(track, "Audio", timebase))
            }

        // Text overlays as a separate track with markers
        if (textOverlays.isNotEmpty()) {
            children.put(buildTextOverlayTrack(textOverlays, timebase))
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Stack.1")
            put("name", "tracks")
            put("children", children)
        }
    }

    private fun buildOtioTrack(track: Track, kind: String, timebase: TimelineTimebase): JSONObject {
        val children = JSONArray()
        val sortedClips = track.clips.sortedBy { it.timelineStartMs }

        var currentTimeMs = 0L
        for (clip in sortedClips) {
            // Insert gap if there's space between clips
            if (clip.timelineStartMs > currentTimeMs) {
                val gapDurationMs = clip.timelineStartMs - currentTimeMs
                children.put(JSONObject().apply {
                    put("OTIO_SCHEMA", "Gap.1")
                    put("name", "ClearCut gap")
                    put("effects", JSONArray())
                    put("markers", JSONArray())
                    put("enabled", true)
                    put("source_range", buildTimeRange(0, gapDurationMs, timebase))
                })
            }

            clip.headTransition?.let { children.put(buildOtioTransition(it, timebase, "head")) }
            children.put(buildOtioClip(clip, timebase))
            clip.tailTransition?.let { children.put(buildOtioTransition(it, timebase, "tail")) }
            currentTimeMs = clip.timelineStartMs + clip.durationMs
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Track.1")
            put("name", "Track ${track.index + 1}")
            put("kind", kind)
            put("children", children)
            put("metadata", JSONObject().apply {
                put("clearcut_track_id", track.id)
                put("clearcut_track_type", track.type.name)
                put("locked", track.isLocked)
                put("visible", track.isVisible)
                put("muted", track.isMuted)
                put("solo", track.isSolo)
                putSafeFloat("volume", track.volume, default = 1f)
                putSafeFloat("pan", track.pan)
                putSafeFloat("opacity", track.opacity, default = 1f)
                put("blend_mode", track.blendMode.name)
            })
        }
    }

    private fun buildOtioClip(clip: Clip, timebase: TimelineTimebase): JSONObject {
        val effects = JSONArray()
        val exportSpeed = safeJsonFloat(clip.speed, default = 1f)
        if (exportSpeed != 1.0f) {
            effects.put(JSONObject().apply {
                put("OTIO_SCHEMA", "LinearTimeWarp.1")
                put("name", "Speed ${exportSpeed}x")
                put("effect_name", "LinearTimeWarp")
                put("time_scalar", exportSpeed.toDouble())
                put("metadata", JSONObject())
            })
        }

        return JSONObject().apply {
            put("OTIO_SCHEMA", "Clip.1")
            put("name", clipDisplayName(clip))
            put("effects", effects)
            put("markers", JSONArray())
            put("enabled", true)
            put("source_range", buildTimeRange(clip.trimStartMs, clip.trimEndMs - clip.trimStartMs, timebase))
            put("media_reference", JSONObject().apply {
                put("OTIO_SCHEMA", "ExternalReference.1")
                put("name", clipDisplayName(clip))
                put("target_url", clip.sourceUri.toString())
                put("available_range", buildTimeRange(0, clip.sourceDurationMs, timebase))
                put("metadata", JSONObject())
            })
            put("metadata", JSONObject().apply {
                put("clearcut_clip_id", clip.id)
                put("clearcut_timeline_start_ms", clip.timelineStartMs)
                clip.name?.let { put("clearcut_name", it) }
                put("clearcut_is_reversed", clip.isReversed)
                put("clearcut_blend_mode", clip.blendMode.name)
                putSafeFloat("opacity", clip.opacity, default = 1f)
                putSafeFloat("volume", clip.volume, default = 1f)
                put("clearcut_effects", serializeEffectMetadata(clip.effects))
                clip.headTransition?.let { put("clearcut_head_transition", serializeTransitionMetadata(it)) }
                clip.tailTransition?.let { put("clearcut_tail_transition", serializeTransitionMetadata(it)) }
                if (clip.isCompound && clip.compoundClips.isNotEmpty()) {
                    put("clearcut_compound_clips", JSONArray().apply {
                        clip.compoundClips.forEach { put(buildOtioClip(it, timebase)) }
                    })
                }
            })
        }
    }

    private fun buildOtioTransition(
        transition: Transition,
        timebase: TimelineTimebase,
        role: String
    ): JSONObject = JSONObject().apply {
        put("OTIO_SCHEMA", "Transition.1")
        put("name", transition.type.displayName)
        put("transition_type", transition.type.name)
        put("in_offset", buildRationalTime(msToFrames(transition.durationMs / 2L, timebase), timebase))
        put("out_offset", buildRationalTime(msToFrames(transition.durationMs - transition.durationMs / 2L, timebase), timebase))
        put("metadata", JSONObject().apply {
            put("clearcut_transition_role", role)
            put("clearcut_transition_type", transition.type.name)
            put("clearcut_transition_duration_ms", transition.durationMs)
            put("clearcut_transition_easing", transition.easing.name)
        })
    }

    private fun buildTextOverlayTrack(overlays: List<TextOverlay>, timebase: TimelineTimebase): JSONObject {
        val children = JSONArray()
        val sorted = overlays
            .filter { it.text.isNotBlank() && it.endTimeMs > it.startTimeMs }
            .sortedBy { it.startTimeMs }

        var currentTimeMs = 0L
        for (overlay in sorted) {
            if (overlay.startTimeMs > currentTimeMs) {
                children.put(JSONObject().apply {
                    put("OTIO_SCHEMA", "Gap.1")
                    put("name", "ClearCut text gap")
                    put("effects", JSONArray())
                    put("markers", JSONArray())
                    put("enabled", true)
                    put("source_range", buildTimeRange(0, overlay.startTimeMs - currentTimeMs, timebase))
                })
            }

            children.put(JSONObject().apply {
                put("OTIO_SCHEMA", "Clip.1")
                put("name", overlay.text.take(30))
                put("effects", JSONArray())
                put("markers", JSONArray())
                put("enabled", true)
                put("source_range", buildTimeRange(
                    0,
                    overlay.endTimeMs - overlay.startTimeMs,
                    timebase
                ))
                put("media_reference", JSONObject().apply {
                    put("OTIO_SCHEMA", "GeneratorReference.1")
                    put("name", "ClearCut TextOverlay")
                    put("generator_kind", "TextOverlay")
                    put("available_range", JSONObject.NULL)
                    put("metadata", JSONObject())
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
                put("clearcut_track_type", "TEXT")
            })
        }
    }

    private fun buildTimeRange(startMs: Long, durationMs: Long, timebase: TimelineTimebase): JSONObject {
        return JSONObject().apply {
            put("OTIO_SCHEMA", "TimeRange.1")
            put("start_time", buildRationalTime(msToFrames(startMs, timebase), timebase))
            put("duration", buildRationalTime(msToFrames(durationMs, timebase), timebase))
        }
    }

    private fun buildRationalTime(frames: Long, timebase: TimelineTimebase): JSONObject {
        return JSONObject().apply {
            put("OTIO_SCHEMA", "RationalTime.1")
            put("value", frames.coerceAtLeast(0L))
            put("rate", timebase.numerator.toDouble() / timebase.denominator.toDouble())
        }
    }

    private fun msToFrames(ms: Long, timebase: TimelineTimebase): Long {
        // Round-to-nearest instead of truncating, otherwise small ms values (e.g. 1ms at
        // 30fps = 0.03 frames) silently round down to 0 frames and cumulative drift on a
        // long timeline misaligns OTIO/FCPXML round-trips.
        val frames = ms.coerceAtLeast(0L).toDouble() * timebase.numerator.toDouble() /
            (1000.0 * timebase.denominator.toDouble())
        if (!frames.isFinite()) return Long.MAX_VALUE
        if (frames >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return frames.roundToLong().coerceAtLeast(0L)
    }

    private fun framesToMs(frames: Long, timebase: TimelineTimebase): Long {
        val ms = frames.coerceAtLeast(0L).toDouble() * 1000.0 * timebase.denominator.toDouble() /
            timebase.numerator.toDouble()
        if (!ms.isFinite()) return Long.MAX_VALUE
        if (ms >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return ms.roundToLong().coerceAtLeast(0L)
    }

    private fun clipDisplayName(clip: Clip): String {
        clip.name?.takeIf { it.isNotBlank() }?.let { return it }
        val path = clip.sourceUri.lastPathSegment ?: clip.sourceUri.toString()
        return path.substringAfterLast("/").substringBeforeLast(".")
    }

    private fun serializeTransitionMetadata(transition: Transition): JSONObject = JSONObject().apply {
        put("type", transition.type.name)
        put("durationMs", transition.durationMs)
        put("easing", transition.easing.name)
    }

    private fun serializeEffectMetadata(effects: List<Effect>): JSONArray = JSONArray().apply {
        effects.forEach { effect ->
            put(JSONObject().apply {
                put("type", effect.type.name)
                put("enabled", effect.enabled)
                put("params", JSONObject().apply {
                    effect.params.forEach { (key, value) -> putSafeFloat(key, value) }
                })
            })
        }
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
     * Import an OpenTimelineIO JSON document into ClearCut tracks and text overlays.
     *
     * @param json OTIO JSON string.
     * @return ExchangeResult with imported tracks, text overlays, and any warnings.
     */
    fun importFromOtio(json: String): ExchangeResult = importFromOtio(json, android.net.Uri::parse)

    fun exportToEditDecisionJson(
        tracks: List<Track>,
        textOverlays: List<TextOverlay> = emptyList(),
        timelineMarkers: List<TimelineMarker> = emptyList(),
        projectName: String = "ClearCut Project",
        timebase: TimelineTimebase = TimelineTimebase(30),
    ): String = EditDecisionJsonEngine.export(
        tracks = tracks,
        textOverlays = textOverlays,
        timelineMarkers = timelineMarkers,
        projectName = projectName,
        timebase = timebase,
    )

    fun importFromEditDecisionJson(json: String): ExchangeResult =
        importFromEditDecisionJson(json, android.net.Uri::parse)

    /** Pure parser seam used by JVM contract tests and media-linker adapters. */
    internal fun importFromEditDecisionJson(
        json: String,
        uriParser: (String) -> android.net.Uri?,
    ): ExchangeResult = EditDecisionJsonEngine.import(json, uriParser)

    /** Pure parser seam used by JVM contract tests and media-linker adapters. */
    internal fun importFromOtio(
        json: String,
        uriParser: (String) -> android.net.Uri?,
    ): ExchangeResult {
        val warnings = mutableListOf<String>()
        val tracks = mutableListOf<Track>()
        val textOverlays = mutableListOf<TextOverlay>()
        val diagnostics = ImportDiagnostics()

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

            val documentTimebase = otioTimebaseFromMetadata(root.optJSONObject("metadata"))
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
                if (metadata?.optString("clearcut_track_type") == "TEXT") {
                    parseTextOverlayTrack(trackJson, textOverlays, warnings, documentTimebase)
                    continue
                }

                val clips = parseOtioClips(trackJson, warnings, documentTimebase, diagnostics, uriParser)
                val declaredType = metadata?.optString("clearcut_track_type", "")
                    ?.let { raw -> runCatching { TrackType.valueOf(raw) }.getOrNull() }
                val trackId = metadata?.optString("clearcut_track_id", "")
                    ?.takeIf { it.isNotBlank() }
                tracks.add(Track(
                    id = trackId ?: java.util.UUID.randomUUID().toString(),
                    type = declaredType?.takeUnless { it == TrackType.TEXT } ?: trackType,
                    index = trackIndex,
                    clips = clips,
                    isLocked = metadata?.optBoolean("locked", false) ?: false,
                    isVisible = metadata?.optBoolean("visible", true) ?: true,
                    isMuted = metadata?.optBoolean("muted", false) ?: false,
                    isSolo = metadata?.optBoolean("solo", false) ?: false,
                    volume = safeFloat(metadata?.optDouble("volume", 1.0) ?: 1.0, 1f)
                        .coerceIn(0f, 2f),
                    pan = safeFloat(metadata?.optDouble("pan", 0.0) ?: 0.0, 0f)
                        .coerceIn(-1f, 1f),
                    opacity = safeFloat(metadata?.optDouble("opacity", 1.0) ?: 1.0, 1f)
                        .coerceIn(0f, 1f),
                    blendMode = parseBlendMode(metadata?.optString("blend_mode"), warnings),
                ))
                trackIndex++
            }
        } catch (e: Exception) {
            warnings.add("Failed to parse OTIO JSON: ${e.message}")
        }

        return ExchangeResult(
            tracks = tracks,
            textOverlays = textOverlays,
            warnings = warnings,
            unresolvedMediaUris = diagnostics.unresolvedMediaUris.distinct(),
            droppedEffects = diagnostics.droppedEffects,
        )
    }

    private data class ImportDiagnostics(
        val unresolvedMediaUris: MutableList<String> = mutableListOf(),
        var droppedEffects: Int = 0,
    )

    private fun parseOtioClips(
        trackJson: JSONObject,
        warnings: MutableList<String>,
        documentTimebase: TimelineTimebase,
        diagnostics: ImportDiagnostics,
        uriParser: (String) -> android.net.Uri?,
    ): List<Clip> {
        val clips = mutableListOf<Clip>()
        val children = trackJson.optJSONArray("children") ?: return clips
        var timelinePositionMs = 0L
        var pendingTransition: Transition? = null

        for (i in 0 until children.length().coerceAtMost(MAX_OTIO_CHILDREN)) {
            val child = children.optJSONObject(i) ?: continue
            val childSchema = child.optString("OTIO_SCHEMA", "")

            when {
                childSchema.startsWith("Gap") -> {
                    val sourceRange = child.optJSONObject("source_range")
                    if (sourceRange != null) {
                        val duration = sourceRange.optJSONObject("duration")
                        val rate = otioTimebase(duration, documentTimebase)
                        val frames = duration?.optLong("value", 0) ?: 0
                        timelinePositionMs = safeAdd(timelinePositionMs, framesToMs(frames, rate))
                    }
                }
                childSchema.startsWith("Transition") -> {
                    pendingTransition = parseOtioTransition(child, warnings, documentTimebase)
                }
                childSchema.startsWith("Clip") -> {
                    val clip = parseOtioClip(
                        clipJson = child,
                        timelinePositionMs = timelinePositionMs,
                        warnings = warnings,
                        documentTimebase = documentTimebase,
                        diagnostics = diagnostics,
                        uriParser = uriParser,
                    )?.let { imported ->
                        if (pendingTransition != null && imported.headTransition == null) {
                            imported.copy(headTransition = pendingTransition)
                        } else {
                            imported
                        }
                    }
                    if (clip != null) {
                        clips.add(clip)
                        timelinePositionMs = safeAdd(clip.timelineStartMs, clip.durationMs)
                        pendingTransition = null
                    }
                }
                else -> {
                    if (childSchema.isNotBlank()) {
                        warnings.add("Unsupported OTIO schema in track: $childSchema")
                    }
                }
            }
        }

        if (children.length() > MAX_OTIO_CHILDREN) {
            warnings.add("OTIO track contains more than $MAX_OTIO_CHILDREN children; remaining items were ignored")
        }

        return clips
    }

    private fun parseOtioClip(
        clipJson: JSONObject,
        timelinePositionMs: Long,
        warnings: MutableList<String>,
        documentTimebase: TimelineTimebase,
        diagnostics: ImportDiagnostics,
        uriParser: (String) -> android.net.Uri?,
    ): Clip? {
        val sourceRange = clipJson.optJSONObject("source_range") ?: return null
        val startTime = sourceRange.optJSONObject("start_time") ?: return null
        val duration = sourceRange.optJSONObject("duration") ?: return null
        val rate = otioTimebase(startTime, documentTimebase)
        val durationRate = otioTimebase(duration, rate)

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
            diagnostics.unresolvedMediaUris += "<missing:${clipJson.optString("name", "clip")}>"
            return null
        }

        val sourceUri = uriParser(targetUrl)
        if (sourceUri == null) {
            diagnostics.unresolvedMediaUris += targetUrl
            warnings.add("Clip '${clipJson.optString("name")}' has an invalid media URI — skipped")
            return null
        }
        if (!isProbeableUri(sourceUri)) {
            diagnostics.unresolvedMediaUris += targetUrl
            warnings.add("Clip '${clipJson.optString("name")}' references an unsupported media URI scheme")
        }

        // Parse available range for source duration
        val availableRange = mediaRef?.optJSONObject("available_range")
        val importedSourceDurationMs = if (availableRange != null) {
            val avDuration = availableRange.optJSONObject("duration")
            val avRate = otioTimebase(avDuration, rate)
            framesToMs(avDuration?.optLong("value", 0) ?: 0, avRate)
        } else {
            safeAdd(trimStartMs, durationMs) // Best guess
        }
        val trimEndMs = safeAdd(trimStartMs, durationMs)
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
                    diagnostics.droppedEffects++
                }
            }
        }

        val metadata = clipJson.optJSONObject("metadata")
        val importedEffects = parseEffectMetadata(metadata?.optJSONArray("clearcut_effects"), warnings, diagnostics)
        val compoundClips = mutableListOf<Clip>()
        val compoundJson = metadata?.optJSONArray("clearcut_compound_clips")
        if (compoundJson != null) {
            for (index in 0 until compoundJson.length().coerceAtMost(MAX_COMPOUND_CLIPS)) {
                val child = compoundJson.optJSONObject(index) ?: continue
                parseOtioClip(
                    clipJson = child,
                    timelinePositionMs = child.optJSONObject("metadata")
                        ?.optLong("clearcut_timeline_start_ms", 0L) ?: 0L,
                    warnings = warnings,
                    documentTimebase = documentTimebase,
                    diagnostics = diagnostics,
                    uriParser = uriParser,
                )?.let(compoundClips::add)
            }
            if (compoundJson.length() > MAX_COMPOUND_CLIPS) {
                warnings.add("Clip '${clipJson.optString("name")}' has too many nested clips; remaining items were ignored")
            }
        }

        return Clip(
            id = metadata?.optString("clearcut_clip_id", "")?.takeIf { it.isNotBlank() }
                ?: java.util.UUID.randomUUID().toString(),
            sourceUri = sourceUri,
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = timelinePositionMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            effects = importedEffects,
            headTransition = parseTransitionMetadata(metadata?.optJSONObject("clearcut_head_transition"), warnings),
            tailTransition = parseTransitionMetadata(metadata?.optJSONObject("clearcut_tail_transition"), warnings),
            volume = safeFloat(metadata?.optDouble("volume", 1.0) ?: 1.0, 1f).coerceIn(0f, 2f),
            speed = speed,
            isReversed = metadata?.optBoolean("clearcut_is_reversed", false) ?: false,
            opacity = safeFloat(metadata?.optDouble("opacity", 1.0) ?: 1.0, 1f).coerceIn(0f, 1f),
            blendMode = parseBlendMode(metadata?.optString("clearcut_blend_mode"), warnings),
            isCompound = compoundClips.isNotEmpty(),
            compoundClips = compoundClips,
            name = metadata?.optString("clearcut_name", "")?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseTextOverlayTrack(
        trackJson: JSONObject,
        overlays: MutableList<TextOverlay>,
        warnings: MutableList<String>,
        documentTimebase: TimelineTimebase,
    ) {
        val children = trackJson.optJSONArray("children") ?: return
        var timelinePositionMs = 0L

        for (i in 0 until children.length().coerceAtMost(MAX_OTIO_CHILDREN)) {
            val child = children.optJSONObject(i) ?: continue
            val childSchema = child.optString("OTIO_SCHEMA", "")

            val sourceRange = child.optJSONObject("source_range") ?: continue
            val startTime = sourceRange.optJSONObject("start_time") ?: continue
            val duration = sourceRange.optJSONObject("duration") ?: continue
            val rate = otioTimebase(startTime, documentTimebase)
            val durationRate = otioTimebase(duration, rate)

            when {
                childSchema.startsWith("Gap") -> {
                    timelinePositionMs = safeAdd(
                        timelinePositionMs,
                        framesToMs(duration.optLong("value", 0), durationRate),
                    )
                }
                childSchema.startsWith("Clip") -> {
                    val mediaRef = child.optJSONObject("media_reference") ?: continue
                    if (!mediaRef.optString("generator_kind", "").equals("TextOverlay", ignoreCase = true)) {
                        warnings.add("Unsupported generator reference in text overlay track")
                        continue
                    }
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
                        endTimeMs = safeAdd(startMs, durationMs),
                    ))
                    timelinePositionMs = safeAdd(startMs, durationMs)
                }
                else -> warnings.add("Unsupported OTIO schema in text overlay track: $childSchema")
            }
        }
    }

    // ──────────────────────────────────────────────
    // FCPXML / EDL Import
    // ──────────────────────────────────────────────

    fun importFromFcpxml(xml: String): ExchangeResult =
        importFromFcpxml(xml, android.net.Uri::parse)

    /** XML parser seam kept injectable so hostile-document tests do not need Android Uri stubs. */
    internal fun importFromFcpxml(
        xml: String,
        uriParser: (String) -> android.net.Uri?,
    ): ExchangeResult {
        val warnings = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        val clips = mutableListOf<Clip>()
        try {
            val document = secureXmlDocument(xml)
            val formatElement = document.getElementsByTagName("format")
                .item(0) as? Element
            val timebase = formatElement?.getAttribute("frameDuration")
                ?.takeIf { it.isNotBlank() }
                ?.let { frameDuration ->
                    val seconds = parseFcpxmlSeconds(frameDuration)
                    if (seconds != null && seconds > 0.0) {
                        timebaseForRate(1.0 / seconds, TimelineTimebase(30))
                    } else {
                        TimelineTimebase(30)
                    }
                } ?: TimelineTimebase(30)

            val assets = mutableMapOf<String, FcpxmlAsset>()
            val assetNodes = document.getElementsByTagName("asset")
            for (index in 0 until assetNodes.length.coerceAtMost(MAX_FCPXML_ASSETS)) {
                val asset = assetNodes.item(index) as? Element ?: continue
                val id = asset.getAttribute("id").trim()
                if (id.isBlank()) continue
                val src = asset.getAttribute("src").trim().ifBlank {
                    (asset.getElementsByTagName("media-rep").item(0) as? Element)
                        ?.getAttribute("src")?.trim().orEmpty()
                }
                assets[id] = FcpxmlAsset(
                    sourceUri = src,
                    sourceDurationMs = parseFcpxmlSeconds(asset.getAttribute("duration"))
                        ?.let(::secondsToMs) ?: 0L,
                )
            }
            if (assetNodes.length > MAX_FCPXML_ASSETS) {
                warnings.add("FCPXML contains more than $MAX_FCPXML_ASSETS assets; remaining assets were ignored")
            }

            val spine = document.getElementsByTagName("spine").item(0) as? Element
            if (spine == null) {
                warnings.add("FCPXML document has no primary storyline")
            } else {
                var cursorMs = 0L
                val children = spine.childNodes
                for (index in 0 until children.length.coerceAtMost(MAX_FCPXML_CHILDREN)) {
                    val element = children.item(index) as? Element ?: continue
                    when (element.tagName) {
                        "gap" -> {
                            val duration = parseFcpxmlSeconds(element.getAttribute("duration"))
                                ?.let(::secondsToMs) ?: 0L
                            cursorMs = safeAdd(cursorMs, duration)
                        }
                        "transition" -> {
                            warnings.add("FCPXML transition was not mapped to a named ClearCut transition")
                        }
                        "asset-clip" -> {
                            val assetId = element.getAttribute("ref").trim()
                            val asset = assets[assetId]
                            val rawUri = asset?.sourceUri.orEmpty()
                            if (rawUri.isBlank()) {
                                unresolved += assetId.ifBlank { "<missing-ref>" }
                                warnings.add("FCPXML asset-clip has no resolvable media reference: $assetId")
                                continue
                            }
                            val uri = uriParser(rawUri)
                            if (uri == null) {
                                unresolved += rawUri
                                warnings.add("FCPXML asset-clip has an invalid media URI")
                                continue
                            }
                            if (!isProbeableUri(uri)) unresolved += rawUri
                            val timelineStartMs = parseFcpxmlSeconds(element.getAttribute("offset"))
                                ?.let(::secondsToMs) ?: cursorMs
                            val trimStartMs = parseFcpxmlSeconds(element.getAttribute("start"))
                                ?.let(::secondsToMs) ?: 0L
                            val durationMs = parseFcpxmlSeconds(element.getAttribute("duration"))
                                ?.let(::secondsToMs) ?: 0L
                            if (durationMs <= 0L) {
                                warnings.add("FCPXML asset-clip '${element.getAttribute("name")}' has non-positive duration")
                                continue
                            }
                            val sourceDurationMs = (asset?.sourceDurationMs ?: 0L)
                                .coerceAtLeast(safeAdd(trimStartMs, durationMs))
                            clips += Clip(
                                id = element.getAttribute("id").trim().ifBlank {
                                    java.util.UUID.randomUUID().toString()
                                },
                                sourceUri = uri,
                                sourceDurationMs = sourceDurationMs,
                                timelineStartMs = timelineStartMs,
                                trimStartMs = trimStartMs,
                                trimEndMs = safeAdd(trimStartMs, durationMs)
                                    .coerceAtMost(sourceDurationMs),
                                name = element.getAttribute("name").trim().takeIf { it.isNotBlank() },
                            )
                            cursorMs = maxOf(cursorMs, safeAdd(timelineStartMs, durationMs))
                        }
                    }
                }
                if (children.length > MAX_FCPXML_CHILDREN) {
                    warnings.add("FCPXML storyline contains more than $MAX_FCPXML_CHILDREN children; remaining items were ignored")
                }
            }
        } catch (e: Exception) {
            warnings.add("Failed to parse FCPXML: ${e.message ?: e::class.java.simpleName}")
        }
        return ExchangeResult(
            tracks = clips.takeIf { it.isNotEmpty() }
                ?.let { listOf(Track(type = TrackType.VIDEO, index = 0, clips = it)) }
                ?: emptyList(),
            textOverlays = emptyList(),
            warnings = warnings,
            unresolvedMediaUris = unresolved.distinct(),
        )
    }

    fun importFromEdl(
        edl: String,
        timebase: TimelineTimebase = TimelineTimebase(30),
    ): ExchangeResult = importFromEdl(edl, timebase, android.net.Uri::parse)

    internal fun importFromEdl(
        edl: String,
        timebase: TimelineTimebase,
        uriParser: (String) -> android.net.Uri?,
    ): ExchangeResult {
        val warnings = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        val droppedEffects = intArrayOf(0)
        val videoClips = mutableListOf<Clip>()
        val audioClips = mutableListOf<Clip>()
        var currentClips: MutableList<Clip>? = null
        var currentIndex = -1
        val eventPattern = Regex(
            "^\\s*\\d+\\s+(\\S+)\\s+([VA])\\s+([A-Z](?:\\s+\\d+)?)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*$"
        )
        val speedPattern = Regex("^\\s*M2\\s+\\S+\\s+([0-9]+(?:\\.[0-9]+)?)")
        for (line in edl.lineSequence()) {
            val event = eventPattern.matchEntire(line)
            if (event != null) {
                val reel = event.groupValues[1]
                val kind = event.groupValues[2]
                val transition = event.groupValues[3]
                val sourceIn = parseEdlTimecode(event.groupValues[4], timebase)
                val sourceOut = parseEdlTimecode(event.groupValues[5], timebase)
                val recordIn = parseEdlTimecode(event.groupValues[6], timebase)
                val recordOut = parseEdlTimecode(event.groupValues[7], timebase)
                if (sourceIn == null || sourceOut == null || recordIn == null || recordOut == null ||
                    sourceOut <= sourceIn || recordOut <= recordIn
                ) {
                    warnings.add("EDL event has invalid timecode: ${line.trim()}")
                    currentClips = null
                    currentIndex = -1
                    continue
                }
                val uriText = "file:///$reel"
                val uri = uriParser(uriText)
                if (uri == null) {
                    unresolved += uriText
                    currentClips = null
                    currentIndex = -1
                    continue
                }
                if (!isProbeableUri(uri)) unresolved += uriText
                val clip = Clip(
                    sourceUri = uri,
                    sourceDurationMs = sourceOut,
                    timelineStartMs = recordIn,
                    trimStartMs = sourceIn,
                    trimEndMs = sourceOut,
                    headTransition = parseEdlTransition(transition, timebase),
                )
                currentClips = if (kind == "A") audioClips else videoClips
                currentClips += clip
                currentIndex = currentClips.lastIndex
                continue
            }
            val fromClip = line.trim().removePrefix("* FROM CLIP NAME:").takeIf {
                line.trim().startsWith("* FROM CLIP NAME:", ignoreCase = true)
            }
            if (fromClip != null && currentClips != null && currentIndex >= 0) {
                val name = fromClip.trim().ifBlank { "unknown" }
                val uriText = if (name.contains("://")) name else "file:///$name"
                val uri = uriParser(uriText)
                if (uri == null) {
                    unresolved += uriText
                } else {
                    currentClips[currentIndex] = currentClips[currentIndex].copy(sourceUri = uri)
                }
                continue
            }
            val speed = speedPattern.find(line)
            if (speed != null && currentClips != null && currentIndex >= 0) {
                val fps = speed.groupValues[1].toFloatOrNull()
                if (fps != null && fps.isFinite() && fps > 0f) {
                    currentClips[currentIndex] = currentClips[currentIndex].copy(
                        speed = (fps / timebase.nominalFramesPerSecond.toFloat()).coerceIn(0.01f, 100f)
                    )
                }
                continue
            }
            if (line.trim().startsWith("* EFFECT NAME:", ignoreCase = true)) {
                droppedEffects[0]++
            }
        }
        if (droppedEffects[0] > 0) {
            warnings.add("${droppedEffects[0]} EDL effect comment(s) require manual re-application")
        }
        val tracks = buildList {
            if (videoClips.isNotEmpty()) add(Track(type = TrackType.VIDEO, index = 0, clips = videoClips))
            if (audioClips.isNotEmpty()) add(Track(type = TrackType.AUDIO, index = size, clips = audioClips))
        }
        return ExchangeResult(
            tracks = tracks,
            textOverlays = emptyList(),
            warnings = warnings,
            unresolvedMediaUris = unresolved.distinct(),
            droppedEffects = droppedEffects[0],
        )
    }

    private data class FcpxmlAsset(val sourceUri: String, val sourceDurationMs: Long)

    private fun secureXmlDocument(xml: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.isXIncludeAware = false
        factory.setExpandEntityReferences(false)
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver(org.xml.sax.EntityResolver { _, _ ->
            InputSource(StringReader(""))
        })
        return builder.parse(InputSource(StringReader(xml)))
    }

    private fun parseFcpxmlSeconds(raw: String?): Double? {
        val value = raw?.trim()?.removeSuffix("s")?.trim().orEmpty()
        if (value.isBlank()) return null
        val parts = value.split('/', limit = 2)
        val seconds = if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull()
            val denominator = parts[1].toDoubleOrNull()
            if (numerator == null || denominator == null || denominator == 0.0) null
            else numerator / denominator
        } else {
            value.toDoubleOrNull()
        }
        return seconds?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun secondsToMs(seconds: Double): Long {
        val ms = seconds * 1_000.0
        return if (!ms.isFinite() || ms >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE
        else ms.roundToLong().coerceAtLeast(0L)
    }

    private fun parseEdlTimecode(raw: String, timebase: TimelineTimebase): Long? {
        val parts = raw.split(':')
        if (parts.size != 4) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toLongOrNull() ?: return null
        val frames = parts[3].toLongOrNull() ?: return null
        if (hours < 0L || minutes !in 0..59 || seconds !in 0..59 ||
            frames !in 0 until timebase.nominalFramesPerSecond
        ) return null
        val totalFrames = (((hours * 60L) + minutes) * 60L + seconds) *
            timebase.nominalFramesPerSecond.toLong() + frames
        return framesToMs(totalFrames, timebase)
    }

    private fun parseEdlTransition(raw: String, timebase: TimelineTimebase): Transition? {
        if (!raw.startsWith("D", ignoreCase = true)) return null
        val frames = raw.substring(1).trim().toLongOrNull() ?: return null
        return Transition(
            type = TransitionType.DISSOLVE,
            durationMs = framesToMs(frames, timebase).coerceAtLeast(1L),
        )
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
     * @param tracks List of ClearCut tracks.
     * @param projectName Project name.
     * @param frameRate Frame rate (e.g., 24, 30, 60).
     * @return FCPXML string.
     */
    fun exportToFcpxml(
        tracks: List<Track>,
        projectName: String = "ClearCut Project",
        frameRate: Int = 30
    ): String = exportToFcpxml(
        tracks = tracks,
        projectName = projectName,
        timebase = TimelineTimebase(normalizedFrameRate(frameRate)),
    )

    /** Export FCPXML with a rational frame duration so NTSC timelines do not drift. */
    fun exportToFcpxml(
        tracks: List<Track>,
        projectName: String,
        timebase: TimelineTimebase,
    ): String {
        val safeFrameRate = timebase.nominalFramesPerSecond
        val frameDuration = fcpxmlTimeForFrames(1L, timebase)
        val totalDurationMs = tracks.flatMap { it.clips }.maxOfOrNull {
            it.timelineStartMs + it.durationMs
        } ?: 0L
        val totalDurationFcpxml = msToFcpxmlTime(totalDurationMs, timebase)

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE fcpxml>""")
        sb.appendLine("""<fcpxml version="1.11">""")
        sb.appendLine("""  <resources>""")
        sb.appendLine("""    <format id="r0" name="ClearCut ${safeFrameRate}p" frameDuration="$frameDuration" width="1920" height="1080"/>""")

        // Collect media references
        val mediaRefs = mutableMapOf<String, Clip>()
        tracks.flatMap { it.clips }.forEach { clip ->
            val key = clip.sourceUri.toString()
            if (key !in mediaRefs) mediaRefs[key] = clip
        }

        mediaRefs.entries.forEachIndexed { index, (uri, clip) ->
            val assetId = "r${index + 1}"
            val hasVideo = if (videoEngine?.hasVisualTrack(clip.sourceUri) ?: true) "1" else "0"
            val hasAudio = if (videoEngine?.hasAudioTrack(clip.sourceUri) ?: true) "1" else "0"
            sb.appendLine("""    <asset id="$assetId" name="${xmlEscape(clipDisplayName(clip))}" src="${xmlEscape(uri)}" start="0s" duration="${msToFcpxmlTime(clip.sourceDurationMs, timebase)}" hasVideo="$hasVideo" hasAudio="$hasAudio">""")
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
            val offset = msToFcpxmlTime(clip.timelineStartMs, timebase)
            val start = msToFcpxmlTime(clip.trimStartMs, timebase)
            val duration = msToFcpxmlTime(clip.trimEndMs - clip.trimStartMs, timebase)

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

    private fun msToFcpxmlTime(ms: Long, timebase: TimelineTimebase): String {
        // Round-to-nearest so a 33 ms offset at 30 fps lands on frame 1, not frame 0.
        // Truncation accumulates into visible drift on long exports round-tripped through
        // Final Cut Pro / DaVinci Resolve — symmetric with msToFrames above.
        return fcpxmlTimeForFrames(msToFrames(ms, timebase), timebase)
    }

    private fun fcpxmlTimeForFrames(frames: Long, timebase: TimelineTimebase): String {
        val numerator = frames.coerceAtLeast(0L) * timebase.denominator.toLong()
        val denominator = timebase.numerator.toLong()
        val divisor = greatestCommonDivisor(numerator.coerceAtLeast(1L), denominator)
        return "${numerator / divisor}/${denominator / divisor}s"
    }

    private fun normalizedFrameRate(frameRate: Int): Int {
        return frameRate.coerceIn(1, 240)
    }

    private fun safeFloat(value: Double, default: Float): Float {
        val asFloat = value.toFloat()
        return if (asFloat.isFinite()) asFloat else default
    }

    private fun otioTimebaseFromMetadata(metadata: JSONObject?): TimelineTimebase {
        val numerator = metadata?.optInt("clearcut_timebase_numerator", 0) ?: 0
        val denominator = metadata?.optInt("clearcut_timebase_denominator", 0) ?: 0
        if (numerator > 0 && denominator > 0) {
            return runCatching { TimelineTimebase(numerator, denominator) }
                .getOrDefault(TimelineTimebase(30))
        }
        return TimelineTimebase(30)
    }

    private fun otioTimebase(json: JSONObject?, fallback: TimelineTimebase): TimelineTimebase {
        val rate = json?.optDouble(
            "rate",
            fallback.numerator.toDouble() / fallback.denominator.toDouble(),
        ) ?: (fallback.numerator.toDouble() / fallback.denominator.toDouble())
        if (!rate.isFinite() || rate <= 0.0) return fallback
        return timebaseForRate(rate, fallback)
    }

    private fun timebaseForRate(rate: Double, fallback: TimelineTimebase): TimelineTimebase {
        val known = listOf(
            TimelineTimebase.NTSC_23_976,
            TimelineTimebase.NTSC_29_97,
            TimelineTimebase.NTSC_59_94,
            TimelineTimebase(24),
            TimelineTimebase(25),
            TimelineTimebase(30),
            TimelineTimebase(50),
            TimelineTimebase(60),
        ).firstOrNull { candidate ->
            kotlin.math.abs(rate - candidate.numerator.toDouble() / candidate.denominator) < 0.002
        }
        if (known != null) return known

        val denominator = 1_000
        val numerator = (rate * denominator).roundToLong()
        return if (numerator in 1..240_000) {
            val divisor = greatestCommonDivisor(numerator, denominator.toLong())
            runCatching {
                TimelineTimebase((numerator / divisor).toInt(), (denominator / divisor).toInt())
            }.getOrDefault(fallback)
        } else {
            fallback
        }
    }

    private fun greatestCommonDivisor(left: Long, right: Long): Long {
        var a = left.coerceAtLeast(1L)
        var b = right.coerceAtLeast(1L)
        while (b != 0L) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a.coerceAtLeast(1L)
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (left >= 0L && right >= 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        return left + right
    }

    private fun isProbeableUri(uri: android.net.Uri): Boolean {
        return uri.scheme?.lowercase() in PROBEABLE_URI_SCHEMES
    }

    private fun parseBlendMode(raw: String?, warnings: MutableList<String>): BlendMode {
        if (raw.isNullOrBlank()) return BlendMode.NORMAL
        return runCatching { BlendMode.valueOf(raw) }.getOrElse {
            warnings.add("Unknown blend mode '$raw'; defaulted to normal")
            BlendMode.NORMAL
        }
    }

    private fun parseTransitionMetadata(
        json: JSONObject?,
        warnings: MutableList<String>,
    ): Transition? {
        if (json == null) return null
        val type = runCatching {
            TransitionType.valueOf(json.optString("type", "DISSOLVE"))
        }.getOrElse {
            warnings.add("Unknown transition type '${json.optString("type")}' — using dissolve")
            TransitionType.DISSOLVE
        }
        val easing = runCatching {
            TransitionEasing.valueOf(json.optString("easing", TransitionEasing.LINEAR.name))
        }.getOrDefault(TransitionEasing.LINEAR)
        return Transition(
            type = type,
            durationMs = json.optLong("durationMs", 500L).coerceAtLeast(1L),
            easing = easing,
        )
    }

    private fun parseOtioTransition(
        json: JSONObject,
        warnings: MutableList<String>,
        documentTimebase: TimelineTimebase,
    ): Transition? {
        val metadata = json.optJSONObject("metadata")
        if (metadata?.has("clearcut_transition_type") == true) {
            val clearCutMetadata = JSONObject().apply {
                put("type", metadata.optString("clearcut_transition_type", "DISSOLVE"))
                put("durationMs", metadata.optLong("clearcut_transition_duration_ms", 500L))
                put("easing", metadata.optString("clearcut_transition_easing", TransitionEasing.LINEAR.name))
            }
            parseTransitionMetadata(clearCutMetadata, warnings)?.let { return it }
        }
        val type = runCatching {
            TransitionType.valueOf(json.optString("transition_type", "DISSOLVE"))
        }.getOrElse {
            warnings.add("Unknown OTIO transition '${json.optString("transition_type")}' — using dissolve")
            TransitionType.DISSOLVE
        }
        val inOffset = json.optJSONObject("in_offset")
        val outOffset = json.optJSONObject("out_offset")
        val inMs = framesToMs(
            inOffset?.optLong("value", 0L) ?: 0L,
            otioTimebase(inOffset, documentTimebase),
        )
        val outMs = framesToMs(
            outOffset?.optLong("value", 0L) ?: 0L,
            otioTimebase(outOffset, documentTimebase),
        )
        return Transition(type = type, durationMs = (inMs + outMs).coerceAtLeast(1L))
    }

    private fun parseEffectMetadata(
        array: JSONArray?,
        warnings: MutableList<String>,
        diagnostics: ImportDiagnostics,
    ): List<Effect> {
        if (array == null) return emptyList()
        return (0 until array.length().coerceAtMost(MAX_EFFECTS)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val type = runCatching { EffectType.valueOf(json.optString("type")) }.getOrNull()
            if (type == null) {
                diagnostics.droppedEffects++
                warnings.add("Unsupported ClearCut effect metadata '${json.optString("type")}'")
                return@mapNotNull null
            }
            val params = mutableMapOf<String, Float>()
            json.optJSONObject("params")?.keys()?.forEach { key ->
                params[key] = safeFloat(json.optJSONObject("params")?.optDouble(key, 0.0) ?: 0.0, 0f)
            }
            Effect(
                type = type,
                params = params,
                enabled = json.optBoolean("enabled", true),
            )
        }.also {
            if (array.length() > MAX_EFFECTS) {
                diagnostics.droppedEffects += array.length() - MAX_EFFECTS
                warnings.add("Too many effect metadata entries; remaining effects were dropped")
            }
        }
    }

    private companion object {
        const val MAX_OTIO_CHILDREN = 10_000
        const val MAX_COMPOUND_CLIPS = 256
        const val MAX_EFFECTS = 256
        const val MAX_FCPXML_ASSETS = 10_000
        const val MAX_FCPXML_CHILDREN = 10_000
        val PROBEABLE_URI_SCHEMES = setOf("content", "file", "asset", "http", "https")
    }
}
