package com.novacut.editor.engine

import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TimelineExportCoordinatorTest {

    private val coordinator = TimelineExportCoordinator(
        timelineExchangeEngine = TimelineExchangeEngine(null),
        timelineExchangeValidator = TimelineExchangeValidator(),
    )

    @Test
    fun exportValidatesSerializesAndAtomicallyWritesTheRequestedFormat() = runBlocking {
        val outputDirectory = Files.createTempDirectory("clearcut-timeline-export-").toFile()
        try {
            val result = coordinator.export(
                TimelineExportCoordinator.Request(
                    format = TimelineExportCoordinator.Format.OTIO,
                    tracks = listOf(videoTrack()),
                    textOverlays = emptyList(),
                    projectName = "Cut & review",
                    frameRate = 30,
                    outputDirectory = outputDirectory,
                )
            )

            assertFalse(result.blocked)
            assertTrue(result.succeeded)
            val file = result.outputFile ?: error("successful export did not return a file")
            assertEquals("Cut & review.otio", file.name)
            assertTrue(file.isFile)
            assertTrue(file.readText().contains("\"OTIO_SCHEMA\": \"Timeline.1\""))
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun blockedValidationDoesNotCreateAnOutputFile() = runBlocking {
        val outputDirectory = Files.createTempDirectory("clearcut-timeline-export-blocked-").toFile()
        try {
            val result = coordinator.export(
                TimelineExportCoordinator.Request(
                    format = TimelineExportCoordinator.Format.FCPXML,
                    tracks = listOf(videoTrack()),
                    textOverlays = listOf(
                        TextOverlay(text = "invalid", startTimeMs = 500L, endTimeMs = 100L)
                    ),
                    projectName = "blocked",
                    frameRate = 30,
                    outputDirectory = outputDirectory,
                )
            )

            assertTrue(result.blocked)
            assertFalse(result.succeeded)
            assertTrue(result.report.errors.isNotEmpty())
            assertFalse(outputDirectory.resolve("blocked.fcpxml").exists())
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun editDecisionJsonExportWritesPortableSchemaAndMarkers() = runBlocking {
        val outputDirectory = Files.createTempDirectory("clearcut-edit-decision-export-").toFile()
        try {
            val result = coordinator.export(
                TimelineExportCoordinator.Request(
                    format = TimelineExportCoordinator.Format.EDIT_DECISION_JSON,
                    tracks = listOf(videoTrack()),
                    textOverlays = emptyList(),
                    projectName = "Portable decisions",
                    frameRate = 30,
                    outputDirectory = outputDirectory,
                    timelineMarkers = listOf(TimelineMarker(id = "marker", timeMs = 250L)),
                    timebase = TimelineTimebase.NTSC_29_97,
                )
            )

            assertFalse(result.blocked)
            assertTrue(result.succeeded)
            val file = result.outputFile ?: error("successful export did not return a file")
            assertEquals("Portable decisions.clearcut-edl.json", file.name)
            assertTrue(file.readText().contains("\"schema\": \"com.clearcut.edit-decision\""))
            assertTrue(file.readText().contains("\"timeMs\": 250"))
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun otioAndFcpxmlUseTheProjectTimebaseNotTheRenderFrameRate() = runBlocking {
        val outputDirectory = Files.createTempDirectory("clearcut-timebase-export-").toFile()
        try {
            fun export(format: TimelineExportCoordinator.Format): String {
                val result = runBlocking {
                    coordinator.export(
                        TimelineExportCoordinator.Request(
                            format = format,
                            tracks = listOf(videoTrack()),
                            textOverlays = emptyList(),
                            projectName = "NTSC",
                            frameRate = 24,
                            outputDirectory = outputDirectory,
                            timebase = TimelineTimebase.NTSC_29_97,
                        )
                    )
                }
                return checkNotNull(result.outputFile) { "$format export did not write a file" }.readText()
            }

            val otio = export(TimelineExportCoordinator.Format.OTIO)
            assertTrue(otio.contains("\"rate\": 29.97002997002997"))
            assertFalse(otio.contains("\"rate\": 24"))
            assertTrue(export(TimelineExportCoordinator.Format.FCPXML).contains("frameDuration=\"1001/30000s\""))
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun edlExportWritesDropFrameTimecodeForAnNtscProject() = runBlocking {
        val outputDirectory = Files.createTempDirectory("clearcut-edl-export-").toFile()
        try {
            val result = coordinator.export(
                TimelineExportCoordinator.Request(
                    format = TimelineExportCoordinator.Format.EDL,
                    tracks = listOf(videoTrack()),
                    textOverlays = emptyList(),
                    projectName = "Conform",
                    frameRate = 30,
                    outputDirectory = outputDirectory,
                    timebase = TimelineTimebase.NTSC_29_97,
                )
            )

            assertTrue(result.succeeded)
            val file = result.outputFile ?: error("successful export did not return a file")
            assertEquals("Conform.edl", file.name)
            val lines = file.readText().lines()
            assertEquals("FCM: DROP FRAME", lines[1])
            assertEquals(
                "001  CLIP     V     C        00:00:00;00 00:00:01;00 00:00:00;00 00:00:01;00",
                lines[3],
            )
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    private fun videoTrack() = Track(
        id = "video",
        type = TrackType.VIDEO,
        index = 0,
        clips = listOf(
            Clip(
                id = "clip",
                sourceUri = TestUri("file:///media/clip.mp4", "file", "clip.mp4"),
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L,
                trimEndMs = 1_000L,
            )
        )
    )
}
