package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- community template marketplace. See ROADMAP.md Tier C.15.
 *
 * The `.clearcut-template` format already exists (v3.8 export/import + share
 * intent); this engine adds discovery and distribution via a self-hostable
 * registry. Default registry URL targets a GitHub-Releases-backed index; users
 * can point at their own in Settings.
 *
 * Registry contract (v1, JSON):
 *   {
 *     "schemaVersion": 1,
 *     "templates": [
 *       { "id": "...", "name": "...", "author": "...",
 *         "downloadUrl": "...", "previewUrl": "...",
 *         "tags": [...], "downloads": 0, "rating": null,
 *         "clearcutMinVersion": "3.8.0" }
 *     ]
 *   }
 */
@Singleton
class TemplateMarketplaceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class MarketplaceTemplate(
        val id: String,
        val name: String,
        val author: String,
        val description: String,
        val downloadUrl: String,
        val previewUrl: String,
        val tags: List<String>,
        val downloadCount: Int,
        val rating: Float?,
        val clearcutMinVersion: String,
        val sizeBytes: Long
    )

    data class SearchFilter(
        val query: String? = null,
        val tags: Set<String> = emptySet(),
        val sort: Sort = Sort.POPULAR,
        val page: Int = 1,
        val pageSize: Int = 30
    ) {
        enum class Sort { POPULAR, NEWEST, TOP_RATED, NAME_ASC }
    }

    private val _registryUrl = MutableStateFlow(DEFAULT_REGISTRY_URL)
    val registryUrl: StateFlow<String> = _registryUrl

    fun setRegistryUrl(url: String) { _registryUrl.value = url }

    suspend fun list(filter: SearchFilter = SearchFilter()): List<MarketplaceTemplate> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "list: stub -- marketplace backend not wired (${filter.query ?: "(no query)"})")
            emptyList()
        }

    suspend fun download(
        template: MarketplaceTemplate,
        destination: Uri,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "download: stub -- ${template.id}")
        false
    }

    companion object {
        private const val TAG = "TemplateMarket"
        const val DEFAULT_REGISTRY_URL = "https://clearcut.dev/marketplace/index.json"
    }
}
