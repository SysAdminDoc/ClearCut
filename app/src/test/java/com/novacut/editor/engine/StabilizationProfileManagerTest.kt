package com.novacut.editor.engine

import com.novacut.editor.model.StabilizationProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StabilizationProfileManagerTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var manager: StabilizationProfileManager

    @Before
    fun setUp() {
        context.filesDir.resolve("stabilization_profiles").deleteRecursively()
        manager = StabilizationProfileManager(context)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("stabilization_profiles").deleteRecursively()
    }

    @Test
    fun encodedProfileRoundTripsAndDisclosesFallbacks() {
        val validation = manager.validateJson(manager.encode(StabilizationProfile()).toString())

        assertTrue(validation.isValid)
        assertEquals(DeclarativePackContract.CURRENT_SCHEMA_VERSION, validation.schemaVersion)
        assertEquals("PACK_OK", validation.reasonCode)
        assertNotNull(validation.contentHash)
        assertTrue(validation.warnings.any { it.contains("Focal length") })
        assertTrue(validation.warnings.any { it.contains("Analysis interval") })
    }

    @Test
    fun missingProfileMetadataFailsWithStableReasonCode() {
        val root = manager.encode(StabilizationProfile())
        root.remove("motion")
        root.put("contentHash", DeclarativePackContract.contentHash(root))

        val validation = manager.validateJson(root.toString())

        assertEquals(StabilizationProfileFailure.MISSING_REQUIRED_METADATA, validation.failure)
        assertEquals("PROFILE_MISSING_REQUIRED_METADATA", validation.reasonCode)
    }

    @Test
    fun executableFieldsAreRejectedBeforeProfileParsing() {
        val root = manager.encode(StabilizationProfile())
        root.put("metadata", JSONObject().put("script", "not-allowed"))
        root.put("contentHash", DeclarativePackContract.contentHash(root))

        val validation = manager.validateJson(root.toString())

        assertEquals(StabilizationProfileFailure.UNSAFE_CONTENT, validation.failure)
        assertEquals("PACK_EXECUTABLE_CONTENT", validation.reasonCode)
    }

    @Test
    fun unknownCapabilityAndTamperedPayloadAreRejected() {
        val unknown = manager.encode(StabilizationProfile())
            .put("requiredCapabilities", JSONArray().put("future-stabilization-v9"))
        unknown.put("contentHash", DeclarativePackContract.contentHash(unknown))
        assertEquals(
            StabilizationProfileFailure.UNKNOWN_REQUIRED_CAPABILITY,
            manager.validateJson(unknown.toString()).failure,
        )

        val tampered = manager.encode(StabilizationProfile())
        tampered.put("name", "Tampered")
        assertEquals(
            StabilizationProfileFailure.HASH_MISMATCH,
            manager.validateJson(tampered.toString()).failure,
        )
    }

    @Test
    fun incompatibleAppVersionIsRejected() {
        val validation = manager.validateJson(
            manager.encode(StabilizationProfile()).toString(),
            supportedAppVersion = "3.78.0",
        )

        assertEquals(StabilizationProfileFailure.INCOMPATIBLE_APP_VERSION, validation.failure)
        assertEquals("PACK_INCOMPATIBLE_APP_VERSION", validation.reasonCode)
    }

    @Test
    fun validProfileCanBeInstalledAndReopenedAsActive() {
        val validation = manager.validateJson(manager.encode(StabilizationProfile(name = "Field rig")).toString())

        val installed = manager.install(validation)

        assertTrue(installed.isValid)
        assertEquals("Field rig", manager.activeProfile()?.name)
        assertTrue(context.filesDir.resolve("stabilization_profiles/active.json").isFile)
    }
}
