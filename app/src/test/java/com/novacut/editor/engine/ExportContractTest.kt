package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportContractTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val exactVideo = ExportObservation(
        valid = true,
        container = "MP4",
        videoMimeType = "video/avc",
        audioMimeType = "audio/mp4a-latm",
        width = 1920,
        height = 1080,
        frameRate = 30f,
        durationMs = 10_000L,
        fastStart = true,
    )

    @Test
    fun eachOutputKindAsksForWhatItsExportSettingsSay() {
        val video = requestedExportContract(ExportConfig(), "mp4", expectedDurationMs = 10_000L)
        assertEquals(ExportOutputKind.VIDEO, video.kind)
        assertEquals("MP4", video.requestedContainer)
        assertEquals("video/avc", video.requestedVideoMimeType)
        assertEquals("audio/mp4a-latm", video.requestedAudioMimeType)
        assertEquals(1920, video.requestedWidth)
        assertEquals(1080, video.requestedHeight)
        assertEquals(30, video.requestedFrameRate)
        assertEquals(10_000L, video.expectedDurationMs)
        assertFalse(video.requestedFastStart)
        assertNull(video.observed)
        assertEquals(ExportContractDisposition.UNVERIFIED, video.disposition)

        val youtube = requestedExportContract(ExportConfig().withPlatformPreset(PlatformPreset.YOUTUBE_4K), "mp4", 10_000L)
        assertTrue(youtube.requestedFastStart)
        assertEquals("video/hevc", youtube.requestedVideoMimeType)
        assertEquals(3840, youtube.requestedWidth)

        // Transparent exports always go out as VP9 in WebM, whatever codec is picked.
        val transparent = requestedExportContract(ExportConfig(transparentBackground = true, codec = VideoCodec.HEVC), "webm", 10_000L)
        assertEquals("WEBM", transparent.requestedContainer)
        assertEquals(VideoCodec.VP9.mimeType, transparent.requestedVideoMimeType)

        val audio = requestedExportContract(ExportConfig(exportAudioOnly = true), "m4a", 10_000L)
        assertEquals(ExportOutputKind.AUDIO, audio.kind)
        assertEquals("MP4", audio.requestedContainer)
        assertNull(audio.requestedVideoMimeType)
        assertNull(audio.requestedWidth)
        assertEquals("audio/mp4a-latm", audio.requestedAudioMimeType)
        assertEquals(ExportOutputKind.AUDIO, requestedExportContract(ExportConfig(exportStemsOnly = true), "m4a", 1L).kind)

        val gif = requestedExportContract(ExportConfig(exportAsGif = true, gifMaxWidth = 320), "gif", 10_000L)
        assertEquals(ExportOutputKind.GIF, gif.kind)
        assertEquals("GIF", gif.requestedContainer)
        assertEquals(320, gif.requestedMaxWidth)
        assertNull(gif.expectedDurationMs)

        val sheet = requestedExportContract(ExportConfig(exportAsContactSheet = true), "png", 10_000L)
        assertEquals(ExportOutputKind.IMAGE, sheet.kind)
        assertEquals("PNG", sheet.requestedContainer)
        assertNull(sheet.requestedAudioMimeType)
    }

    @Test
    fun dispositionsFollowWhatTheFileHoldsAndWhatTheRunNoted() {
        val request = requestedExportContract(ExportConfig(), "mp4", 10_000L)

        assertEquals(ExportContractDisposition.EXACT, request.evaluate(exactVideo).disposition)
        assertTrue(request.evaluate(exactVideo).mismatches.isEmpty())

        val fellBack = request.copy(fallbackSummary = "Hardware encoder failed; used software encoder.")
        assertEquals(ExportContractDisposition.ACCEPTED_FALLBACK, fellBack.evaluate(exactVideo).disposition)

        val accepted = fellBack.copy(degradationSummary = "User accepted 1 export warning(s).")
        assertEquals(ExportContractDisposition.DEGRADED, accepted.evaluate(exactVideo).disposition)

        // Nobody noted a fallback, yet the file isn't what was asked for.
        val hevc = requestedExportContract(ExportConfig(codec = VideoCodec.HEVC), "mp4", 10_000L)
        val silentSwap = hevc.evaluate(exactVideo)
        assertEquals(ExportContractDisposition.DEGRADED, silentSwap.disposition)
        assertEquals(listOf(ExportContractField.VIDEO_CODEC), silentSwap.mismatches)
        val explained = hevc.copy(fallbackSummary = "HEVC unsupported; encoded H.264.").evaluate(exactVideo)
        assertEquals(ExportContractDisposition.ACCEPTED_FALLBACK, explained.disposition)
        assertEquals(listOf(ExportContractField.VIDEO_CODEC), explained.mismatches)

        val broken = request.evaluate(ExportObservation(valid = false, failure = "No video track"))
        assertEquals(ExportContractDisposition.REJECTED, broken.disposition)
        assertTrue(broken.mismatches.isEmpty())
        // A run whose fallback produced an unusable file is still rejected.
        assertEquals(ExportContractDisposition.REJECTED, accepted.evaluate(ExportObservation(valid = false)).disposition)
    }

    @Test
    fun mismatchesCoverEveryRequestedProperty() {
        val request = requestedExportContract(ExportConfig().withPlatformPreset(PlatformPreset.YOUTUBE_1080), "mp4", 10_000L)
        val off = request.evaluate(
            exactVideo.copy(
                container = "WEBM",
                videoMimeType = "video/hevc",
                audioMimeType = "audio/opus",
                width = 1280,
                height = 720,
                frameRate = 24f,
                fastStart = false,
            )
        )
        assertEquals(
            listOf(
                ExportContractField.CONTAINER,
                ExportContractField.VIDEO_CODEC,
                ExportContractField.AUDIO_CODEC,
                ExportContractField.SIZE,
                ExportContractField.FRAME_RATE,
                ExportContractField.FAST_START,
            ),
            off.mismatches,
        )

        // Within half a frame per second, a silent timeline, and properties that
        // couldn't be read are not differences.
        val close = request.evaluate(
            exactVideo.copy(frameRate = 29.97f, audioMimeType = null, container = null, fastStart = null)
        )
        assertTrue(close.mismatches.toString(), close.mismatches.isEmpty())
        assertEquals(listOf(ExportContractField.FRAME_RATE), request.evaluate(exactVideo.copy(frameRate = 29.4f)).mismatches)

        val gif = requestedExportContract(ExportConfig(exportAsGif = true, gifMaxWidth = 480), "gif", 0L)
        assertTrue(gif.evaluate(ExportObservation(valid = true, container = "GIF", width = 480, height = 270)).mismatches.isEmpty())
        assertEquals(
            listOf(ExportContractField.SIZE),
            gif.evaluate(ExportObservation(valid = true, container = "GIF", width = 481, height = 270)).mismatches,
        )
        val sheet = requestedExportContract(ExportConfig(exportAsContactSheet = true), "png", 0L)
        assertEquals(
            listOf(ExportContractField.CONTAINER),
            sheet.evaluate(ExportObservation(valid = true, container = "JPEG", width = 10, height = 10)).mismatches,
        )
    }

    @Test
    fun theStoreReadsFinishedFilesBackAndKeepsTheResult() {
        val output = temp.newFile("final.mp4").apply { writeBytes(ByteArray(64)) }
        val entry = buildExportHistoryEntry(
            projectId = "project",
            projectName = "Road Trip",
            status = ExportHistoryStatus.COMPLETE,
            startedAtEpochMs = 100L,
            finishedAtEpochMs = 200L,
            outputFile = output,
            config = ExportConfig(codec = VideoCodec.HEVC),
            timelineDurationMs = 10_000L,
            fallbackSummary = "  HEVC unsupported; encoded H.264.  ",
            degradationSummary = " ",
        )
        assertEquals(ExportContractDisposition.UNVERIFIED, entry.contract?.disposition)
        assertEquals("HEVC unsupported; encoded H.264.", entry.contract?.fallbackSummary)
        assertNull(entry.contract?.degradationSummary)

        val inspected = mutableListOf<File>()
        val historyFile = temp.root.resolve("history.json")
        ExportHistoryStore(historyFile, inspectOutput = { file, contract ->
            inspected += file
            assertEquals("video/hevc", contract.requestedVideoMimeType)
            exactVideo
        }).append(entry)

        val restored = ExportHistoryStore(historyFile).read().single().contract!!
        assertEquals(listOf(output), inspected)
        assertEquals(ExportContractDisposition.ACCEPTED_FALLBACK, restored.disposition)
        assertEquals(listOf(ExportContractField.VIDEO_CODEC), restored.mismatches)
        assertEquals(exactVideo, restored.observed)
        assertEquals("video/hevc", restored.requestedVideoMimeType)
        assertEquals(1920, restored.requestedWidth)
        assertEquals(10_000L, restored.expectedDurationMs)

        // Appending it again doesn't read the file a second time or replace what was read.
        val rereads = mutableListOf<File>()
        val reappended = ExportHistoryStore(historyFile, inspectOutput = { file, _ ->
            rereads += file
            exactVideo.copy(videoMimeType = "video/avc", width = 640, height = 360)
        }).append(ExportHistoryStore(historyFile).read().single())
        assertEquals(emptyList<File>(), rereads)
        assertEquals(exactVideo, reappended.single().contract?.observed)
    }

    @Test
    fun runsWithoutAFinishedFileAreNeverMarkedVerified() {
        val output = temp.newFile("partial.mp4").apply { writeBytes(ByteArray(64)) }
        fun run(status: ExportHistoryStatus, file: File?) = buildExportHistoryEntry(
            projectId = status.name,
            projectName = "Road Trip",
            status = status,
            startedAtEpochMs = 100L,
            finishedAtEpochMs = 200L,
            outputFile = file,
            config = ExportConfig(),
            timelineDurationMs = 10_000L,
        )
        assertNull(run(ExportHistoryStatus.FAILED, output).contract)
        assertNull(run(ExportHistoryStatus.CANCELLED, output).contract)
        assertNull(run(ExportHistoryStatus.COMPLETE, null).contract)

        val historyFile = temp.root.resolve("history.json")
        val moved = run(ExportHistoryStatus.COMPLETE, output)
        output.delete()
        val unreadable = run(ExportHistoryStatus.COMPLETE, temp.newFile("unreadable.mp4").apply { writeBytes(ByteArray(8)) })
            .copy(id = "unreadable")
        val throwing = run(ExportHistoryStatus.COMPLETE, temp.newFile("throws.mp4").apply { writeBytes(ByteArray(8)) })
            .copy(id = "throws")
        val store = ExportHistoryStore(historyFile, inspectOutput = { file, _ ->
            if (file.name == "throws.mp4") error("extractor crashed") else null
        })
        store.append(moved)
        store.append(unreadable)
        store.append(throwing)

        store.read().forEach { entry ->
            assertEquals(entry.id, ExportContractDisposition.UNVERIFIED, entry.contract?.disposition)
            assertNull(entry.id, entry.contract?.observed)
        }
    }

    @Test
    fun olderHistoryAndTamperedContractsReadAsUnverified() {
        val historyFile = temp.root.resolve("history.json")
        historyFile.writeText(
            """
            [
              {"id":"legacy","projectId":"p","projectName":"Old","status":"COMPLETE","startedAtEpochMs":1,
               "finishedAtEpochMs":2,"elapsedMs":1,"outputPath":null,"outputName":"old.mp4","outputBytes":10,
               "codecLabel":"H.264","resolutionLabel":"1080p","frameRate":30,"timelineDurationMs":1000},
              {"id":"claims-exact","projectId":"p","projectName":"Odd","status":"COMPLETE","startedAtEpochMs":1,
               "finishedAtEpochMs":2,"elapsedMs":1,"codecLabel":"H.264","resolutionLabel":"1080p","frameRate":30,
               "timelineDurationMs":1000,
               "contract":{"kind":"VIDEO","requestedVideoMimeType":"video/avc","mismatches":["SIZE"],"disposition":"EXACT"}},
              {"id":"unknown-kind","projectId":"p","projectName":"Odd","status":"COMPLETE","startedAtEpochMs":1,
               "finishedAtEpochMs":2,"elapsedMs":1,"codecLabel":"H.264","resolutionLabel":"1080p","frameRate":30,
               "timelineDurationMs":1000,"contract":{"kind":"HOLOGRAM","disposition":"EXACT"}}
            ]
            """.trimIndent()
        )

        val entries = ExportHistoryStore(historyFile).read().associateBy { it.id }
        assertEquals(3, entries.size)
        assertNull(entries.getValue("legacy").contract)
        val claimed = entries.getValue("claims-exact").contract!!
        assertEquals(ExportContractDisposition.UNVERIFIED, claimed.disposition)
        assertNull(claimed.observed)
        assertTrue(claimed.mismatches.isEmpty())
        assertNull(entries.getValue("unknown-kind").contract)
    }

    @Test
    fun imageHeadersGiveFormatAndSizeWithoutDecoding() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
            0, 0, 0x07, 0x80.toByte(), 0, 0, 0x04, 0x38,
            8, 6, 0, 0, 0,
        )
        assertEquals(ImageHeader("PNG", 1920, 1080), ImageHeader.parse(png))

        val gif = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0xE0.toByte(), 0x01, 0x0E, 0x01, 0, 0, 0)
        assertEquals(ImageHeader("GIF", 480, 270), ImageHeader.parse(gif))

        // APP0 and a Huffman table come before the frame header; DHT shares the
        // SOF marker range and must be skipped.
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0, 16, 0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
            0xFF.toByte(), 0xC4.toByte(), 0, 7, 0, 0x01, 0x02, 0x03, 0x04,
            0xFF.toByte(), 0xC0.toByte(), 0, 17, 8, 0x02, 0xD0.toByte(), 0x05, 0x00, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1,
        )
        assertEquals(ImageHeader("JPEG", 1280, 720), ImageHeader.parse(jpeg))

        assertNull(ImageHeader.parse(ByteArray(0)))
        assertNull(ImageHeader.parse("not an image at all".toByteArray()))
        assertNull(ImageHeader.parse(png.copyOf(20)))
        val zeroWidthGif = "GIF87a".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0x0E, 0x01)
        assertNull(ImageHeader.parse(zeroWidthGif))

        val file = temp.newFile("sheet.png").apply { writeBytes(png + ByteArray(200_000)) }
        assertEquals(ImageHeader("PNG", 1920, 1080), ImageHeader.read(file))
        val observation = inspectExportOutput(file, requestedExportContract(ExportConfig(exportAsContactSheet = true), "png", 0L))
        assertEquals(ExportObservation(valid = true, container = "PNG", width = 1920, height = 1080), observation)
        val notImage = temp.newFile("sheet2.png").apply { writeText("plain text") }
        assertFalse(inspectExportOutput(notImage, requestedExportContract(ExportConfig(exportAsGif = true), "gif", 0L)).valid)
    }
}
