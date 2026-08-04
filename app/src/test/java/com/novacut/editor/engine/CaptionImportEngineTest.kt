package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionImportEngineTest {

    @Test
    fun srtPreviewParsesEncodingLanguageDurationAndOverlaps() {
        val input = """
            1
            00:00:01,000 --> 00:00:03,000
            The first line
            continues here

            2
            00:00:02,500 --> 00:00:04,250
            Overlapping line
        """.trimIndent()

        val preview = CaptionImportEngine.analyze(
            input.toByteArray(Charsets.UTF_8),
            CaptionImportEngine.Format.SRT,
        )

        assertTrue(preview.isValid)
        assertEquals(CaptionImportEngine.Encoding.UTF_8, preview.encoding)
        assertEquals(2, preview.cues.size)
        assertEquals("The first line\ncontinues here", preview.cues.first().text)
        assertEquals(4_250L, preview.durationMs)
        assertEquals(1, preview.overlapCount)
        assertEquals("en", preview.language)
        assertTrue(preview.languageConfidence > 0f)
    }

    @Test
    fun webVttAcceptsShortTimestampsCueSettingsAndLanguageMetadata() {
        val input = """
            WEBVTT
            Language: es

            cue-1
            00:01.000 --> 00:03.500 align:start position:10%
            Hola <b>mundo</b>
        """.trimIndent()

        val preview = CaptionImportEngine.analyze(
            input.toByteArray(Charsets.UTF_8),
            CaptionImportEngine.Format.WEBVTT,
        )

        assertTrue(preview.isValid)
        assertEquals("es", preview.language)
        assertEquals(950f / 1000f, preview.languageConfidence, 0.001f)
        assertEquals(1_000L, preview.cues.single().startTimeMs)
        assertEquals(3_500L, preview.cues.single().endTimeMs)
        assertEquals("Hola mundo", preview.cues.single().text)
    }

    @Test
    fun malformedBinaryAndExcessiveInputsAreRejectedWithoutUsableCues() {
        val malformed = CaptionImportEngine.analyze(
            "1\n00:00:01,000 --> 00:00:00,900\nbackwards".toByteArray(),
            CaptionImportEngine.Format.SRT,
        )
        val binary = CaptionImportEngine.analyze(
            byteArrayOf(0x57, 0x45, 0x42, 0x00, 0x56, 0x54, 0x54),
            CaptionImportEngine.Format.WEBVTT,
        )
        val excessive = CaptionImportEngine.analyze(
            buildString {
                repeat(CaptionImportEngine.MAX_CUES + 1) { index ->
                    append(index + 1)
                    append("\n00:00:00,000 --> 00:00:01,000\ncue\n\n")
                }
            }.toByteArray(),
            CaptionImportEngine.Format.SRT,
        )

        assertEquals(CaptionImportEngine.Failure.INVALID_CUES, malformed.failure)
        assertEquals(CaptionImportEngine.Failure.BINARY, binary.failure)
        assertEquals(CaptionImportEngine.Failure.EXCESSIVE_CUES, excessive.failure)
        assertFalse(malformed.isValid)
        assertFalse(binary.isValid)
        assertFalse(excessive.isValid)
    }

    @Test
    fun oversizedInputIsRejectedBeforeDecoding() {
        val preview = CaptionImportEngine.analyze(
            ByteArray((CaptionImportEngine.MAX_BYTES + 1L).toInt()),
            CaptionImportEngine.Format.SRT,
        )

        assertEquals(CaptionImportEngine.Failure.OVERSIZED, preview.failure)
        assertFalse(preview.isValid)
    }

    @Test
    fun utf16AndOffsetMappingClipCuesAndReportSkippedWork() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\nhello"
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val preview = CaptionImportEngine.analyze(withBom, CaptionImportEngine.Format.SRT)

        val mapping = CaptionImportEngine.mapToClip(
            preview = preview,
            clipDurationMs = 2_000L,
            targetOffsetMs = 2_000L,
        )

        assertTrue(preview.isValid)
        assertEquals(CaptionImportEngine.Encoding.UTF_16_LE, preview.encoding)
        assertEquals(1, mapping.captions.size)
        assertEquals("hello", mapping.captions.single().text)
        assertEquals(0L, mapping.captions.single().startTimeMs)
        assertEquals(1_000L, mapping.captions.single().endTimeMs)
        assertEquals(1, mapping.clippedCueCount)
        assertEquals(0, mapping.skippedCueCount)
    }
}
