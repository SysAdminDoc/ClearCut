package com.novacut.editor.engine

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.SourceHdrFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

private const val MEDIA_IMPORT_ENGINE_TAG = "MediaImportEngine"
private const val ULTRA_HDR_DECODE_MAX_SIDE = 1024
private const val GAINMAP_DIRECTION_SDR_TO_HDR = 0
private const val GAINMAP_DIRECTION_HDR_TO_SDR = 1

@Singleton
class MediaImportEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun inspectSourceColor(uri: Uri): SourceColorMetadata {
        val mimeType = resolveMimeType(uri)
        return if (isImageSource(uri, mimeType)) {
            inspectImage(uri, mimeType)
        } else {
            inspectVideo(uri, mimeType)
        }
    }

    private fun inspectImage(uri: Uri, mimeType: String?): SourceColorMetadata {
        val formats = buildSet {
            inspectUltraHdrGainMapFormat(uri)?.let { add(it) }
        }
        return SourceColorMetadata(
            mimeType = mimeType,
            hdrFormats = formats,
            inspectedAtMs = System.currentTimeMillis()
        )
    }

    private fun inspectVideo(uri: Uri, fallbackMimeType: String?): SourceColorMetadata {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            var firstVideoFormat: MediaFormat? = null
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val trackMimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (trackMimeType.startsWith("video/", ignoreCase = true)) {
                    firstVideoFormat = format
                    break
                }
            }

            firstVideoFormat?.let { format ->
                buildVideoSourceColorMetadata(format, System.currentTimeMillis())
            } ?: SourceColorMetadata(
                mimeType = fallbackMimeType,
                inspectedAtMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(MEDIA_IMPORT_ENGINE_TAG, "Unable to inspect source color metadata for $uri", e)
            SourceColorMetadata(
                mimeType = fallbackMimeType,
                inspectedAtMs = System.currentTimeMillis()
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun inspectUltraHdrGainMapFormat(uri: Uri): SourceHdrFormat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = max(info.size.width, info.size.height)
                if (maxSide > ULTRA_HDR_DECODE_MAX_SIDE) {
                    decoder.setTargetSampleSize(ceil(maxSide.toDouble() / ULTRA_HDR_DECODE_MAX_SIDE).toInt())
                }
            }
            try {
                if (!bitmap.hasGainmap()) {
                    null
                } else {
                    val direction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        bitmap.gainmap?.gainmapDirection
                    } else {
                        null
                    }
                    classifyGainMapFormat(Build.VERSION.SDK_INT, direction)
                }
            } finally {
                bitmap.recycle()
            }
        } catch (t: Throwable) {
            Log.w(MEDIA_IMPORT_ENGINE_TAG, "Unable to inspect Ultra HDR gain map for $uri", t)
            null
        }
    }

    private fun resolveMimeType(uri: Uri): String? {
        context.contentResolver.getType(uri)?.let { return it }
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun isImageSource(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/", ignoreCase = true) == true) return true
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?: return false
        return extension in setOf("jpg", "jpeg", "heic", "heif", "avif", "png", "webp")
    }

    companion object {
        internal fun buildVideoSourceColorMetadata(
            format: MediaFormat,
            inspectedAtMs: Long = System.currentTimeMillis()
        ): SourceColorMetadata {
            val mimeType = format.getString(MediaFormat.KEY_MIME)
            val colorTransfer = format.getOptionalInt(MediaFormat.KEY_COLOR_TRANSFER)
            val colorStandard = format.getOptionalInt(MediaFormat.KEY_COLOR_STANDARD)
            val codecString = format.getOptionalString(MediaFormat.KEY_CODECS_STRING)
            val hdrFormats = classifyHdrFormats(
                mimeType = mimeType,
                colorTransfer = colorTransfer,
                colorStandard = colorStandard,
                hasHdrStaticInfo = format.hasBuffer(MediaFormat.KEY_HDR_STATIC_INFO),
                hasHdr10PlusInfo = format.hasBuffer(MediaFormat.KEY_HDR10_PLUS_INFO),
                codecString = codecString
            )
            return SourceColorMetadata(
                mimeType = mimeType,
                colorStandard = colorStandard?.toColorStandardName(),
                colorTransfer = colorTransfer?.toColorTransferName(),
                hdrFormats = hdrFormats,
                inspectedAtMs = inspectedAtMs
            )
        }

        internal fun classifyHdrFormats(
            mimeType: String?,
            colorTransfer: Int?,
            colorStandard: Int?,
            hasHdrStaticInfo: Boolean,
            hasHdr10PlusInfo: Boolean,
            codecString: String?
        ): Set<SourceHdrFormat> {
            val lowerMime = mimeType.orEmpty().lowercase(Locale.US)
            val lowerCodec = codecString.orEmpty().lowercase(Locale.US)
            val formats = linkedSetOf<SourceHdrFormat>()

            if (lowerMime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION || lowerCodec.startsWith("dv")) {
                formats += SourceHdrFormat.DOLBY_VISION
            }
            if (hasHdr10PlusInfo || lowerCodec.contains("hdr10plus")) {
                formats += SourceHdrFormat.HDR10_PLUS
            }
            when (colorTransfer) {
                MediaFormat.COLOR_TRANSFER_HLG -> formats += SourceHdrFormat.HLG
                MediaFormat.COLOR_TRANSFER_ST2084 -> {
                    if (SourceHdrFormat.DOLBY_VISION !in formats &&
                        SourceHdrFormat.HDR10_PLUS !in formats
                    ) {
                        formats += SourceHdrFormat.HDR10
                    }
                }
            }
            if (hasHdrStaticInfo && SourceHdrFormat.HDR10_PLUS !in formats &&
                SourceHdrFormat.DOLBY_VISION !in formats
            ) {
                formats += SourceHdrFormat.HDR10
            }
            if (colorStandard == MediaFormat.COLOR_STANDARD_BT2020 &&
                colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 &&
                SourceHdrFormat.HDR10_PLUS !in formats &&
                SourceHdrFormat.DOLBY_VISION !in formats
            ) {
                formats += SourceHdrFormat.HDR10
            }

            return formats
        }

        internal fun classifyGainMapFormat(
            sdkInt: Int,
            gainmapDirection: Int?
        ): SourceHdrFormat {
            return when {
                sdkInt >= Build.VERSION_CODES.BAKLAVA &&
                    gainmapDirection == GAINMAP_DIRECTION_HDR_TO_SDR ->
                    SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP
                sdkInt >= Build.VERSION_CODES.BAKLAVA &&
                    gainmapDirection == GAINMAP_DIRECTION_SDR_TO_HDR ->
                    SourceHdrFormat.ULTRA_HDR_GAIN_MAP
                else -> SourceHdrFormat.ULTRA_HDR_GAIN_MAP
            }
        }

        private fun MediaFormat.getOptionalInt(key: String): Int? {
            return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
        }

        private fun MediaFormat.getOptionalString(key: String): String? {
            return if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
        }

        private fun MediaFormat.hasBuffer(key: String): Boolean {
            return containsKey(key) && runCatching { getByteBuffer(key) != null }.getOrDefault(false)
        }

        private fun Int.toColorStandardName(): String {
            return when (this) {
                MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
                MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
                MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
                MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
                else -> "standard:$this"
            }
        }

        private fun Int.toColorTransferName(): String {
            return when (this) {
                MediaFormat.COLOR_TRANSFER_ST2084 -> "ST 2084"
                MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR video"
                MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
                else -> "transfer:$this"
            }
        }
    }
}
