package com.novacut.editor.engine

import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeInterpolation
import com.novacut.editor.model.KeyframeProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeVolumeMuteTest {

    @Test
    fun muteRangeAddsBoundariesWithoutChangingTheDefaultLevelOutsideIt() {
        val muted = KeyframeEngine.applyVolumeMuteRange(
            keyframes = emptyList(),
            startOffsetMs = 500L,
            endOffsetMs = 1_000L,
            fallbackVolume = 0.8f,
        )

        assertEquals(0.8f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 0L))
        assertEquals(0.8f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 499L))
        assertEquals(0f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 500L))
        assertEquals(0f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 999L))
        assertEquals(0.8f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 1_000L))
        assertEquals(listOf(499L, 500L, 1_000L), muted.map { it.timeOffsetMs })
    }

    @Test
    fun muteRangeRemovesConflictingVolumeKeysAndRestoresTheSampledEndLevel() {
        val original = listOf(
            Keyframe(0L, KeyframeProperty.VOLUME, 0.4f, interpolation = KeyframeInterpolation.LINEAR),
            Keyframe(1_000L, KeyframeProperty.VOLUME, 1.2f),
            Keyframe(700L, KeyframeProperty.VOLUME, 0.2f),
            Keyframe(200L, KeyframeProperty.OPACITY, 0.5f),
        )
        val expectedEnd = KeyframeEngine.getValueAt(original, KeyframeProperty.VOLUME, 600L)
        val muted = KeyframeEngine.applyVolumeMuteRange(original, 400L, 600L, 1f)

        assertTrue(muted.none { it.property == KeyframeProperty.VOLUME && it.timeOffsetMs in 400L..600L && it.timeOffsetMs != 400L && it.timeOffsetMs != 600L })
        assertEquals(0f, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 500L))
        assertEquals(expectedEnd, KeyframeEngine.getValueAt(muted, KeyframeProperty.VOLUME, 600L))
        assertTrue(muted.any { it.property == KeyframeProperty.OPACITY && it.timeOffsetMs == 200L })
    }

    @Test
    fun muteRangeRejectsAnEmptyInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeEngine.applyVolumeMuteRange(emptyList(), 100L, 100L, 1f)
        }
    }
}
