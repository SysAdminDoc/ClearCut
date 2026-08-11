package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.StabilizationData
import com.novacut.editor.model.StabilizationLensProfile
import com.novacut.editor.model.StabilizationMotionPoint
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationDataPersistenceTest {

    @Test
    fun correctionUsesSyncOffsetAndInterpolatesMotion() {
        val data = StabilizationData(
            motion = listOf(
                StabilizationMotionPoint(timestampMs = 100L, dx = 0f, dy = 0f),
                StabilizationMotionPoint(timestampMs = 200L, dx = 0.2f, dy = -0.2f),
            ),
            syncOffsetMs = 10L,
        )

        val correction = data.correctionAtSourceTimeMs(140L)

        assertNotNull(correction)
        assertEquals(0.1f, correction!!.dx, 0.0001f)
        assertEquals(-0.1f, correction.dy, 0.0001f)
        assertNull(data.copy(motion = emptyList()).correctionAtSourceTimeMs(140L))
    }

    @Test
    fun stabilizationDataRoundTripsThroughAutosave() {
        val data = StabilizationData(
            motion = listOf(
                StabilizationMotionPoint(0L, 0.04f, -0.02f, 0.8f),
                StabilizationMotionPoint(1_000L, -0.03f, 0.01f, 0.9f),
            ),
            lensProfile = StabilizationLensProfile(
                name = "phone-wide",
                focalLengthMm = 4.2f,
                distortionK1 = -0.08f,
                distortionK2 = 0.01f,
            ),
            syncOffsetMs = -18L,
            cropScale = 1.17f,
            sourceDurationMs = 1_000L,
        )
        val state = AutoSaveState(
            projectId = "stabilization-project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            sourceUri = FakeUri,
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L,
                            trimEndMs = 1_000L,
                            stabilizationData = data,
                        )
                    ),
                )
            ),
        )

        val restored = AutoSaveState.deserialize(state.serialize()) { FakeUri }
        val restoredData = restored.tracks.single().clips.single().stabilizationData

        assertEquals(data, restoredData)
        assertTrue(restoredData!!.isUsable)
    }
}
