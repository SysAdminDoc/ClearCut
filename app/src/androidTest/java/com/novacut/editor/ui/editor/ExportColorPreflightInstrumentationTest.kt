package com.novacut.editor.ui.editor

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Clips that reach export without a color inspection (imported before it existed, or a
 * batch source cut) are inspected there, so the color plan sees the HLG it will render.
 */
@RunWith(AndroidJUnit4::class)
class ExportColorPreflightInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val workDir = File(target.cacheDir, "color-inspection-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun uninspectedClipsAreInspectedBeforeTheyExport() = runBlocking {
        val hlg = File(workDir, "hlg.mp4")
        instrumentation.context.assets.open("hlg-hevc-main10.mp4").use { input ->
            hlg.outputStream().use(input::copyTo)
        }
        val missing = Uri.fromFile(File(workDir, "gone.mp4"))
        val nested = clip("nested", Uri.fromFile(hlg))
        val state = EditorState(
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        clip("hlg", Uri.fromFile(hlg)),
                        clip("compound", missing).copy(compoundClips = listOf(nested)),
                    ),
                ),
            ),
        )

        val inspected = state.withSourceColorInspected(target).tracks.single().clips

        assertEquals("HLG", inspected[0].sourceColorMetadata.colorTransfer)
        assertTrue(inspected[1].sourceColorMetadata.isInspected)
        assertNull(inspected[1].sourceColorMetadata.colorTransfer)
        assertEquals("HLG", inspected[1].compoundClips.single().sourceColorMetadata.colorTransfer)
    }

    @Test
    fun audioOnlyExportsSkipTheInspection() = runBlocking {
        val state = EditorState(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip("a", Uri.fromFile(workDir))))),
        ).copyExport { it.copy(config = ExportConfig(exportAudioOnly = true)) }

        assertSame(state, state.withSourceColorInspected(target))
    }

    private fun clip(id: String, uri: Uri) = Clip(
        id = id,
        sourceUri = uri,
        sourceDurationMs = 1_000L,
        timelineStartMs = 0L,
        trimStartMs = 0L,
        trimEndMs = 1_000L,
    )
}
