package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R6.11 — APV (Advanced Professional Video) ingest probe.
 *
 * The probe itself reads `MediaCodecList.REGULAR_CODECS`, which is an Android
 * runtime call and returns nothing on a plain JVM. These tests cover the
 * surface-level invariants we can guarantee without a device:
 *
 *  - The APV MIME constant is pinned at the value Android 16 declares.
 *  - The ApvSupport value type is correctly shaped and its convenience
 *    accessor agrees with hasDecoder.
 *
 * On-device behavior (probe returns hasDecoder=true on a Galaxy S26 Ultra,
 * false elsewhere) is verified manually + via a device smoke test rather
 * than mocked. See ROADMAP R6.11.
 */
class EncoderCapabilityProbeApvTest {

    @Test
    fun apvMimeTypeIsPinned() {
        // Android 16 publishes APV under "video/apv" in MediaFormat. Locking
        // the constant here catches any rename in our own codebase.
        assertEquals("video/apv", EncoderCapabilityProbe.MIME_APV)
    }

    @Test
    fun apvSupportNoDecoder_isNotUsable() {
        val s = EncoderCapabilityProbe.ApvSupport(
            hasDecoder = false,
            isHardwareDecoder = false,
            decoderNames = emptyList(),
        )
        assertEquals(false, s.isUsable)
    }

    @Test
    fun apvSupportWithDecoder_isUsable() {
        val s = EncoderCapabilityProbe.ApvSupport(
            hasDecoder = true,
            isHardwareDecoder = true,
            decoderNames = listOf("c2.samsung.apv.decoder"),
        )
        assertEquals(true, s.isUsable)
    }

    @Test
    fun apvSupportIsValueObject() {
        val a = EncoderCapabilityProbe.ApvSupport(true, true, listOf("c2.samsung.apv.decoder"))
        val b = EncoderCapabilityProbe.ApvSupport(true, true, listOf("c2.samsung.apv.decoder"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun probeApvIngest_returnsEmptyOnJvm() {
        // On a plain JVM MediaCodecList throws and the probe catches it,
        // returning hasDecoder=false. Verify the contract.
        val s = EncoderCapabilityProbe.probeApvIngest()
        assertEquals(false, s.hasDecoder)
        assertEquals(false, s.isHardwareDecoder)
        assertEquals(emptyList<String>(), s.decoderNames)
    }
}
