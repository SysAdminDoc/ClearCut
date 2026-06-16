package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockAssetEngineTest {

    private val engine = StockAssetEngine()

    @Test
    fun providerCapabilitiesKeepSearchDisabledUntilProviderIsConfigured() {
        val capabilities = engine.providerCapabilities()

        assertEquals(StockAssetEngine.Provider.entries.size, capabilities.size)
        assertTrue(capabilities.all { !it.configured })
        assertTrue(capabilities.all { it.unavailableReason!!.contains("requires") })
        assertTrue(
            capabilities
                .filter { it.access == StockAssetEngine.ProviderAccess.API_KEY_REQUIRED }
                .all { "API key" in it.unavailableReason!! }
        )
    }

    @Test
    fun configuredProviderClearsUnavailableReasonWithoutEnablingOthers() {
        val capabilities = engine.providerCapabilities(
            configuredProviders = setOf(StockAssetEngine.Provider.PEXELS_VIDEO)
        ).associateBy { it.provider }

        assertTrue(capabilities.getValue(StockAssetEngine.Provider.PEXELS_VIDEO).configured)
        assertNull(capabilities.getValue(StockAssetEngine.Provider.PEXELS_VIDEO).unavailableReason)
        assertFalse(capabilities.getValue(StockAssetEngine.Provider.PIXABAY_VIDEO).configured)
    }

    @Test
    fun providerTermsEncodeBrandingCacheHotlinkAndCommercialRules() {
        val pexels = engine.providerCapability(StockAssetEngine.Provider.PEXELS_PHOTO)
        val pixabay = engine.providerCapability(StockAssetEngine.Provider.PIXABAY_VIDEO)
        val freesound = engine.providerCapability(StockAssetEngine.Provider.FREESOUND)
        val fma = engine.providerCapability(StockAssetEngine.Provider.FREE_MUSIC_ARCHIVE)

        assertTrue(pexels.sourceBrandingRequired)
        assertFalse(pexels.hotlinkingAllowed)
        assertEquals(24, pixabay.cacheTtlHours)
        assertFalse(pixabay.hotlinkingAllowed)
        assertEquals(StockAssetEngine.CommercialUsePolicy.NON_COMMERCIAL_ONLY, freesound.commercialUse)
        assertEquals(StockAssetEngine.ProviderAccess.MANUAL_SOURCE_REVIEW_REQUIRED, fma.access)
        assertEquals(StockAssetEngine.CommercialUsePolicy.PER_ASSET_LICENSE, fma.commercialUse)
    }

    @Test
    fun pixabayCachePolicyUsesProviderTtlOnly() {
        val now = 1_000_000_000L

        assertTrue(engine.shouldUseCachedResult(StockAssetEngine.Provider.PIXABAY_PHOTO, now - 23 * HOUR_MS, now))
        assertFalse(engine.shouldUseCachedResult(StockAssetEngine.Provider.PIXABAY_PHOTO, now - 25 * HOUR_MS, now))
        assertFalse(engine.shouldUseCachedResult(StockAssetEngine.Provider.PEXELS_PHOTO, now - HOUR_MS, now))
        assertFalse(engine.shouldUseCachedResult(StockAssetEngine.Provider.PIXABAY_PHOTO, now + HOUR_MS, now))
    }

    @Test
    fun attributionLinePrefersPersistedProviderAttribution() {
        val asset = stockAsset(
            attribution = "Photo by Ava on Pexels",
            licenseName = "Pexels License"
        )

        assertEquals("Photo by Ava on Pexels", engine.attributionLine(asset))
    }

    @Test
    fun exportUseBlocksNonCommercialAndNoDerivativesAssets() {
        val nonCommercial = stockAsset(
            provider = StockAssetEngine.Provider.FREESOUND,
            licenseName = "CC-BY-NC 4.0"
        )
        val noDerivatives = stockAsset(
            provider = StockAssetEngine.Provider.FREE_MUSIC_ARCHIVE,
            licenseName = "CC-BY-ND 4.0"
        )

        assertEquals(
            StockAssetEngine.ExportUseStatus.BLOCKED_NON_COMMERCIAL,
            engine.assessExportUse(nonCommercial, commercialIntent = true, materiallyRemixed = false).status
        )
        assertEquals(
            StockAssetEngine.ExportUseStatus.BLOCKED_NO_DERIVATIVES,
            engine.assessExportUse(noDerivatives, commercialIntent = false, materiallyRemixed = true).status
        )
    }

    @Test
    fun exportUseRequiresAttributionForProviderAssetsAndAllowsCc0() {
        val pexels = stockAsset(licenseName = "Pexels License")
        val cc0 = stockAsset(
            provider = StockAssetEngine.Provider.PIXABAY_PHOTO,
            licenseName = "CC0"
        )

        assertEquals(
            StockAssetEngine.ExportUseStatus.REQUIRES_ATTRIBUTION,
            engine.assessExportUse(pexels, commercialIntent = false, materiallyRemixed = false).status
        )
        assertEquals(
            StockAssetEngine.ExportUseStatus.ALLOWED,
            engine.assessExportUse(cc0, commercialIntent = true, materiallyRemixed = true).status
        )
    }

    private fun stockAsset(
        provider: StockAssetEngine.Provider = StockAssetEngine.Provider.PEXELS_PHOTO,
        licenseName: String,
        attribution: String = ""
    ): StockAssetEngine.StockAsset {
        return StockAssetEngine.StockAsset(
            id = "asset-1",
            provider = provider,
            title = "Clip",
            previewUrl = "https://example.invalid/preview.jpg",
            downloadUrl = "https://example.invalid/download.jpg",
            author = "Ava",
            authorUrl = "https://example.invalid/ava",
            licenseName = licenseName,
            attribution = attribution,
            sourceUrl = "https://example.invalid/source",
            licenseUrl = "https://example.invalid/license",
            fetchedAtEpochMs = 1_000L
        )
    }

    private companion object {
        const val HOUR_MS = 60L * 60L * 1000L
    }
}
