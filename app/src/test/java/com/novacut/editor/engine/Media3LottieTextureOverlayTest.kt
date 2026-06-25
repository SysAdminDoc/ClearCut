package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class Media3LottieTextureOverlayTest {
    @Test
    fun chooseLottieOverlayBackend_usesMedia3ForNonHdrFiniteTitleWindow() {
        val decision = chooseLottieOverlayBackend(
            preserveHdr = false,
            overlayDurationUs = 2_000_000L,
            compositionDurationUs = 3_000_000L
        )

        assertEquals(LottieOverlayBackend.MEDIA3_LOTTIE, decision.backend)
    }

    @Test
    fun chooseLottieOverlayBackend_keepsCustomPathForHdrExports() {
        val decision = chooseLottieOverlayBackend(
            preserveHdr = true,
            overlayDurationUs = 2_000_000L,
            compositionDurationUs = 3_000_000L
        )

        assertEquals(LottieOverlayBackend.CLEARCUT_SHADER, decision.backend)
    }

    @Test
    fun chooseLottieOverlayBackend_keepsCustomPathWhenOfficialLoopingWouldChangeOutput() {
        val decision = chooseLottieOverlayBackend(
            preserveHdr = false,
            overlayDurationUs = 5_000_000L,
            compositionDurationUs = 3_000_000L
        )

        assertEquals(LottieOverlayBackend.CLEARCUT_SHADER, decision.backend)
    }

    @Test
    fun lottieOverlayAlphaScale_gatesOutsideWindow() {
        assertEquals(0f, lottieOverlayAlphaScale(999L, 1_000L, 4_000L), 0.0001f)
        assertEquals(1f, lottieOverlayAlphaScale(1_000L, 1_000L, 4_000L), 0.0001f)
        assertEquals(1f, lottieOverlayAlphaScale(5_000L, 1_000L, 4_000L), 0.0001f)
        assertEquals(0f, lottieOverlayAlphaScale(5_001L, 1_000L, 4_000L), 0.0001f)
    }

    @Test
    fun lottieScaleToFrame_usesSafePositiveDimensions() {
        val (scaleX, scaleY) = lottieScaleToFrame(
            compositionWidth = 960,
            compositionHeight = 540,
            frameWidth = 1920,
            frameHeight = 1080
        )

        assertEquals(2f, scaleX, 0.0001f)
        assertEquals(2f, scaleY, 0.0001f)
    }
}
