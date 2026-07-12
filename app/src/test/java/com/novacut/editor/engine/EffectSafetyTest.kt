package com.novacut.editor.engine

import com.novacut.editor.model.EffectCategory
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TransitionEasing
import com.novacut.editor.ui.editor.genericAddableEffectTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EffectSafetyTest {

    @Test
    fun `generic browser contains only effects that generic add can render`() {
        assertFalse(EffectType.TRACKED_MOSAIC in genericAddableEffectTypes)
        assertFalse(EffectType.BG_REMOVAL in genericAddableEffectTypes)
        assertFalse(EffectType.SPEED in genericAddableEffectTypes)
        assertFalse(EffectType.REVERSE in genericAddableEffectTypes)
        assertFalse(genericAddableEffectTypes.any { it.category == EffectCategory.SPEED })
        assertTrue(EffectType.CHROMA_KEY in genericAddableEffectTypes)
    }

    @Test
    fun `nonlinear outgoing transition easing stays inside the clip tail`() {
        val result = easedTransitionPresentationTimeSeconds(
            presentationTimeUs = 9_750_000L,
            durationUs = 500_000f,
            clipDurationUs = 10_000_000f,
            easing = TransitionEasing.EASE_IN
        )

        assertEquals(9.625f, result, 0.0001f)
        assertTrue(result >= 9.5f)
    }

    @Test
    fun `effect shader sources preserve alpha and avoid undefined edge math`() {
        val source = locate("app/src/main/java/com/novacut/editor/engine/ShaderEffect.kt").readText()
        assertTrue(source.contains("fragColor = vec4(c.rgb * opacity, c.a * opacity)"))
        assertTrue(source.contains("dist > 0.00001 && dist < 0.5"))
        assertFalse(source.contains("smoothstep(uRadius + 0.4, uRadius - 0.3"))
        assertFalse(source.contains("smoothstep(radius + 0.02, radius - 0.02"))
        assertFalse(source.contains("smoothstep(0.1, -0.1"))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
}
