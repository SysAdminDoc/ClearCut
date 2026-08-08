package com.novacut.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCodecPolicyTest {
    @Test
    fun selectableExportCodecs_areLimitedToTheVerifiedAacPath() {
        assertEquals(listOf(AudioCodec.AAC), AudioCodec.supportedExportCodecs)
    }

    @Test
    fun legacyCodecValues_remainRepresentableButAreNotExportSupported() {
        assertTrue(AudioCodec.isSupportedForExport(AudioCodec.AAC))
        assertFalse(AudioCodec.isSupportedForExport(AudioCodec.OPUS))
        assertFalse(AudioCodec.isSupportedForExport(AudioCodec.FLAC))
        assertEquals(AudioCodec.OPUS, ExportConfig(audioCodec = AudioCodec.OPUS).audioCodec)
    }
}
