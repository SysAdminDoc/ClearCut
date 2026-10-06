package com.novacut.editor.engine

import com.novacut.editor.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.StringReader
import java.util.Locale
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

    /** Version and adapter promises kept alongside each interchange format. */
    data class TimelineExchangeContract(
        val schema: String,
        val adapterRange: String? = null,
    )

    /** Stable interchange identifiers shared by the app, fixtures, and release gate. */
    object InterchangeContracts {
        const val OTIO_ROOT_SCHEMA = "Timeline.1"
        const val OTIO_SCHEMA_VERSION = "0.15"
        const val OTIO_ADAPTER_RANGE = "0.15-0.16"
        const val FCPXML_VERSION = "1.11"
        const val EDL_STANDARD = "CMX 3600"
        const val OTIO_SCHEMA_METADATA_KEY = "clearcut_otio_schema_version"
        const val OTIO_ADAPTER_METADATA_KEY = "clearcut_otio_adapter_range"
    }

    /**
     * Supported timeline interchange formats.
     */
    enum class TimelineExchangeFormat(
        val displayName: String,
        val extension: String,
        val canImport: Boolean,
        val canExport: Boolean,
        val contract: TimelineExchangeContract? = null,
    ) {
        OTIO(
            "OpenTimelineIO",
            ".otio",
            canImport = true,
            canExport = true,
            contract = TimelineExchangeContract(
                schema = InterchangeContracts.OTIO_SCHEMA_VERSION,
                adapterRange = InterchangeContracts.OTIO_ADAPTER_RANGE,
            ),
        ),
        FCPXML(
            "Final Cut Pro XML",
            ".fcpxml",
            canImport = true,
            canExport = true,
            contract = TimelineExchangeContract(InterchangeContracts.FCPXML_VERSION),
        ),
        EDL_CMX3600(
            "EDL (CMX 3600)",
            ".edl",
            canImport = true,
            canExport = true,
            contract = TimelineExchangeContract(InterchangeContracts.EDL_STANDARD),
        ),
        EDIT_DECISION_JSON(
            "ClearCut edit-decision JSON",
            ".${EditDecisionJsonEngine.FILE_EXTENSION}",
            canImport = true,
            canExport = true,
            contract = TimelineExchangeContract(
                schema = EditDecisionJsonEngine.SCHEMA_ID,
                adapterRange = "v${EditDecisionJsonEngine.SCHEMA_VERSION}",
            ),
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
            put("OTIO_SCHEMA", InterchangeContracts.OTIO_ROOT_SCHEMA)
            put("name", projectName)
            put("metadata", JSONObject().apply {
                put("clearcut_version", "3.0.0")
                put("export_format", "otio")
                put(InterchangeContracts.OTIO_SCHEMA_METADATA_KEY, InterchangeContracts.OTIO_SCHEMA_VERSION)
                put(InterchangeContracts.OTIO_ADAPTER_METADATA_KEY, InterchangeContracts.OTIO_ADAPTER_RANGE)
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
            val rootSchemaVersion = parseOtioSchemaVersion(schema)
            if (rootSchemaVersion != null && rootSchemaVersion > 1) {
                return ExchangeResult(
                    tracks = emptyList(),
                    textOverlays = emptyList(),
                    warnings = listOf(
                        "OTIO root schema '$schema' is newer than ClearCut's supported " +
                            "${InterchangeContracts.OTIO_ROOT_SCHEMA}; nothing was imported."
                    ),
                    schemaVersion = rootSchemaVersion,
                    schemaTooNew = true,
                )
            }
            if (schema != InterchangeContracts.OTIO_ROOT_SCHEMA) {
                warnings.add("Unexpected root schema: $schema (expected Timeline)")
            }

            val metadata = root.optJSONObject("metadata")
            val declaredSchemaVersion = metadata
                ?.optString(InterchangeContracts.OTIO_SCHEMA_METADATA_KEY, "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val declaredSchemaCode = declaredSchemaVersion?.let(::parseOtioMetadataVersion)
            if (declaredSchemaVersion != null && declaredSchemaCode == null) {
                warnings.add(
                    "OTIO metadata schema version '$declaredSchemaVersion' is not recognised; " +
                        "import will use the Timeline.1 contract."
                )
            } else if (declaredSchemaCode != null && declaredSchemaCode > OTIO_MAX_SUPPORTED_SCHEMA_CODE) {
                return ExchangeResult(
                    tracks = emptyList(),
                    textOverlays = emptyList(),
                    warnings = listOf(
                        "OTIO metadata schema version '$declaredSchemaVersion' is newer than " +
                            "ClearCut's supported range ${InterchangeContracts.OTIO_ADAPTER_RANGE}; " +
                            "nothing was imported."
                    ),
                    schemaVersion = declaredSchemaCode,
                    schemaTooNew = true,
                )
            } else if (declaredSchemaVersion != null &&
                declaredSchemaVersion !in SUPPORTED_OTIO_SCHEMA_VERSIONS
            ) {
                warnings.add(
                    "OTIO metadata schema version '$declaredSchemaVersion' is outside the " +
                        "documented range ${InterchangeContracts.OTIO_ADAPTER_RANGE}; known Timeline.1 fields will be used."
                )
            }

            val stack = root.optJSONObject("tracks") ?: run {
                warnings.add("No tracks found in OTIO document")
                return ExchangeResult(emptyList(), emptyList(), warnings)
            }

            val documentTimebase = otioTimebaseFromMetadata(metadata, warnings)
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

    private fun parseOtioSchemaVersion(schema: String): Int? {
        if (!schema.startsWith("Timeline.")) return null
        return schema.substringAfter('.', missingDelimiterValue = "").toIntOrNull()
    }

    private fun parseOtioMetadataVersion(version: String): Int? {
        val parts = version.split('.', limit = 3)
        if (parts.size != 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        if (major < 0 || minor !in 0..99) return null
        return major * 100 + minor
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
        var currentTimebase = timebase
        var fcmDropFrame = false
        val dropFrameTimebase = edlDropFrameTimebase(edl, timebase)
        val eventPattern = EDL_EVENT_LINE
        val speedPattern = Regex("^\\s*M2\\s+\\S+\\s+([0-9]+(?:\\.[0-9]+)?)")
        val fcmPattern = Regex("^\\s*FCM:\\s*(NON[- ]?DROP|DROP)(?:\\s+FRAME)?\\s*$", RegexOption.IGNORE_CASE)
        for (line in edl.lineSequence()) {
            val fcm = fcmPattern.matchEntire(line)
            if (fcm != null) {
                fcmDropFrame = fcm.groupValues[1].equals("DROP", ignoreCase = true)
                continue
            }
            val event = eventPattern.matchEntire(line)
            if (event != null) {
                val reel = event.groupValues[1]
                val kind = event.groupValues[2]
                val transition = event.groupValues[3]
                val fields = (4..7).map { EdlTimecode.parse(event.groupValues[it]) }
                // FCM governs colon-only timecode; `;` (or Sony's `,` and `.`) marks drop-frame on its own.
                val dropFrame = fcmDropFrame || fields.any { it?.dropFrame == true }
                currentTimebase = if (dropFrame) dropFrameTimebase else timebase
                val eventTimebase = currentTimebase
                val (sourceIn, sourceOut, recordIn, recordOut) = fields.map { field ->
                    field?.let { EdlTimecode.toFrame(it, eventTimebase.nominalFramesPerSecond, dropFrame) }
                        ?.let { framesToMs(it, eventTimebase) }
                }
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
                    headTransition = parseEdlTransition(transition, eventTimebase),
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
                        speed = (fps / currentTimebase.nominalFramesPerSecond.toFloat()).coerceIn(0.01f, 100f)
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
        // Refuse entity declarations and external DTDs before any parser sees them, so
        // the guarantee doesn't depend on which XML stack the phone ships. A bare
        // <!DOCTYPE fcpxml>, as Final Cut and ClearCut write it, is fine.
        fcpxmlDoctypeIssue(xml)?.let { issue ->
            throw java.io.IOException("the file declares $issue, which ClearCut doesn't load")
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // The JDK parser takes these switches. Android's rejects every feature but
        // namespaces and validation (a rejected switch used to fail every FCPXML import
        // on a phone), and it never fetches external entities anyway.
        listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        ).forEach { feature -> runCatching { factory.setFeature(feature, false) } }
        runCatching { factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.isXIncludeAware = false }
        factory.setExpandEntityReferences(false)
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver(org.xml.sax.EntityResolver { _, _ ->
            InputSource(StringReader(""))
        })
        // A file saved with a byte-order mark keeps it as the string's first character,
        // and a parser reading characters rather than bytes refuses it as prolog content.
        return builder.parse(InputSource(StringReader(xml.removePrefix("\uFEFF"))))
    }

    /**
     * What a document's DOCTYPE asks the parser to load, or null when it asks for nothing:
     * an internal subset (where entities, including nested expansion bombs, are declared)
     * or an external DTD by SYSTEM or PUBLIC id. Only the prolog is read.
     */
    internal fun fcpxmlDoctypeIssue(xml: String): String? {
        val length = xml.length
        var index = if (xml.startsWith('\uFEFF')) 1 else 0
        while (index < length) {
            while (index < length && xml[index].isWhitespace()) index++
            when {
                xml.startsWith("<?", index) -> {
                    index = xml.indexOf("?>", index + 2).takeIf { it >= 0 }?.plus(2) ?: return null
                }
                xml.startsWith("<!--", index) -> {
                    index = xml.indexOf("-->", index + 4).takeIf { it >= 0 }?.plus(3) ?: return null
                }
                xml.startsWith("<!DOCTYPE", index) -> {
                    val declaration = StringBuilder()
                    var quote: Char? = null
                    var cursor = index + "<!DOCTYPE".length
                    while (cursor < length) {
                        val char = xml[cursor]
                        if (quote != null) {
                            if (char == quote) quote = null
                        } else {
                            when (char) {
                                '"', '\'' -> quote = char
                                '[' -> return "an internal DTD"
                                '>' -> break
                                else -> declaration.append(char)
                            }
                        }
                        cursor++
                    }
                    val words = declaration.split(Regex("\\s+"))
                    return if ("SYSTEM" in words || "PUBLIC" in words) "an external DTD" else null
                }
                else -> return null
            }
        }
        return null
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

    /**
     * Drop-frame timecode only exists at 29.97 and 59.94, and an EDL doesn't say which.
     * Trust a caller already at 59.94, otherwise a frame field of 30 or more means 59.94.
     */
    private fun edlDropFrameTimebase(edl: String, timebase: TimelineTimebase): TimelineTimebase {
        if (timebase.nominalFramesPerSecond == 60) return TimelineTimebase.NTSC_59_94
        // Only event timecode counts: a TITLE or comment like `12.31.20.45` is not a frame field.
        val highestFrameField = edl.lineSequence()
            .mapNotNull { EDL_EVENT_LINE.matchEntire(it) }
            .flatMap { event -> (4..7).asSequence().mapNotNull { EdlTimecode.parse(event.groupValues[it])?.frames } }
            .maxOrNull() ?: 0
        return if (highestFrameField >= 30) TimelineTimebase.NTSC_59_94 else TimelineTimebase.NTSC_29_97
    }

    // ──────────────────────────────────────────────
    // EDL Export
    // ──────────────────────────────────────────────

    /**
     * Export a CMX 3600 EDL: the first visible video track as V events and the first
     * unmuted audio track as A events, in record order.
     *
     * 29.97 and 59.94 timelines write `FCM: DROP FRAME` with `;` before the frame field,
     * so the timecode keeps pace with the clock. Every other rate writes non-drop
     * timecode counted in frames at the nominal rate, which is what a receiving editor
     * reads back. Reels are unique per source and never longer than 8 characters.
     */
    fun exportToEdl(
        tracks: List<Track>,
        projectName: String,
        timebase: TimelineTimebase,
    ): String {
        val dropFrame = EdlTimecode.isDropFrameRate(timebase)
        val fps = timebase.nominalFramesPerSecond
        fun timecode(ms: Long): String = EdlTimecode.format(msToFrames(ms, timebase), fps, dropFrame)

        val videoTrack = tracks.firstOrNull { it.type == TrackType.VIDEO && it.isVisible }
        val audioTrack = tracks.firstOrNull { it.type == TrackType.AUDIO && !it.isMuted }
        val events = buildList {
            videoTrack?.clips?.forEach { add(Triple(videoTrack, it, "V")) }
            audioTrack?.clips?.forEach { add(Triple(audioTrack, it, "A")) }
        }.filter { (track, clip) -> track.effectiveTimelineEndMs(clip) > 0L }.sortedWith(
            compareBy<Triple<Track, Clip, String>>({ (track, clip) -> track.effectiveTimelineStartMs(clip) })
                .thenBy { it.third != "V" }
        )
        val reels = edlReelNames(events.map { it.second })

        val sb = StringBuilder()
        sb.appendLine("TITLE: ${edlSafeText(projectName, fallback = "ClearCut Project")}")
        sb.appendLine(if (dropFrame) "FCM: DROP FRAME" else "FCM: NON-DROP FRAME")
        sb.appendLine()
        events.forEachIndexed { index, (track, clip, channel) ->
            val reel = reels.getValue(clip.sourceUri.toString())
            val speed = clip.speedCurve?.averageSpeed((clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L))
                ?: clip.speed
            val safeSpeed = if (speed.isFinite() && speed > 0f) speed.coerceIn(0.01f, 100f) else 1f
            val timelineStartMs = track.effectiveTimelineStartMs(clip)
            val recordInMs = timelineStartMs.coerceAtLeast(0L)
            val recordOutMs = track.effectiveTimelineEndMs(clip).coerceAtLeast(recordInMs)
            // A sync offset can pull a clip before zero. Its record in is clamped to the start,
            // so the event drops the media that would have played before it, measured along the
            // clip's speed curve: the head of the source, or the tail when the clip is reversed.
            val clippedMs = recordInMs - timelineStartMs
            val (sourceInMs, sourceOutMs) = if (clippedMs <= 0L) {
                clip.trimStartMs to clip.trimEndMs
            } else {
                val playedThroughMs = clip.timelineOffsetToSourceMs(clippedMs)
                if (clip.isReversed) clip.trimStartMs to playedThroughMs else playedThroughMs to clip.trimEndMs
            }
            val sourceIn = timecode(sourceInMs)
            val sourceOut = EdlTimecode.format(
                maxOf(msToFrames(sourceOutMs, timebase), msToFrames(sourceInMs, timebase) + 1L),
                fps,
                dropFrame,
            )
            val recordOut = EdlTimecode.format(
                maxOf(msToFrames(recordOutMs, timebase), msToFrames(recordInMs, timebase) + 1L),
                fps,
                dropFrame,
            )
            val transition = clip.headTransition?.let {
                String.format(Locale.US, "D    %03d ", msToFrames(it.durationMs, timebase).coerceIn(1L, 999L))
            } ?: "C        "
            sb.appendLine(
                String.format(
                    Locale.US,
                    "%03d  %-8s %-5s %s%s %s %s %s",
                    index + 1, reel, channel, transition, sourceIn, sourceOut, timecode(recordInMs), recordOut,
                )
            )
            if (kotlin.math.abs(safeSpeed - 1f) > 0.001f) {
                sb.appendLine(
                    String.format(Locale.US, "M2   %-8s       %05.1f                %s", reel, fps * safeSpeed, sourceIn)
                )
            }
            val fileName = clip.sourceUri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
            sb.appendLine("* FROM CLIP NAME: ${edlSafeText(fileName, fallback = "unknown")}")
            clip.effects.filter { it.enabled }.forEach { effect ->
                sb.appendLine("* EFFECT NAME: ${edlSafeText(effect.type.displayName, fallback = "Effect")}")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    /**
     * CMX 3600 reels are at most 8 characters. Phone footage shares long prefixes
     * (VID_20260105_...), so sources whose first 8 characters collide get a numbered
     * reel instead of silently sharing one. Sources with no usable name share the aux
     * reel `AX`, which conform tools match by the `FROM CLIP NAME` comment instead.
     */
    private fun edlReelNames(clips: List<Clip>): Map<String, String> {
        val sources = clips.map { it.sourceUri }.distinctBy { it.toString() }
        val bases = sources.associate { uri ->
            uri.toString() to (uri.lastPathSegment ?: "")
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
                .uppercase(Locale.ROOT)
        }
        val counts = bases.values.groupingBy { it.take(EDL_REEL_LENGTH) }.eachCount()
        fun keepsOwnName(base: String) =
            base.isNotEmpty() && counts[base.take(EDL_REEL_LENGTH)] == 1 && base.take(EDL_REEL_LENGTH) !in EDL_RESERVED_REELS
        val used = bases.values.filter(::keepsOwnName).map { it.take(EDL_REEL_LENGTH) }.toMutableSet()
        return bases.mapValues { (_, base) ->
            when {
                base.isEmpty() -> "AX"
                keepsOwnName(base) -> base.take(8)
                else -> generateSequence(1) { it + 1 }
                    .map { n ->
                        val number = String.format(Locale.US, "%03d", n)
                        base.take(EDL_REEL_LENGTH - number.length) + number
                    }
                    .first { it !in used }
                    .also { used += it }
            }
        }
    }

    private fun edlSafeText(value: String, fallback: String): String {
        return value
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .ifBlank { fallback }
            .take(120)
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
     * Unlike the single-track EDL export, it carries multiple tracks,
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
        sb.appendLine("""<fcpxml version="${InterchangeContracts.FCPXML_VERSION}">""")
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

    private fun otioTimebaseFromMetadata(
        metadata: JSONObject?,
        warnings: MutableList<String>,
    ): TimelineTimebase {
        val hasNumerator = metadata?.has("clearcut_timebase_numerator") == true
        val hasDenominator = metadata?.has("clearcut_timebase_denominator") == true
        if (!hasNumerator && !hasDenominator) return TimelineTimebase(30)

        val numerator = metadata?.optInt("clearcut_timebase_numerator", 0) ?: 0
        val denominator = metadata?.optInt("clearcut_timebase_denominator", 0) ?: 0
        if (numerator > 0 && denominator > 0) {
            return runCatching { TimelineTimebase(numerator, denominator) }
                .onFailure {
                    warnings.add(
                        "OTIO timebase metadata '$numerator/$denominator' is invalid; defaulted to 30 fps."
                    )
                }
                .getOrDefault(TimelineTimebase(30))
        }
        warnings.add("OTIO timebase metadata is incomplete or invalid; defaulted to 30 fps.")
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
        const val OTIO_MAX_SUPPORTED_SCHEMA_CODE = 16
        val SUPPORTED_OTIO_SCHEMA_VERSIONS = setOf("0.15", "0.16")
        val PROBEABLE_URI_SCHEMES = setOf("content", "file", "asset", "http", "https")
        val EDL_EVENT_LINE = Regex(
            "^\\s*\\d+\\s+(\\S+)\\s+([VA])\\s+([A-Z](?:\\s+\\d+)?)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*$"
        )

        /** `BL` is black and `AX` is the aux source in CMX 3600, so a file can't claim either. */
        val EDL_RESERVED_REELS = setOf("BL", "AX")
        const val EDL_REEL_LENGTH = 8
    }
}

/** SMPTE timecode as CMX 3600 EDLs write it, drop-frame included. */
internal object EdlTimecode {
    data class Fields(
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val frames: Int,
        /** True when a separator other than `:` marked the timecode as drop-frame. */
        val dropFrame: Boolean,
    )

    private val PATTERN = Regex("^(\\d{1,2})([:;.,])(\\d{2})([:;.,])(\\d{2})([:;.,])(\\d{2})$")

    /** Drop-frame exists only for the 1000/1001 versions of 30 and 60 fps. */
    fun isDropFrameRate(timebase: TimelineTimebase): Boolean =
        timebase.denominator == 1_001 && (timebase.numerator == 30_000 || timebase.numerator == 60_000)

    /** `;` marks drop-frame, and so do Sony's `,` and `.`; colons alone leave it to the FCM line. */
    fun parse(raw: String): Fields? {
        val match = PATTERN.matchEntire(raw.trim()) ?: return null
        val values = match.groupValues
        return Fields(
            hours = values[1].toInt(),
            minutes = values[3].toInt(),
            seconds = values[5].toInt(),
            frames = values[7].toInt(),
            dropFrame = listOf(values[2], values[4], values[6]).any { it != ":" },
        )
    }

    /** Frame count for [fields], or null when a field is out of range or names a dropped frame. */
    fun toFrame(fields: Fields, fps: Int, dropFrame: Boolean): Long? {
        if (fields.minutes !in 0..59 || fields.seconds !in 0..59 || fields.frames !in 0 until fps) return null
        val dropped = droppedPerMinute(fps, dropFrame)
        if (dropped > 0 && fields.seconds == 0 && fields.minutes % 10 != 0 && fields.frames < dropped) return null
        val totalMinutes = fields.hours * 60L + fields.minutes
        val nominal = (totalMinutes * 60L + fields.seconds) * fps + fields.frames
        return nominal - dropped * (totalMinutes - totalMinutes / 10L)
    }

    fun format(frame: Long, fps: Int, dropFrame: Boolean): String {
        val dropped = droppedPerMinute(fps, dropFrame)
        var frameNumber = frame.coerceAtLeast(0L)
        if (dropped > 0) {
            // Skip the dropped frame numbers: the first `dropped` of every minute except each tenth.
            val framesPerMinute = fps * 60L - dropped
            val framesPerTenMinutes = fps * 600L - dropped * 9L
            val tens = frameNumber / framesPerTenMinutes
            val remainder = frameNumber % framesPerTenMinutes
            frameNumber += dropped * 9L * tens
            if (remainder > dropped) frameNumber += dropped * ((remainder - dropped) / framesPerMinute)
        }
        val totalSeconds = frameNumber / fps
        return String.format(
            Locale.US,
            "%02d:%02d:%02d%s%02d",
            totalSeconds / 3_600L,
            (totalSeconds / 60L) % 60L,
            totalSeconds % 60L,
            if (dropped > 0) ";" else ":",
            frameNumber % fps,
        )
    }

    private fun droppedPerMinute(fps: Int, dropFrame: Boolean): Long =
        if (dropFrame && (fps == 30 || fps == 60)) fps / 15L else 0L
}
