package com.novacut.editor.engine

import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackBlendModeCapabilityTest {

    @Test
    fun only_normal_track_blend_mode_is_mutable_until_a_compositor_exists() {
        assertTrue(TrackBlendModeCapability.isSupported(BlendMode.NORMAL))
        assertFalse(TrackBlendModeCapability.isSupported(BlendMode.MULTIPLY))
    }

    @Test
    fun imported_non_normal_tracks_are_disclosed_as_render_fallbacks() {
        val normal = Track(type = TrackType.VIDEO, index = 0)
        val multiply = Track(type = TrackType.VIDEO, index = 1, blendMode = BlendMode.MULTIPLY)

        assertEquals(
            listOf(multiply),
            TrackBlendModeCapability.unsupportedTracks(listOf(normal, multiply))
        )
    }
}
