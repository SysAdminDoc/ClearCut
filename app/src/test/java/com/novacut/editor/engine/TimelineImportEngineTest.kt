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

    private fun testUri(raw: String): Uri {
        val scheme = raw.substringBefore(':', missingDelimiterValue = "")
        return TestUri(raw = raw, schemeValue = scheme, segment = raw.substringAfterLast('/'))
    }
}
