package com.novacut.editor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeVideoPolicyTest {

    @Test
    fun knownGenerativeVideoProvidersAreCloudOptionalOnly() {
        GenerativeVideoPolicy.Provider.entries.forEach { provider ->
            assertTrue(GenerativeVideoPolicy.cloudCalloutRequired(provider))
            assertFalse(GenerativeVideoPolicy.bundledOnDeviceAllowed(provider))
        }
    }

    @Test
    fun cloudEffectCannotStartWithoutConsent() {
        val disclosure = GenerativeVideoPolicy.CloudDisclosure(
            provider = GenerativeVideoPolicy.Provider.WAN_2_2,
            destinationLabel = "Self-hosted render worker",
            estimatedUploadBytes = 48L * 1024L * 1024L,
            retentionSummary = "Input media is deleted after render completion.",
            userConsented = false
        )

        assertFalse(GenerativeVideoPolicy.canStartCloudEffect(disclosure))
    }

    @Test
    fun cloudEffectCanStartAfterFullDisclosureAndConsent() {
        val disclosure = GenerativeVideoPolicy.CloudDisclosure(
            provider = GenerativeVideoPolicy.Provider.HUNYUAN_VIDEO,
            destinationLabel = "Self-hosted GPU endpoint",
            estimatedUploadBytes = 96L * 1024L * 1024L,
            retentionSummary = "Input media is retained for up to one hour for retry, then deleted.",
            userConsented = true
        )

        assertTrue(GenerativeVideoPolicy.canStartCloudEffect(disclosure))
    }
}
