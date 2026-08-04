package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaJobIdentityTest {

    @Test
    fun timelineEditsChangeTheCapturedMediaIdentity() {
        val original = clip()
        val identity = original.timelineMediaJobIdentity()

        assertTrue(shouldApplyMediaJobResult(identity, identity))
        assertFalse(
            shouldApplyMediaJobResult(
                identity,
                original.copy(trimStartMs = 100L).timelineMediaJobIdentity(),
            )
        )
        assertFalse(
            shouldApplyMediaJobResult(
                identity,
                original.copy(timelineStartMs = 500L).timelineMediaJobIdentity(),
            )
        )
        assertFalse(
            shouldApplyMediaJobResult(
                identity,
                identity.copy(sourceUri = "content://media/relinked.mp4"),
            )
        )
    }

    @Test
    fun deletedClipAndReusedIdCannotAcceptAnOldResult() {
        val identity = clip().timelineMediaJobIdentity()

        assertFalse(shouldApplyMediaJobResult(identity, null))
        assertFalse(
            shouldApplyMediaJobResult(
                identity,
                clip().copy(sourceDurationMs = 2_000L, trimEndMs = 2_000L)
                    .timelineMediaJobIdentity(),
            )
        )
    }

    @Test
    fun proxyResultRequiresTheSameNonEmptyVersion() {
        val version = clip().timelineMediaJobIdentity().version

        assertTrue(shouldApplyProxyResult(version, version))
        assertFalse(shouldApplyProxyResult(version, "new-version"))
        assertFalse(shouldApplyProxyResult(version, null))
        assertFalse(shouldApplyProxyResult("", ""))
    }

    private fun clip(): Clip = Clip(
        sourceUri = FakeUri,
        sourceDurationMs = 1_000L,
        timelineStartMs = 0L,
        trimEndMs = 1_000L,
    )
}
