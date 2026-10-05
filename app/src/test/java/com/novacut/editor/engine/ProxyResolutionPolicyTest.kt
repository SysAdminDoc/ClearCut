package com.novacut.editor.engine

import com.novacut.editor.engine.ProxyResolutionPolicy.Plan
import com.novacut.editor.model.ProxyResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyResolutionPolicyTest {

    @Test
    fun proxyHeightIsTheRequestedFractionOfTheSource() {
        val expected = mapOf(
            2160 to listOf(1080, 540, 270),
            1080 to listOf(540, 270, 136),
            720 to listOf(360, 180, 120),
        )
        expected.forEach { (sourceHeight, heights) ->
            val actual = ProxyResolution.entries.map { resolution ->
                (ProxyResolutionPolicy.plan(sourceHeight, resolution) as Plan.Generate).targetHeight
            }
            assertEquals("${sourceHeight}p source", heights, actual)
        }
    }

    @Test
    fun aProxyIsNeverTallerThanItsSourceAndAlwaysEven() {
        for (sourceHeight in 136..4320 step 7) {
            for (resolution in ProxyResolution.entries) {
                val plan = ProxyResolutionPolicy.plan(sourceHeight, resolution)
                if (plan is Plan.Generate) {
                    assertTrue("$sourceHeight $resolution -> ${plan.targetHeight}", plan.targetHeight < sourceHeight)
                    assertEquals(0, plan.targetHeight % Media3ExportRobustnessPolicy.ENCODER_DIMENSION_DIVISOR)
                    assertTrue(plan.targetHeight >= ProxyResolutionPolicy.MIN_PROXY_HEIGHT)
                }
            }
        }
    }

    @Test
    fun a480pSourceAtHalfIsEditedDirectlyWithTheReasonStated() {
        val plan = ProxyResolutionPolicy.plan(480, ProxyResolution.HALF)

        assertTrue(plan is Plan.UseSource)
        assertEquals(
            "source is 480px tall, already at or below the 540px 1/2 proxy tier, so it is edited directly",
            (plan as Plan.UseSource).reason,
        )
        assertEquals(Plan.Generate(120), ProxyResolutionPolicy.plan(480, ProxyResolution.QUARTER))
        // 1/8 of 480 is 60, below the floor.
        assertEquals(Plan.Generate(120), ProxyResolutionPolicy.plan(480, ProxyResolution.EIGHTH))
    }

    @Test
    fun aRotatedPortraitSourceScalesFromTheHeightItIsShownAt() {
        // A phone's portrait clip is usually stored 1920x1080 with a 90 degree rotation.
        val shown = ProxyResolutionPolicy.displayHeight(codedWidth = 1920, codedHeight = 1080, rotationDegrees = 90)

        assertEquals(1920, shown)
        assertEquals(Plan.Generate(960), ProxyResolutionPolicy.plan(shown, ProxyResolution.HALF))
        assertEquals(1920, ProxyResolutionPolicy.displayHeight(1920, 1080, 270))
        assertEquals(1080, ProxyResolutionPolicy.displayHeight(1920, 1080, 180))
        assertEquals(1920, ProxyResolutionPolicy.displayHeight(1080, 1920, 0))
    }

    @Test
    fun anUnreadableSourceFallsBackToTheOld1080Basis() {
        assertEquals(0, ProxyResolutionPolicy.displayHeight(0, 1080, 0))
        assertEquals(Plan.Generate(540), ProxyResolutionPolicy.plan(0, ProxyResolution.HALF))
        assertEquals(Plan.Generate(270), ProxyResolutionPolicy.plan(-1, ProxyResolution.QUARTER))
    }
}
