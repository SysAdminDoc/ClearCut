package com.novacut.editor.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.transformer.AssetLoader
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException

/**
 * Transformer's built-in decoder factory only ever tries the first decoder Android lists for
 * a format. Some OEM codecs under-report what they can open (androidx/media#2751), so a
 * source that a second decoder would play fails the whole export. This factory lets Media3
 * move on to the next decoder and reports when it had to, so the export record says the run
 * used a fallback instead of passing it off as an exact result.
 */
@OptIn(UnstableApi::class)
internal object Media3DecoderFallback {

    fun assetLoaderFactory(
        context: Context,
        mediaCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
        onFallback: (String) -> Unit,
    ): AssetLoader.Factory {
        val decoderFactory = DefaultDecoderFactory.Builder(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(mediaCodecSelector)
            .setListener { codecName, failures ->
                val track = failures.firstNotNullOfOrNull { it.codecInfo }?.let { if (it.isVideo) "video" else "audio" }
                fallbackNote(codecName, track, failures.map(::describe))?.let(onFallback)
            }
            .build()
        // Transformer builds the same default when no factory is set, except that it also passes
        // the per-export LogSessionId it creates inside start(). A factory set up front can't
        // receive that id, so these decoders aren't tagged with the editing session in the
        // platform's MediaMetrics on API 35+. Transformer's own editing metrics are unaffected.
        return DefaultAssetLoaderFactory(context, decoderFactory, Clock.DEFAULT, null)
    }

    /**
     * The export note for a decoder that started after others failed, or null when the first
     * one worked. [track] is "video" or "audio" when Media3 said which.
     */
    fun fallbackNote(codecName: String, track: String?, failedDecoders: List<String>): String? {
        if (failedDecoders.isEmpty()) return null
        val decoder = track?.let { "$it decoder" } ?: "decoder"
        return "Media3 $decoder fallback applied: $codecName started after " +
            "${failedDecoders.size} decoder(s) failed (${failedDecoders.joinToString()})."
    }

    private fun describe(failure: ExportException): String =
        failure.codecInfo?.name?.let { "$it: ${failure.errorCodeName}" } ?: failure.errorCodeName
}
