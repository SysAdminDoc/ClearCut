package com.novacut.editor.engine

import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.SubtitleFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SubtitleExporterTest {

    @Test
    fun export_createsParentDirectoriesAndEscapesVttText() {
        val dir = Files.createTempDirectory("subtitle-export-").toFile()
        try {
            val outputFile = File(dir, "nested/captions.vtt")
            val caption = Caption(
                text = "M&M <draft> --> approved",
                startTimeMs = 0L,
                endTimeMs = 1_000L
            )

            val exported = SubtitleExporter.export(
                captions = listOf(caption),
                format = SubtitleFormat.VTT,
                outputFile = outputFile
            )

            assertTrue(exported)
            val content = outputFile.readText(Charsets.UTF_8)
            assertTrue(content.contains("WEBVTT"))
            assertTrue(content.contains("M&amp;M &lt;draft&gt; -&gt; approved"))
            assertFalse(content.contains("M&amp;M &lt;draft&gt; --&gt; approved"))
            assertFalse(content.contains("M&M <draft> --> approved"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun export_rejectsBlankOnlyCaptions() {
        val dir = Files.createTempDirectory("subtitle-export-blank-").toFile()
        try {
            val outputFile = File(dir, "blank.srt")
            val caption = Caption(
                text = "   ",
                startTimeMs = 0L,
                endTimeMs = 1_000L
            )

            assertFalse(
                SubtitleExporter.export(
                    captions = listOf(caption),
                    format = SubtitleFormat.SRT,
                    outputFile = outputFile
                )
            )
            assertFalse(outputFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun assExportPreservesUnicodeAndEmitsScriptAwareStyles() {
        val dir = Files.createTempDirectory("subtitle-export-unicode-").toFile()
        try {
            val outputFile = File(dir, "captions.ass")
            val captions = listOf(
                Caption(
                    text = "\u4F60\u597D\u4E16\u754C",
                    startTimeMs = 0L,
                    endTimeMs = 1_000L,
                    style = CaptionStyle(fontFamily = "serif", fontSize = 52f),
                ),
                Caption(
                    text = "\u0645\u0631\u062D\u0628\u0627 ClearCut 2026",
                    startTimeMs = 1_000L,
                    endTimeMs = 2_000L,
                    style = CaptionStyle(fontFamily = "custom:LatinOnly.ttf", fontSize = 44f),
                ),
            )

            assertTrue(SubtitleExporter.export(captions, SubtitleFormat.ASS, outputFile))
            val content = outputFile.readText(Charsets.UTF_8)
            assertTrue(content.contains("Noto Sans CJK SC,52"))
            assertTrue(content.contains("Noto Sans Arabic,44"))
            assertTrue(content.contains("\u4F60\u597D\u4E16\u754C"))
            assertTrue(content.contains("\u0645\u0631\u062D\u0628\u0627 ClearCut 2026"))
            assertFalse(content.contains('\u200E'))
            assertFalse(content.contains('\u200F'))
            assertTrue(content.contains("Dialogue: 0,0:00:00.00,0:00:01.00,Caption1"))
            assertTrue(content.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Caption2"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun assColorConvertsArgbToInvertedAlphaBbggrr() {
        assertTrue(SubtitleExporter.assColor(0xFF112233).equals("&H00332211"))
        assertTrue(SubtitleExporter.assColor(0x80112233).equals("&H7F332211"))
    }
}
