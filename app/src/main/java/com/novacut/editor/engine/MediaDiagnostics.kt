package com.novacut.editor.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaDiagnosticKind {
    VIDEO,
    AUDIO,
    IMAGE,
    UNKNOWN
}

enum class MediaColorConfidence {
    HDR,
    SDR,
    UNKNOWN
}

enum class SyncFrameDirection {
    PREVIOUS,
    NEXT
}

data class MediaTrackDiagnostic(
    val trackIndex: Int,
    val mediaType: String,
    val mimeType: String? = null,
    val codec: String? = null,
    val language: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val colorStandard: String? = null,
    val colorTransfer: String? = null,
    val colorRange: String? = null,
    val hdrFormats: Set<String> = emptySet(),
    val syncFrameCount: Int = 0,
    val syncFrameScanTruncated: Boolean = false,
    val firstSyncFrameMs: Long? = null,
    val lastSyncFrameMs: Long? = null,
    val timestampRisk: String? = null,
) {
    val isVideo: Boolean get() = mediaType == "video"
    val isAudio: Boolean get() = mediaType == "audio"

    val colorConfidence: MediaColorConfidence
        get() = when {
            hdrFormats.isNotEmpty() -> MediaColorConfidence.HDR
            colorStandard != null && colorTransfer != null -> MediaColorConfidence.SDR
            else -> MediaColorConfidence.UNKNOWN
        }
}

data class MediaDiagnostic(
    val uri: String,
    val kind: MediaDiagnosticKind,
    val containerMimeType: String? = null,
    val durationMs: Long? = null,
    val rotationDegrees: Int? = null,
    val tracks: List<MediaTrackDiagnostic> = emptyList(),
    val colorConfidence: MediaColorConfidence = MediaColorConfidence.UNKNOWN,
    val colorStandard: String? = null,
    val colorTransfer: String? = null,
    val hdrFormats: Set<String> = emptySet(),
    val timestampRisk: String? = null,
    val colorRisk: String? = null,
    val probeError: String? = null,
) {
    val videoTracks: List<MediaTrackDiagnostic> get() = tracks.filter { it.isVideo }
    val keyframeCount: Int get() = videoTracks.sumOf { it.syncFrameCount }
    val keyframeScanTruncated: Boolean get() = videoTracks.any { it.syncFrameScanTruncated }

    fun exportWarningMessages(): List<String> = buildList {
        timestampRisk?.let { risk ->
            add("Media diagnostics (${redactedDiagnosticUri(uri)}): timestamp risk — $risk")
        }
        colorRisk?.let { risk ->
            add("Media diagnostics (${redactedDiagnosticUri(uri)}): color risk — $risk")
        }
    }
}

internal fun redactedDiagnosticUri(uri: String): String =
    runCatching { uri.toUri().redacted() }.getOrDefault("<source>")

internal data class MediaTimestampStats(
    val sampleCount: Int,
    val hasNonMonotonicTimestamps: Boolean,
    val hasSyncFrames: Boolean,
    val firstSyncFrameUs: Long? = null,
    val lastSyncFrameUs: Long? = null,
    val syncFrameCount: Int = 0,
    val scanTruncated: Boolean = false,
)

internal fun timestampRiskFor(
    stats: MediaTimestampStats,
    isVideo: Boolean,
): String? = when {
    stats.sampleCount == 0 -> "No readable samples were found."
    stats.hasNonMonotonicTimestamps -> "Sample timestamps are not monotonic."
    isVideo && !stats.hasSyncFrames -> "No video sync frames were reported."
    else -> null
}

internal fun colorRiskFor(
    hasHdr: Boolean,
    colorStandard: String?,
    colorTransfer: String?,
): String? {
    return if (hasHdr && (colorStandard == null || colorTransfer == null)) {
        "HDR was detected but the source does not report complete color metadata."
    } else {
        null
    }
}

internal fun nearestSyncFrameMs(
    syncFramesMs: List<Long>,
    targetMs: Long,
    direction: SyncFrameDirection,
): Long? {
    val sorted = syncFramesMs.asSequence().filter { it >= 0L }.distinct().sorted().toList()
    return when (direction) {
        SyncFrameDirection.PREVIOUS -> sorted.lastOrNull { it <= targetMs }
        SyncFrameDirection.NEXT -> sorted.firstOrNull { it >= targetMs }
    }
}

@Singleton
class MediaDiagnosticsProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun probeTracks(
        tracks: List<Track>,
        imageOverlays: List<ImageOverlay> = emptyList(),
    ): Map<String, MediaDiagnostic> = withContext(Dispatchers.IO) {
        val uris = buildList {
            tracks.forEach { track -> collectClipUris(track.clips, this) }
            imageOverlays.forEach { add(it.sourceUri) }
        }.distinctBy { it.toString() }
        uris.associate { uri -> uri.toString() to probe(uri) }
    }

    fun findNearestSyncFrame(
        uri: Uri,
        targetMs: Long,
        direction: SyncFrameDirection,
    ): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/", ignoreCase = true) == true
            } ?: return null
            extractor.selectTrack(videoTrack)
            val queryUs = targetMs.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
            extractor.seekTo(
                queryUs,
                when (direction) {
                    SyncFrameDirection.PREVIOUS -> MediaExtractor.SEEK_TO_PREVIOUS_SYNC
                    SyncFrameDirection.NEXT -> MediaExtractor.SEEK_TO_NEXT_SYNC
                }
            )
            extractor.sampleTime.takeIf { it >= 0L }?.div(1000L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun probe(uri: Uri): MediaDiagnostic {
        val uriString = uri.toString()
        val containerMimeType = resolveMimeType(uri)
        if (isImage(uri, containerMimeType)) {
            return MediaDiagnostic(
                uri = uriString,
                kind = MediaDiagnosticKind.IMAGE,
                containerMimeType = containerMimeType,
            )
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            val tracks = (0 until extractor.trackCount).map { index ->
                inspectTrack(extractor, index, extractor.getTrackFormat(index))
            }
            val videoTracks = tracks.filter { it.isVideo }
            val durationMs = tracks.mapNotNull { it.durationMs }.maxOrNull()
            val colorStandard = videoTracks.mapNotNull { it.colorStandard }.distinct().singleOrNull()
            val colorTransfer = videoTracks.mapNotNull { it.colorTransfer }.distinct().singleOrNull()
            val hdrFormats = videoTracks.flatMap { it.hdrFormats }.toSet()
            val timestampRisk = tracks.mapNotNull { it.timestampRisk }.distinct().firstOrNull()
            val retrieverMetadata = readRetrieverMetadata(uri, containerMimeType)
            val colorRisk = when {
                videoTracks.map { it.colorStandard to it.colorTransfer }.distinct().size > 1 ->
                    "Video tracks report conflicting color metadata."
                else -> colorRiskFor(
                    hasHdr = hdrFormats.isNotEmpty(),
                    colorStandard = colorStandard,
                    colorTransfer = colorTransfer,
                )
            }
            MediaDiagnostic(
                uri = uriString,
                kind = when {
                    videoTracks.isNotEmpty() -> MediaDiagnosticKind.VIDEO
                    tracks.any { it.isAudio } -> MediaDiagnosticKind.AUDIO
                    tracks.isNotEmpty() -> MediaDiagnosticKind.UNKNOWN
                    else -> MediaDiagnosticKind.UNKNOWN
                },
                containerMimeType = containerMimeType,
                durationMs = durationMs ?: retrieverMetadata.durationMs,
                rotationDegrees = retrieverMetadata.rotationDegrees,
                tracks = tracks,
                colorConfidence = when {
                    hdrFormats.isNotEmpty() -> MediaColorConfidence.HDR
                    videoTracks.isNotEmpty() && videoTracks.all {
                        it.colorStandard != null && it.colorTransfer != null
                    } -> MediaColorConfidence.SDR
                    else -> MediaColorConfidence.UNKNOWN
                },
                colorStandard = colorStandard,
                colorTransfer = colorTransfer,
                hdrFormats = hdrFormats,
                timestampRisk = timestampRisk,
                colorRisk = colorRisk,
                probeError = "No readable media tracks.".takeIf { tracks.isEmpty() },
            )
        } catch (t: Throwable) {
            MediaDiagnostic(
                uri = uriString,
                kind = MediaDiagnosticKind.UNKNOWN,
                containerMimeType = containerMimeType,
                probeError = t.message?.take(120) ?: t::class.java.simpleName,
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun inspectTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        format: MediaFormat,
    ): MediaTrackDiagnostic {
        val mimeType = format.safeString(MediaFormat.KEY_MIME)
        val mediaType = mimeType?.substringBefore('/', missingDelimiterValue = "unknown")?.lowercase(Locale.US)
            ?: "unknown"
        val isVideo = mediaType == "video"
        val codecString = format.safeCodecString()
        val hdrFormats = MediaImportEngine.classifyHdrFormats(
            mimeType = mimeType,
            colorTransfer = format.safeInt(MediaFormat.KEY_COLOR_TRANSFER),
            colorStandard = format.safeInt(MediaFormat.KEY_COLOR_STANDARD),
            hasHdrStaticInfo = format.hasBuffer(MediaFormat.KEY_HDR_STATIC_INFO),
            hasHdr10PlusInfo = format.hasHdr10PlusInfo(),
            codecString = codecString,
        ).map { it.displayName }.toSet()
        val stats = scanTrack(extractor, trackIndex, isVideo)
        val durationUs = format.safeLong(MediaFormat.KEY_DURATION)
        return MediaTrackDiagnostic(
            trackIndex = trackIndex,
            mediaType = mediaType,
            mimeType = mimeType,
            codec = codecString ?: mimeType,
            language = format.safeString(MediaFormat.KEY_LANGUAGE),
            durationMs = durationUs?.div(1000L),
            width = format.safeInt(MediaFormat.KEY_WIDTH),
            height = format.safeInt(MediaFormat.KEY_HEIGHT),
            frameRate = format.safeInt(MediaFormat.KEY_FRAME_RATE)?.toFloat(),
            colorStandard = format.safeInt(MediaFormat.KEY_COLOR_STANDARD)?.toColorStandardName(),
            colorTransfer = format.safeInt(MediaFormat.KEY_COLOR_TRANSFER)?.toColorTransferName(),
            colorRange = format.safeInt(MediaFormat.KEY_COLOR_RANGE)?.toColorRangeName(),
            hdrFormats = hdrFormats,
            syncFrameCount = stats.syncFrameCount,
            syncFrameScanTruncated = stats.scanTruncated,
            firstSyncFrameMs = stats.firstSyncFrameUs?.div(1000L),
            lastSyncFrameMs = stats.lastSyncFrameUs?.div(1000L),
            timestampRisk = timestampRiskFor(stats, isVideo),
        )
    }

    private fun scanTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        isVideo: Boolean,
    ): MediaTimestampStats {
        extractor.selectTrack(trackIndex)
        runCatching {
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        var sampleCount = 0
        var hasNonMonotonic = false
        var previousSampleTimeUs: Long? = null
        var firstSyncFrameUs: Long? = null
        var lastSyncFrameUs: Long? = null
        var syncFrameCount = 0
        var scanTruncated = false
        try {
            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (previousSampleTimeUs?.let { previous -> sampleTimeUs < previous } == true) {
                    hasNonMonotonic = true
                }
                previousSampleTimeUs = sampleTimeUs
                sampleCount++
                if (isVideo && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    syncFrameCount++
                    if (firstSyncFrameUs == null) firstSyncFrameUs = sampleTimeUs
                    lastSyncFrameUs = sampleTimeUs
                }
                if (sampleCount >= MAX_SAMPLES_TO_SCAN) {
                    scanTruncated = true
                    break
                }
                if (!extractor.advance()) break
            }
        } finally {
            extractor.unselectTrack(trackIndex)
        }
        return MediaTimestampStats(
            sampleCount = sampleCount,
            hasNonMonotonicTimestamps = hasNonMonotonic,
            hasSyncFrames = syncFrameCount > 0,
            firstSyncFrameUs = firstSyncFrameUs,
            lastSyncFrameUs = lastSyncFrameUs,
            syncFrameCount = syncFrameCount,
            scanTruncated = scanTruncated,
        )
    }

    private data class RetrieverMetadata(
        val durationMs: Long? = null,
        val rotationDegrees: Int? = null,
    )

    private fun readRetrieverMetadata(uri: Uri, mimeType: String?): RetrieverMetadata {
        val lease = CodecInstanceBudget.acquireRetrieverBlocking(mimeType)
        return try {
            lease.resource.setDataSource(context, uri)
            RetrieverMetadata(
                durationMs = lease.resource
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0L },
                rotationDegrees = lease.resource
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull(),
            )
        } catch (_: Exception) {
            RetrieverMetadata()
        } finally {
            lease.close()
        }
    }

    private fun resolveMimeType(uri: Uri): String? {
        context.contentResolver.getType(uri)?.let { return it }
        return uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    }

    private fun isImage(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/", ignoreCase = true) == true) return true
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
        return extension in IMAGE_EXTENSIONS
    }

    private fun collectClipUris(
        clips: List<com.novacut.editor.model.Clip>,
        destination: MutableList<Uri>,
    ) {
        clips.forEach { clip ->
            destination += clip.sourceUri
            if (clip.isCompound) collectClipUris(clip.compoundClips, destination)
        }
    }

    private fun MediaFormat.safeString(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull()?.takeIf { it.isNotBlank() } else null

    private fun MediaFormat.safeInt(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull()?.takeIf { it > 0 } else null

    private fun MediaFormat.safeLong(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull()?.takeIf { it > 0L } else null

    private fun MediaFormat.safeCodecString(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            safeString(MediaFormat.KEY_CODECS_STRING)
        } else {
            null
        }

    private fun MediaFormat.hasHdr10PlusInfo(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)
        } else {
            false
        }

    private fun MediaFormat.hasBuffer(key: String): Boolean =
        containsKey(key) && runCatching { getByteBuffer(key) != null }.getOrDefault(false)

    private fun Int.toColorStandardName(): String = when (this) {
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
        MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
        else -> "standard:$this"
    }

    private fun Int.toColorTransferName(): String = when (this) {
        MediaFormat.COLOR_TRANSFER_ST2084 -> "ST 2084"
        MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR video"
        MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
        else -> "transfer:$this"
    }

    private fun Int.toColorRangeName(): String = when (this) {
        MediaFormat.COLOR_RANGE_FULL -> "Full"
        MediaFormat.COLOR_RANGE_LIMITED -> "Limited"
        else -> "range:$this"
    }

    companion object {
        private const val MAX_SAMPLES_TO_SCAN = 200_000
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "heic", "heif", "avif", "png", "webp")
    }
}
