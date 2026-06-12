package com.novacut.editor.ui.editor

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorMediaManifestCacheTest {

    @Test
    fun transformAndTimingChangesKeepCacheKeyStable() {
        val base = tracksWith(
            Clip(
                sourceUri = FakeUri,
                sourceDurationMs = 10_000L,
                timelineStartMs = 0L
            )
        )
        val edited = tracksWith(
            base.first().clips.first().copy(
                timelineStartMs = 2_000L,
                trimStartMs = 250L,
                trimEndMs = 8_000L,
                scaleX = 1.25f,
                scaleY = 0.9f,
                positionX = 0.4f,
                positionY = -0.2f,
                rotation = 12f,
                opacity = 0.7f
            )
        )

        assertEquals(
            mediaManifestCacheKey(base, emptyList()),
            mediaManifestCacheKey(edited, emptyList())
        )
    }

    @Test
    fun sourceUriChangesCacheKey() {
        val first = tracksWith(
            Clip(
                sourceUri = FakeUri,
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L
            )
        )
        val second = tracksWith(
            Clip(
                sourceUri = SecondFakeUri,
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L
            )
        )

        assertNotEquals(
            mediaManifestCacheKey(first, emptyList()),
            mediaManifestCacheKey(second, emptyList())
        )
    }

    @Test
    fun compoundClipSourcesAffectCacheKey() {
        val parentOnly = tracksWith(
            Clip(
                sourceUri = FakeUri,
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L
            )
        )
        val withCompoundChild = tracksWith(
            parentOnly.first().clips.first().copy(
                isCompound = true,
                compoundClips = listOf(
                    Clip(
                        sourceUri = SecondFakeUri,
                        sourceDurationMs = 500L,
                        timelineStartMs = 0L
                    )
                )
            )
        )

        assertNotEquals(
            mediaManifestCacheKey(parentOnly, emptyList()),
            mediaManifestCacheKey(withCompoundChild, emptyList())
        )
        assertTrue(mediaManifestCacheKey(withCompoundChild, emptyList()).contains(SecondFakeUri.toString()))
    }

    @Test
    fun imageOverlaySourcesAffectCacheKey() {
        val tracks = tracksWith(
            Clip(
                sourceUri = FakeUri,
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L
            )
        )
        val overlay = ImageOverlay(
            sourceUri = TestUri(
                raw = "content://overlay/sticker.png",
                schemeValue = "content",
                segment = "sticker.png"
            ),
            startTimeMs = 0L,
            endTimeMs = 1_000L
        )

        assertNotEquals(
            mediaManifestCacheKey(tracks, emptyList()),
            mediaManifestCacheKey(tracks, listOf(overlay))
        )
        assertTrue(mediaManifestCacheKey(tracks, listOf(overlay)).contains("image:content://overlay/sticker.png"))
    }

    private fun tracksWith(clip: Clip): List<Track> {
        return listOf(
            Track(
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(clip)
            )
        )
    }
}

