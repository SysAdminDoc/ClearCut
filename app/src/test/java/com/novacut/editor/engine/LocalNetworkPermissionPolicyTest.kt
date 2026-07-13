package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalNetworkPermissionPolicyTest {

    @Test
    fun disabledFeatureNeverRequestsOrAttemptsLocalConnections() {
        OutputStreamingEngine.LocalNetworkScope.entries.forEach { scope ->
            val decision = LocalNetworkPermissionPolicy.evaluate(
                scope = scope,
                runtimeSdkInt = 37,
                targetSdkInt = 37,
                permissionGranted = true,
                featureEnabled = false,
            )
            assertEquals(LocalNetworkPermissionPolicy.GateState.FEATURE_DISABLED, decision.gateState)
            assertNull(decision.permissionName)
            assertFalse(decision.canAttemptConnection)
        }
    }

    @Test
    fun publicInternetAndLoopbackNeverRequireLocalNetworkPermission() {
        val publicDecision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.PUBLIC_INTERNET,
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            permissionGranted = false,
        )
        val loopbackDecision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOOPBACK,
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            permissionGranted = false,
        )

        assertEquals(LocalNetworkPermissionPolicy.GateState.NOT_REQUIRED, publicDecision.gateState)
        assertEquals(LocalNetworkPermissionPolicy.GateState.NOT_REQUIRED, loopbackDecision.gateState)
        assertNull(publicDecision.permissionName)
        assertTrue(publicDecision.canAttemptConnection)
    }

    @Test
    fun android35DoesNotGateLanDestinations() {
        val decision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            runtimeSdkInt = 35,
            targetSdkInt = 36,
            permissionGranted = false,
        )

        assertEquals(LocalNetworkPermissionPolicy.GateState.NOT_REQUIRED, decision.gateState)
        assertFalse(decision.requiresPermission)
    }

    @Test
    fun android16Target36UsesNearbyWifiDevicesForLanAndMulticast() {
        listOf(
            OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            OutputStreamingEngine.LocalNetworkScope.MULTICAST,
        ).forEach { scope ->
            val decision = LocalNetworkPermissionPolicy.evaluate(
                scope = scope,
                runtimeSdkInt = 36,
                targetSdkInt = 36,
                permissionGranted = false,
            )
            assertEquals(LocalNetworkPermissionPolicy.GateState.NEEDS_PERMISSION, decision.gateState)
            assertEquals(LocalNetworkPermissionPolicy.ANDROID_16_PERMISSION, decision.permissionName)
            assertNotNull(decision.rationaleBody)
            assertFalse(decision.canAttemptConnection)
        }
    }

    @Test
    fun android17Target37UsesAccessLocalNetwork() {
        val decision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            permissionGranted = false,
        )

        assertEquals(LocalNetworkPermissionPolicy.GateState.NEEDS_PERMISSION, decision.gateState)
        assertEquals(LocalNetworkPermissionPolicy.ANDROID_17_PERMISSION, decision.permissionName)
    }

    @Test
    fun grantedAndDeniedStatesMapToConnectionGate() {
        val granted = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            runtimeSdkInt = 36,
            targetSdkInt = 36,
            permissionGranted = true,
        )
        val denied = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            runtimeSdkInt = 36,
            targetSdkInt = 36,
            permissionGranted = false,
            permissionDenied = true,
        )

        assertEquals(LocalNetworkPermissionPolicy.GateState.ALLOWED, granted.gateState)
        assertTrue(granted.canAttemptConnection)
        assertEquals(LocalNetworkPermissionPolicy.GateState.DENIED, denied.gateState)
        assertFalse(denied.canAttemptConnection)
        assertTrue(denied.deniedMessage!!.contains("Public internet"))
    }

    @Test
    fun permissionFailureMessageTreatsDeniedLanTimeoutAsPermissionProblem() {
        val decision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN,
            runtimeSdkInt = 37,
            targetSdkInt = 37,
            permissionGranted = false,
            permissionDenied = true,
        )

        val message = LocalNetworkPermissionPolicy.permissionFailureMessage(
            decision,
            java.net.SocketTimeoutException("timed out"),
        )
        assertNotNull(message)
        assertTrue(message!!.contains("Local network permission"))
    }

    @Test
    fun diagnosticLineIsStable() {
        val decision = LocalNetworkPermissionPolicy.evaluate(
            scope = OutputStreamingEngine.LocalNetworkScope.MULTICAST,
            runtimeSdkInt = 36,
            targetSdkInt = 36,
            permissionGranted = false,
        )

        assertEquals(
            "scope=MULTICAST; gate=NEEDS_PERMISSION; permission=android.permission.NEARBY_WIFI_DEVICES; canAttemptConnection=false",
            LocalNetworkPermissionPolicy.diagnosticLine(decision),
        )
    }

    @Test
    fun normalManifestOmitsPermissionsAndStreamingVariantOwnsThem() {
        val mainText = locateManifest("main").readText()
        val streamingText = locateManifest("streaming").readText()

        assertFalse(mainText.contains("android.permission.NEARBY_WIFI_DEVICES"))
        assertFalse(mainText.contains("android.permission.ACCESS_LOCAL_NETWORK"))
        assertTrue(streamingText.contains("android.permission.NEARBY_WIFI_DEVICES"))
        assertTrue(streamingText.contains("android:usesPermissionFlags=\"neverForLocation\""))
        assertTrue(streamingText.contains("android.permission.ACCESS_LOCAL_NETWORK"))
        assertTrue(streamingText.contains("tools:targetApi=\"37\""))

        val buildFile = locateProjectFile("app/build.gradle.kts").readText()
        assertTrue(buildFile.contains("LOCAL_NETWORK_STREAMING_ENABLED\", \"false"))
        assertTrue(buildFile.contains("create(\"streaming\")"))
        assertTrue(buildFile.contains("LOCAL_NETWORK_STREAMING_ENABLED\", \"true"))
    }

    private fun locateManifest(sourceSet: String): File {
        val candidates = listOf(
            File("app/src/$sourceSet/AndroidManifest.xml"),
            File("src/$sourceSet/AndroidManifest.xml"),
            File("../app/src/$sourceSet/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml not found")
    }

    private fun locateProjectFile(relative: String): File {
        val candidates = listOf(File(relative), File("../$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("$relative not found")
    }
}
