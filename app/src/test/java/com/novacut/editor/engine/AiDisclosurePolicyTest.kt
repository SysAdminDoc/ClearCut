package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDisclosurePolicyTest {

    @Test
    fun `background removal is in-scope`() {
        assertEquals(AiDisclosurePolicy.Scope.IN_SCOPE, AiDisclosurePolicy.classify("remove_bg"))
        assertEquals(AiDisclosurePolicy.Scope.IN_SCOPE, AiDisclosurePolicy.classify("ai_background"))
        assertEquals(AiDisclosurePolicy.Scope.IN_SCOPE, AiDisclosurePolicy.classify("bg_replace"))
    }

    @Test
    fun `style transfer and object removal are in-scope`() {
        assertTrue(AiDisclosurePolicy.isInScope("ai_style_transfer"))
        assertTrue(AiDisclosurePolicy.isInScope("style_transfer"))
        assertTrue(AiDisclosurePolicy.isInScope("object_remove"))
        assertTrue(AiDisclosurePolicy.isInScope("inpaint"))
    }

    @Test
    fun `generative and deepfake-class tools are in-scope`() {
        assertTrue(AiDisclosurePolicy.isInScope("generative_video"))
        assertTrue(AiDisclosurePolicy.isInScope("generative_fill"))
        assertTrue(AiDisclosurePolicy.isInScope("voice_clone"))
        assertTrue(AiDisclosurePolicy.isInScope("lip_sync"))
    }

    @Test
    fun `upscale and frame interpolation are in-scope`() {
        assertTrue(AiDisclosurePolicy.isInScope("video_upscale"))
        assertTrue(AiDisclosurePolicy.isInScope("frame_interp"))
    }

    @Test
    fun `auto-edit and smart reframe are in-scope`() {
        assertTrue(AiDisclosurePolicy.isInScope("auto_edit"))
        assertTrue(AiDisclosurePolicy.isInScope("smart_reframe"))
    }

    @Test
    fun `captions are exempt`() {
        assertEquals(AiDisclosurePolicy.Scope.EXEMPT, AiDisclosurePolicy.classify("auto_captions"))
    }

    @Test
    fun `colour correction is exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("auto_color"))
    }

    @Test
    fun `noise reduction is exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("denoise"))
        assertFalse(AiDisclosurePolicy.isInScope("noise_reduction"))
    }

    @Test
    fun `scene detection and motion tracking are exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("scene_detect"))
        assertFalse(AiDisclosurePolicy.isInScope("track_motion"))
    }

    @Test
    fun `TTS and caption translation are exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("tts"))
        assertFalse(AiDisclosurePolicy.isInScope("caption_translate"))
    }

    @Test
    fun `editing assistants are exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("cut_assistant"))
        assertFalse(AiDisclosurePolicy.isInScope("filler_removal"))
        assertFalse(AiDisclosurePolicy.isInScope("beat_sync"))
        assertFalse(AiDisclosurePolicy.isInScope("stabilize"))
    }

    @Test
    fun `unknown tools default to exempt`() {
        assertFalse(AiDisclosurePolicy.isInScope("nonexistent_tool"))
    }

    @Test
    fun `EffectKind classification matches tool ID classification`() {
        assertEquals(
            AiDisclosurePolicy.Scope.IN_SCOPE,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD)
        )
        assertEquals(
            AiDisclosurePolicy.Scope.IN_SCOPE,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE)
        )
        assertEquals(
            AiDisclosurePolicy.Scope.IN_SCOPE,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL)
        )
        assertEquals(
            AiDisclosurePolicy.Scope.IN_SCOPE,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL)
        )
        assertEquals(
            AiDisclosurePolicy.Scope.EXEMPT,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.TTS_LOCAL)
        )
        assertEquals(
            AiDisclosurePolicy.Scope.EXEMPT,
            AiDisclosurePolicy.classify(AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL)
        )
    }

    @Test
    fun `requiresMachineReadableLabel returns true when any entry is in-scope`() {
        val entries = listOf(
            AiUsageLedger.Entry("c1", AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL, "mediapipe", 0, 1000, 1000)
        )
        assertTrue(AiDisclosurePolicy.requiresMachineReadableLabel(entries))
    }

    @Test
    fun `requiresMachineReadableLabel returns false when all entries are exempt`() {
        val entries = listOf(
            AiUsageLedger.Entry("c1", AiUsageLedger.EffectKind.TTS_LOCAL, "system", 0, 1000, 1000),
            AiUsageLedger.Entry("c2", AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL, "bergamot", 0, 1000, 1000)
        )
        assertFalse(AiDisclosurePolicy.requiresMachineReadableLabel(entries))
    }

    @Test
    fun `requiresMachineReadableLabel returns false for empty ledger`() {
        assertFalse(AiDisclosurePolicy.requiresMachineReadableLabel(emptyList()))
    }

    @Test
    fun `IPTC constants are well-formed URIs`() {
        assertTrue(AiDisclosurePolicy.IPTC_DIGIT_SOURCE_TYPE_COMPOSITE_WITH_AI.startsWith("http://cv.iptc.org/"))
        assertTrue(AiDisclosurePolicy.IPTC_DIGIT_SOURCE_TYPE_TRAINED_ALGORITHMIC.startsWith("http://cv.iptc.org/"))
    }
}
