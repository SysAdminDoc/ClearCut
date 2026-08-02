package com.novacut.editor.engine

import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDocumentApplicatorTest {

    @Test
    fun envelopeRoundTripsProjectMetadataAndEditState() {
        val document = ProjectDocumentApplicator.capture(
            project = Project(
                id = "project",
                name = "Portrait cut",
                aspectRatio = AspectRatio.RATIO_9_16,
                frameRate = 24,
                frameRateNumerator = 24_000,
                frameRateDenominator = 1_001,
                resolution = Resolution.UHD_4K,
                createdAt = 10L,
                updatedAt = 20L,
                durationMs = 2_500L,
                thumbnailUri = "content://thumb",
                templateId = "template",
                proxyEnabled = true,
                version = 7,
                notes = "Keep the opening beat",
                deletedAtEpochMs = 30L,
            ),
            state = AutoSaveState(
                projectId = "stale-id",
                timestamp = 40L,
                playheadMs = 1_200L,
                tracks = listOf(Track(type = TrackType.VIDEO, index = 0)),
                textOverlays = listOf(TextOverlay(id = "title", text = "Hello")),
            ),
        )

        val result = ProjectDocumentApplicator.read(ProjectDocumentApplicator.encode(document))

        assertTrue(result is ProjectDocumentReadResult.Loaded)
        val loaded = (result as ProjectDocumentReadResult.Loaded).document
        assertEquals(document.project, loaded.project)
        assertEquals(document.state.copy(projectId = "project"), loaded.state)
        assertFalse((result as ProjectDocumentReadResult.Loaded).migratedFromLegacyState)
    }

    @Test
    fun legacyStateIsWrappedAndMarkedAsMigrated() {
        val state = AutoSaveState(projectId = "legacy", playheadMs = 250L)

        val result = ProjectDocumentApplicator.read(state.serialize())

        assertTrue(result is ProjectDocumentReadResult.Loaded)
        val loaded = result as ProjectDocumentReadResult.Loaded
        assertEquals("legacy", loaded.document.project.id)
        assertEquals(state, loaded.document.state)
        assertTrue(loaded.migratedFromLegacyState)
        assertTrue(loaded.warnings.single().contains("Legacy"))
    }

    @Test
    fun unknownEnvelopeFieldsAreReportedAndIdMismatchIsNormalized() {
        val root = JSONObject(
            ProjectDocumentApplicator.encode(
                ProjectDocumentApplicator.capture(Project(id = "project"), AutoSaveState("project"))
            )
        )
        root.put("futureMetadata", JSONObject().put("owner", "editor"))
        root.getJSONObject("project").put("id", "wrong-project")

        val result = ProjectDocumentApplicator.read(root.toString())

        assertTrue(result is ProjectDocumentReadResult.Loaded)
        val loaded = result as ProjectDocumentReadResult.Loaded
        assertEquals("project", loaded.document.project.id)
        assertTrue(loaded.warnings.any { it.contains("futureMetadata") })
        assertTrue(loaded.warnings.any { it.contains("did not match") })
    }

    @Test
    fun futureDocumentAndStateVersionsAreRejectedBeforeDeserialization() {
        val root = JSONObject(
            ProjectDocumentApplicator.encode(
                ProjectDocumentApplicator.capture(Project(id = "project"), AutoSaveState("project"))
            )
        )
        root.put("documentVersion", ProjectDocumentApplicator.FORMAT_VERSION + 1)

        val futureDocument = ProjectDocumentApplicator.read(root.toString())
        assertTrue(futureDocument is ProjectDocumentReadResult.FutureSchema)

        root.put("documentVersion", ProjectDocumentApplicator.FORMAT_VERSION)
        root.getJSONObject("state").put("schemaVersion", AutoSaveState.FORMAT_VERSION + 1)
        val futureState = ProjectDocumentApplicator.read(root.toString())
        assertTrue(futureState is ProjectDocumentReadResult.FutureSchema)
    }

    @Test
    fun rekeyKeepsAllEditDomainsAndUsesTheNewProjectIdentity() {
        val original = ProjectDocumentApplicator.capture(
            Project(id = "old", name = "Original"),
            AutoSaveState("old", playheadMs = 999L, textOverlays = listOf(TextOverlay(text = "Title")))
        )

        val copied = ProjectDocumentApplicator.rekey(original, Project(id = "new", name = "Copy"))

        assertEquals("new", copied.project.id)
        assertEquals("new", copied.state.projectId)
        assertEquals(original.state.playheadMs, copied.state.playheadMs)
        assertEquals(original.state.textOverlays, copied.state.textOverlays)
    }

    @Test
    fun timelineExchangeResultsUseTheSameDocumentBoundary() {
        val project = Project(id = "timeline-project", name = "Imported timeline")
        val result = TimelineExchangeEngine.ExchangeResult(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0)),
            textOverlays = listOf(TextOverlay(text = "Imported title")),
            warnings = listOf("Effect was not supported"),
        )

        val document = result.toProjectDocument(project, playheadMs = 450L)

        assertEquals(project, document.project)
        assertEquals(project.id, document.state.projectId)
        assertEquals(result.tracks, document.state.tracks)
        assertEquals(result.textOverlays, document.state.textOverlays)
        assertEquals(450L, document.state.playheadMs)
    }
}
