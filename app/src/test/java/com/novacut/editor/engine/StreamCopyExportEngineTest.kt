package com.novacut.editor.engine

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.Uri
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for StreamCopyExportEngine.analyze — the central eligibility
 * oracle for the zero-transcode export path. A regression here would either
 * allow a modified clip to silently ship without its edits (silent data loss)
 * or cause the engine to needlessly fall back to Transformer (50× slowdown).
 *
 * The suspend `execute()` path is not tested here because it depends on the
 * real MediaExtractor / MediaMuxer. That coverage lives in instrumentation
 * tests where a physical source file is available.
 */
class StreamCopyExportEngineTest {

    private val engine = StreamCopyExportEngine(
        streamCopyMuxer = StreamCopyMuxer(FakeContext())
    )

    @Test
    fun analyze_singleCleanClip_isEligible() {
        val clip = baseClip()
        val tracks = listOf(videoTrack(clip))
        val result = engine.analyze(tracks, hasEffectsOrOverlays = false)
        assertTrue("single unmodified clip should be eligible: ${result.reason}", result.eligible)
        assertEquals(1, result.ranges.size)
        assertEquals(0L, result.ranges[0].startMs)
        assertEquals(5_000L, result.ranges[0].endMs)
    }

    @Test
    fun analyze_hasOverlaysFlag_disqualifies() {
        val result = engine.analyze(listOf(videoTrack(baseClip())), hasEffectsOrOverlays = true)
        assertFalse(result.eligible)
        assertEquals("effects or overlays present", result.reason)
    }

    @Test
    fun analyze_effectsOnClip_disqualifies() {
        val clip = baseClip().copy(
            effects = listOf(Effect(type = EffectType.BRIGHTNESS, params = mapOf("amount" to 0.1f)))
        )
        val result = engine.analyze(listOf(videoTrack(clip)), hasEffectsOrOverlays = false)
        assertFalse(result.eligible)
        assertTrue(result.reason.contains("effects"))
    }

    @Test
    fun analyze_speedChange_disqualifies() {
        val clip = baseClip().copy(speed = 2f)
        assertFalse(engine.analyze(listOf(videoTrack(clip)), false).eligible)
    }

    @Test
    fun analyze_speedCurve_disqualifies() {
        val clip = baseClip().copy(
            speedCurve = SpeedCurve(listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 2f)))
        )
        assertFalse(engine.analyze(listOf(videoTrack(clip)), false).eligible)
    }

    @Test
    fun analyze_audioFadeDisqualifies() {
        val clip = baseClip().copy(fadeInMs = 200L)
        val result = engine.analyze(listOf(videoTrack(clip)), false)
        assertFalse(result.eligible)
        assertEquals("clip has audio fade-in", result.reason)
    }

    @Test
    fun analyze_audioVolumeDisqualifies() {
        val clip = baseClip().copy(volume = 0.5f)
        val result = engine.analyze(listOf(videoTrack(clip)), false)
        assertFalse(result.eligible)
        assertEquals("clip volume ≠ 1×", result.reason)
    }

    @Test
    fun analyze_audioEffectsDisqualifies() {
        val clip = baseClip().copy(
            audioEffects = listOf(AudioEffect(type = AudioEffectType.COMPRESSOR))
        )
        val result = engine.analyze(listOf(videoTrack(clip)), false)
        assertFalse(result.eligible)
        assertEquals("clip has audio effects", result.reason)
    }

    @Test
    fun analyze_keyframesDisqualify() {
        val clip = baseClip().copy(
            keyframes = listOf(
                Keyframe(timeOffsetMs = 0L, property = KeyframeProperty.OPACITY, value = 1f)
            )
        )
        val result = engine.analyze(listOf(videoTrack(clip)), false)
        assertFalse(result.eligible)
        assertEquals("clip has keyframes", result.reason)
    }

    @Test
    fun analyze_colorGradeDisqualifies() {
        val clip = baseClip().copy(colorGrade = ColorGrade(enabled = true))
        val result = engine.analyze(listOf(videoTrack(clip)), false)
        assertFalse(result.eligible)
        assertEquals("clip has color grade", result.reason)
    }

    @Test
    fun analyze_blendModeDisqualifies() {
        val clip = baseClip().copy(blendMode = BlendMode.MULTIPLY)
        assertFalse(engine.analyze(listOf(videoTrack(clip)), false).eligible)
    }

    @Test
    fun analyze_multiVideoTrack_disqualifies() {
        val tracks = listOf(videoTrack(baseClip()), videoTrack(baseClip(), index = 1))
        val result = engine.analyze(tracks, false)
        assertFalse(result.eligible)
        assertEquals("multi-track video", result.reason)
    }

    @Test
    fun analyze_additionalAudioTrack_disqualifies() {
        val tracks = listOf(
            videoTrack(baseClip()),
            Track(
                type = TrackType.AUDIO,
                index = 1,
                clips = listOf(baseClip())
            )
        )
        val result = engine.analyze(tracks, false)
        assertFalse(result.eligible)
        assertEquals("additional audio tracks", result.reason)
    }

    @Test
    fun analyze_multiClipSameSource_isEligible() {
        val c1 = baseClip().copy(trimStartMs = 0L, trimEndMs = 2_000L, timelineStartMs = 0L)
        val c2 = baseClip().copy(trimStartMs = 3_000L, trimEndMs = 5_000L, timelineStartMs = 2_000L)
        val result = engine.analyze(
            listOf(videoTrack(c1, c2)),
            hasEffectsOrOverlays = false
        )
        assertTrue("multi-clip same-source should be eligible: ${result.reason}", result.eligible)
        assertEquals(2, result.ranges.size)
        assertEquals(0L, result.ranges[0].startMs)
        assertEquals(2_000L, result.ranges[0].endMs)
        assertEquals(3_000L, result.ranges[1].startMs)
        assertEquals(5_000L, result.ranges[1].endMs)
    }

    @Test
    fun analyze_multiClipDifferentSource_disqualifies() {
        val c1 = baseClip(uri = FakeUriA)
        val c2 = baseClip(uri = FakeUriB)
        val result = engine.analyze(listOf(videoTrack(c1, c2)), false)
        assertFalse(result.eligible)
        assertEquals("multiple source files", result.reason)
    }

    @Test
    fun analyze_trackMutedDisqualifies() {
        val track = videoTrack(baseClip()).copy(isMuted = true)
        val result = engine.analyze(listOf(track), false)
        assertFalse(result.eligible)
        assertEquals("video track has non-default mix", result.reason)
    }

    @Test
    fun analyze_trackOpacityDisqualifies() {
        val track = videoTrack(baseClip()).copy(opacity = 0.5f)
        assertFalse(engine.analyze(listOf(track), false).eligible)
    }

    @Test
    fun analyze_noClips_disqualifies() {
        val track = videoTrack()
        val result = engine.analyze(listOf(track), false)
        assertFalse(result.eligible)
        assertEquals("no clips", result.reason)
    }

    @Test
    fun analyze_clipsAreSortedByTimeline() {
        // Intentionally reversed input — analyze should still produce ranges
        // in timeline order so concat runs them in monotonic time.
        val c1 = baseClip().copy(trimStartMs = 0L, trimEndMs = 1_000L, timelineStartMs = 2_000L)
        val c2 = baseClip().copy(trimStartMs = 2_000L, trimEndMs = 3_000L, timelineStartMs = 0L)
        val result = engine.analyze(listOf(videoTrack(c1, c2)), false)
        assertTrue(result.eligible)
        // c2 comes first because its timelineStartMs is earlier.
        assertEquals(2_000L, result.ranges[0].startMs)
        assertEquals(0L, result.ranges[1].startMs)
    }

    // --- fixtures ---

    private val FakeUriA: Uri = FakeUri
    private val FakeUriB: Uri = SecondFakeUri as Uri

    private fun baseClip(uri: Uri = FakeUri): Clip = Clip(
        sourceUri = uri,
        sourceDurationMs = 10_000L,
        timelineStartMs = 0L,
        trimStartMs = 0L,
        trimEndMs = 5_000L
    )

    private fun videoTrack(vararg clips: Clip, index: Int = 0): Track = Track(
        type = TrackType.VIDEO,
        index = index,
        clips = clips.toList()
    )
}

/**
 * Minimal Context stub — the StreamCopyMuxer constructor demands one but we
 * never call any engine path that uses it during analyze(), so a bare
 * ContextWrapper is enough. The real inputs here are the Tracks and the
 * hasEffectsOrOverlays flag.
 */
private class FakeContext : android.content.ContextWrapper(null)
