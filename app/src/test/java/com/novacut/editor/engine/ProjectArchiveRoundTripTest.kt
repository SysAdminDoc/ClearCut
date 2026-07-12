package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Watermark
import com.novacut.editor.model.WatermarkPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProjectArchiveRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun customFontLutAndWatermarkSurviveAfterOriginalAssetsAreRemoved() = runBlocking {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        val media = temp.newFile("source.mp4").apply { writeBytes("media".toByteArray()) }
        val lut = temp.newFile("look.cube").apply { writeText("LUT_3D_SIZE 2\n0 0 0\n1 1 1\n") }
        val watermark = temp.newFile("brand.png").apply { writeBytes("watermark-bytes".toByteArray()) }
        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val font = File(fontsDir, "portable-font.ttf").apply { writeBytes("font-bytes".toByteArray()) }
        val fontFamily = FontRegistry.CUSTOM_PREFIX + font.name
        val state = AutoSaveState(
            projectId = "portable-project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            sourceUri = Uri.fromFile(media),
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L,
                            colorGrade = ColorGrade(lutPath = lut.absolutePath),
                            captions = listOf(
                                Caption(
                                    text = "Portable",
                                    startTimeMs = 0L,
                                    endTimeMs = 900L,
                                    style = CaptionStyle(fontFamily = fontFamily)
                                )
                            )
                        )
                    )
                )
            ),
            exportWatermark = Watermark(
                sourceUri = Uri.fromFile(watermark),
                position = WatermarkPosition.TOP_LEFT,
                opacity = 0.65f,
                scalePercent = 19
            )
        )
        val archive = File(temp.root, "portable.clearcut")

        assertTrue(ProjectArchive.exportArchive(context, state, archive))
        assertTrue(media.delete())
        assertTrue(lut.delete())
        assertTrue(watermark.delete())
        assertTrue(font.delete())

        val result = ProjectArchive.importArchiveWithReport(
            context = context,
            archiveUri = Uri.fromFile(archive),
            targetDir = File(temp.root, "clean-import")
        )

        assertNotNull(result.state)
        val restored = requireNotNull(result.state)
        assertTrue(result.report.warnings.isEmpty())
        val restoredClip = restored.tracks.single().clips.single()
        val restoredLut = File(requireNotNull(restoredClip.colorGrade?.lutPath))
        assertNotEquals(lut.absolutePath, restoredLut.absolutePath)
        assertTrue(restoredLut.isFile)
        assertTrue(restoredLut.readText().startsWith("LUT_3D_SIZE 2"))

        val restoredFamily = restoredClip.captions.single().style.fontFamily
        assertNotEquals(fontFamily, restoredFamily)
        assertTrue(restoredFamily.startsWith(FontRegistry.CUSTOM_PREFIX))
        val restoredFont = File(fontsDir, restoredFamily.removePrefix(FontRegistry.CUSTOM_PREFIX))
        assertEquals("font-bytes", restoredFont.readText())

        val restoredWatermark = requireNotNull(restored.exportWatermark)
        assertNotEquals(Uri.fromFile(watermark).toString(), restoredWatermark.sourceUri.toString())
        assertEquals("watermark-bytes", File(requireNotNull(restoredWatermark.sourceUri.path)).readText())
        assertEquals(WatermarkPosition.TOP_LEFT, restoredWatermark.position)
        assertEquals(0.65f, restoredWatermark.opacity)
        assertEquals(19, restoredWatermark.scalePercent)
    }
}
