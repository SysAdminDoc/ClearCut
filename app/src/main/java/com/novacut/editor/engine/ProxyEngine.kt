package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.novacut.editor.model.ProxyResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Generates low-resolution proxy files for smooth timeline editing.
 * Proxies are stored in app cache and swapped with originals during export.
 */
@Singleton
class ProxyEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val proxyDir = File(context.cacheDir, "proxies").also { it.mkdirs() }
    private val proxyMap = ConcurrentHashMap<String, Uri>()

    // Per-source-key mutexes serialise concurrent `generateProxy(sameUri)`
    // calls. Without this, two near-simultaneous invocations both pass the
    // `outFile.exists()` check (line 61), both start a Transformer writing
    // to the same `proxy_<hash>.mp4`, and the second write corrupts or
    // truncates the first. computeIfAbsent guarantees only one Mutex
    // instance per key without a coarse-grained lock on the whole map.
    private val perKeyMutex = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private fun mutexFor(key: String): kotlinx.coroutines.sync.Mutex =
        perKeyMutex.computeIfAbsent(key) { kotlinx.coroutines.sync.Mutex() }

    private fun keyFor(sourceUri: Uri): String {
        val bytes = sourceUri.toString().toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun proxyFileForKey(key: String): File = File(proxyDir, "proxy_$key.mp4")

    private fun canonicalManagedProxyFile(file: File): File? {
        val proxyRoot = runCatching { proxyDir.canonicalFile }.getOrNull() ?: return null
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return canonicalFile.takeIf {
            it.parentFile == proxyRoot &&
                it.name.startsWith("proxy_") &&
                it.name.endsWith(".mp4")
        }
    }

    fun deleteProxyUri(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val file = uri.path?.let(::File) ?: return false
        val managedFile = canonicalManagedProxyFile(file) ?: return false
        proxyMap.entries.removeIf { it.value == uri }
        return managedFile.isFile && managedFile.delete()
    }

    fun proxyFileLength(uri: Uri): Long {
        if (uri.scheme != "file") return 0L
        val file = uri.path?.let(::File) ?: return 0L
        val managedFile = canonicalManagedProxyFile(file) ?: return 0L
        return managedFile.takeIf { it.isFile }?.length() ?: 0L
    }

    fun getProxyUri(sourceUri: Uri): Uri? {
        return proxyMap[keyFor(sourceUri)]
    }

    fun hasProxy(sourceUri: Uri): Boolean {
        val key = keyFor(sourceUri)
        val file = proxyFileForKey(key)
        return file.isFile.also { if (it) proxyMap[key] = Uri.fromFile(file) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun generateProxy(
        sourceUri: Uri,
        resolution: ProxyResolution,
        onProgress: (Float) -> Unit = {}
    ): Uri? = withContext(Dispatchers.Main) {
        val key = keyFor(sourceUri)
        val outFile = proxyFileForKey(key)

        // Hold the per-key mutex across the existence check + transformer
        // start so concurrent callers for the same source serialise: the
        // second caller sees the completed file on re-entry instead of
        // kicking off a duplicate render.
        mutexFor(key).lock()
        try {
            if (outFile.exists()) {
                proxyMap[key] = Uri.fromFile(outFile)
                return@withContext Uri.fromFile(outFile)
            }
        } catch (t: Throwable) {
            mutexFor(key).unlock()
            throw t
        }

        try {
            suspendCancellableCoroutine { cont ->
                val mediaItem = MediaItem.fromUri(sourceUri)

                // Downscale to proxy resolution using scale factor
                // HALF = 540p, QUARTER = 270p, EIGHTH = 135p (based on 1080p source)
                val targetHeight = (1080 * resolution.scale).toInt().coerceAtLeast(120)
                val presentation = Presentation.createForHeight(targetHeight)

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), listOf(presentation)))
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                            if (!cont.isActive) {
                                outFile.delete()
                                proxyMap.remove(key)
                                return
                            }
                            if (outFile.isFile && outFile.length() > 0L) {
                                val proxyUri = Uri.fromFile(outFile)
                                proxyMap[key] = proxyUri
                                cont.resume(proxyUri)
                            } else {
                                outFile.delete()
                                cont.resume(null)
                            }
                        }
                        override fun onError(composition: Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                            Log.e("ProxyEngine", "Proxy generation failed", exportException)
                            outFile.delete()
                            proxyMap.remove(key)
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                    .build()

                @Suppress("DEPRECATION")
                val sequence = EditedMediaItemSequence.Builder().addItem(editedItem).build()
                transformer.start(
                    Composition.Builder(sequence).build(),
                    outFile.absolutePath
                )

                cont.invokeOnCancellation {
                    transformer.cancel()
                    outFile.delete()
                    proxyMap.remove(key)
                }
            }
        } catch (e: Exception) {
            Log.e("ProxyEngine", "Proxy generation error", e)
            proxyMap.remove(key)
            null
        } finally {
            // Always release the per-key mutex so the next caller (or a
            // retry after a failure) can try again. Using runCatching so a
            // stray mutex-state mismatch can't mask the real exception
            // being propagated out of this suspend fn.
            runCatching { mutexFor(key).unlock() }
        }
    }

    fun clearProxies() {
        proxyDir.listFiles()?.forEach { file ->
            canonicalManagedProxyFile(file)?.takeIf { it.isFile }?.delete()
        }
        proxyMap.clear()
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        proxyDir.listFiles()?.sumOf { file ->
            canonicalManagedProxyFile(file)?.takeIf { it.isFile }?.length() ?: 0L
        } ?: 0L
    }
}
