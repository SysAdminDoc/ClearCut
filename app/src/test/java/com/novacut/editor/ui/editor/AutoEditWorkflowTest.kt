package com.novacut.editor.ui.editor

import android.net.TestUri
import com.novacut.editor.ai.AutoEditBeatSupport
import com.novacut.editor.ai.AutoEditIntent
import com.novacut.editor.ai.AutoEditResult
import com.novacut.editor.ai.AutoEditScoreComponents
import com.novacut.editor.ai.AutoEditSegment
import com.novacut.editor.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEditWorkflowTest {
    @Test
    fun `apply builder creates deterministic plain excerpts without source ownership`() {
        val source = sourceClip().copy(
            speed = 2f,
            volume = 0.25f,
            isReversed = true,
            opacity = 0.4f,
            rotation = 90f,
            linkedClipId = "audio",
            groupId = "group"
        )
        val proposal = proposal(source)

        val excerpt = buildAutoEditExcerptClips(listOf(source), proposal) { "new-id" }.single()

        assertEquals("new-id", excerpt.id)
        assertEquals(2_000L, excerpt.trimStartMs)
        assertEquals(5_000L, excerpt.trimEndMs)
        assertEquals(0L, excerpt.timelineStartMs)
        assertEquals(1f, excerpt.speed)
        assertEquals(1f, excerpt.volume)
        assertFalse(excerpt.isReversed)
        assertEquals(1f, excerpt.opacity)
        assertEquals(0f, excerpt.rotation)
        assertNull(excerpt.linkedClipId)
        assertNull(excerpt.groupId)
        assertTrue(excerpt.effects.isEmpty())
        assertTrue(excerpt.keyframes.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply builder rejects a source edited after proposal generation`() {
        val source = sourceClip()
        val proposal = proposal(source)

        buildAutoEditExcerptClips(
            sourceClips = listOf(source.copy(trimEndMs = 8_000L)),
            proposal = proposal
        )
    }

    private fun sourceClip() = Clip(
        id = "source-id",
        sourceUri = TestUri(
            raw = "content://media/video/1",
            schemeValue = "content",
            segment = "1"
        ),
        sourceDurationMs = 10_000L,
        timelineStartMs = 0L,
        trimStartMs = 1_000L,
        trimEndMs = 9_000L
    )

    private fun proposal(source: Clip) = AutoEditResult(
        segments = listOf(
            AutoEditSegment(
                clipIndex = 0,
                clipId = source.id,
                clipFingerprint = autoEditClipFingerprint(source),
                trimStartMs = 2_000L,
                trimEndMs = 5_000L,
                timelineStartMs = 0L,
                timelineEndMs = 3_000L,
                scoreComponents = AutoEditScoreComponents(0.8f, 0.7f, 0.6f, 0f),
                score = 0.7f,
                confidence = 0.7f,
                rationale = listOf("Strong visual quality"),
                beatAligned = false
            )
        ),
        intent = AutoEditIntent.HIGHLIGHT_REEL,
        requestedDurationMs = 3_000L,
        plannedDurationMs = 3_000L,
        confidence = 0.7f,
        beatSupport = AutoEditBeatSupport.NOT_REQUESTED
    )
}
