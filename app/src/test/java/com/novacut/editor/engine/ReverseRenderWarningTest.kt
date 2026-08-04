package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseRenderWarningTest {

    @Test
    fun unavailableBackendExplainsTheForwardFallback() {
        assertEquals(
            "Clip clip-7 is reversed, but reverse rendering is unavailable on this device. " +
                "It would be exported playing forward.",
            reverseRenderFallbackMessage(
                clipId = "clip-7",
                clipDurationMs = 4_000L,
                reverseRenderAvailable = false,
            ),
        )
    }

    @Test
    fun overLimitClipExplainsWhyItUsesForwardPlayback() {
        assertEquals(
            "Clip clip-8 is reversed and 301s long, over the 300s reverse limit. " +
                "It would be exported playing forward.",
            reverseRenderFallbackMessage(
                clipId = "clip-8",
                clipDurationMs = 301_000L,
                reverseRenderAvailable = true,
            ),
        )
    }

    @Test
    fun renderableClipHasNoFallbackWarning() {
        assertNull(
            reverseRenderFallbackMessage(
                clipId = "clip-9",
                clipDurationMs = MAX_REVERSE_CLIP_DURATION_MS,
                reverseRenderAvailable = true,
            )
        )
    }
}
