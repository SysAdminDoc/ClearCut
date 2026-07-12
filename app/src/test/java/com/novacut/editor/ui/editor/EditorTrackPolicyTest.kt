package com.novacut.editor.ui.editor

import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTrackPolicyTest {

    @Test
    fun ensureEditorTracksAddsOneCollapsedTextLaneToLegacyProjects() {
        val legacyTracks = listOf(
            Track(id = "video", type = TrackType.VIDEO, index = 0),
            Track(id = "audio", type = TrackType.AUDIO, index = 1)
        )

        val upgraded = ensureEditorTracks(legacyTracks)

        assertEquals(3, upgraded.size)
        assertEquals(1, upgraded.count { it.type == TrackType.TEXT })
        assertTrue(upgraded.first { it.type == TrackType.TEXT }.isCollapsed)
    }

    @Test
    fun ensureEditorTracksDoesNotDuplicateExistingTextLane() {
        val tracks = listOf(
            Track(id = "video", type = TrackType.VIDEO, index = 0),
            Track(id = "text", type = TrackType.TEXT, index = 1)
        )

        assertEquals(tracks, ensureEditorTracks(tracks))
    }

    @Test
    fun orderedTimelineTracksKeepsTextAndOverlaysAboveVideoAndAudio() {
        val tracks = listOf(
            Track(id = "audio", type = TrackType.AUDIO, index = 0),
            Track(id = "video", type = TrackType.VIDEO, index = 1),
            Track(id = "adjustment", type = TrackType.ADJUSTMENT, index = 2),
            Track(id = "overlay", type = TrackType.OVERLAY, index = 3),
            Track(id = "text", type = TrackType.TEXT, index = 4)
        )

        assertEquals(
            listOf("text", "overlay", "adjustment", "video", "audio"),
            orderedTimelineTracks(tracks).map { it.id }
        )
    }
}
