package com.novacut.editor.engine

import com.novacut.editor.model.EffectType
import org.json.JSONArray
import org.json.JSONObject

data class TemplateCompatibilityMetadata(
    val schemaVersion: Int = 1,
    val minVersionCode: Int = 1,
    val minVersionName: String = "3.8.0",
    val features: List<TemplateFeatureRequirement> = emptyList(),
    val slotCount: Int = 0,
    val mediaSlotCount: Int = 0,
    val textSlotCount: Int = 0
)

data class TemplateFeatureRequirement(
    val type: TemplateFeatureType,
    val key: String,
    val displayName: String,
    val required: Boolean = true
)

enum class TemplateFeatureType {
    TRACK_TYPE,
    EFFECT,
    AUDIO_EFFECT,
    TRANSITION,
    COLOR_GRADE,
    SPEED_CURVE,
    MASK,
    CAPTION,
    TEXT_OVERLAY,
    IMAGE_OVERLAY,
    DRAWING,
    CHAPTER_MARKER,
    BEAT_MARKER,
    TIMELINE_MARKER,
    TRANSCRIPT,
    TRACKED_OBJECT,
    TRACKED_MOSAIC,
    MOTION_TRACKING,
    COMPOUND_CLIP,
    UNKNOWN
}

enum class TemplateCompatibilityStatus {
    COMPATIBLE,
    WARNING,
    BLOCKED
}

data class TemplateCompatibilityIssue(
    val code: String,
    val message: String,
    val blocking: Boolean
)

data class TemplateCompatibilityReport(
    val status: TemplateCompatibilityStatus,
    val issues: List<TemplateCompatibilityIssue>
) {
    val canImport: Boolean get() = issues.none { it.blocking }
}

object TemplateCompatibilityEngine {
    val supportedFeatureTypes: Set<TemplateFeatureType> =
        TemplateFeatureType.entries.filterNot { it == TemplateFeatureType.UNKNOWN }.toSet()

    fun createMetadata(
        state: AutoSaveState,
        minVersionCode: Int = 1,
        minVersionName: String = "3.8.0",
        schemaVersion: Int = 1
    ): TemplateCompatibilityMetadata {
        val requirements = mutableListOf<TemplateFeatureRequirement>()
        fun add(type: TemplateFeatureType, key: String, displayName: String) {
            requirements += TemplateFeatureRequirement(
                type = type,
                key = key.trim().ifBlank { type.name },
                displayName = displayName.trim().ifBlank { humanize(key) }
            )
        }

        state.tracks.forEach { track ->
            add(
                type = TemplateFeatureType.TRACK_TYPE,
                key = track.type.name,
                displayName = humanize(track.type.name)
            )
            track.audioEffects.forEach { audioEffect ->
                add(
                    type = TemplateFeatureType.AUDIO_EFFECT,
                    key = audioEffect.type.name,
                    displayName = audioEffect.type.displayName
                )
            }
        }

        val clips = state.tracks.flatMap { it.clips }
        clips.forEach { clip ->
            clip.effects.forEach { effect ->
                add(
                    type = TemplateFeatureType.EFFECT,
                    key = effect.type.name,
                    displayName = effect.type.displayName
                )
                if (effect.type == EffectType.TRACKED_MOSAIC) {
                    add(
                        type = TemplateFeatureType.TRACKED_MOSAIC,
                        key = EffectType.TRACKED_MOSAIC.name,
                        displayName = EffectType.TRACKED_MOSAIC.displayName
                    )
                }
            }
            clip.audioEffects.forEach { audioEffect ->
                add(
                    type = TemplateFeatureType.AUDIO_EFFECT,
                    key = audioEffect.type.name,
                    displayName = audioEffect.type.displayName
                )
            }
            clip.transition?.let { transition ->
                add(
                    type = TemplateFeatureType.TRANSITION,
                    key = transition.type.name,
                    displayName = transition.type.displayName
                )
            }
            if (clip.colorGrade != null) {
                add(TemplateFeatureType.COLOR_GRADE, "COLOR_GRADE", "Color grade")
            }
            if (clip.speedCurve != null) {
                add(TemplateFeatureType.SPEED_CURVE, "SPEED_CURVE", "Speed curve")
            }
            if (clip.masks.isNotEmpty()) {
                add(TemplateFeatureType.MASK, "MASK", "Mask")
            }
            if (clip.captions.isNotEmpty()) {
                add(TemplateFeatureType.CAPTION, "CAPTION", "Captions")
            }
            if (clip.motionTrackingData != null) {
                add(TemplateFeatureType.MOTION_TRACKING, "MOTION_TRACKING", "Motion tracking")
            }
            if (clip.isCompound || clip.compoundClips.isNotEmpty()) {
                add(TemplateFeatureType.COMPOUND_CLIP, "COMPOUND_CLIP", "Compound clip")
            }
        }

        if (state.textOverlays.isNotEmpty()) {
            add(TemplateFeatureType.TEXT_OVERLAY, "TEXT_OVERLAY", "Text overlays")
        }
        if (state.imageOverlays.isNotEmpty()) {
            add(TemplateFeatureType.IMAGE_OVERLAY, "IMAGE_OVERLAY", "Image overlays")
        }
        if (state.drawingPaths.isNotEmpty()) {
            add(TemplateFeatureType.DRAWING, "DRAWING", "Drawings")
        }
        if (state.chapterMarkers.isNotEmpty()) {
            add(TemplateFeatureType.CHAPTER_MARKER, "CHAPTER_MARKER", "Chapter markers")
        }
        if (state.beatMarkers.isNotEmpty()) {
            add(TemplateFeatureType.BEAT_MARKER, "BEAT_MARKER", "Beat markers")
        }
        if (state.timelineMarkers.isNotEmpty()) {
            add(TemplateFeatureType.TIMELINE_MARKER, "TIMELINE_MARKER", "Timeline markers")
        }
        if (state.transcript != null) {
            add(TemplateFeatureType.TRANSCRIPT, "TRANSCRIPT", "Transcript")
        }
        if (state.trackedObjects.isNotEmpty()) {
            add(TemplateFeatureType.TRACKED_OBJECT, "TRACKED_OBJECT", "Tracked objects")
        }

        val textSlotCount = state.textOverlays.size + clips.sumOf { it.captions.size }
        val mediaSlotCount = clips.size + state.imageOverlays.size

        return TemplateCompatibilityMetadata(
            schemaVersion = schemaVersion.coerceAtLeast(1),
            minVersionCode = minVersionCode.coerceAtLeast(1),
            minVersionName = minVersionName.trim().ifBlank { "3.8.0" },
            features = requirements.normalizedRequirements(),
            slotCount = mediaSlotCount + textSlotCount,
            mediaSlotCount = mediaSlotCount,
            textSlotCount = textSlotCount
        )
    }

    fun merge(
        declared: TemplateCompatibilityMetadata?,
        inferred: TemplateCompatibilityMetadata
    ): TemplateCompatibilityMetadata {
        if (declared == null) return inferred.copy(features = inferred.features.normalizedRequirements())
        val mergedMinVersionCode = maxOf(declared.minVersionCode, inferred.minVersionCode)
        return TemplateCompatibilityMetadata(
            schemaVersion = maxOf(declared.schemaVersion, inferred.schemaVersion),
            minVersionCode = mergedMinVersionCode,
            minVersionName = if (declared.minVersionCode >= inferred.minVersionCode) {
                declared.minVersionName
            } else {
                inferred.minVersionName
            }.ifBlank { inferred.minVersionName },
            features = (declared.features + inferred.features).normalizedRequirements(),
            slotCount = maxOf(declared.slotCount, inferred.slotCount),
            mediaSlotCount = maxOf(declared.mediaSlotCount, inferred.mediaSlotCount),
            textSlotCount = maxOf(declared.textSlotCount, inferred.textSlotCount)
        )
    }

    fun validate(
        metadata: TemplateCompatibilityMetadata,
        currentSchemaVersion: Int = 1,
        currentVersionCode: Int = Int.MAX_VALUE,
        supportedFeatures: Set<TemplateFeatureType> = supportedFeatureTypes
    ): TemplateCompatibilityReport {
        val issues = mutableListOf<TemplateCompatibilityIssue>()
        if (metadata.schemaVersion > currentSchemaVersion) {
            issues += TemplateCompatibilityIssue(
                code = "future_schema",
                message = "Template schema ${metadata.schemaVersion} requires a newer NovaCut template parser.",
                blocking = true
            )
        }
        if (metadata.minVersionCode > currentVersionCode) {
            issues += TemplateCompatibilityIssue(
                code = "future_app_version",
                message = "Template requires NovaCut ${metadata.minVersionName}.",
                blocking = true
            )
        }

        metadata.features.forEach { feature ->
            val supported = feature.type in supportedFeatures && feature.type != TemplateFeatureType.UNKNOWN
            if (!supported) {
                issues += TemplateCompatibilityIssue(
                    code = "unsupported_feature",
                    message = "Template uses unsupported feature: ${feature.displayName}.",
                    blocking = feature.required
                )
            }
        }

        val status = when {
            issues.any { it.blocking } -> TemplateCompatibilityStatus.BLOCKED
            issues.isNotEmpty() -> TemplateCompatibilityStatus.WARNING
            else -> TemplateCompatibilityStatus.COMPATIBLE
        }
        return TemplateCompatibilityReport(status = status, issues = issues)
    }

    fun toJson(metadata: TemplateCompatibilityMetadata): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", metadata.schemaVersion)
            put("minAppVersionCode", metadata.minVersionCode)
            put("minAppVersionName", metadata.minVersionName)
            put("slotCount", metadata.slotCount)
            put("mediaSlotCount", metadata.mediaSlotCount)
            put("textSlotCount", metadata.textSlotCount)
            put("features", JSONArray().apply {
                metadata.features.normalizedRequirements().forEach { feature ->
                    put(JSONObject().apply {
                        put("type", feature.type.name)
                        put("key", feature.key)
                        put("displayName", feature.displayName)
                        put("required", feature.required)
                    })
                }
            })
        }
    }

    fun fromJson(json: JSONObject?): TemplateCompatibilityMetadata? {
        if (json == null) return null
        val featuresJson = json.optJSONArray("features")
        val features = featuresJson?.let { arr ->
            (0 until arr.length()).mapNotNull { index ->
                val featureJson = arr.optJSONObject(index) ?: return@mapNotNull null
                val rawType = featureJson.optString("type", "")
                val type = parseFeatureType(rawType)
                val key = featureJson.optString("key", rawType).trim().ifBlank { type.name }
                TemplateFeatureRequirement(
                    type = type,
                    key = key,
                    displayName = featureJson.optString("displayName", humanize(key)).trim()
                        .ifBlank { humanize(key) },
                    required = featureJson.optBoolean("required", true)
                )
            }
        }.orEmpty()

        return TemplateCompatibilityMetadata(
            schemaVersion = json.optInt("schemaVersion", 1).coerceAtLeast(1),
            minVersionCode = json.optInt(
                "minAppVersionCode",
                json.optInt("minVersionCode", 1)
            ).coerceAtLeast(1),
            minVersionName = json.optString(
                "minAppVersionName",
                json.optString("minVersionName", "3.8.0")
            ).trim().ifBlank { "3.8.0" },
            features = features.normalizedRequirements(),
            slotCount = json.optInt("slotCount", 0).coerceAtLeast(0),
            mediaSlotCount = json.optInt("mediaSlotCount", 0).coerceAtLeast(0),
            textSlotCount = json.optInt("textSlotCount", 0).coerceAtLeast(0)
        )
    }

    private fun parseFeatureType(raw: String): TemplateFeatureType {
        return try {
            TemplateFeatureType.valueOf(raw)
        } catch (_: Exception) {
            TemplateFeatureType.UNKNOWN
        }
    }

    private fun List<TemplateFeatureRequirement>.normalizedRequirements(): List<TemplateFeatureRequirement> {
        val byKey = linkedMapOf<String, TemplateFeatureRequirement>()
        forEach { feature ->
            val key = feature.key.trim().ifBlank { feature.type.name }
            val normalized = feature.copy(
                key = key,
                displayName = feature.displayName.trim().ifBlank { humanize(key) }
            )
            val mapKey = "${normalized.type.name}:$key"
            val existing = byKey[mapKey]
            byKey[mapKey] = if (existing == null) {
                normalized
            } else {
                existing.copy(required = existing.required || normalized.required)
            }
        }
        return byKey.values.sortedWith(compareBy({ it.type.name }, { it.key }))
    }

    private fun humanize(raw: String): String {
        return raw.trim()
            .ifBlank { "Unknown" }
            .lowercase()
            .split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }
}
