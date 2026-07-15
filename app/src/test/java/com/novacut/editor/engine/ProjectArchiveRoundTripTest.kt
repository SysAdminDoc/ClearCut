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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ProjectArchiveRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun resetStorageStats() {
        ShadowStatFs.reset()
    }

    @Test
    fun previewReadsBoundedMetadataWithoutExtractingPayloads() = runBlocking {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        val media = temp.newFile("preview-source.mp4").apply { writeBytes("preview-media".toByteArray()) }
        val state = AutoSaveState(
            projectId = "preview-project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            sourceUri = Uri.fromFile(media),
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L
                        )
                    )
                )
            )
        )
        val archive = File(temp.root, "preview.clearcut")
        assertTrue(ProjectArchive.exportArchive(context, state, archive))
        val stagingDir = File(context.cacheDir, "project-archive-staging")
        val filesBefore = stagingDir.listFiles().orEmpty().map { it.name }.sorted()

        val preview = ProjectArchive.previewArchive(context, Uri.fromFile(archive))

        assertTrue(preview.valid)
        assertEquals(1, preview.packagedMedia)
        assertEquals(1, preview.report.mediaTotal)
        assertEquals(filesBefore, stagingDir.listFiles().orEmpty().map { it.name }.sorted())
        assertFalse(File(temp.root, "preview-import").exists())
    }

    @Test
    fun rejectedCompressionBombLeavesNoDestinationOrImportStage() = runBlocking {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        val archive = File(temp.root, "compression-bomb.clearcut")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(AutoSaveState(projectId = "bomb").serialize().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media/repeated.bin"))
            repeat(1_024) { zip.write(ByteArray(1_024)) }
            zip.closeEntry()
        }
        val target = File(temp.root, "rejected-import")
        registerStorageCapacity(context, temp.root)

        val result = ProjectArchive.importArchiveWithReport(
            context = context,
            archiveUri = Uri.fromFile(archive),
            targetDir = target
        )

        assertNull(result.state)
        assertTrue(result.errorMessage, result.errorMessage.orEmpty().contains("compression-ratio"))
        assertFalse(target.exists())
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.startsWith(".rejected-import.import-") })
        assertTrue(
            File(context.cacheDir, "project-archive-staging")
                .listFiles()
                .orEmpty()
                .none { it.name.startsWith("project-import-") }
        )
    }

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
        registerStorageCapacity(context, temp.root)

        val result = ProjectArchive.importArchiveWithReport(
            context = context,
            archiveUri = Uri.fromFile(archive),
            targetDir = File(temp.root, "clean-import")
        )

        assertNotNull(result.errorMessage, result.state)
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.startsWith(".clean-import.import-") })
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

    private fun registerStorageCapacity(context: Context, destinationParent: File) {
        val blockCount = 2_000_000
        val freeBlocks = 1_800_000
        ShadowStatFs.registerStats(destinationParent, blockCount, freeBlocks, freeBlocks)
        ShadowStatFs.registerStats(
            File(context.cacheDir, "project-archive-staging"),
            blockCount,
            freeBlocks,
            freeBlocks
        )
    }
}
