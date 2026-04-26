package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.TrackedObject
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCompatibilityEngineTest {

    @Test
    fun createMetadata_collectsFeaturesAndSlots() {
        val state = AutoSaveState(
            projectId = "template",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            id = "clip",
                            sourceUri = FakeUri,
                            sourceDurationMs = 2_000L,
                            timelineStartMs = 0L,
                            effects = listOf(Effect(type = EffectType.TRACKED_MOSAIC)),
                            transition = Transition(type = TransitionType.DISSOLVE),
                            audioEffects = listOf(AudioEffect(type = AudioEffectType.COMPRESSOR))
                        )
                    )
                )
            ),
            textOverlays = listOf(TextOverlay(text = "Title")),
            trackedObjects = listOf(
                TrackedObject(
                    id = "subject",
                    label = "Subject",
                    sourceClipId = "clip"
                )
            )
        )

        val metadata = TemplateCompatibilityEngine.createMetadata(
            state = state,
            minVersionCode = 132,
            minVersionName = "3.71.0"
        )
        val featureKeys = metadata.features.map { it.type to it.key }.toSet()

        assertEquals(132, metadata.minVersionCode)
        assertEquals("3.71.0", metadata.minVersionName)
        assertEquals(2, metadata.slotCount)
        assertEquals(1, metadata.mediaSlotCount)
        assertEquals(1, metadata.textSlotCount)
        assertTrue(TemplateFeatureType.TRACK_TYPE to TrackType.VIDEO.name in featureKeys)
        assertTrue(TemplateFeatureType.EFFECT to EffectType.TRACKED_MOSAIC.name in featureKeys)
        assertTrue(TemplateFeatureType.TRACKED_MOSAIC to EffectType.TRACKED_MOSAIC.name in featureKeys)
        assertTrue(TemplateFeatureType.TRANSITION to TransitionType.DISSOLVE.name in featureKeys)
        assertTrue(TemplateFeatureType.AUDIO_EFFECT to AudioEffectType.COMPRESSOR.name in featureKeys)
        assertTrue(TemplateFeatureType.TEXT_OVERLAY to "TEXT_OVERLAY" in featureKeys)
        assertTrue(TemplateFeatureType.TRACKED_OBJECT to "TRACKED_OBJECT" in featureKeys)
    }

    @Test
    fun validate_blocksFutureSchemaAndAppVersion() {
        val report = TemplateCompatibilityEngine.validate(
            metadata = TemplateCompatibilityMetadata(
                schemaVersion = 99,
                minVersionCode = 10_000,
                minVersionName = "9.0.0"
            ),
            currentSchemaVersion = 1,
            currentVersionCode = 132
        )

        assertFalse(report.canImport)
        assertEquals(TemplateCompatibilityStatus.BLOCKED, report.status)
        assertTrue(report.issues.any { it.code == "future_schema" })
        assertTrue(report.issues.any { it.code == "future_app_version" })
    }

    @Test
    fun validate_blocksUnknownRequiredFeatures() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("minAppVersionCode", 1)
            put("minAppVersionName", "3.8.0")
            put("features", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "FUTURE_AI_TOOL")
                    put("key", "FUTURE_AI_TOOL")
                    put("displayName", "Future AI tool")
                    put("required", true)
                })
            })
        }

        val metadata = TemplateCompatibilityEngine.fromJson(json)!!
        val report = TemplateCompatibilityEngine.validate(
            metadata = metadata,
            currentSchemaVersion = 1,
            currentVersionCode = 132
        )

        assertEquals(TemplateFeatureType.UNKNOWN, metadata.features.single().type)
        assertFalse(report.canImport)
        assertEquals(TemplateCompatibilityStatus.BLOCKED, report.status)
        assertTrue(report.issues.any { it.code == "unsupported_feature" })
    }

    @Test
    fun jsonRoundTrip_preservesMetadata() {
        val metadata = TemplateCompatibilityMetadata(
            schemaVersion = 1,
            minVersionCode = 132,
            minVersionName = "3.71.0",
            features = listOf(
                TemplateFeatureRequirement(
                    type = TemplateFeatureType.EFFECT,
                    key = EffectType.GAUSSIAN_BLUR.name,
                    displayName = EffectType.GAUSSIAN_BLUR.displayName
                ),
                TemplateFeatureRequirement(
                    type = TemplateFeatureType.TEXT_OVERLAY,
                    key = "TEXT_OVERLAY",
                    displayName = "Text overlays"
                )
            ),
            slotCount = 3,
            mediaSlotCount = 2,
            textSlotCount = 1
        )

        val restored = TemplateCompatibilityEngine.fromJson(
            TemplateCompatibilityEngine.toJson(metadata)
        )

        assertEquals(metadata, restored)
    }
}
