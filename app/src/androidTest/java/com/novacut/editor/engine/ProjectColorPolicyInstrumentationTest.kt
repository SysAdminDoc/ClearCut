package com.novacut.editor.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.ui.editor.EditorState
import com.novacut.editor.ui.editor.copyExport
import com.novacut.editor.ui.editor.exportColorPreflight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device contract for the project color policy on a real HLG HEVC Main 10 clip: an SDR
 * project tone-maps it by whatever route the device has, and Keep HDR either delivers HLG
 * or is refused before render on devices whose GPU or encoder can't keep HDR. The file's
 * own color tags are the evidence.
 */
@RunWith(AndroidJUnit4::class)
class ProjectColorPolicyInstrumentationTest {
    @Test
    fun hlgClipFollowsTheProjectPolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "hlg-source-${System.nanoTime()}.mp4")
        val outputs = mutableListOf<File>()
        instrumentation.context.assets.open("hlg-hevc-main10.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        // Emulator HEVC decoders can't decode Main 10, so there's nothing to render there.
        val firstFrame = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source.absolutePath)
            retriever.getFrameAtTime(0L)
        }
        assumeTrue("this device can't decode HEVC Main 10", firstFrame != null)
        val engine = buildVideoEngine(context)
        try {
            val metadata = MediaImportEngine(context).inspectSourceColor(Uri.fromFile(source))
            assertEquals("HLG", metadata.colorTransfer)
            val track = Track(
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(
                    Clip(
                        sourceUri = Uri.fromFile(source),
                        sourceDurationMs = 2_000L,
                        timelineStartMs = 0L,
                        trimStartMs = 0L,
                        trimEndMs = 2_000L,
                        sourceColorMetadata = metadata,
                    )
                ),
            )
            val sdrConfig = ExportConfig(resolution = Resolution.SD_480P, codec = VideoCodec.HEVC, allowStreamCopy = false)
            val keepConfig = sdrConfig.copy(colorPolicy = ProjectColorPolicy.KEEP_HDR)
            assertEquals(DeliveredColor.SDR, requestedExportColor(sdrConfig, listOf(track)))
            assertEquals(DeliveredColor.HLG, requestedExportColor(keepConfig, listOf(track)))

            val sdrOutput = export(engine, context, track, sdrConfig).also(outputs::add)
            assertEquals(DeliveredColor.SDR, ExportOutputVerifier.observedColor(sdrOutput))

            val keepState = EditorState(tracks = listOf(track)).copyExport { it.copy(config = keepConfig) }
            val preflight = runBlocking { exportColorPreflight(context, keepState) }
            val device = HdrDeviceSupport.current
            val encoderKeepsHdr = EncoderCapabilityProbe.queryHdrProfiles(VideoCodec.HEVC).canPreserveHdr
            val canKeep = device == HdrDeviceSupport.OPEN_GL && encoderKeepsHdr
            Log.i(TAG, "device=$device encoderKeepsHdr=$encoderKeepsHdr preflight blockers=${preflight.blockers}")
            if (canKeep) {
                assertTrue(preflight.blockers.toString(), preflight.blockers.isEmpty())
                val hdrOutput = export(engine, context, track, keepConfig).also(outputs::add)
                assertEquals(DeliveredColor.HLG, ExportOutputVerifier.observedColor(hdrOutput))
            } else {
                assertTrue("Keep HDR was not refused before render", preflight.blockers.isNotEmpty())
            }
        } finally {
            engine.release()
            source.delete()
            outputs.forEach(File::delete)
        }
    }

    private fun export(engine: VideoEngine, context: Context, track: Track, config: ExportConfig): File {
        val output = File(context.cacheDir, "color-policy-output-${System.nanoTime()}.mp4")
        var completed = false
        var error: Exception? = null
        engine.resetExportState()
        runBlocking {
            withTimeout(120_000L) {
                engine.export(
                    tracks = listOf(track),
                    config = config,
                    outputFile = output,
                    onComplete = { completed = true },
                    onError = { error = it },
                )
            }
        }
        assertNull("export reported an error: ${error?.message}", error)
        assertTrue("export did not complete", completed)
        return output
    }

    private fun buildVideoEngine(context: Context): VideoEngine {
        val scope = CoroutineScope(SupervisorJob())
        val segmentation = SegmentationEngine(
            context,
            ModelDownloadManager(context),
            MediaPipeUsageGate(
                consentVersionFlow = flowOf(0),
                persistConsentVersion = {},
                scope = scope,
            ),
        )
        return VideoEngine(
            context = context,
            segmentationEngine = segmentation,
            streamCopyEngine = StreamCopyExportEngine(StreamCopyMuxer(context)),
            ffmpegEngine = FFmpegEngine(context),
            fontRegistry = FontRegistry(context),
            memoryTrimRegistry = MemoryTrimRegistry(),
            productHealthLedger = ProductHealthLedger(context),
        )
    }

    private companion object {
        const val TAG = "ColorPolicyDeviceTest"
    }
}
