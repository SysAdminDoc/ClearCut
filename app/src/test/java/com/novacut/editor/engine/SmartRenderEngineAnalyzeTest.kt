package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Caption
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskType
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRenderEngineAnalyzeTest {

    private val config = ExportConfig()

    private fun clip(
        id: String = "c1",
        startMs: Long = 0L,
        durationMs: Long = 2_000L,
        effects: List<Effect> = emptyList(),
        headTransition: Transition? = null,
        tailTransition: Transition? = null,
        speed: Float = 1f,
        speedCurve: SpeedCurve? = null,
        isReversed: Boolean = false,
        rotation: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        positionX: Float = 0f,
        positionY: Float = 0f,
        opacity: Float = 1f,
        masks: List<Mask> = emptyList(),
        blendMode: BlendMode = BlendMode.NORMAL,
        keyframes: List<Keyframe> = emptyList(),
        colorGrade: ColorGrade? = null,
        captions: List<Caption> = emptyList(),
    ): Clip = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = durationMs,
        timelineStartMs = startMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        effects = effects,
        headTransition = headTransition,
        tailTransition = tailTransition,
        speed = speed,
        speedCurve = speedCurve,
        isReversed = isReversed,
        rotation = rotation,
        scaleX = scaleX,
        scaleY = scaleY,
        positionX = positionX,
        positionY = positionY,
        opacity = opacity,
        masks = masks,
        blendMode = blendMode,
        keyframes = keyframes,
        colorGrade = colorGrade,
        captions = captions,
    )

    private fun track(vararg clips: Clip) = Track(
        id = "t1",
        type = TrackType.VIDEO,
        index = 0,
        clips = clips.toList()
    )

    @Test
    fun passThrough_plainClip() {
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(clip())), config)
        assertEquals(1, segments.size)
        assertFalse(segments[0].needsReEncode)
        assertEquals("pass-through", segments[0].reason)
    }

    @Test
    fun reEncode_withEnabledEffect() {
        val c = clip(effects = listOf(Effect(type = EffectType.BRIGHTNESS)))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("effects" in segments[0].reason)
    }

    @Test
    fun passThrough_disabledEffectIgnored() {
        val c = clip(effects = listOf(Effect(type = EffectType.BRIGHTNESS, enabled = false)))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertFalse(segments[0].needsReEncode)
    }

    @Test
    fun reEncode_withHeadTransition() {
        val c = clip(headTransition = Transition(TransitionType.DISSOLVE))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("transition" in segments[0].reason)
    }

    @Test
    fun reEncode_withTailTransition() {
        val c = clip(tailTransition = Transition(TransitionType.FADE_BLACK))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("transition" in segments[0].reason)
    }

    @Test
    fun reEncode_withSpeedChange() {
        val c = clip(speed = 2f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("speed" in segments[0].reason)
    }

    @Test
    fun reEncode_withSpeedCurve() {
        val c = clip(speedCurve = SpeedCurve(listOf()))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("speed" in segments[0].reason)
    }

    @Test
    fun reEncode_withReverse() {
        val c = clip(isReversed = true)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("reversed" in segments[0].reason)
    }

    @Test
    fun reEncode_withTransform() {
        val c = clip(rotation = 90f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("transform" in segments[0].reason)
    }

    @Test
    fun reEncode_withScale() {
        val c = clip(scaleX = 0.5f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("transform" in segments[0].reason)
    }

    @Test
    fun reEncode_withPosition() {
        val c = clip(positionX = 0.1f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("transform" in segments[0].reason)
    }

    @Test
    fun reEncode_withOpacity() {
        val c = clip(opacity = 0.5f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("opacity" in segments[0].reason)
    }

    @Test
    fun reEncode_withMask() {
        val c = clip(masks = listOf(Mask(type = MaskType.RECTANGLE)))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("masks" in segments[0].reason)
    }

    @Test
    fun reEncode_withBlendMode() {
        val c = clip(blendMode = BlendMode.MULTIPLY)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("blend" in segments[0].reason)
    }

    @Test
    fun reEncode_withKeyframes() {
        val c = clip(keyframes = listOf(Keyframe(timeOffsetMs = 0L, value = 1f, property = KeyframeProperty.OPACITY)))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("keyframes" in segments[0].reason)
    }

    @Test
    fun reEncode_withColorGrade() {
        val c = clip(colorGrade = ColorGrade(enabled = true))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("color grade" in segments[0].reason)
    }

    @Test
    fun passThrough_disabledColorGrade() {
        val c = clip(colorGrade = ColorGrade(enabled = false))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertFalse(segments[0].needsReEncode)
    }

    @Test
    fun reEncode_withCaptions() {
        val c = clip(captions = listOf(Caption(text = "hello", startTimeMs = 0L, endTimeMs = 1000L)))
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("captions" in segments[0].reason)
    }

    @Test
    fun reEncode_withOverlappingTextOverlay() {
        val c = clip(startMs = 0L, durationMs = 2000L)
        val overlay = TextOverlay(text = "test", startTimeMs = 500L, endTimeMs = 1500L)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config, listOf(overlay))
        assertTrue(segments[0].needsReEncode)
        assertTrue("text overlay" in segments[0].reason)
    }

    @Test
    fun passThrough_nonOverlappingTextOverlay() {
        val c = clip(startMs = 0L, durationMs = 1000L)
        val overlay = TextOverlay(text = "test", startTimeMs = 2000L, endTimeMs = 3000L)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config, listOf(overlay))
        assertFalse(segments[0].needsReEncode)
    }

    @Test
    fun hiddenTrack_excluded() {
        val t = Track(id = "t1", type = TrackType.VIDEO, index = 0, clips = listOf(clip()), isVisible = false)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(t), config)
        assertTrue(segments.isEmpty())
    }

    @Test
    fun multipleReasons_aggregated() {
        val c = clip(
            effects = listOf(Effect(type = EffectType.BRIGHTNESS)),
            headTransition = Transition(TransitionType.DISSOLVE),
            speed = 2f
        )
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c)), config)
        assertTrue(segments[0].needsReEncode)
        assertTrue("effects" in segments[0].reason)
        assertTrue("transition" in segments[0].reason)
        assertTrue("speed" in segments[0].reason)
    }

    @Test
    fun multipleClips_correctSegmentBounds() {
        val c1 = clip(id = "c1", startMs = 0L, durationMs = 1000L)
        val c2 = clip(id = "c2", startMs = 1000L, durationMs = 2000L, speed = 2f)
        val segments = SmartRenderEngine.analyzeTimeline(listOf(track(c1, c2)), config)
        assertEquals(2, segments.size)
        assertFalse(segments[0].needsReEncode)
        assertTrue(segments[1].needsReEncode)
        assertEquals(0L, segments[0].startMs)
        assertEquals(1000L, segments[0].endMs)
        assertEquals(1000L, segments[1].startMs)
    }
}
