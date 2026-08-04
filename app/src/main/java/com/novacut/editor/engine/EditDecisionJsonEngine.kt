package com.novacut.editor.engine

import android.net.Uri
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.model.CaptionWord
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ClipLabel
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.clampTrackTimelineOffsetMs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Portable, local-only edit-decision JSON interchange.
 *
 * The format intentionally uses milliseconds and URI strings rather than
 * ClearCut database IDs, so a script can generate it without opening a
 * project store. It carries the edit decisions that are useful at the
 * preview/import boundary: source ranges, timeline placement, common clip
 * transforms, captions, markers, and text overlays. Unknown optional fields
 * are ignored; unsupported schema versions are rejected before any candidate
 * timeline is constructed.
 */
internal object EditDecisionJsonEngine {
    const val SCHEMA_ID = "com.clearcut.edit-decision"
    const val SCHEMA_VERSION = 1
    const val FILE_EXTENSION = "clearcut-edl.json"

    private const val MAX_TRACKS = 256
    private const val MAX_CLIPS_PER_TRACK = 10_000
    private const val MAX_MARKERS = 10_000
    private const val MAX_CAPTIONS_PER_CLIP = 10_000
    private const val MAX_CAPTION_WORDS = 20_000
    private const val MAX_TEXT_OVERLAYS = 4_096
    private const val MAX_EFFECTS_PER_CLIP = 256
    private const val MAX_TEXT_CHARS = 1_000_000
    private const val MAX_URI_CHARS = 16_384
    private const val MAX_DURATION_MS = 24L * 60L * 60L * 1_000L

    fun export(
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        timelineMarkers: List<TimelineMarker>,
        projectName: String,
        timebase: TimelineTimebase,
    ): String = JSONObject().apply {
        put("schema", SCHEMA_ID)
        put("schemaVersion", SCHEMA_VERSION)
        put("project", JSONObject().apply {
            put("name", projectName.take(MAX_TEXT_CHARS))
            put("frameRateNumerator", timebase.numerator)
            put("frameRateDenominator", timebase.denominator)
        })
        put("tracks", JSONArray().apply {
            tracks.take(MAX_TRACKS).forEach { put(exportTrack(it)) }
        })
        put("markers", JSONArray().apply {
            timelineMarkers.take(MAX_MARKERS).forEach { put(exportMarker(it)) }
        })
        put("textOverlays", JSONArray().apply {
            textOverlays.take(MAX_TEXT_OVERLAYS).forEach { put(exportTextOverlay(it)) }
        })
    }.toString(2)

    fun import(
        raw: String,
        uriParser: (String) -> Uri?,
    ): TimelineExchangeEngine.ExchangeResult {
        val warnings = mutableListOf<String>()
        return try {
            val root = JSONObject(raw)
            val schema = root.optString("schema", root.optString("schemaId", ""))
            if (schema != SCHEMA_ID) {
                return rejected(
                    warnings,
                    "Unsupported edit-decision schema '$schema'; expected $SCHEMA_ID.",
                )
            }

            val version = root.optInt("schemaVersion", 0)
            if (version > SCHEMA_VERSION) {
                return rejected(
                    warnings = warnings,
                    message = "Edit-decision schema v$version is newer than ClearCut's supported v$SCHEMA_VERSION; nothing was imported.",
                    schemaVersion = version,
                    schemaTooNew = true,
                )
            }
            if (version < 1) {
                return rejected(
                    warnings,
                    "Edit-decision schemaVersion must be a positive integer.",
                    schemaVersion = version.takeIf { it != 0 },
                )
            }

            val unresolved = mutableListOf<String>()
            val tracks = parseTracks(
                root.optJSONArray("tracks") ?: JSONArray(),
                warnings,
                unresolved,
                uriParser,
            )
            val markers = parseMarkers(root.optJSONArray("markers"), warnings)
            val textOverlays = parseTextOverlays(root.optJSONArray("textOverlays"), warnings)
            TimelineExchangeEngine.ExchangeResult(
                tracks = tracks,
                textOverlays = textOverlays,
                warnings = warnings,
                unresolvedMediaUris = unresolved.distinct(),
                timelineMarkers = markers,
                schemaVersion = version,
            )
        } catch (error: Exception) {
            TimelineExchangeEngine.ExchangeResult(
                tracks = emptyList(),
                textOverlays = emptyList(),
                warnings = listOf("Failed to parse edit-decision JSON: ${error.message ?: error.javaClass.simpleName}"),
                schemaVersion = null,
            )
        }
    }

    private fun rejected(
        warnings: MutableList<String>,
        message: String,
        schemaVersion: Int? = null,
        schemaTooNew: Boolean = false,
    ): TimelineExchangeEngine.ExchangeResult {
        warnings += message
        return TimelineExchangeEngine.ExchangeResult(
            tracks = emptyList(),
            textOverlays = emptyList(),
            warnings = warnings.toList(),
            schemaVersion = schemaVersion,
            schemaTooNew = schemaTooNew,
        )
    }

    private fun exportTrack(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("type", track.type.name)
        put("index", track.index)
        put("timelineOffsetMs", track.timelineOffsetMs)
        put("isLocked", track.isLocked)
        put("isVisible", track.isVisible)
        put("isMuted", track.isMuted)
        put("isSolo", track.isSolo)
        putFiniteFloat("volume", track.volume, 1f)
        putFiniteFloat("pan", track.pan, 0f)
        putFiniteFloat("opacity", track.opacity, 1f)
        put("blendMode", track.blendMode.name)
        put("isLinkedAV", track.isLinkedAV)
        put("showWaveform", track.showWaveform)
        put("trackHeight", track.trackHeight)
        put("isCollapsed", track.isCollapsed)
        put("clips", JSONArray().apply {
            track.clips.take(MAX_CLIPS_PER_TRACK).forEach { put(exportClip(it)) }
        })
    }

    private fun exportClip(clip: Clip): JSONObject = JSONObject().apply {
        put("id", clip.id)
        clip.assetId?.let { put("assetId", it) }
        put("source", clip.sourceUri.toString())
        put("sourceDurationMs", clip.sourceDurationMs)
        put("timelineStartMs", clip.timelineStartMs)
        put("trimStartMs", clip.trimStartMs)
        put("trimEndMs", clip.trimEndMs)
        putFiniteFloat("volume", clip.volume, 1f)
        putFiniteFloat("speed", clip.speed, 1f)
        put("isReversed", clip.isReversed)
        putFiniteFloat("opacity", clip.opacity, 1f)
        putFiniteFloat("rotation", clip.rotation, 0f)
        putFiniteFloat("scaleX", clip.scaleX, 1f)
        putFiniteFloat("scaleY", clip.scaleY, 1f)
        put("flipHorizontal", clip.flipHorizontal)
        put("flipVertical", clip.flipVertical)
        putFiniteFloat("positionX", clip.positionX, 0f)
        putFiniteFloat("positionY", clip.positionY, 0f)
        putFiniteFloat("anchorX", clip.anchorX, 0.5f)
        putFiniteFloat("anchorY", clip.anchorY, 0.5f)
        put("fadeInMs", clip.fadeInMs)
        put("fadeOutMs", clip.fadeOutMs)
        put("blendMode", clip.blendMode.name)
        clip.linkedClipId?.let { put("linkedClipId", it) }
        clip.groupId?.let { put("groupId", it) }
        put("clipLabel", clip.clipLabel.name)
        clip.name?.let { put("name", it) }
        if (clip.effects.isNotEmpty()) {
            put("effects", JSONArray().apply {
                clip.effects.take(MAX_EFFECTS_PER_CLIP).forEach { effect ->
                    put(JSONObject().apply {
                        put("id", effect.id)
                        put("type", effect.type.name)
                        put("enabled", effect.enabled)
                        put("params", JSONObject().apply {
                            effect.params.entries.take(MAX_EFFECTS_PER_CLIP).forEach { (key, value) ->
                                putFiniteFloat(key, value, 0f)
                            }
                        })
                    })
                }
            })
        }
        if (clip.captions.isNotEmpty()) {
            put("captions", JSONArray().apply {
                clip.captions.take(MAX_CAPTIONS_PER_CLIP).forEach { put(exportCaption(it)) }
            })
        }
    }

    private fun exportCaption(caption: Caption): JSONObject = JSONObject().apply {
        put("id", caption.id)
        put("text", caption.text.take(MAX_TEXT_CHARS))
        put("startTimeMs", caption.startTimeMs)
        put("endTimeMs", caption.endTimeMs)
        if (caption.words.isNotEmpty()) {
            put("words", JSONArray().apply {
                caption.words.take(MAX_CAPTION_WORDS).forEach { word ->
                    put(JSONObject().apply {
                        put("text", word.text.take(MAX_TEXT_CHARS))
                        put("startTimeMs", word.startTimeMs)
                        put("endTimeMs", word.endTimeMs)
                        putFiniteFloat("confidence", word.confidence, 1f)
                    })
                }
            })
        }
        put("style", exportCaptionStyle(caption.style))
    }

    private fun exportCaptionStyle(style: CaptionStyle): JSONObject = JSONObject().apply {
        put("type", style.type.name)
        put("fontFamily", style.fontFamily.take(256))
        putFiniteFloat("fontSize", style.fontSize, 36f)
        put("color", style.color)
        put("backgroundColor", style.backgroundColor)
        put("highlightColor", style.highlightColor)
        putFiniteFloat("positionY", style.positionY, 0.85f)
        put("outline", style.outline)
        put("outlineColor", style.outlineColor)
        putFiniteFloat("outlineWidth", style.outlineWidth, 2f)
        put("shadow", style.shadow)
    }

    private fun exportMarker(marker: TimelineMarker): JSONObject = JSONObject().apply {
        put("id", marker.id)
        put("timeMs", marker.timeMs)
        put("label", marker.label.take(MAX_TEXT_CHARS))
        put("color", marker.color.name)
        put("notes", marker.notes.take(MAX_TEXT_CHARS))
    }

    private fun exportTextOverlay(overlay: TextOverlay): JSONObject = JSONObject().apply {
        put("id", overlay.id)
        put("text", overlay.text.take(MAX_TEXT_CHARS))
        put("fontFamily", overlay.fontFamily.take(256))
        putFiniteFloat("fontSize", overlay.fontSize, 48f)
        put("color", overlay.color)
        put("backgroundColor", overlay.backgroundColor)
        put("strokeColor", overlay.strokeColor)
        putFiniteFloat("strokeWidth", overlay.strokeWidth, 0f)
        put("bold", overlay.bold)
        put("italic", overlay.italic)
        put("alignment", overlay.alignment.name)
        putFiniteFloat("positionX", overlay.positionX, 0.5f)
        putFiniteFloat("positionY", overlay.positionY, 0.5f)
        put("startTimeMs", overlay.startTimeMs)
        put("endTimeMs", overlay.endTimeMs)
        put("animationIn", overlay.animationIn.name)
        put("animationOut", overlay.animationOut.name)
        putFiniteFloat("rotation", overlay.rotation, 0f)
        putFiniteFloat("scaleX", overlay.scaleX, 1f)
        putFiniteFloat("scaleY", overlay.scaleY, 1f)
        put("shadowColor", overlay.shadowColor)
        putFiniteFloat("shadowOffsetX", overlay.shadowOffsetX, 0f)
        putFiniteFloat("shadowOffsetY", overlay.shadowOffsetY, 0f)
        putFiniteFloat("shadowBlur", overlay.shadowBlur, 0f)
        putFiniteFloat("letterSpacing", overlay.letterSpacing, 0f)
        putFiniteFloat("lineHeight", overlay.lineHeight, 1.2f)
        overlay.templateId?.let { put("templateId", it) }
        put("wordStaggerMs", overlay.wordStaggerMs)
    }

    private fun parseTracks(
        array: JSONArray,
        warnings: MutableList<String>,
        unresolved: MutableList<String>,
        uriParser: (String) -> Uri?,
    ): List<Track> {
        if (array.length() > MAX_TRACKS) {
            warnings += "Edit-decision file contains more than $MAX_TRACKS tracks; remaining tracks were ignored."
        }
        return (0 until array.length().coerceAtMost(MAX_TRACKS)).mapNotNull { index ->
            val json = array.optJSONObject(index)
            if (json == null) {
                warnings += "Skipped malformed track at index $index."
                return@mapNotNull null
            }
            runCatching {
                val clips = parseClips(
                    json.optJSONArray("clips") ?: JSONArray(),
                    index,
                    warnings,
                    unresolved,
                    uriParser,
                )
                Track(
                    id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                    type = enumOrDefault(json.optString("type"), TrackType.VIDEO) { raw ->
                        warnings += "Unknown track type '$raw' at index $index; defaulted to VIDEO."
                    },
                    index = json.optInt("index", index).coerceAtLeast(0),
                    clips = clips,
                    timelineOffsetMs = clampTrackTimelineOffsetMs(json.optLong("timelineOffsetMs", 0L)),
                    isLocked = json.optBoolean("isLocked", false),
                    isVisible = json.optBoolean("isVisible", true),
                    isMuted = json.optBoolean("isMuted", false),
                    isSolo = json.optBoolean("isSolo", false),
                    volume = json.safeFloat("volume", 1f).coerceIn(0f, 2f),
                    pan = json.safeFloat("pan", 0f).coerceIn(-1f, 1f),
                    opacity = json.safeFloat("opacity", 1f).coerceIn(0f, 1f),
                    blendMode = enumOrDefault(json.optString("blendMode"), BlendMode.NORMAL) { raw ->
                        warnings += "Unknown track blend mode '$raw' at index $index; defaulted to normal."
                    },
                    isLinkedAV = json.optBoolean("isLinkedAV", true),
                    showWaveform = json.optBoolean("showWaveform", true),
                    trackHeight = json.optInt("trackHeight", 64).coerceIn(24, 512),
                    isCollapsed = json.optBoolean("isCollapsed", false),
                )
            }.onFailure { error ->
                warnings += "Skipped malformed track at index $index: ${error.message ?: error.javaClass.simpleName}."
            }.getOrNull()
        }
    }

    private fun parseClips(
        array: JSONArray,
        trackIndex: Int,
        warnings: MutableList<String>,
        unresolved: MutableList<String>,
        uriParser: (String) -> Uri?,
    ): List<Clip> {
        if (array.length() > MAX_CLIPS_PER_TRACK) {
            warnings += "Track $trackIndex contains more than $MAX_CLIPS_PER_TRACK clips; remaining clips were ignored."
        }
        return (0 until array.length().coerceAtMost(MAX_CLIPS_PER_TRACK)).mapNotNull { clipIndex ->
            val json = array.optJSONObject(clipIndex)
            if (json == null) {
                warnings += "Skipped malformed clip at track $trackIndex, index $clipIndex."
                return@mapNotNull null
            }
            runCatching {
                parseClip(json, trackIndex, clipIndex, warnings, unresolved, uriParser)
            }.onFailure { error ->
                warnings += "Skipped malformed clip at track $trackIndex, index $clipIndex: ${error.message ?: error.javaClass.simpleName}."
            }.getOrNull()
        }
    }

    private fun parseClip(
        json: JSONObject,
        trackIndex: Int,
        clipIndex: Int,
        warnings: MutableList<String>,
        unresolved: MutableList<String>,
        uriParser: (String) -> Uri?,
    ): Clip? {
        val sourceRaw = json.optString("source", json.optString("sourceUri", ""))
            .take(MAX_URI_CHARS)
        val sourceUri = sourceRaw.takeIf { it.isNotBlank() }?.let(uriParser) ?: Uri.EMPTY
        if (sourceRaw.isBlank()) {
            warnings += "Clip at track $trackIndex, index $clipIndex has no source URI."
        } else if (sourceUri == Uri.EMPTY || sourceUri.scheme?.lowercase() !in PROBEABLE_URI_SCHEMES) {
            unresolved += sourceRaw
            warnings += "Clip at track $trackIndex, index $clipIndex has media that requires relinking: $sourceRaw."
        }

        val sourceDurationMs = json.optLong("sourceDurationMs", 0L)
            .coerceIn(1L, MAX_DURATION_MS)
        val trimStartMs = json.optLong("trimStartMs", 0L)
            .coerceIn(0L, sourceDurationMs)
        val trimEndMs = json.optLong("trimEndMs", sourceDurationMs)
            .coerceIn(trimStartMs, sourceDurationMs)
        if (trimEndMs <= trimStartMs) {
            warnings += "Clip at track $trackIndex, index $clipIndex has an empty trim range and was skipped."
            return null
        }

        return Clip(
            id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
            assetId = json.optString("assetId", "").takeIf { it.isNotBlank() },
            sourceUri = sourceUri,
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = json.optLong("timelineStartMs", 0L),
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            effects = parseEffects(json.optJSONArray("effects"), warnings, trackIndex, clipIndex),
            volume = json.safeFloat("volume", 1f).coerceIn(0f, 2f),
            speed = json.safeFloat("speed", 1f).coerceAtLeast(0.01f),
            isReversed = json.optBoolean("isReversed", false),
            opacity = json.safeFloat("opacity", 1f).coerceIn(0f, 1f),
            rotation = json.safeFloat("rotation", 0f),
            scaleX = json.safeFloat("scaleX", 1f),
            scaleY = json.safeFloat("scaleY", 1f),
            flipHorizontal = json.optBoolean("flipHorizontal", false),
            flipVertical = json.optBoolean("flipVertical", false),
            positionX = json.safeFloat("positionX", 0f),
            positionY = json.safeFloat("positionY", 0f),
            anchorX = json.safeFloat("anchorX", 0.5f),
            anchorY = json.safeFloat("anchorY", 0.5f),
            fadeInMs = json.optLong("fadeInMs", 0L).coerceAtLeast(0L),
            fadeOutMs = json.optLong("fadeOutMs", 0L).coerceAtLeast(0L),
            blendMode = enumOrDefault(json.optString("blendMode"), BlendMode.NORMAL) { raw ->
                warnings += "Unknown clip blend mode '$raw' at track $trackIndex, index $clipIndex; defaulted to normal."
            },
            linkedClipId = json.optString("linkedClipId", "").takeIf { it.isNotBlank() },
            groupId = json.optString("groupId", "").takeIf { it.isNotBlank() },
            clipLabel = enumOrDefault(json.optString("clipLabel"), ClipLabel.NONE) { raw ->
                warnings += "Unknown clip label '$raw' at track $trackIndex, index $clipIndex; defaulted to none."
            },
            captions = parseCaptions(json.optJSONArray("captions"), warnings, trackIndex, clipIndex),
            name = json.optString("name", "").takeIf { it.isNotBlank() }?.take(MAX_TEXT_CHARS),
        )
    }

    private fun parseEffects(
        array: JSONArray?,
        warnings: MutableList<String>,
        trackIndex: Int,
        clipIndex: Int,
    ): List<Effect> {
        if (array == null) return emptyList()
        return (0 until array.length().coerceAtMost(MAX_EFFECTS_PER_CLIP)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val type = runCatching { EffectType.valueOf(json.optString("type")) }.getOrNull()
            if (type == null) {
                warnings += "Unknown effect at track $trackIndex, clip $clipIndex, index $index; it was dropped."
                return@mapNotNull null
            }
            val params = mutableMapOf<String, Float>()
            val paramsJson = json.optJSONObject("params")
            paramsJson?.keys()?.forEach { key ->
                params[key.take(128)] = paramsJson.safeFloat(key, 0f)
            }
            Effect(
                id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                type = type,
                params = params,
                enabled = json.optBoolean("enabled", true),
            )
        }
    }

    private fun parseCaptions(
        array: JSONArray?,
        warnings: MutableList<String>,
        trackIndex: Int,
        clipIndex: Int,
    ): List<Caption> {
        if (array == null) return emptyList()
        if (array.length() > MAX_CAPTIONS_PER_CLIP) {
            warnings += "Clip at track $trackIndex, index $clipIndex has too many captions; remaining captions were ignored."
        }
        return (0 until array.length().coerceAtMost(MAX_CAPTIONS_PER_CLIP)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                val start = json.optLong("startTimeMs", 0L).coerceAtLeast(0L)
                val end = json.optLong("endTimeMs", start).coerceAtLeast(start)
                Caption(
                    id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                    text = json.optString("text", "").take(MAX_TEXT_CHARS),
                    startTimeMs = start,
                    endTimeMs = end,
                    words = parseCaptionWords(json.optJSONArray("words")),
                    style = parseCaptionStyle(json.optJSONObject("style")),
                )
            }.onFailure { error ->
                warnings += "Skipped malformed caption at track $trackIndex, clip $clipIndex, index $index: ${error.message ?: error.javaClass.simpleName}."
            }.getOrNull()
        }
    }

    private fun parseCaptionWords(array: JSONArray?): List<CaptionWord> {
        if (array == null) return emptyList()
        return (0 until array.length().coerceAtMost(MAX_CAPTION_WORDS)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val start = json.optLong("startTimeMs", 0L).coerceAtLeast(0L)
            CaptionWord(
                text = json.optString("text", "").take(MAX_TEXT_CHARS),
                startTimeMs = start,
                endTimeMs = json.optLong("endTimeMs", start).coerceAtLeast(start),
                confidence = json.safeFloat("confidence", 1f).coerceIn(0f, 1f),
            )
        }
    }

    private fun parseCaptionStyle(json: JSONObject?): CaptionStyle {
        if (json == null) return CaptionStyle()
        return CaptionStyle(
            type = enumOrDefault(json.optString("type"), CaptionStyleType.SUBTITLE_BAR),
            fontFamily = json.optString("fontFamily", "sans-serif-medium").take(256),
            fontSize = json.safeFloat("fontSize", 36f).coerceAtLeast(1f),
            color = json.optLong("color", 0xFFFFFFFF),
            backgroundColor = json.optLong("backgroundColor", 0xCC000000),
            highlightColor = json.optLong("highlightColor", 0xFFFFD700),
            positionY = json.safeFloat("positionY", 0.85f).coerceIn(0f, 1f),
            outline = json.optBoolean("outline", true),
            outlineColor = json.optLong("outlineColor", 0xFF000000),
            outlineWidth = json.safeFloat("outlineWidth", 2f).coerceAtLeast(0f),
            shadow = json.optBoolean("shadow", true),
        )
    }

    private fun parseMarkers(array: JSONArray?, warnings: MutableList<String>): List<TimelineMarker> {
        if (array == null) return emptyList()
        if (array.length() > MAX_MARKERS) {
            warnings += "Edit-decision file contains more than $MAX_MARKERS markers; remaining markers were ignored."
        }
        return (0 until array.length().coerceAtMost(MAX_MARKERS)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                TimelineMarker(
                    id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                    timeMs = json.optLong("timeMs", 0L).coerceAtLeast(0L),
                    label = json.optString("label", "").take(MAX_TEXT_CHARS),
                    color = enumOrDefault(json.optString("color"), MarkerColor.BLUE),
                    notes = json.optString("notes", "").take(MAX_TEXT_CHARS),
                )
            }.onFailure { error ->
                warnings += "Skipped malformed timeline marker at index $index: ${error.message ?: error.javaClass.simpleName}."
            }.getOrNull()
        }
    }

    private fun parseTextOverlays(array: JSONArray?, warnings: MutableList<String>): List<TextOverlay> {
        if (array == null) return emptyList()
        if (array.length() > MAX_TEXT_OVERLAYS) {
            warnings += "Edit-decision file contains more than $MAX_TEXT_OVERLAYS text overlays; remaining overlays were ignored."
        }
        return (0 until array.length().coerceAtMost(MAX_TEXT_OVERLAYS)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                TextOverlay(
                    id = json.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                    text = json.optString("text", "").take(MAX_TEXT_CHARS),
                    fontFamily = json.optString("fontFamily", "sans-serif").take(256),
                    fontSize = json.safeFloat("fontSize", 48f).coerceAtLeast(1f),
                    color = json.optLong("color", 0xFFFFFFFF),
                    backgroundColor = json.optLong("backgroundColor", 0L),
                    strokeColor = json.optLong("strokeColor", 0xFF000000),
                    strokeWidth = json.safeFloat("strokeWidth", 0f).coerceAtLeast(0f),
                    bold = json.optBoolean("bold", false),
                    italic = json.optBoolean("italic", false),
                    alignment = enumOrDefault(json.optString("alignment"), TextAlignment.CENTER),
                    positionX = json.safeFloat("positionX", 0.5f),
                    positionY = json.safeFloat("positionY", 0.5f),
                    startTimeMs = json.optLong("startTimeMs", 0L).coerceAtLeast(0L),
                    endTimeMs = json.optLong("endTimeMs", 3_000L).coerceAtLeast(0L),
                    animationIn = enumOrDefault(json.optString("animationIn"), TextAnimation.NONE),
                    animationOut = enumOrDefault(json.optString("animationOut"), TextAnimation.NONE),
                    rotation = json.safeFloat("rotation", 0f),
                    scaleX = json.safeFloat("scaleX", 1f),
                    scaleY = json.safeFloat("scaleY", 1f),
                    shadowColor = json.optLong("shadowColor", 0x80000000),
                    shadowOffsetX = json.safeFloat("shadowOffsetX", 0f),
                    shadowOffsetY = json.safeFloat("shadowOffsetY", 0f),
                    shadowBlur = json.safeFloat("shadowBlur", 0f),
                    letterSpacing = json.safeFloat("letterSpacing", 0f),
                    lineHeight = json.safeFloat("lineHeight", 1.2f).coerceAtLeast(0.1f),
                    templateId = json.optString("templateId", "").takeIf { it.isNotBlank() },
                    wordStaggerMs = json.optLong("wordStaggerMs", 0L).coerceAtLeast(0L),
                )
            }.onFailure { error ->
                warnings += "Skipped malformed text overlay at index $index: ${error.message ?: error.javaClass.simpleName}."
            }.getOrNull()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        raw: String,
        default: T,
        noinline onUnknown: ((String) -> Unit)? = null,
    ): T {
        if (raw.isBlank()) return default
        return runCatching { enumValueOf<T>(raw) }.getOrElse {
            onUnknown?.invoke(raw)
            default
        }
    }

    private fun JSONObject.safeFloat(name: String, default: Float): Float {
        val value = optDouble(name, default.toDouble()).toFloat()
        return value.takeIf { it.isFinite() } ?: default
    }

    private fun JSONObject.putFiniteFloat(name: String, value: Float, default: Float) {
        put(name, if (value.isFinite()) value else default)
    }

    private val PROBEABLE_URI_SCHEMES = setOf("content", "file", "asset", "http", "https")
}
