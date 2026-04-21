package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.ProxyResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 3-tier proxy media management following DaVinci Resolve / Premiere Pro patterns.
 *
 * Tiers:
 * 1. Thumbnail (JPEG strips for timeline scrubbing) — generated on import
 * 2. Proxy (540p H.264 CRF 28 for editing) — background-generated via WorkManager
 * 3. Original (full-res for export) — always used for final render
 *
 * Auto-switch: Use proxy during preview if original > 1080p. Always original for export.
 * Storage: Proxy files stored in app's cache dir with .proxy.mp4 suffix.
 *
 * Dependencies:
 *   - ProxyEngine (existing) for actual transcoding via Media3 Transformer
 *   - WorkManager (androidx.work:work-runtime-ktx:2.9.+) for background generation
 */
@Singleton
class ProxyWorkflowEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proxyEngine: ProxyEngine
) {
    enum class MediaTier { THUMBNAIL, PROXY, ORIGINAL }

    data class MediaEntry(
        val originalUri: Uri,
        val proxyUri: Uri? = null,
        val thumbnailStripPath: String? = null,
        val originalWidth: Int = 0,
        val originalHeight: Int = 0,
        val proxyGenerated: Boolean = false,
        val proxyGenerating: Boolean = false
    )

    private val generationMutex = Mutex()

    private val _entries = MutableStateFlow<Map<String, MediaEntry>>(emptyMap())
    val entries: StateFlow<Map<String, MediaEntry>> = _entries

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    /**
     * Register a new media source. Starts thumbnail extraction immediately.
     */
    suspend fun registerMedia(clipId: String, uri: Uri, width: Int, height: Int) {
        _entries.update { current -> current + (clipId to MediaEntry(
            originalUri = uri,
            originalWidth = width,
            originalHeight = height
        )) }
    }

    /**
     * Get the best available URI for a clip based on current mode.
     * During preview: return proxy if available and original > 1080p
     * During export: always return original
     */
    fun getMediaUri(clipId: String, forExport: Boolean = false): Uri {
        val entry = _entries.value[clipId] ?: return Uri.EMPTY
        if (forExport) return entry.originalUri
        // Use proxy during preview if original is high-res and proxy exists
        if (entry.proxyGenerated && entry.proxyUri != null && entry.originalHeight > 1080) {
            return entry.proxyUri
        }
        return entry.originalUri
    }

    /**
     * Generate proxies for all registered media that needs them.
     * Should be called via WorkManager for background processing.
     */
    suspend fun generateAllProxies(
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            _isGenerating.value = true
            val needsProxy = _entries.value.filter { !it.value.proxyGenerated && it.value.originalHeight > 1080 }
            if (needsProxy.isEmpty()) {
                onProgress(1f)
                _isGenerating.value = false
                return@withLock
            }
            var completed = 0

            for ((clipId, entry) in needsProxy) {
                // Check cancellation before each potentially multi-minute proxy job so
                // WorkManager can stop the worker promptly without force-killing the process.
                ensureActive()
                try {
                    _entries.update { current -> current + (clipId to entry.copy(proxyGenerating = true)) }

                    // Use QUARTER resolution (540p from 4K) for proxy editing
                    val proxyUri = proxyEngine.generateProxy(
                        entry.originalUri,
                        ProxyResolution.QUARTER
                    ) { /* per-clip progress */ }

                    _entries.update { current -> current + (clipId to entry.copy(
                        proxyUri = proxyUri,
                        proxyGenerated = proxyUri != null,
                        proxyGenerating = false
                    )) }
                } catch (e: Exception) {
                    _entries.update { current -> current + (clipId to entry.copy(proxyGenerating = false)) }
                }
                completed++
                onProgress(completed.toFloat() / needsProxy.size)
            }
            _isGenerating.value = false
        }
    }

    /**
     * Delete all proxy files to reclaim storage.
     */
    suspend fun deleteAllProxies() = withContext(Dispatchers.IO) {
        val current = _entries.value
        for ((_, entry) in current) {
            entry.proxyUri?.let { proxyEngine.deleteProxyUri(it) }
        }
        _entries.update { it.mapValues { (_, e) -> e.copy(proxyUri = null, proxyGenerated = false) } }
    }

    /**
     * Get total proxy storage usage in bytes.
     */
    suspend fun getProxyStorageBytes(): Long = withContext(Dispatchers.IO) {
        _entries.value.values.sumOf { entry ->
            entry.proxyUri?.let { proxyEngine.proxyFileLength(it) } ?: 0L
        }
    }
}
