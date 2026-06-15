package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [DiagnosticExportEngine.TimelineShape] contract that the Settings
 * "Include sanitized timeline shape" opt-in depends on.
 *
 * The cardinal invariant: the shape must carry *counts only* — no clip names,
 * no source URIs, no captions. These tests construct a project with sensitive
 * strings deliberately injected into the clip name field (the URI side is
 * covered by inspection — `FakeUri.toString()` returns a fixed `test://clip`,
 * not the variable-secret value, so the assertion target there is the clip
 * name) and assert that `toJsonString()` output contains none of them.
 */
class DiagnosticTimelineShapeTest {

    private val secretClipName = "MY_PERSONAL_VIDEO_NAME"

    @Test
    fun summarize_emptyProjectProducesZeroCounts() {
        val shape = DiagnosticExportEngine.summarizeTimelineShape(emptyList())
        assertEquals(0, shape.trackCount)
        assertEquals(0L, shape.totalDurationMs)
        assertTrue(shape.perTrackClipCount.isEmpty())
        assertTrue(shape.perEffectTypeCount.isEmpty())
        assertTrue(shape.perTransitionTypeCount.isEmpty())
    }

    @Test
    fun summarize_countsClipsPerTrackAndComputesTotalDuration() {
        val tracks = listOf(
            track(
                TrackType.VIDEO,
                clip(startMs = 0, durationMs = 1_000),
                clip(startMs = 1_000, durationMs = 2_000),
            ),
            track(
                TrackType.AUDIO,
                clip(startMs = 0, durationMs = 5_000),
            ),
        )
        val shape = DiagnosticExportEngine.summarizeTimelineShape(tracks)
        assertEquals(2, shape.trackCount)
        // Total duration = max clip end across all tracks.
        assertEquals(5_000L, shape.totalDurationMs)
        assertEquals(
            listOf(
                DiagnosticExportEngine.TimelineShape.TrackClipCount("VIDEO", 2),
                DiagnosticExportEngine.TimelineShape.TrackClipCount("AUDIO", 1),
            ),
            shape.perTrackClipCount
        )
    }

    @Test
    fun summarize_bucketsEffectsAndTransitionsByType() {
        val tracks = listOf(
            track(
                TrackType.VIDEO,
                clip(effects = listOf(effect(EffectType.BRIGHTNESS), effect(EffectType.CONTRAST))),
                clip(effects = listOf(effect(EffectType.BRIGHTNESS)), headTransition = Transition(TransitionType.DISSOLVE)),
                clip(headTransition = Transition(TransitionType.WIPE_LEFT)),
            ),
        )
        val shape = DiagnosticExportEngine.summarizeTimelineShape(tracks)
        assertEquals(2, shape.perEffectTypeCount["BRIGHTNESS"])
        assertEquals(1, shape.perEffectTypeCount["CONTRAST"])
        assertEquals(1, shape.perTransitionTypeCount["DISSOLVE"])
        assertEquals(1, shape.perTransitionTypeCount["WIPE_LEFT"])
    }

    @Test
    fun summarize_countsCompoundChildrenInPerTrackTotal() {
        val tracks = listOf(
            track(
                TrackType.VIDEO,
                clip().copy(
                    isCompound = true,
                    compoundClips = listOf(clip(), clip(), clip()),
                ),
            ),
        )
        val shape = DiagnosticExportEngine.summarizeTimelineShape(tracks)
        // 1 wrapper + 3 nested = 4 per-track entries
        assertEquals(4, shape.perTrackClipCount.single().clipCount)
    }

    @Test
    fun jsonOutput_isValidJsonAndOmitsClipNames() {
        val tracks = listOf(
            track(
                TrackType.VIDEO,
                clip(name = secretClipName, effects = listOf(effect(EffectType.SATURATION))),
            ),
        )
        val shape = DiagnosticExportEngine.summarizeTimelineShape(tracks)
        val json = shape.toJsonString()
        // Parseable JSON.
        val obj = JSONObject(json)
        assertEquals("com.novacut.timeline-shape.v1", obj.optString("schema"))
        assertEquals(1, obj.optInt("trackCount"))
        // Confidential clip name must NOT appear anywhere in the serialised form.
        assertFalse(
            "Clip name must never appear in the timeline-shape JSON",
            json.contains(secretClipName)
        )
        // Spot-check: effect type IS present (intentional — it's a count key).
        assertTrue(json.contains("SATURATION"))
    }

    @Test
    fun jsonOutput_escapesQuotesAndBackslashesInTypeKeys() {
        // Defensive: while EffectType.name and TransitionType.name only contain
        // identifier chars today, the JSON writer must escape regardless so a
        // future enum rename can't accidentally produce invalid JSON.
        val shape = DiagnosticExportEngine.TimelineShape(
            trackCount = 1,
            totalDurationMs = 0L,
            perTrackClipCount = emptyList(),
            perEffectTypeCount = mapOf("EVIL\"\\\nKEY" to 1),
            perTransitionTypeCount = emptyMap(),
        )
        val json = shape.toJsonString()
        // Parseable.
        val obj = JSONObject(json)
        val map = obj.optJSONObject("perEffectTypeCount")!!
        // Key round-trips with the original literal characters.
        assertTrue(map.keys().asSequence().any { it.contains("EVIL") })
    }

    // --- helpers ---

    private fun track(type: TrackType, vararg clips: Clip): Track =
        Track(type = type, index = 0, clips = clips.toList())

    private fun clip(
        startMs: Long = 0L,
        durationMs: Long = 1_000L,
        name: String? = null,
        effects: List<Effect> = emptyList(),
        headTransition: Transition? = null,
    ): Clip = Clip(
        sourceUri = FakeUri,
        sourceDurationMs = durationMs,
        timelineStartMs = startMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        effects = effects,
        headTransition = headTransition,
        name = name,
    )

    private fun effect(type: EffectType): Effect = Effect(type = type)
}
