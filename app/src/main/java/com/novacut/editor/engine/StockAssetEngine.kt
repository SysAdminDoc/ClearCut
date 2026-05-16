package com.novacut.editor.engine

import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- stock asset library integration. See ROADMAP.md Tier C.7.
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
        val attribution: String
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

    fun isProviderConfigured(provider: Provider): Boolean = false

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
        "${asset.title} by ${asset.author} (${asset.provider.displayName}, ${asset.licenseName})"

    companion object {
        private const val TAG = "StockAssets"

        const val PEXELS_API_DOCS = "https://www.pexels.com/api/documentation/"
        const val PIXABAY_API_DOCS = "https://pixabay.com/api/docs/"
        const val FREESOUND_API_DOCS = "https://freesound.org/docs/api/"
        const val FMA_DATA_HOST = "https://freemusicarchive.org/"
    }
}
