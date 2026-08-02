package com.novacut.editor.ai

import com.novacut.editor.engine.SubtitleExporter
import com.novacut.editor.model.Caption
import com.novacut.editor.model.SubtitleFormat
import com.novacut.editor.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Energy segmentation measures *when* audio was loud. It has no idea what was said.
 * It used to fabricate "[Speech segment N]" and hand that to the project, the
 * renderer, the exporter, and the SRT writer as if it were a transcript. These tests
 * hold the line at every one of those exits.
 */
class TimingOnlyCaptionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun timingOnlyCaptionsNeverBecomeTextOverlays() {
        val overlays = AiFeatures.captionsToOverlays(
            listOf(
                CaptionEntry(startMs = 0, endMs = 900, text = "", source = CaptionSource.TIMING_ONLY),
                CaptionEntry(startMs = 1_200, endMs = 2_000, text = "", source = CaptionSource.TIMING_ONLY),
            )
        )

        assertTrue(
            "a measurement with no words must not become a rendered, exported overlay",
            overlays.isEmpty()
        )
    }

    @Test
    fun theEnergyPathIsDeclaredTimingOnlyAndCarriesNoText() {
        val entry = CaptionEntry(startMs = 0, endMs = 900, text = "", source = CaptionSource.TIMING_ONLY)

        assertEquals(CaptionSource.TIMING_ONLY, entry.source)
        assertTrue(entry.text.isEmpty())
        assertEquals(
            "a caption is transcribed unless the producer says otherwise",
            CaptionSource.TRANSCRIBED,
            CaptionEntry(startMs = 0, endMs = 1, text = "hi").source
        )
    }

    @Test
    fun transcribedCaptionsKeepTheirWordsAndBlankOnesAreDropped() {
        val overlays = AiFeatures.captionsToOverlays(
            listOf(
                CaptionEntry(startMs = 0, endMs = 900, text = "hello there", source = CaptionSource.TRANSCRIBED),
                CaptionEntry(startMs = 1_000, endMs = 1_500, text = "  ", source = CaptionSource.TRANSCRIBED),
            )
        )

        assertEquals(1, overlays.size)
        assertEquals("hello there", overlays.single().text)
    }

    @Test
    fun theTextOverlayModelRefusesToHoldAnEmptyCaption() {
        val failure = runCatching {
            TextOverlay(text = "", startTimeMs = 0, endTimeMs = 900)
        }.exceptionOrNull()

        assertTrue(
            "an empty overlay is the shape a fabricated caption would have to take",
            failure is IllegalArgumentException
        )
    }

    @Test
    fun subtitleExportOmitsEmptySlots() {
        val output = File(temporaryFolder.newFolder(), "captions.srt")
        val wrote = SubtitleExporter.export(
            captions = listOf(
                Caption(startTimeMs = 0, endTimeMs = 900, text = ""),
                Caption(startTimeMs = 1_000, endTimeMs = 1_800, text = "real words"),
            ),
            format = SubtitleFormat.SRT,
            outputFile = output,
        )

        assertTrue(wrote)
        val srt = output.readText()
        assertTrue(srt.contains("real words"))
        assertEquals("only the filled cue may be written", 1, Regex("-->").findAll(srt).count())
    }

    @Test
    fun theFabricatedPlaceholderIsGoneFromProductionSource() {
        val sourceRoot = listOf(File("app/src/main"), File("../app/src/main")).first { it.isDirectory }
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.readText().contains("[Speech segment") }
            .map { it.path }
            .toList()

        assertTrue(
            "the placeholder caption text must not exist in production source: $offenders",
            offenders.isEmpty()
        )
    }
}
