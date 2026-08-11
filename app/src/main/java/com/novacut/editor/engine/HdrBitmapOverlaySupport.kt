package com.novacut.editor.engine

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Gainmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlin.math.roundToInt

/** Android 14+ gain-map helpers shared by HDR preflight and Media3 overlays. */
internal object HdrBitmapOverlaySupport {
    private const val TAG = "HdrBitmapOverlaySupport"
    private const val MIN_GAINMAP_API = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun decodeHasGainMap(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < MIN_GAINMAP_API) return false
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bitmap ->
                    val result = bitmap.hasGainmap()
                    bitmap.recycleSafely()
                    result
                }
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect gain map ${uri.redacted()}", e)
            false
        }
    }

    /**
     * Matrix scaling can detach a gain map on some Android releases. Reattach
     * a proportionally scaled gain-map bitmap so Media3 still sees the source
     * as an Ultra HDR overlay after the export-size transform.
     */
    @SuppressLint("NewApi")
    fun scalePreservingGainMap(
        bitmap: Bitmap,
        scaleX: Float,
        scaleY: Float = scaleX,
    ): Bitmap {
        if (scaleX == 1f && scaleY == 1f) return bitmap
        val sourceGainMap = if (Build.VERSION.SDK_INT >= MIN_GAINMAP_API) {
            bitmap.gainmapOrNull()
        } else {
            null
        }
        val matrix = Matrix().apply { postScale(scaleX, scaleY) }
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (scaled !== bitmap && sourceGainMap != null && !scaled.hasGainmap()) {
            attachScaledGainMap(bitmap, scaled, sourceGainMap)
        }
        if (scaled !== bitmap) bitmap.recycleSafely()
        return scaled
    }

    @SuppressLint("NewApi")
    private fun attachScaledGainMap(
        source: Bitmap,
        target: Bitmap,
        sourceGainMap: Gainmap,
    ) {
        try {
            val sourceContents = sourceGainMap.gainmapContents
            val targetGainMapWidth = (sourceContents.width *
                (target.width.toFloat() / source.width.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)
            val targetGainMapHeight = (sourceContents.height *
                (target.height.toFloat() / source.height.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)
            val scaledContents = if (
                sourceContents.width == targetGainMapWidth &&
                sourceContents.height == targetGainMapHeight
            ) {
                sourceContents
            } else {
                Bitmap.createScaledBitmap(
                    sourceContents,
                    targetGainMapWidth,
                    targetGainMapHeight,
                    true,
                )
            }
            target.setGainmap(Gainmap(sourceGainMap, scaledContents))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to retain gain map while scaling bitmap", e)
        }
    }

    @SuppressLint("NewApi")
    private fun Bitmap.gainmapOrNull(): Gainmap? = takeIf { it.hasGainmap() }?.gainmap

    private fun Bitmap.recycleSafely() {
        try {
            if (!isRecycled) recycle()
        } catch (_: Exception) {
            // The platform already detached this bitmap.
        }
    }
}
