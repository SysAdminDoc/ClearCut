package com.novacut.editor.engine

import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewClipCoalescingTest {
    private val source = Uri.parse("file:///video.mp4")

    @Test
    fun adjacentPlainCutsOfTheSameSourceUseOnePreviewMediaItem() {
        val left = clip(id = "left", start = 0L, trimStart = 0L, trimEnd = 2_414L)
        val right = clip(id = "right", start = 2_414L, trimStart = 2_414L, trimEnd = 10_500L)

        assertTrue(canCoalesceAdjacentPreviewCuts(left, right))
        val result = coalesceAdjacentPreviewCuts(listOf(left, right))

        assertEquals(1, result.size)
        assertEquals(0L, result.single().trimStartMs)
        assertEquals(10_500L, result.single().trimEndMs)
        assertEquals(0L, result.single().timelineStartMs)
    }

    @Test
    fun visualDifferencesPreserveTheCutInThePreviewPlaylist() {
        val left = clip(id = "left", start = 0L, trimStart = 0L, trimEnd = 2_414L)
        val right = clip(
            id = "right",
            start = 2_414L,
            trimStart = 2_414L,
            trimEnd = 10_500L,
            effects = listOf(Effect(type = EffectType.BRIGHTNESS))
        )

        assertFalse(canCoalesceAdjacentPreviewCuts(left, right))
        assertEquals(2, coalesceAdjacentPreviewCuts(listOf(left, right)).size)
    }

    private fun clip(
        id: String,
        start: Long,
        trimStart: Long,
        trimEnd: Long,
        effects: List<Effect> = emptyList()
    ) = Clip(
        id = id,
        assetId = "asset",
        sourceUri = source,
        sourceDurationMs = 10_500L,
        timelineStartMs = start,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        effects = effects
    )
}
