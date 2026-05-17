package com.novacut.editor.engine

import com.novacut.editor.engine.FrameExtractionPolicy.Backend
import com.novacut.editor.engine.FrameExtractionPolicy.Reason
import com.novacut.editor.engine.FrameExtractionPolicy.UseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FrameExtractionPolicyTest {
    @Test
    fun timelineAndContactSheetThumbnailsStayOnPlatformRetriever() {
        val timeline = FrameExtractionPolicy.chooseBackend(
            FrameExtractionPolicy.Request(UseCase.TIMELINE_THUMBNAIL_STRIP)
        )
        val contactSheet = FrameExtractionPolicy.chooseBackend(
            FrameExtractionPolicy.Request(UseCase.CONTACT_SHEET_THUMBNAIL)
        )

        assertEquals(Backend.PLATFORM_MEDIA_METADATA_RETRIEVER, timeline.backend)
        assertEquals(Reason.SMALL_CACHED_SDR_THUMBNAIL, timeline.reason)
        assertNull(timeline.requiredDependency)
        assertEquals(Backend.PLATFORM_MEDIA_METADATA_RETRIEVER, contactSheet.backend)
    }

    @Test
    fun hdrAndEffectAwareFramesUseNewInspectorFrameModule() {
        val hdr = FrameExtractionPolicy.chooseBackend(
            FrameExtractionPolicy.Request(UseCase.HDR_REVIEW_FRAME)
        )
        val effectAware = FrameExtractionPolicy.chooseBackend(
            FrameExtractionPolicy.Request(UseCase.TIMELINE_THUMBNAIL_STRIP, requiresEffectStack = true)
        )

        listOf(hdr, effectAware).forEach { decision ->
            assertEquals(Backend.MEDIA3_FRAME_EXTRACTOR, decision.backend)
            assertEquals(FrameExtractionPolicy.MEDIA3_INSPECTOR_FRAME_COORDINATE, decision.requiredDependency)
            assertEquals(FrameExtractionPolicy.MEDIA3_FRAME_EXTRACTOR_IMPORT, decision.requiredImport)
        }
    }

    @Test
    fun customDecoderSelectionForcesMedia3FrameExtractor() {
        val decision = FrameExtractionPolicy.chooseBackend(
            FrameExtractionPolicy.Request(
                useCase = UseCase.FREEZE_FRAME_EXPORT,
                requiresCustomDecoderSelection = true,
            )
        )

        assertEquals(Backend.MEDIA3_FRAME_EXTRACTOR, decision.backend)
        assertEquals(Reason.CUSTOM_DECODER_SELECTION, decision.reason)
    }

    @Test
    fun sourceTreeDoesNotUseDeprecatedMedia3FrameExtractorImports() {
        val sourceRoot = locateSourceRoot()
            ?: error("Could not locate app/src/main/java from the test working directory")
        val offendingFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "FrameExtractionPolicy.kt" }
            .filter { file ->
                val text = file.readText()
                FrameExtractionPolicy.OLD_INSPECTOR_FRAME_EXTRACTOR_IMPORT in text ||
                    FrameExtractionPolicy.OLD_TRANSFORMER_FRAME_EXTRACTOR_IMPORT in text
            }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Use ${FrameExtractionPolicy.MEDIA3_FRAME_EXTRACTOR_IMPORT} from " +
                "${FrameExtractionPolicy.MEDIA3_INSPECTOR_FRAME_COORDINATE}: $offendingFiles",
            offendingFiles.isEmpty()
        )
    }

    @Test
    fun media3InspectorFrameDependencyIsNotAddedUntilPolicyRequiresRuntimeUse() {
        val root = locateRepoRoot()
            ?: error("Could not locate repository root from the test working directory")
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        val build = File(root, "app/build.gradle.kts").readText()

        assertFalse("R6.10c audit should not add an unused inspector-frame catalog entry", "media3-inspector-frame" in catalog)
        assertFalse("R6.10c audit should not add an unused inspector-frame app dependency", "inspector.frame" in build)
    }

    private fun locateSourceRoot(): File? {
        val candidates = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    private fun locateRepoRoot(): File? {
        val candidates = listOf(
            File("."),
            File(".."),
            File("../.."),
        )
        return candidates.firstOrNull {
            File(it, "gradle/libs.versions.toml").isFile && File(it, "app/build.gradle.kts").isFile
        }
    }
}
