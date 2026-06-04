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

    // --- R8.15 LNP classification ---

    @Test
    fun classifyNetworkScope_publicInternetHostnames() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            engine.classifyNetworkScope("rtmp://live.twitch.tv/app/streamkey")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            engine.classifyNetworkScope("rtmps://a.rtmp.youtube.com/live2/streamkey")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            engine.classifyNetworkScope("srt://ingest.example.com:9999?streamid=foo")
        )
    }

    @Test
    fun classifyNetworkScope_rfc1918BlocksAreLocalLan() {
        listOf(
            "rtmp://10.0.0.5/app",
            "rtmp://10.255.255.254/app",
            "rtmp://172.16.0.10/app",
            "rtmp://172.31.255.254/app",
            "rtmp://192.168.1.42/app",
            "rtmp://192.168.0.1/app",
            "rtsp://169.254.1.2/stream"
        ).forEach { url ->
            assertEquals(
                "URL $url should classify as LOCAL_LAN",
                OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
                engine.classifyNetworkScope(url)
            )
        }
    }

    @Test
    fun classifyNetworkScope_multicastAddresses() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.MULTICAST,
            engine.classifyNetworkScope("rist://224.0.0.1:5000")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.MULTICAST,
            engine.classifyNetworkScope("rist://239.255.255.250:1900")
        )
    }

    @Test
    fun classifyNetworkScope_loopbackAddresses() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOOPBACK,
            engine.classifyNetworkScope("rtmp://localhost:1935/app")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOOPBACK,
            engine.classifyNetworkScope("rtmp://127.0.0.1:1935/app")
        )
    }

    @Test
    fun classifyNetworkScope_ipv6HeuristicsCoverLoopbackLinkLocalAndMulticast() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOOPBACK,
            engine.classifyNetworkScope("rtmp://[::1]:1935/app")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            engine.classifyNetworkScope("rtmp://[fe80::1]:1935/app")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            engine.classifyNetworkScope("rtmp://[fd00::1]:1935/app")
        )
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.MULTICAST,
            engine.classifyNetworkScope("rist://[ff02::1]:5000")
        )
    }

    @Test
    fun classifyNetworkScope_mdnsHostnamesAreLocalLan() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            engine.classifyNetworkScope("rtmp://stream.local/app")
        )
    }

    @Test
    fun classifyNetworkScope_userInfoIsStripped() {
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            engine.classifyNetworkScope("rtmp://user:pass@192.168.5.10:1935/app")
        )
    }

    @Test
    fun classifyNetworkScope_malformedUrlsFallToPublic() {
        // No scheme separator.
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            engine.classifyNetworkScope("not-a-url")
        )
        // Empty authority.
        assertEquals(
            OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            engine.classifyNetworkScope("rtmp:///app")
        )
    }

    @Test
    fun requiresLocalNetworkPermission_truthTable() {
        // True for LAN + multicast, false for public + loopback.
        assertTrue(engine.requiresLocalNetworkPermission("rtmp://192.168.1.5/app"))
        assertTrue(engine.requiresLocalNetworkPermission("rist://224.0.0.1:5000"))
        assertEquals(false, engine.requiresLocalNetworkPermission("rtmp://live.twitch.tv/app"))
        assertEquals(false, engine.requiresLocalNetworkPermission("rtmp://localhost/app"))
    }

    @Test
    fun localNetworkPermissionDecision_gatesOnlyLanDestinations() {
        val publicDecision = engine.localNetworkPermissionDecision(
            url = "rtmp://live.twitch.tv/app",
            runtimeSdkInt = 36,
            targetSdkInt = 36,
        )
        val lanDecision = engine.localNetworkPermissionDecision(
            url = "rtmp://192.168.1.5/app",
            runtimeSdkInt = 36,
            targetSdkInt = 36,
        )

        assertEquals(LocalNetworkPermissionPolicy.GateState.NOT_REQUIRED, publicDecision.gateState)
        assertEquals(LocalNetworkPermissionPolicy.GateState.NEEDS_PERMISSION, lanDecision.gateState)
        assertEquals(LocalNetworkPermissionPolicy.ANDROID_16_PERMISSION, lanDecision.permissionName)
    }

    @Test
    fun permissionAwareLocalNetworkFailureMessage_onlyForLocalPermissionFailures() {
        val publicMessage = engine.permissionAwareLocalNetworkFailureMessage(
            url = "rtmp://live.twitch.tv/app",
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            throwable = java.net.SocketTimeoutException("timed out"),
        )
        val lanMessage = engine.permissionAwareLocalNetworkFailureMessage(
            url = "rtmp://192.168.1.5/app",
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            permissionDenied = true,
            throwable = java.net.SocketTimeoutException("timed out"),
        )

        assertNull(publicMessage)
        assertNotNull(lanMessage)
        assertTrue(lanMessage!!.contains("Local network permission"))
    }
}
