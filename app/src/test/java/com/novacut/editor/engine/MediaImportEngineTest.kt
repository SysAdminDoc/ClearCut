package com.novacut.editor.engine

import android.media.MediaFormat
import android.os.Build
import com.novacut.editor.model.SourceHdrFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImportEngineTest {

    @Test
    fun classifyHdr10PlusPrefersDynamicMetadata() {
        val formats = MediaImportEngine.classifyHdrFormats(
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            colorTransfer = MediaFormat.COLOR_TRANSFER_ST2084,
            colorStandard = MediaFormat.COLOR_STANDARD_BT2020,
            hasHdrStaticInfo = true,
            hasHdr10PlusInfo = true,
            codecString = null
        )

        assertEquals(setOf(SourceHdrFormat.HDR10_PLUS), formats)
    }

    @Test
    fun classifyHlgFromTransferCurve() {
        val formats = MediaImportEngine.classifyHdrFormats(
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            colorTransfer = MediaFormat.COLOR_TRANSFER_HLG,
            colorStandard = MediaFormat.COLOR_STANDARD_BT2020,
            hasHdrStaticInfo = false,
            hasHdr10PlusInfo = false,
            codecString = null
        )

        assertEquals(setOf(SourceHdrFormat.HLG), formats)
    }

    @Test
    fun classifyDolbyVisionFromMimeType() {
        val formats = MediaImportEngine.classifyHdrFormats(
            mimeType = MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
            colorTransfer = MediaFormat.COLOR_TRANSFER_ST2084,
            colorStandard = MediaFormat.COLOR_STANDARD_BT2020,
            hasHdrStaticInfo = true,
            hasHdr10PlusInfo = false,
            codecString = "dvav.10.09"
        )

        assertTrue(SourceHdrFormat.DOLBY_VISION in formats)
        assertTrue(SourceHdrFormat.HDR10 !in formats)
    }

    @Test
    fun classifyPreAndroid16GainMapAsSdrBaseUltraHdr() {
        val format = MediaImportEngine.classifyGainMapFormat(
            sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            gainmapDirection = 1
        )

        assertEquals(SourceHdrFormat.ULTRA_HDR_GAIN_MAP, format)
    }

    @Test
    fun classifyAndroid16HdrBaseGainMapDirection() {
        val format = MediaImportEngine.classifyGainMapFormat(
            sdkInt = Build.VERSION_CODES.BAKLAVA,
            gainmapDirection = 1
        )

        assertEquals(SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP, format)
    }

    @Test
    fun classifyAndroid16SdrBaseGainMapDirection() {
        val format = MediaImportEngine.classifyGainMapFormat(
            sdkInt = Build.VERSION_CODES.BAKLAVA,
            gainmapDirection = 0
        )

        assertEquals(SourceHdrFormat.ULTRA_HDR_GAIN_MAP, format)
    }
}
