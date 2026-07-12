package com.novacut.editor.ui.editor

import android.net.Uri
import com.novacut.editor.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewClipDisplayPolicyTest {

    private val first = Clip(
        id = "first",
        sourceUri = Uri.parse("file:///first.mp4"),
        timelineStartMs = 0L,
        sourceDurationMs = 1_000L,
        trimEndMs = 1_000L
    )
    private val last = Clip(
        id = "last",
        sourceUri = Uri.parse("file:///last.mp4"),
        timelineStartMs = 1_000L,
        sourceDurationMs = 1_000L,
        trimEndMs = 1_000L
    )

    @Test
    fun exactTimelineEndKeepsTheLastDecodedClipVisible() {
        assertEquals(last, previewClipForDisplay(listOf(first, last), 2_000L, 2_000L))
    }

    @Test
    fun realGapStillReturnsNoClip() {
        assertNull(previewClipForDisplay(listOf(first), 1_500L, 2_000L))
    }

    @Test
    fun activePositionUsesHalfOpenClipRanges() {
        assertEquals(first, previewClipForDisplay(listOf(first, last), 999L, 2_000L))
        assertEquals(last, previewClipForDisplay(listOf(first, last), 1_000L, 2_000L))
    }
}
