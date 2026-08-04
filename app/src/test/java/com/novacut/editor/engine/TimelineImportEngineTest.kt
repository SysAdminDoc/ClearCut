package com.novacut.editor.engine

import android.net.TestUri
import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineImportEngineTest {

    private val exchange = TimelineExchangeEngine(null)
    private val importer = TimelineImportEngine(
        context = android.content.ContextWrapper(null),
        timelineExchangeEngine = exchange,
        timelineExchangeValidator = TimelineExchangeValidator(),
        mediaRelinkProbe = MediaRelinkProbe(android.content.ContextWrapper(null)),
    )

    @Test
    fun previewProducesFidelityReportAndCommitBuildsOneCanonicalDocument() = runBlocking {
        val source = testUri("file:///media/source.mp4")
        val clip = Clip(
            id = "clip",
            sourceUri = source,
            sourceDurationMs = 2_000L,
            timelineStartMs = 0L,
            trimEndMs = 2_000L,
        )
        val json = exchange.exportToOtio(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))),
            textOverlays = emptyList(),
            projectName = "Import preview",
            timebase = TimelineTimebase(30),
        )

        val result = importer.importText(
            raw = json,
            format = TimelineImportEngine.Format.OTIO,
            probeMedia = false,
            uriParser = ::testUri,
        )

        assertTrue(result.readyForAtomicCommit)
        assertNotNull(result.fidelityReport)
        assertTrue(result.fidelityReport?.canProceed == true)
        val document = importer.commit(Project(id = "target", name = "Imported"), result, playheadMs = 125L)
        assertNotNull(document)
        assertEquals("target", document?.state?.projectId)
        assertEquals(125L, document?.state?.playheadMs)
        assertEquals("clip", document?.state?.tracks?.single()?.clips?.single()?.id)
    }

    @Test
    fun unresolvedMediaBlocksCommitUntilRelinked() = runBlocking {
        val hostileSource = testUri("javascript:alert('x')")
        val clip = Clip(
            id = "unresolved",
            sourceUri = hostileSource,
            sourceDurationMs = 1_000L,
            timelineStartMs = 0L,
            trimEndMs = 1_000L,
        )
        val json = exchange.exportToOtio(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))),
            textOverlays = emptyList(),
            projectName = "Relink",
            timebase = TimelineTimebase(30),
        )

        val unresolved = importer.importText(
            raw = json,
            format = TimelineImportEngine.Format.OTIO,
            probeMedia = false,
            uriParser = ::testUri,
        )
        assertFalse(unresolved.readyForAtomicCommit)
        assertEquals(listOf("javascript:alert('x')"), unresolved.unresolvedMediaUris)
        assertTrue(unresolved.fidelityReport?.errors?.isNotEmpty() == true)
        assertEquals(null, importer.commit(Project(id = "target"), unresolved))

        val relinked = importer.importText(
            raw = json,
            format = TimelineImportEngine.Format.OTIO,
            mediaRelocation = mapOf("javascript:alert('x')" to testUri("content://media/source")),
            probeMedia = false,
            uriParser = ::testUri,
        )
        assertTrue(relinked.readyForAtomicCommit)
        assertTrue(relinked.unresolvedMediaUris.isEmpty())
        assertEquals(
            "content://media/source",
            relinked.exchangeResult?.tracks?.single()?.clips?.single()?.sourceUri?.toString(),
        )
    }

    @Test
    fun editDecisionPreviewIsNonMutatingAndCommitMapsMarkersAndCaptions() = runBlocking {
        val source = testUri("file:///media/source.mp4")
        val clip = Clip(
            id = "portable-clip",
            sourceUri = source,
            sourceDurationMs = 2_000L,
            timelineStartMs = 250L,
            trimEndMs = 2_000L,
            captions = listOf(
                com.novacut.editor.model.Caption(
                    id = "portable-caption",
                    text = "Portable caption",
                    startTimeMs = 20L,
                    endTimeMs = 400L,
                )
            ),
        )
        val marker = com.novacut.editor.model.TimelineMarker(id = "portable-marker", timeMs = 500L)
        val original = Project(id = "target", name = "Unchanged")
        val json = exchange.exportToEditDecisionJson(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))),
            timelineMarkers = listOf(marker),
            projectName = "Portable",
            timebase = TimelineTimebase(30),
        )

        val preview = importer.importText(
            raw = json,
            format = TimelineImportEngine.Format.EDIT_DECISION_JSON,
            probeMedia = false,
            uriParser = ::testUri,
        )

        assertTrue(preview.readyForAtomicCommit)
        assertEquals("target", original.id)
        assertEquals("Unchanged", original.name)
        val document = importer.commit(original, preview, playheadMs = 125L)
        assertEquals("Unchanged", original.name)
        assertEquals(listOf(marker), document?.state?.timelineMarkers)
        assertEquals("Portable caption", document?.state?.tracks?.single()?.clips?.single()?.captions?.single()?.text)
    }

    @Test
    fun editDecisionPreviewRejectsFutureSchemaBeforeAtomicCommit() = runBlocking {
        val future = org.json.JSONObject()
            .put("schema", "com.clearcut.edit-decision")
            .put("schemaVersion", 99)
            .put("tracks", org.json.JSONArray())

        val result = importer.importText(
            raw = future.toString(),
            format = TimelineImportEngine.Format.EDIT_DECISION_JSON,
            probeMedia = false,
            uriParser = ::testUri,
        )

        assertFalse(result.readyForAtomicCommit)
        assertTrue(result.exchangeResult?.schemaTooNew == true)
        assertEquals(null, importer.commit(Project(id = "target"), result))
    }

    private fun testUri(raw: String): Uri {
        val scheme = raw.substringBefore(':', missingDelimiterValue = "")
        return TestUri(raw = raw, schemeValue = scheme, segment = raw.substringAfterLast('/'))
    }
}
