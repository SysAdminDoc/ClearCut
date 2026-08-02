package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.editor.shouldBlockAutoSaveForRecoveryOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A restore that silently drops elements is the worst failure mode this file has:
 * the project opens looking whole, the user keeps editing, and the next autosave
 * writes the truncation back over the only copy that still had the missing pieces.
 * These fixtures prove the loss is reported and that the write is held.
 */
class ProjectRestoreReportTest {

    @Test
    fun aCleanProjectReportsNothingDropped() {
        val restored = AutoSaveState.deserializeWithReport(
            project(clips = JSONArray().put(validClip("clip-a"))).toString(),
            uriParser = { FakeUri }
        )

        assertFalse(restored.report.isPartial)
        assertTrue(restored.report.dropped.isEmpty())
        assertEquals(1, restored.state.tracks.single().clips.size)
        assertFalse(
            shouldBlockAutoSaveForRecoveryOutcome(
                ProjectAutoSave.LoadOutcome.Loaded(restored.state, restored.report)
            )
        )
    }

    @Test
    fun aClipWithNoSourceMediaIsReportedRatherThanQuietlyDropped() {
        val broken = validClip("clip-b").apply { remove("sourceUri") }
        val restored = AutoSaveState.deserializeWithReport(
            project(clips = JSONArray().put(validClip("clip-a")).put(broken)).toString(),
            uriParser = { FakeUri }
        )

        assertTrue("the surviving clip still loads", restored.state.tracks.single().clips.size == 1)
        assertTrue(restored.report.isPartial)
        val dropped = restored.report.dropped.single()
        assertEquals("clip", dropped.kind)
        assertEquals(DropReason.BAD_URI, dropped.reason)
    }

    @Test
    fun aClipWithAnImpossibleDurationIsReported() {
        val broken = validClip("clip-b").apply { put("sourceDurationMs", 0L) }
        val restored = AutoSaveState.deserializeWithReport(
            project(clips = JSONArray().put(broken)).toString(),
            uriParser = { FakeUri }
        )

        assertTrue(restored.report.isPartial)
        assertEquals(DropReason.MALFORMED, restored.report.dropped.single().reason)
    }

    @Test
    fun aMalformedImageOverlayIsReported() {
        val root = project(clips = JSONArray().put(validClip("clip-a"))).apply {
            put("imageOverlays", JSONArray().put(JSONObject().apply { put("sourceUri", "") }))
        }
        val restored = AutoSaveState.deserializeWithReport(root.toString(), uriParser = { FakeUri })

        assertTrue(restored.report.isPartial)
        assertEquals("image overlay", restored.report.dropped.single().kind)
    }

    @Test
    fun aPartialRestoreBlocksTheNextAutosaveOverwrite() {
        val report = ProjectRestoreReport(
            listOf(DroppedElement("clip", 3, DropReason.MALFORMED, "JSONException"))
        )
        val outcome = ProjectAutoSave.LoadOutcome.Loaded(AutoSaveState(projectId = "p"), report)

        assertTrue(
            "a partial load must hold the write until the user decides",
            shouldBlockAutoSaveForRecoveryOutcome(outcome)
        )
    }

    @Test
    fun theReportSummarizesLossByKindForTheUser() {
        val report = ProjectRestoreReport(
            listOf(
                DroppedElement("clip", 0, DropReason.MALFORMED, "x"),
                DroppedElement("clip", 4, DropReason.BAD_URI, "y"),
                DroppedElement("effect", 1, DropReason.MALFORMED, "z"),
            )
        )

        assertEquals(listOf("clip" to 2, "effect" to 1), report.countsByKind())
        assertTrue(report.summary().contains("2 clip"))
    }

    @Test
    fun theReportDoesNotLeakIntoALaterDeserializeCall() {
        AutoSaveState.deserializeWithReport(
            project(clips = JSONArray().put(validClip("clip-a").apply { remove("sourceUri") })).toString(),
            uriParser = { FakeUri }
        )
        val second = AutoSaveState.deserializeWithReport(
            project(clips = JSONArray().put(validClip("clip-a"))).toString(),
            uriParser = { FakeUri }
        )

        assertFalse("each restore reports only its own losses", second.report.isPartial)
    }

    private fun project(clips: JSONArray): JSONObject = JSONObject().apply {
        put("version", AutoSaveState.FORMAT_VERSION)
        put("projectId", "project")
        put("tracks", JSONArray().put(JSONObject().apply {
            put("type", TrackType.VIDEO.name)
            put("index", 0)
            put("clips", clips)
        }))
    }

    private fun validClip(id: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceUri", FakeUri.toString())
        put("sourceDurationMs", 5_000L)
        put("trimStartMs", 0L)
        put("trimEndMs", 5_000L)
        put("effects", JSONArray())
        put("keyframes", JSONArray())
    }
}
