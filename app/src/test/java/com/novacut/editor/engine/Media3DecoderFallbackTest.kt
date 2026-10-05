package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3DecoderFallbackTest {

    @Test
    fun theFirstDecoderStartingIsAnExactRunWithNoNote() {
        assertNull(Media3DecoderFallback.fallbackNote("c2.qti.avc.decoder", "video", emptyList()))
    }

    @Test
    fun aDecoderThatStartedAfterOthersFailedIsDisclosed() {
        val note = Media3DecoderFallback.fallbackNote(
            "c2.android.avc.decoder",
            "video",
            listOf("c2.qti.avc.decoder: ERROR_CODE_DECODER_INIT_FAILED"),
        )

        assertEquals(
            "Media3 video decoder fallback applied: c2.android.avc.decoder started after 1 decoder(s) failed " +
                "(c2.qti.avc.decoder: ERROR_CODE_DECODER_INIT_FAILED).",
            note,
        )
    }

    @Test
    fun anAudioFallbackSaysAudioAndAnUnknownTrackSaysNeither() {
        val failures = listOf("c2.vendor.aac.decoder: ERROR_CODE_DECODER_INIT_FAILED")

        assertEquals(
            "Media3 audio decoder fallback applied: c2.android.aac.decoder started after 1 decoder(s) failed " +
                "(c2.vendor.aac.decoder: ERROR_CODE_DECODER_INIT_FAILED).",
            Media3DecoderFallback.fallbackNote("c2.android.aac.decoder", "audio", failures),
        )
        assertEquals(
            "Media3 decoder fallback applied: c2.android.aac.decoder started after 1 decoder(s) failed " +
                "(c2.vendor.aac.decoder: ERROR_CODE_DECODER_INIT_FAILED).",
            Media3DecoderFallback.fallbackNote("c2.android.aac.decoder", null, failures),
        )
    }
}
