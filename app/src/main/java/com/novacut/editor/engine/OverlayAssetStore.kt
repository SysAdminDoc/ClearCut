package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.novacut.editor.model.ImageOverlayType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

sealed class OverlayAssetImportResult {
    data class Imported(
        val uri: Uri,
        val type: ImageOverlayType,
        val kind: OverlayAssetKind,
    ) : OverlayAssetImportResult()

    data class Rejected(
        val reason: OverlayAssetRejectionReason,
        val userMessage: String,
    ) : OverlayAssetImportResult()
}

enum class OverlayAssetKind {
    BUNDLED_STICKER,
    STILL_IMAGE,
    ANIMATED_IMAGE,
}

enum class OverlayAssetRejectionReason {
    ANIMATED_GIF_UNSUPPORTED,
    UNREADABLE_SOURCE,
    TOO_LARGE,
}

data class BundledStickerRef(
    val category: String,
    val index: Int,
)

data class OverlayAssetDecision(
    val accepted: Boolean,
    val kind: OverlayAssetKind? = null,
    val extension: String? = null,
    val rejectionReason: OverlayAssetRejectionReason? = null,
)

/**
 * Imports sticker/image overlay sources into ClearCut-owned storage before the
 * project state is mutated. This avoids saving Photo Picker one-shot grants or
 * synthetic shelf URIs that cannot survive process death, reboot, archive, or
 * export.
 */
@Singleton
class OverlayAssetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun importImageOverlay(
        sourceUri: Uri,
        requestedType: ImageOverlayType,
    ): OverlayAssetImportResult = withContext(Dispatchers.IO) {
        try {
            val bundled = parseBundledStickerUri(sourceUri.toString())
            if (bundled != null) {
                val sticker = renderBundledSticker(bundled)
                return@withContext OverlayAssetImportResult.Imported(
                    uri = Uri.fromFile(sticker),
                    type = ImageOverlayType.STICKER,
                    kind = OverlayAssetKind.BUNDLED_STICKER,
                )
            }

            if (isAppOwnedOverlayUri(sourceUri)) {
                return@withContext OverlayAssetImportResult.Imported(
                    uri = sourceUri,
                    type = requestedType.normalizedStillType(),
                    kind = OverlayAssetKind.STILL_IMAGE,
                )
            }

            val mimeType = runCatching { context.contentResolver.getType(sourceUri) }
                .getOrNull()
            val decision = decideImport(
                mimeType = mimeType,
                fileName = sourceUri.lastPathSegment,
                requestedType = requestedType,
            )
            if (!decision.accepted) {
                return@withContext OverlayAssetImportResult.Rejected(
                    reason = decision.rejectionReason ?: OverlayAssetRejectionReason.UNREADABLE_SOURCE,
                    userMessage = unsupportedGifMessage,
                )
            }

            val copied = copyExternalOverlayAsset(
                sourceUri = sourceUri,
                extension = decision.extension ?: "img",
            )
            OverlayAssetImportResult.Imported(
                uri = Uri.fromFile(copied),
                type = requestedType.normalizedStillType(),
                kind = OverlayAssetKind.STILL_IMAGE,
            )
        } catch (tooLarge: OverlayAssetTooLargeException) {
            OverlayAssetImportResult.Rejected(
                reason = OverlayAssetRejectionReason.TOO_LARGE,
                userMessage = "Sticker image is too large. Import an image under ${MAX_OVERLAY_BYTES / (1024 * 1024)} MB.",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import overlay asset $sourceUri", e)
            OverlayAssetImportResult.Rejected(
                reason = OverlayAssetRejectionReason.UNREADABLE_SOURCE,
                userMessage = "Could not import that sticker image.",
            )
        }
    }

    private fun copyExternalOverlayAsset(sourceUri: Uri, extension: String): File {
        val dir = importsDir().apply { mkdirs() }
        val stem = "overlay-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val target = File(dir, "$stem.$extension")
        val partial = File(dir, "$stem.$extension.partial")
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            partial.outputStream().use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytes += read
                    if (bytes > MAX_OVERLAY_BYTES) {
                        throw OverlayAssetTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IOException("provider returned null stream")

        if (bytes <= 0L) {
            partial.delete()
            throw IOException("empty overlay source")
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    private fun renderBundledSticker(ref: BundledStickerRef): File {
        val dir = File(bundledDir(), ref.category).apply { mkdirs() }
        val target = File(dir, "${ref.index}.png")
        if (target.exists() && target.length() > 0L) return target

        val partial = File(dir, "${ref.index}.png.partial")
        val bitmap = Bitmap.createBitmap(STICKER_BITMAP_SIZE, STICKER_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            when (ref.category) {
                "shapes" -> drawShapeSticker(canvas, ref.index)
                "arrows" -> drawArrowSticker(canvas, ref.index)
                "social" -> drawTextSticker(canvas, socialStickerText(ref.index), 104f)
                else -> drawTextSticker(canvas, emojiStickerText(ref.index), 132f)
            }
            partial.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("PNG compression failed")
                }
            }
        } finally {
            bitmap.recycle()
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    private fun isAppOwnedOverlayUri(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        return runCatching {
            val candidate = File(path).canonicalFile
            candidate.path.startsWith(overlayRootDir().canonicalFile.path)
        }.getOrDefault(false)
    }

    private fun overlayRootDir(): File = File(context.filesDir, OVERLAY_ROOT)
    private fun bundledDir(): File = File(overlayRootDir(), "bundled")
    private fun importsDir(): File = File(overlayRootDir(), "imports")

    private fun drawTextSticker(canvas: Canvas, text: String, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(18f, 0f, 8f, Color.argb(180, 0, 0, 0))
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 26, 28, 44)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(256f, 256f, 220f, bg)
        val y = 256f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, 256f, y, paint)
    }

    private fun drawShapeSticker(canvas: Canvas, index: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SHAPE_COLORS[index.mod(SHAPE_COLORS.size)]
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 20f
            strokeJoin = Paint.Join.ROUND
        }
        when (index.mod(6)) {
            0 -> canvas.drawCircle(256f, 256f, 170f, fill)
            1 -> canvas.drawRoundRect(92f, 92f, 420f, 420f, 56f, 56f, fill)
            2 -> canvas.drawPath(Path().apply {
                moveTo(256f, 72f); lineTo(438f, 420f); lineTo(74f, 420f); close()
            }, fill)
            3 -> canvas.drawPath(Path().apply {
                moveTo(256f, 56f); lineTo(456f, 256f); lineTo(256f, 456f); lineTo(56f, 256f); close()
            }, fill)
            4 -> canvas.drawRoundRect(72f, 124f, 440f, 388f, 132f, 132f, fill)
            else -> canvas.drawOval(76f, 128f, 436f, 384f, fill)
        }
        canvas.drawCircle(256f, 256f, 198f, stroke)
    }

    private fun drawArrowSticker(canvas: Canvas, index: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ARROW_COLORS[index.mod(ARROW_COLORS.size)]
            style = Paint.Style.STROKE
            strokeWidth = 44f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val angle = (index.mod(8) * 45f - 90f) * (Math.PI.toFloat() / 180f)
        val startX = 256f - cos(angle) * 132f
        val startY = 256f - sin(angle) * 132f
        val endX = 256f + cos(angle) * 132f
        val endY = 256f + sin(angle) * 132f
        canvas.drawLine(startX, startY, endX, endY, paint)
        val headAngleA = angle + Math.PI.toFloat() * 0.75f
        val headAngleB = angle - Math.PI.toFloat() * 0.75f
        canvas.drawLine(endX, endY, endX + cos(headAngleA) * 92f, endY + sin(headAngleA) * 92f, paint)
        canvas.drawLine(endX, endY, endX + cos(headAngleB) * 92f, endY + sin(headAngleB) * 92f, paint)
    }

    private fun ImageOverlayType.normalizedStillType(): ImageOverlayType =
        if (this == ImageOverlayType.GIF) ImageOverlayType.IMAGE else this

    private class OverlayAssetTooLargeException : IOException("overlay asset too large")

    companion object {
        private const val TAG = "OverlayAssetStore"
        const val BUNDLED_STICKER_AUTHORITY = "com.novacut.editor.stickers"
        private const val OVERLAY_ROOT = "media/overlays"
        private const val STICKER_BITMAP_SIZE = 512
        private const val MAX_OVERLAY_BYTES = 25L * 1024L * 1024L
        private val SUPPORTED_CATEGORIES = setOf("emoji", "shapes", "arrows", "social")
        private val SHAPE_COLORS = intArrayOf(
            Color.rgb(245, 189, 230),
            Color.rgb(166, 227, 161),
            Color.rgb(137, 180, 250),
            Color.rgb(250, 179, 135),
        )
        private val ARROW_COLORS = intArrayOf(
            Color.rgb(249, 226, 175),
            Color.rgb(137, 220, 235),
            Color.rgb(243, 139, 168),
        )

        val unsupportedGifMessage: String =
            "Animated GIF stickers are not supported yet. Import a PNG, JPEG, or WebP sticker instead."

        fun parseBundledStickerUri(uri: String?): BundledStickerRef? {
            if (uri.isNullOrBlank()) return null
            val prefix = "content://$BUNDLED_STICKER_AUTHORITY/"
            if (!uri.startsWith(prefix)) return null
            val parts = uri.removePrefix(prefix).split('/').filter { it.isNotBlank() }
            if (parts.size != 2) return null
            val category = parts[0].lowercase(Locale.US)
            if (category !in SUPPORTED_CATEGORIES) return null
            val index = parts[1].toIntOrNull() ?: return null
            if (index < 0 || index >= stickerCountFor(category)) return null
            return BundledStickerRef(category, index)
        }

        fun decideImport(
            mimeType: String?,
            fileName: String?,
            requestedType: ImageOverlayType,
        ): OverlayAssetDecision {
            val isAnimated = requestedType == ImageOverlayType.GIF || isGif(mimeType, fileName)
                || isAnimatedWebp(mimeType, fileName)
            val extension = normalizedExtension(mimeType, fileName)
            return OverlayAssetDecision(
                accepted = true,
                kind = if (isAnimated) OverlayAssetKind.ANIMATED_IMAGE else OverlayAssetKind.STILL_IMAGE,
                extension = extension ?: if (isAnimated) "gif" else "img",
            )
        }

        private fun isAnimatedWebp(mimeType: String?, fileName: String?): Boolean {
            val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)
            return ext == "webp" && mimeType?.contains("webp", ignoreCase = true) == true
        }

        private fun isGif(mimeType: String?, fileName: String?): Boolean {
            val mime = mimeType?.lowercase(Locale.US)?.substringBefore(';')?.trim()
            if (mime == "image/gif") return true
            return fileName?.substringAfterLast('.', "")?.equals("gif", ignoreCase = true) == true
        }

        private fun normalizedExtension(mimeType: String?, fileName: String?): String? {
            val mime = mimeType?.lowercase(Locale.US)?.substringBefore(';')?.trim()
            val fromMime = when (mime) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                else -> mime?.substringAfter('/')?.takeIf { it.length in 2..8 }
            }
            if (!fromMime.isNullOrBlank()) return fromMime
            val ext = fileName
                ?.substringBefore('?')
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.US)
                ?.takeIf { it.length in 2..8 && it.all { c -> c.isLetterOrDigit() } }
            if (!ext.isNullOrBlank()) return ext
            return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        }

        private fun stickerCountFor(category: String): Int = when (category) {
            "emoji", "shapes", "arrows", "social" -> 12
            else -> 0
        }

        private fun emojiStickerText(index: Int): String = listOf(
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D",
            "\uD83E\uDD29", "\uD83D\uDE0E", "\uD83E\uDD14",
            "\uD83D\uDE31", "\uD83D\uDE4C", "\uD83D\uDD25",
            "\u2B50", "\uD83C\uDF89", "\u2764\uFE0F",
        )[index.mod(12)]

        private fun socialStickerText(index: Int): String = listOf(
            "LIKE", "NOPE", "CHAT", "PHOTO", "VIDEO", "MUSIC",
            "LOUD", "BELL", "LOOK", "OK", "NO", "PIN",
        )[index.mod(12)]
    }
}
