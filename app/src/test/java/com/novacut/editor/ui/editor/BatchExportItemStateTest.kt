package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportSourceRange
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchExportItemStateTest {

    private val keepHdr = ProjectColorPolicy.KEEP_HDR
    private val project = EditorState().copyExport { export ->
        export.copy(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = keepHdr),
            state = ExportState.COMPLETE,
            progress = 1f,
        )
    }

    @Test
    fun aPresetItemRendersWithTheProjectsColorAndKeepsItsOwnSettings() {
        val item = BatchExportItem(
            config = ExportConfig(resolution = Resolution.HD_720P, codec = VideoCodec.H264),
            outputName = "preset",
        )

        val state = project.forBatchItem(item)

        assertEquals(keepHdr, state.exportConfig.colorPolicy)
        assertEquals(Resolution.HD_720P, state.exportConfig.resolution)
        assertEquals(VideoCodec.H264, state.exportConfig.codec)
        assertEquals(ExportState.IDLE, state.exportState)
        assertEquals(0f, state.exportProgress)
    }

    @Test
    fun aSourceCutItemFollowsTheProjectsColorToo() {
        val range = BatchExportSourceRange(
            clipId = "clip-1",
            sourceUri = FakeUri,
            sourceDurationMs = 10_000L,
            startMs = 2_000L,
            endMs = 5_000L,
            displayName = "Cut",
        )
        val item = BatchExportItem(config = ExportConfig(), outputName = "cut", sourceRange = range)

        val state = project.forBatchItem(item)

        assertEquals(keepHdr, state.exportConfig.colorPolicy)
        assertNull(state.exportConfig.timelineRange)
        assertEquals(3_000L, state.totalDurationMs)
        assertEquals(listOf(2_000L), state.tracks.single().clips.map { it.trimStartMs })
    }
}
