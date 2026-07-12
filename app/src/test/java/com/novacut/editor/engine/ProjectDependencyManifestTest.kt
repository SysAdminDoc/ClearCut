package com.novacut.editor.engine

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.TestUri
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.StoryboardCard
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDependencyManifestTest {

    @Test
    fun collectsNestedProjectAndEditorDependenciesOnce() {
        val overlayUri = TestUri("content://overlay/logo.png", "content", "logo.png")
        val storyboardUri = TestUri("content://storyboard/take.mp4", "content", "take.mp4")
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        clip(
                            uri = FakeUri,
                            lutPath = "/looks/hero.cube",
                            captions = listOf(
                                Caption(
                                    text = "Caption",
                                    startTimeMs = 0L,
                                    endTimeMs = 500L,
                                    style = CaptionStyle(fontFamily = "custom:brand.ttf")
                                )
                            ),
                            children = listOf(clip(SecondFakeUri as android.net.Uri))
                        ),
                        clip(FakeUri)
                    )
                )
            ),
            textOverlays = listOf(TextOverlay(text = "Title", fontFamily = "custom:brand.ttf")),
            imageOverlays = listOf(
                ImageOverlay(sourceUri = overlayUri, startTimeMs = 0L, endTimeMs = 1000L)
            ),
            storyboardCards = listOf(
                StoryboardCard(ordinal = 0, shotText = "Take", mediaUri = storyboardUri)
            )
        )
        val inputs = ProjectDependencyEditorInputs(
            watermarkReference = "content://brand/watermark.png",
            customFontReferencesByFamily = mapOf("custom:brand.ttf" to "/fonts/brand.ttf"),
            modelDependencies = listOf(
                ProjectDependencyRequest(
                    kind = ProjectDependencyKind.MODEL,
                    reference = "/models/segmenter.tflite"
                )
            )
        )

        val manifest = ProjectDependencyManifest.collect(state, inputs) { ProjectDependencyStatus.AVAILABLE }

        assertEquals(8, manifest.dependencies.size)
        assertEquals(
            listOf(
                ProjectDependencyKind.MEDIA,
                ProjectDependencyKind.LUT,
                ProjectDependencyKind.MEDIA,
                ProjectDependencyKind.MEDIA,
                ProjectDependencyKind.MEDIA,
                ProjectDependencyKind.CUSTOM_FONT,
                ProjectDependencyKind.WATERMARK,
                ProjectDependencyKind.MODEL
            ),
            manifest.dependencies.map { it.request.kind }
        )
        assertEquals(7, manifest.archiveDependencies.size)
        assertTrue(manifest.canProceed)
    }

    @Test
    fun missingRequestedDependencyBlocksUnlessFallbackIsExplicit() {
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip(FakeUri))))
        )

        val blocked = ProjectDependencyManifest.collect(state) { ProjectDependencyStatus.MISSING }
        val allowed = ProjectDependencyManifest.collect(
            state = state,
            editorInputs = ProjectDependencyEditorInputs(
                fallbackAllowedReferences = setOf(FakeUri.toString())
            )
        ) { ProjectDependencyStatus.MISSING }

        assertFalse(blocked.canProceed)
        assertEquals(1, blocked.blockingDependencies.size)
        assertTrue(allowed.canProceed)
        assertTrue(allowed.blockingDependencies.isEmpty())
    }

    @Test
    fun probesEveryDependencyKindAndHonorsArchivePolicies() {
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(clip(FakeUri, lutPath = "/looks/missing.cube"))
                )
            ),
            textOverlays = listOf(TextOverlay(text = "Title", fontFamily = "custom:title.otf"))
        )
        val probedKinds = mutableListOf<ProjectDependencyKind>()

        val manifest = ProjectDependencyManifest.collect(
            state = state,
            editorInputs = ProjectDependencyEditorInputs(
                watermarkReference = "content://brand/logo.png",
                customFontReferencesByFamily = mapOf("custom:title.otf" to "/fonts/title.otf"),
                modelDependencies = listOf(
                    ProjectDependencyRequest(ProjectDependencyKind.MODEL, "/models/model.onnx")
                )
            )
        ) { request ->
            probedKinds += request.kind
            if (request.kind == ProjectDependencyKind.LUT) {
                ProjectDependencyStatus.UNREADABLE
            } else {
                ProjectDependencyStatus.AVAILABLE
            }
        }

        assertEquals(ProjectDependencyKind.entries.toSet(), probedKinds.toSet())
        assertFalse(manifest.canProceed)
        assertEquals(ProjectDependencyKind.LUT, manifest.blockingDependencies.single().request.kind)
        assertFalse(
            manifest.dependencies.single { it.request.kind == ProjectDependencyKind.MODEL }
                .shouldIncludeInArchive
        )
    }

    @Test
    fun enabledBackgroundRemovalRequiresRuntimeModelRecursively() {
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        clip(
                            uri = FakeUri,
                            children = listOf(
                                clip(SecondFakeUri as android.net.Uri).copy(
                                    effects = listOf(Effect(type = EffectType.BG_REMOVAL))
                                )
                            )
                        )
                    )
                )
            )
        )

        val manifest = ProjectDependencyManifest.collect(state) { request ->
            if (request.kind == ProjectDependencyKind.MODEL) {
                ProjectDependencyStatus.MISSING
            } else {
                ProjectDependencyStatus.AVAILABLE
            }
        }

        val model = manifest.dependencies.single { it.request.kind == ProjectDependencyKind.MODEL }
        assertEquals(SEGMENTATION_MODEL_DEPENDENCY, model.request.reference)
        assertFalse(model.shouldIncludeInArchive)
        assertTrue(model.blocksRequestedOperation)
    }

    private fun clip(
        uri: android.net.Uri,
        lutPath: String? = null,
        captions: List<Caption> = emptyList(),
        children: List<Clip> = emptyList()
    ) = Clip(
        sourceUri = uri,
        sourceDurationMs = 1000L,
        timelineStartMs = 0L,
        colorGrade = lutPath?.let { ColorGrade(lutPath = it) },
        captions = captions,
        isCompound = children.isNotEmpty(),
        compoundClips = children
    )
}
