package com.novacut.editor.engine

import com.novacut.editor.model.Caption
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
}
