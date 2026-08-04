package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Media3ExportRobustnessPolicyTest {
    @Test
    fun roundsOddOutputDimensionsToEncoderSafeMultiples() {
        assertEquals(
            Media3ExportRobustnessPolicy.Dimensions(width = 854, height = 480),
            Media3ExportRobustnessPolicy.encoderSafeDimensions(width = 853, height = 479),
        )
        assertEquals(
            Media3ExportRobustnessPolicy.Dimensions(width = 1_920, height = 1_080),
            Media3ExportRobustnessPolicy.encoderSafeDimensions(width = 1_920, height = 1_080),
        )
        assertEquals(
            Media3ExportRobustnessPolicy.Dimensions(width = 2, height = 2),
            Media3ExportRobustnessPolicy.encoderSafeDimensions(width = 1, height = 1),
        )
    }

    @Test
    fun capsOnlySpeedProcessedItems() {
        assertNull(Media3ExportRobustnessPolicy.speedFrameRateCap(30, speedChanged = false))
        assertEquals(24, Media3ExportRobustnessPolicy.speedFrameRateCap(24, speedChanged = true))
        assertEquals(60, Media3ExportRobustnessPolicy.speedFrameRateCap(120, speedChanged = true))
        assertEquals(1, Media3ExportRobustnessPolicy.speedFrameRateCap(0, speedChanged = true))
    }

    @Test
    fun media3HooksAreConnectedToReleaseAndProxyPaths() {
        val videoEngine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val proxyEngine = locate("app/src/main/java/com/novacut/editor/engine/ProxyEngine.kt").readText()

        assertTrue(videoEngine.contains(".setEnableCodecDbLite(true)"))
        assertTrue(videoEngine.contains("speedFrameRateCap("))
        assertTrue(videoEngine.contains("encoderSafeOutputDimensions(config)"))
        assertTrue(
            proxyEngine.contains(
                "copyWithUnsetSideRoundedTo(Media3ExportRobustnessPolicy.ENCODER_DIMENSION_DIVISOR)"
            )
        )
    }

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }
}
