package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6.17 — OutputStreamingEngine.
 *
 * The engine is a stub today; these tests cover the pure pre-flight surface
 * the UI can rely on regardless of whether a streaming library is wired:
 * URL validation and recommended-bitrate math.
 */
class OutputStreamingEngineTest {

    private val engine = OutputStreamingEngine(context = throwingContext())

    /**
     * Test seam for the @ApplicationContext injection. The engine never
     * touches `context` directly outside of future native-lib initialization,
     * so a throwing stub is the safest fake.
     */
    private fun throwingContext(): android.content.Context =
        object : android.content.ContextWrapper(null) {}

    // --- protocol metadata ---

    @Test
    fun protocolEnumExposesUrlScheme() {
        assertEquals("rtmp://", OutputStreamingEngine.Protocol.RTMP.urlScheme)
        assertEquals("srt://", OutputStreamingEngine.Protocol.SRT.urlScheme)
        assertEquals("rtmps://", OutputStreamingEngine.Protocol.RTMPS.urlScheme)
        assertEquals("RTMP", OutputStreamingEngine.Protocol.RTMP.displayName)
    }

    // --- URL validation ---

    @Test
    fun validateDestination_acceptsConformantUrls() {
        assertNull(
            engine.validateDestination(
                OutputStreamingEngine.Protocol.RTMP,
                "rtmp://live.example.com/app/streamkey"
            )
        )
        assertNull(
            engine.validateDestination(
                OutputStreamingEngine.Protocol.SRT,
                "srt://198.51.100.10:9000?passphrase=secret"
            )
        )
    }

    @Test
    fun validateDestination_rejectsBlank() {
        val err = engine.validateDestination(OutputStreamingEngine.Protocol.RTMP, "  ")
        assertNotNull(err)
        assertTrue(err!!.contains("required"))
    }

    @Test
    fun validateDestination_rejectsWrongScheme() {
        val err = engine.validateDestination(
            OutputStreamingEngine.Protocol.SRT,
            "rtmp://server/key"
        )
        assertNotNull(err)
        assertTrue(err!!.contains("srt://"))
    }

    @Test
    fun validateDestination_rejectsControlChars() {
        val err = engine.validateDestination(
            OutputStreamingEngine.Protocol.RTMP,
            "rtmp://server/key with space"
        )
        assertNotNull(err)
        assertTrue(err!!.contains("whitespace"))
    }

    @Test
    fun validateDestination_rejectsSchemeOnly() {
        val err = engine.validateDestination(
            OutputStreamingEngine.Protocol.RTMP,
            "rtmp://"
        )
        assertNotNull(err)
        assertTrue(err!!.contains("host"))
    }

    // --- bitrate recommendation ---

    @Test
    fun recommendedBitrate_1080p30_isRoughly3_25Mbps() {
        // 1080p base = 6.5 Mbps @ 60 fps → 3.25 Mbps @ 30 fps.
        assertEquals(
            3_250_000,
            engine.recommendedBitrateBps(width = 1920, height = 1080, fps = 30)
        )
    }

    @Test
    fun recommendedBitrate_4K60_is25Mbps() {
        assertEquals(
            25_000_000,
            engine.recommendedBitrateBps(width = 3840, height = 2160, fps = 60)
        )
    }

    @Test
    fun recommendedBitrate_720p30_is1_75Mbps() {
        assertEquals(
            1_750_000,
            engine.recommendedBitrateBps(width = 1280, height = 720, fps = 30)
        )
    }

    @Test
    fun recommendedBitrate_verticalReelsBudget() {
        // Reels vertical 1080x1920 has the same pixel count as 1920x1080 so the
        // bitrate matches; the recommendation is pixel-driven, not aspect-driven.
        val horizontal = engine.recommendedBitrateBps(1920, 1080, 30)
        val vertical = engine.recommendedBitrateBps(1080, 1920, 30)
        assertEquals(horizontal, vertical)
    }

    @Test
    fun recommendedBitrate_hardFloorAt500Kbps() {
        // 360p @ 1 fps → way below the 500 kbps floor.
        assertEquals(
            500_000,
            engine.recommendedBitrateBps(width = 640, height = 360, fps = 1)
        )
    }

    @Test
    fun recommendedBitrate_invalidArgsThrow() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.recommendedBitrateBps(0, 1080, 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.recommendedBitrateBps(1920, -1, 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.recommendedBitrateBps(1920, 1080, 0)
        }
    }

    // --- isAvailable probe ---

    @Test
    fun isAvailable_returnsFalseWhenNoStreamingLibraryOnClasspath() {
        assertEquals(false, engine.isAvailable())
    }
}
