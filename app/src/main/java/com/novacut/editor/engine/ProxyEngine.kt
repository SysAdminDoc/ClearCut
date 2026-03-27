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

    private fun keyFor(sourceUri: Uri): String {
        val bytes = sourceUri.toString().toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun getProxyUri(sourceUri: Uri): Uri? {
        return proxyMap[keyFor(sourceUri)]
    }

    fun hasProxy(sourceUri: Uri): Boolean {
        val key = keyFor(sourceUri)
        val file = File(proxyDir, "proxy_$key.mp4")
        return file.exists().also { if (it) proxyMap[key] = Uri.fromFile(file) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun generateProxy(
        sourceUri: Uri,
        resolution: ProxyResolution,
        onProgress: (Float) -> Unit = {}
    ): Uri? = withContext(Dispatchers.Main) {
        val key = keyFor(sourceUri)
        val outFile = File(proxyDir, "proxy_$key.mp4")

        if (outFile.exists()) {
            proxyMap[key] = Uri.fromFile(outFile)
            return@withContext Uri.fromFile(outFile)
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
                            proxyMap[key] = Uri.fromFile(outFile)
                            cont.resume(Uri.fromFile(outFile))
                        }
                        override fun onError(composition: Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                            Log.e("ProxyEngine", "Proxy generation failed", exportException)
                            outFile.delete()
                            cont.resume(null)
                        }
                    })
                    .build()

                transformer.start(
                    Composition.Builder(
                        EditedMediaItemSequence.Builder(editedItem).build()
                    ).build(),
                    outFile.absolutePath
                )

                cont.invokeOnCancellation {
                    transformer.cancel()
                    outFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ProxyEngine", "Proxy generation error", e)
            null
        }
    }

    fun clearProxies() {
        proxyDir.listFiles()?.forEach { it.delete() }
        proxyMap.clear()
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        proxyDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
