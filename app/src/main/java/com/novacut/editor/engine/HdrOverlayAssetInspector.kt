package com.novacut.editor.engine

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Watermark

/**
 * Inspects overlay assets once before export. The result is deliberately
 * conservative: an unreadable or unrecognised bitmap is not treated as HDR
 * safe, so Media3 cannot discover the missing gain map after rendering starts.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object HdrOverlayAssetInspector {
    fun inspect(
        context: Context,
        textOverlays: List<TextOverlay>,
        imageOverlays: List<ImageOverlay>,
        watermark: Watermark?,
    ): HdrOverlaySummary {
        val animated = imageOverlays.filter {
            ExportAnimatedImageOverlay.isAnimatedSource(context, it.sourceUri)
        }
        val still = imageOverlays - animated.toSet()
        val gainMappedStill = still.filter {
            HdrBitmapOverlaySupport.decodeHasGainMap(context, it.sourceUri)
        }
        val watermarkHasGainMap = watermark?.let {
            HdrBitmapOverlaySupport.decodeHasGainMap(context, it.sourceUri)
        } == true

        return HdrOverlaySummary(
            textOverlayCount = textOverlays.size,
            strokedTextOverlayCount = textOverlays.count { it.strokeWidth > 0f },
            imageOverlayCount = still.size,
            gainMappedImageOverlayCount = gainMappedStill.size,
            animatedImageOverlayCount = animated.size,
            watermarkPresent = watermark != null,
            watermarkHasGainMap = watermarkHasGainMap,
            maxConcurrentHdrSamplerCount = maxConcurrentHdrSamplerCount(
                textOverlays = textOverlays,
                gainMappedImages = gainMappedStill,
                watermarkHasGainMap = watermarkHasGainMap,
            ),
        )
    }

    private fun maxConcurrentHdrSamplerCount(
        textOverlays: List<TextOverlay>,
        gainMappedImages: List<ImageOverlay>,
        watermarkHasGainMap: Boolean,
    ): Int? {
        if (textOverlays.isEmpty() && gainMappedImages.isEmpty() && !watermarkHasGainMap) return null
        val events = buildList {
            textOverlays.forEach { add(it.startTimeMs to 1); add(it.endTimeMs to -1) }
            gainMappedImages.forEach { add(it.startTimeMs to 2); add(it.endTimeMs to -2) }
        }.groupingBy { it.first }.fold(0) { sum, event -> sum + event.second }
        var current = if (watermarkHasGainMap) 2 else 0
        var maximum = current
        events.toSortedMap().forEach { (_, delta) ->
            current = (current + delta).coerceAtLeast(0)
            maximum = maxOf(maximum, current)
        }
        return maximum
    }
}
