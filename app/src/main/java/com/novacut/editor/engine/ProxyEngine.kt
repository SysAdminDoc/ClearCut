package com.novacut.editor.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.novacut.editor.engine.AppLog
import androidx.media3.common.C
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
 * What a proxy-cache clear actually did.
 *
 * Clearing is legitimately partial: proxies belonging to an open project are kept
 * on purpose, and a delete can still fail. Settings used to report "Proxy cache
 * cleared" unconditionally, so both cases read as a full success.
 */
data class ProxyClearResult(
    val deleted: Int,
    val failed: Int,
    val keptInUse: Int,
) {
    /** True when every proxy that was eligible for deletion is gone. */
    val isComplete: Boolean get() = failed == 0
}

/**
 * Generates low-resolution proxy files for smooth timeline editing.
 * Proxies are stored in app cache and swapped with originals during export.
 */
@Singleton
class ProxyEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    memoryTrimRegistry: MemoryTrimRegistry,
) {
    private val proxyDir = File(context.cacheDir, "proxies").also { it.mkdirs() }
    private val proxyMap = ConcurrentHashMap<String, Uri>()
    private val activeProxyKeys = ConcurrentHashMap.newKeySet<String>()

    // Per-source-key mutexes serialise concurrent `generateProxy(sameUri)`
    // calls. Without this, two near-simultaneous invocations both pass the
    // `outFile.exists()` check (line 61), both start a Transformer writing
    // to the same `proxy_<hash>.mp4`, and the second write corrupts or
    // truncates the first. computeIfAbsent guarantees only one Mutex
    // instance per key without a coarse-grained lock on the whole map.
    private val perKeyMutex = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private fun mutexFor(key: String): kotlinx.coroutines.sync.Mutex =
        perKeyMutex.computeIfAbsent(key) { kotlinx.coroutines.sync.Mutex() }

    init {
        memoryTrimRegistry.register(
            MemoryTrimAction.CLEAR_PROXY_SCRATCH,
            "proxy.generatedMediaCache",
        ) {
            clearProxies()
        }
    }

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

    /**
     * How tall [sourceUri]'s proxy would be at [resolution], or why it needs none, from the
     * source's displayed height.
     */
    suspend fun planProxy(sourceUri: Uri, resolution: ProxyResolution): ProxyResolutionPolicy.Plan =
        ProxyResolutionPolicy.plan(sourceDisplayHeight(sourceUri), resolution)

    /** The height [uri] is shown at, rotation included, or 0 when it can't be read. */
    private suspend fun sourceDisplayHeight(uri: Uri): Int = withContext(Dispatchers.IO) {
        val retrieverLease = CodecInstanceBudget.acquireRetriever(context.contentResolver.getType(uri))
        val retriever = retrieverLease.resource
        try {
            retriever.setDataSource(context, uri)
            fun read(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            ProxyResolutionPolicy.displayHeight(
                codedWidth = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                codedHeight = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                rotationDegrees = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
            )
        } catch (e: Exception) {
            AppLog.w("ProxyEngine", "Couldn't read source dimensions for a proxy: ${e.javaClass.simpleName}")
            0
        } finally {
            retrieverLease.close()
        }
    }

    /**
     * Renders a proxy at [resolution] of the source's displayed height. Returns null when the
     * render fails, and also when [plan] (worked out here when not passed) says the source is
     * already small enough to edit directly; the reason is logged.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun generateProxy(
        sourceUri: Uri,
        resolution: ProxyResolution,
        plan: ProxyResolutionPolicy.Plan? = null,
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
            // A previous export attempt may have left a zero-byte file on disk
            // (e.g. the transformer crashed before writing any data).  Treat
            // zero-length files as absent so we re-render rather than returning
            // a broken URI — mirrors the check inside the onCompleted callback.
            if (outFile.isFile && outFile.length() > 0L) {
                proxyMap[key] = Uri.fromFile(outFile)
                return@withContext Uri.fromFile(outFile)
            }
        } catch (t: Throwable) {
            mutexFor(key).unlock()
            throw t
        }

        val targetHeight = try {
            when (val resolved = plan ?: planProxy(sourceUri, resolution)) {
                is ProxyResolutionPolicy.Plan.Generate -> resolved.targetHeight
                is ProxyResolutionPolicy.Plan.UseSource -> {
                    AppLog.i("ProxyEngine", "No proxy generated: ${resolved.reason}")
                    null
                }
            }
        } catch (t: Throwable) {
            mutexFor(key).unlock()
            throw t
        }
        if (targetHeight == null) {
            mutexFor(key).unlock()
            return@withContext null
        }

        activeProxyKeys.add(key)
        var pipelineLease: CodecLease<Unit>? = null
        try {
            // Transformer owns the source decoder and output encoder. Hold a
            // shared video-family permit for the complete proxy lifecycle so
            // proxy generation queues behind preview/retriever work instead
            // of turning a codec-ceiling collision into a failed render.
            pipelineLease = CodecInstanceBudget.acquirePipelineBlocking()
            suspendCancellableCoroutine { cont ->
                val mediaItem = MediaItem.fromUri(sourceUri)

                // Presentation sizes the displayed (rotated) frame, so the proxy keeps the
                // source's orientation at the planned height.
                val presentation = Presentation.createForHeight(targetHeight)
                    .copyWithUnsetSideRoundedTo(Media3ExportRobustnessPolicy.ENCODER_DIMENSION_DIVISOR)

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), listOf(presentation)))
                    .build()

                val transformer = Transformer.Builder(context)
                    .setAssetLoaderFactory(
                        Media3DecoderFallback.assetLoaderFactory(context) { note -> AppLog.w("ProxyEngine", note) }
                    )
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
                            AppLog.e("ProxyEngine", "Proxy generation failed", exportException)
                            outFile.delete()
                            proxyMap.remove(key)
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                    .build()

                val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                    .addItem(editedItem)
                    .build()
                transformer.start(
                    Composition.Builder(sequence).build(),
                    outFile.absolutePath
                )

                cont.invokeOnCancellation {
                    // invokeOnCancellation runs on whichever thread triggers
                    // cancellation (e.g. WorkManager's executor when a
                    // CoroutineWorker is stopped mid-transcode). Media3
                    // Transformer.cancel() must run on its application thread
                    // (main) or it throws IllegalStateException — and an
                    // exception inside a cancellation handler crashes the
                    // process. Post it to the main looper.
                    Handler(Looper.getMainLooper()).post {
                        runCatching { transformer.cancel() }
                        outFile.delete()
                        proxyMap.remove(key)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Preserve structured cancellation — don't mask it as a null result.
            proxyMap.remove(key)
            throw e
        } catch (e: Exception) {
            AppLog.e("ProxyEngine", "Proxy generation error", e)
            proxyMap.remove(key)
            null
        } finally {
            pipelineLease?.close()
            activeProxyKeys.remove(key)
            // Always release the per-key mutex so the next caller (or a
            // retry after a failure) can try again. Using runCatching so a
            // stray mutex-state mismatch can't mask the real exception
            // being propagated out of this suspend fn.
            runCatching { mutexFor(key).unlock() }
        }
    }

    fun clearProxies(): ProxyClearResult {
        val activeKeys = activeProxyKeys.toSet()
        var deleted = 0
        var failed = 0
        var keptInUse = 0
        proxyDir.listFiles()?.forEach { file ->
            val managedFile = canonicalManagedProxyFile(file)?.takeIf { it.isFile } ?: return@forEach
            val key = managedFile.name.removePrefix("proxy_").removeSuffix(".mp4")
            if (key in activeKeys) {
                keptInUse++
                return@forEach
            }
            // A proxy an open project is not using can still refuse to delete: another
            // process may hold the handle. Reporting "cleared" for a file that is still
            // on disk is the failure this counter exists to prevent.
            if (managedFile.delete()) {
                deleted++
                proxyMap.remove(key)
            } else {
                failed++
            }
        }
        proxyMap.keys.removeIf { it !in activeKeys }
        return ProxyClearResult(deleted = deleted, failed = failed, keptInUse = keptInUse)
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        proxyDir.listFiles()?.sumOf { file ->
            canonicalManagedProxyFile(file)?.takeIf { it.isFile }?.length() ?: 0L
        } ?: 0L
    }
}
