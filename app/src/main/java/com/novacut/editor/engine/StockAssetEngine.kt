package com.novacut.editor.engine

import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-gated stock asset library integration. See ROADMAP.md Tier C.7.
 *
 * Wraps Pexels / Pixabay / Freesound / Free Music Archive APIs behind a single
 * search/fetch interface. Each provider surfaces its required attribution string
 * in [StockAsset.attribution]; exporters must honour that attribution per provider
 * terms.
 *
 * API keys are user-supplied via Settings (keeps the app Play-safe and
 * respects each provider's rate limits per developer).
 */
@Singleton
class StockAssetEngine @Inject constructor() {

    enum class Provider(val displayName: String, val type: AssetType) {
        PEXELS_VIDEO("Pexels", AssetType.VIDEO),
        PEXELS_PHOTO("Pexels", AssetType.PHOTO),
        PIXABAY_VIDEO("Pixabay", AssetType.VIDEO),
        PIXABAY_PHOTO("Pixabay", AssetType.PHOTO),
        FREESOUND("Freesound", AssetType.SFX),
        FREE_MUSIC_ARCHIVE("Free Music Archive", AssetType.MUSIC)
    }

    enum class AssetType { VIDEO, PHOTO, SFX, MUSIC }

    enum class ProviderAccess { API_KEY_REQUIRED, MANUAL_SOURCE_REVIEW_REQUIRED }
    enum class CommercialUsePolicy { ALLOWED, NON_COMMERCIAL_ONLY, PER_ASSET_LICENSE, UNKNOWN }
    enum class DerivativePolicy { ALLOWED, NO_DERIVATIVES, SHARE_ALIKE, PER_ASSET_LICENSE, UNKNOWN }
    enum class ExportUseStatus {
        ALLOWED,
        REQUIRES_ATTRIBUTION,
        BLOCKED_NON_COMMERCIAL,
        BLOCKED_NO_DERIVATIVES,
        UNKNOWN_TERMS
    }

    data class ProviderCapability(
        val provider: Provider,
        val configured: Boolean,
        val access: ProviderAccess,
        val unavailableReason: String?,
        val termsUrl: String,
        val sourceBrandingRequired: Boolean,
        val attributionRequired: Boolean,
        val cacheTtlHours: Int?,
        val hotlinkingAllowed: Boolean,
        val commercialUse: CommercialUsePolicy,
        val derivatives: DerivativePolicy
    )

    data class ReuseConstraints(
        val attributionRequired: Boolean = true,
        val commercialUse: CommercialUsePolicy = CommercialUsePolicy.UNKNOWN,
        val derivatives: DerivativePolicy = DerivativePolicy.UNKNOWN
    )

    data class StockAsset(
        val id: String,
        val provider: Provider,
        val title: String,
        val previewUrl: String,
        val downloadUrl: String,
        val durationMs: Long? = null,
        val widthPx: Int? = null,
        val heightPx: Int? = null,
        val author: String,
        val authorUrl: String,
        val licenseName: String,
        val attribution: String,
        val sourceUrl: String = previewUrl,
        val licenseUrl: String = "",
        val fetchedAtEpochMs: Long? = null,
        val constraints: ReuseConstraints = defaultReuseConstraints(provider, licenseName)
    )

    data class SearchQuery(
        val text: String,
        val providers: Set<Provider>,
        val minDurationMs: Long? = null,
        val maxDurationMs: Long? = null,
        val orientation: Orientation? = null,
        val page: Int = 1,
        val pageSize: Int = 24
    ) {
        enum class Orientation { LANDSCAPE, PORTRAIT, SQUARE }
    }

    data class SearchResult(
        val assets: List<StockAsset>,
        val totalResults: Int,
        val page: Int,
        val hasMore: Boolean
    )

    data class ExportUseDecision(
        val status: ExportUseStatus,
        val message: String
    )

    fun isProviderConfigured(provider: Provider): Boolean = false

    fun providerCapability(
        provider: Provider,
        configuredProviders: Set<Provider> = emptySet()
    ): ProviderCapability = providerCapabilityFor(provider, configuredProviders)

    fun providerCapabilities(
        configuredProviders: Set<Provider> = emptySet()
    ): List<ProviderCapability> = Provider.entries.map { providerCapability(it, configuredProviders) }

    suspend fun search(query: SearchQuery): SearchResult {
        Log.d(TAG, "search: stub -- provider API keys not configured (${query.text})")
        return SearchResult(emptyList(), 0, query.page, hasMore = false)
    }

    suspend fun download(
        asset: StockAsset,
        destination: Uri,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        Log.d(TAG, "download: stub -- ${asset.provider.displayName} not configured")
        return false
    }

    /**
     * Validate that a search query is well-formed before dispatching to a
     * provider. Pure function — runs without any API key so the search bar
     * can pre-validate creator input.
     *
     * Returns null if valid, otherwise a UI-displayable error message.
     */
    fun validateQuery(query: SearchQuery): String? {
        if (query.text.isBlank()) return "Search query is required"
        if (query.providers.isEmpty()) return "Pick at least one provider"
        if (query.page < 1) return "Page must be >= 1"
        if (query.pageSize !in 1..100) return "Page size must be 1..100"
        if (query.minDurationMs != null && query.minDurationMs < 0) {
            return "Min duration must be non-negative"
        }
        if (query.maxDurationMs != null && query.minDurationMs != null &&
            query.maxDurationMs < query.minDurationMs
        ) {
            return "Max duration must be >= min duration"
        }
        return null
    }

    /**
     * Build the attribution line a renderer should overlay on (or credit in
     * the description of) a finished export that uses [asset]. Provider
     * terms vary; this returns a single human-readable string suitable for
     * a video credits frame or an Instagram caption.
     */
    fun attributionLine(asset: StockAsset): String =
        asset.attribution.ifBlank {
            "${asset.title} by ${asset.author} (${asset.provider.displayName}, ${asset.licenseName})"
        }

    fun shouldUseCachedResult(
        provider: Provider,
        fetchedAtEpochMs: Long,
        nowEpochMs: Long
    ): Boolean {
        val ttlHours = providerCapability(provider).cacheTtlHours ?: return false
        if (nowEpochMs < fetchedAtEpochMs) return false
        return nowEpochMs - fetchedAtEpochMs <= ttlHours * 60L * 60L * 1000L
    }

    fun assessExportUse(
        asset: StockAsset,
        commercialIntent: Boolean,
        materiallyRemixed: Boolean
    ): ExportUseDecision {
        val constraints = asset.constraints
        if (commercialIntent && constraints.commercialUse == CommercialUsePolicy.NON_COMMERCIAL_ONLY) {
            return ExportUseDecision(
                ExportUseStatus.BLOCKED_NON_COMMERCIAL,
                "Asset license blocks commercial-use exports."
            )
        }
        if (materiallyRemixed && constraints.derivatives == DerivativePolicy.NO_DERIVATIVES) {
            return ExportUseDecision(
                ExportUseStatus.BLOCKED_NO_DERIVATIVES,
                "Asset license blocks derivative or remixed exports."
            )
        }
        if (constraints.commercialUse == CommercialUsePolicy.UNKNOWN ||
            constraints.derivatives == DerivativePolicy.UNKNOWN
        ) {
            return ExportUseDecision(
                ExportUseStatus.UNKNOWN_TERMS,
                "Asset terms are unknown; review the source license before export."
            )
        }
        if (constraints.attributionRequired) {
            return ExportUseDecision(
                ExportUseStatus.REQUIRES_ATTRIBUTION,
                attributionLine(asset)
            )
        }
        return ExportUseDecision(ExportUseStatus.ALLOWED, "Asset is cleared for this export.")
    }

    companion object {
        private const val TAG = "StockAssets"

        const val PEXELS_API_DOCS = "https://www.pexels.com/api/documentation/"
        const val PIXABAY_API_DOCS = "https://pixabay.com/api/docs/"
        const val FREESOUND_API_DOCS = "https://freesound.org/docs/api/terms_of_use.html"
        const val FMA_DATA_HOST = "https://freemusicarchive.org/"
        const val FMA_LICENSE_GUIDE = "https://freemusicarchive.org/License_Guide"
    }
}

internal fun providerCapabilityFor(
    provider: StockAssetEngine.Provider,
    configuredProviders: Set<StockAssetEngine.Provider> = emptySet()
): StockAssetEngine.ProviderCapability {
    val terms = providerTerms(provider)
    val configured = provider in configuredProviders
    val unavailableReason = if (configured) {
        null
    } else {
        when (terms.access) {
            StockAssetEngine.ProviderAccess.API_KEY_REQUIRED ->
                "${provider.displayName} requires a user-supplied API key before search is enabled."
            StockAssetEngine.ProviderAccess.MANUAL_SOURCE_REVIEW_REQUIRED ->
                "${provider.displayName} requires a documented manual source or provider adapter before search is enabled."
        }
    }
    return terms.copy(configured = configured, unavailableReason = unavailableReason)
}

private fun providerTerms(provider: StockAssetEngine.Provider): StockAssetEngine.ProviderCapability {
    return when (provider) {
        StockAssetEngine.Provider.PEXELS_PHOTO,
        StockAssetEngine.Provider.PEXELS_VIDEO -> StockAssetEngine.ProviderCapability(
            provider = provider,
            configured = false,
            access = StockAssetEngine.ProviderAccess.API_KEY_REQUIRED,
            unavailableReason = null,
            termsUrl = StockAssetEngine.PEXELS_API_DOCS,
            sourceBrandingRequired = true,
            attributionRequired = true,
            cacheTtlHours = null,
            hotlinkingAllowed = false,
            commercialUse = StockAssetEngine.CommercialUsePolicy.ALLOWED,
            derivatives = StockAssetEngine.DerivativePolicy.ALLOWED
        )
        StockAssetEngine.Provider.PIXABAY_PHOTO,
        StockAssetEngine.Provider.PIXABAY_VIDEO -> StockAssetEngine.ProviderCapability(
            provider = provider,
            configured = false,
            access = StockAssetEngine.ProviderAccess.API_KEY_REQUIRED,
            unavailableReason = null,
            termsUrl = StockAssetEngine.PIXABAY_API_DOCS,
            sourceBrandingRequired = true,
            attributionRequired = true,
            cacheTtlHours = 24,
            hotlinkingAllowed = false,
            commercialUse = StockAssetEngine.CommercialUsePolicy.ALLOWED,
            derivatives = StockAssetEngine.DerivativePolicy.ALLOWED
        )
        StockAssetEngine.Provider.FREESOUND -> StockAssetEngine.ProviderCapability(
            provider = provider,
            configured = false,
            access = StockAssetEngine.ProviderAccess.API_KEY_REQUIRED,
            unavailableReason = null,
            termsUrl = StockAssetEngine.FREESOUND_API_DOCS,
            sourceBrandingRequired = true,
            attributionRequired = true,
            cacheTtlHours = null,
            hotlinkingAllowed = false,
            commercialUse = StockAssetEngine.CommercialUsePolicy.NON_COMMERCIAL_ONLY,
            derivatives = StockAssetEngine.DerivativePolicy.PER_ASSET_LICENSE
        )
        StockAssetEngine.Provider.FREE_MUSIC_ARCHIVE -> StockAssetEngine.ProviderCapability(
            provider = provider,
            configured = false,
            access = StockAssetEngine.ProviderAccess.MANUAL_SOURCE_REVIEW_REQUIRED,
            unavailableReason = null,
            termsUrl = StockAssetEngine.FMA_LICENSE_GUIDE,
            sourceBrandingRequired = true,
            attributionRequired = true,
            cacheTtlHours = null,
            hotlinkingAllowed = false,
            commercialUse = StockAssetEngine.CommercialUsePolicy.PER_ASSET_LICENSE,
            derivatives = StockAssetEngine.DerivativePolicy.PER_ASSET_LICENSE
        )
    }
}

internal fun defaultReuseConstraints(
    provider: StockAssetEngine.Provider,
    licenseName: String
): StockAssetEngine.ReuseConstraints {
    val lower = licenseName.lowercase()
    val providerCapability = providerCapabilityFor(provider)
    val commercialUse = when {
        lower.contains("cc0") || lower.contains("public domain") -> StockAssetEngine.CommercialUsePolicy.ALLOWED
        lower.contains("noncommercial") || lower.contains("-nc") -> StockAssetEngine.CommercialUsePolicy.NON_COMMERCIAL_ONLY
        providerCapability.commercialUse == StockAssetEngine.CommercialUsePolicy.ALLOWED -> StockAssetEngine.CommercialUsePolicy.ALLOWED
        providerCapability.commercialUse == StockAssetEngine.CommercialUsePolicy.NON_COMMERCIAL_ONLY ->
            StockAssetEngine.CommercialUsePolicy.NON_COMMERCIAL_ONLY
        lower.isBlank() -> StockAssetEngine.CommercialUsePolicy.UNKNOWN
        else -> StockAssetEngine.CommercialUsePolicy.PER_ASSET_LICENSE
    }
    val derivatives = when {
        lower.contains("no derivatives") || lower.contains("-nd") -> StockAssetEngine.DerivativePolicy.NO_DERIVATIVES
        lower.contains("sharealike") || lower.contains("-sa") -> StockAssetEngine.DerivativePolicy.SHARE_ALIKE
        providerCapability.derivatives == StockAssetEngine.DerivativePolicy.ALLOWED -> StockAssetEngine.DerivativePolicy.ALLOWED
        lower.isBlank() -> StockAssetEngine.DerivativePolicy.UNKNOWN
        else -> StockAssetEngine.DerivativePolicy.PER_ASSET_LICENSE
    }
    return StockAssetEngine.ReuseConstraints(
        attributionRequired = providerCapability.attributionRequired && !lower.contains("cc0"),
        commercialUse = commercialUse,
        derivatives = derivatives
    )
}
