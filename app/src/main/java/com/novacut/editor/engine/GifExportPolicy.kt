package com.novacut.editor.engine

import kotlin.math.roundToInt

internal fun aspectPreservingThumbnailSize(
    targetWidth: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    fallbackHeight: Int = 90,
): Pair<Int, Int> {
    val width = targetWidth.coerceAtLeast(1)
    val height = if (sourceWidth > 0 && sourceHeight > 0) {
        (width.toDouble() * sourceHeight / sourceWidth).roundToInt().coerceAtLeast(1)
    } else {
        fallbackHeight.coerceAtLeast(1)
    }
    return width to height
}

internal fun quantizedGifRgb(quantized: Int): Int {
    val red = (quantized ushr 8) and 0xF0
    val green = quantized and 0xF0
    val blue = (quantized and 0x0F) shl 4
    return (red shl 16) or (green shl 8) or blue
}

internal fun nearestGifPaletteIndex(rgb: Int, palette: List<Int>): Int {
    if (palette.isEmpty()) return 0
    val red = (rgb ushr 16) and 0xFF
    val green = (rgb ushr 8) and 0xFF
    val blue = rgb and 0xFF
    var bestIndex = 0
    var bestDistance = Long.MAX_VALUE
    palette.forEachIndexed { index, color ->
        val redDelta = red - ((color ushr 16) and 0xFF)
        val greenDelta = green - ((color ushr 8) and 0xFF)
        val blueDelta = blue - (color and 0xFF)
        val distance = redDelta.toLong() * redDelta +
            greenDelta.toLong() * greenDelta +
            blueDelta.toLong() * blueDelta
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}
