package com.novacut.editor.engine

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The local diagnostic formats ClearCut can hand to another application. */
enum class MetadataSidecarFormat(
    val extension: String,
    val mimeType: String,
) {
    GPX("gpx", "application/gpx+xml"),
    CSV("csv", "text/csv"),
    VTT("vtt", "text/vtt"),
    SRT("srt", "application/x-subrip"),
}

enum class MetadataSidecarKind {
    GPS,
    SUBTITLE,
    OTHER,
}

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val mediaTimeMs: Long? = null,
)

/**
 * A metadata-like stream found in a source file. The track index is the
 * MediaExtractor index; -1 identifies a container-level location tag.
 *
 * This value intentionally contains no URI or display name. It is attached to
 * an already-probed [MediaDiagnostic], while the source URI remains owned by
 * the media manager row that the user is looking at.
 */
data class MetadataSidecarTrack(
    val trackIndex: Int,
    val kind: MetadataSidecarKind,
    val mimeType: String? = null,
    val language: String? = null,
    val supportedFormats: Set<MetadataSidecarFormat> = emptySet(),
    val unsupportedReason: String? = null,
    val location: GpsPoint? = null,
)

sealed interface MetadataSidecarExportResult {
    data class Success(
        val file: File,
        val format: MetadataSidecarFormat,
    ) : MetadataSidecarExportResult

    data class Unsupported(val reason: String) : MetadataSidecarExportResult

    data class Failed(val reason: String) : MetadataSidecarExportResult
}

/** Pure classification and formatting rules shared by the probe and tests. */
object MetadataSidecarPolicy {
    private val SUBTITLE_MIMES = setOf(
        "application/cea-608",
        "application/cea-708",
        "application/ttml",
        "application/ttml+xml",
        "application/tx3g",
        "application/x-ass",
        "application/x-mp4-vtt",
        "application/x-subrip",
        "application/x-quicktime-tx3g",
        "text/ass",
        "text/ssa",
        "text/vtt",
        "text/x-ssa",
    )

    private val NMEA_MIMES = setOf(
        "application/gps",
        "application/nmea",
        "application/x-gps",
        "application/x-nmea",
        "text/gps",
        "text/nmea",
    )

    private val GPS_MIME_MARKERS = listOf(
        "gpmd",
        "gps",
        "location",
        "telemetry",
        "nmea",
        "geotag",
    )

    fun classifyTrack(
        trackIndex: Int,
        mimeType: String?,
        language: String? = null,
    ): MetadataSidecarTrack? {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

        return when {
            normalized == null -> null
            normalized in NMEA_MIMES -> MetadataSidecarTrack(
                trackIndex = trackIndex,
                kind = MetadataSidecarKind.GPS,
                mimeType = normalized,
                language = language,
                supportedFormats = setOf(MetadataSidecarFormat.GPX, MetadataSidecarFormat.CSV),
            )
            normalized in SUBTITLE_MIMES || normalized.startsWith("text/") -> MetadataSidecarTrack(
                trackIndex = trackIndex,
                kind = MetadataSidecarKind.SUBTITLE,
                mimeType = normalized,
                language = language,
                supportedFormats = setOf(MetadataSidecarFormat.VTT, MetadataSidecarFormat.SRT),
            )
            GPS_MIME_MARKERS.any(normalized::contains) -> MetadataSidecarTrack(
                trackIndex = trackIndex,
                kind = MetadataSidecarKind.GPS,
                mimeType = normalized,
                language = language,
                unsupportedReason = "GPS telemetry was detected, but this codec is not a locally decoded format.",
            )
            normalized.startsWith("application/") || normalized.startsWith("metadata/") ->
                MetadataSidecarTrack(
                    trackIndex = trackIndex,
                    kind = MetadataSidecarKind.OTHER,
                    mimeType = normalized,
                    language = language,
                    unsupportedReason = "This metadata track was detected, but ClearCut has no local sidecar decoder for it.",
                )
            else -> null
        }
    }

    fun containerLocation(point: GpsPoint): MetadataSidecarTrack = MetadataSidecarTrack(
        trackIndex = -1,
        kind = MetadataSidecarKind.GPS,
        mimeType = "container/location",
        supportedFormats = setOf(MetadataSidecarFormat.GPX, MetadataSidecarFormat.CSV),
        location = point,
    )

    fun formatCsv(points: List<GpsPoint>): String = buildString {
        appendLine("media_time_ms,latitude,longitude")
        points.forEach { point ->
            append(point.mediaTimeMs ?: "")
                .append(',')
                .append(formatCoordinate(point.latitude))
                .append(',')
                .append(formatCoordinate(point.longitude))
                .appendLine()
        }
    }

    fun formatGpx(points: List<GpsPoint>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"ClearCut\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        appendLine("  <metadata><name>ClearCut local GPS sidecar</name></metadata>")
        appendLine("  <trk><name>Extracted GPS points</name><trkseg>")
        points.forEach { point ->
            append("    <trkpt lat=\"")
                .append(formatCoordinate(point.latitude))
                .append("\" lon=\"")
                .append(formatCoordinate(point.longitude))
                .appendLine("\"/>")
        }
        appendLine("  </trkseg></trk>")
        appendLine("</gpx>")
    }

    internal fun parseNmeaSentence(line: String, mediaTimeMs: Long?): GpsPoint? {
        val fields = line.trim()
            .removeSuffix("\r")
            .split(',')
        val sentence = fields.firstOrNull()
            ?.removePrefix("$")
            ?.removePrefix("!")
            ?.uppercase(Locale.ROOT)
            ?: return null

        val indices = when {
            sentence.endsWith("GGA") && fields.size > 6 ->
                NmeaIndices(2, 3, 4, 5, fields[6].isNotBlank() && fields[6] != "0")
            sentence.endsWith("RMC") && fields.size > 6 ->
                NmeaIndices(3, 4, 5, 6, fields[2].equals("A", ignoreCase = true))
            else -> return null
        }
        if (!indices.valid) return null
        val latitude = nmeaCoordinate(
            fields[indices.latIndex],
            fields[indices.latRefIndex],
            isLatitude = true,
        )
            ?: return null
        val longitude = nmeaCoordinate(
            fields[indices.lonIndex],
            fields[indices.lonRefIndex],
            isLatitude = false,
        )
            ?: return null
        return GpsPoint(latitude, longitude, mediaTimeMs)
    }

    private data class NmeaIndices(
        val latIndex: Int,
        val latRefIndex: Int,
        val lonIndex: Int,
        val lonRefIndex: Int,
        val valid: Boolean,
    )

    private fun nmeaCoordinate(raw: String, reference: String, isLatitude: Boolean): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        val degrees = kotlin.math.floor(value / 100.0)
        val minutes = value - degrees * 100.0
        if (degrees < 0.0 || minutes < 0.0 || minutes >= 60.0) return null
        val signed = (degrees + minutes / 60.0) * when (reference.uppercase(Locale.ROOT)) {
            "S", "W" -> -1.0
            "N", "E" -> 1.0
            else -> return null
        }
        return signed.takeIf {
            if (isLatitude) it in -90.0..90.0 else it in -180.0..180.0
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.7f", value)
}

@Singleton
class MetadataSidecarEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ffmpegEngine: FFmpegEngine,
) {
    suspend fun export(
        uri: Uri,
        track: MetadataSidecarTrack,
        format: MetadataSidecarFormat,
        now: Long = System.currentTimeMillis(),
    ): MetadataSidecarExportResult = withContext(Dispatchers.IO) {
        if (format !in track.supportedFormats) {
            return@withContext MetadataSidecarExportResult.Unsupported(
                track.unsupportedReason ?: "This sidecar format is not supported for the detected track."
            )
        }

        val outputDir = File(context.filesDir, DiagnosticExportEngine.DIAGNOSTIC_SHARE_DIR).apply { mkdirs() }
        val temporary = runCatching {
            File.createTempFile("metadata-sidecar-", ".tmp", outputDir)
        }.getOrElse {
            return@withContext MetadataSidecarExportResult.Failed("ClearCut could not create a local sidecar file.")
        }

        try {
            val wrote = when (track.kind) {
                MetadataSidecarKind.GPS -> writeGpsSidecar(uri, track, format, temporary)
                MetadataSidecarKind.SUBTITLE -> writeSubtitleSidecar(uri, track, format, temporary)
                MetadataSidecarKind.OTHER -> false
            }
            if (!wrote || temporary.length() <= 0L) {
                val reason = track.unsupportedReason
                    ?: "ClearCut could not decode this metadata track locally."
                return@withContext MetadataSidecarExportResult.Unsupported(reason)
            }
            if (temporary.length() > MAX_SIDECAR_BYTES) {
                return@withContext MetadataSidecarExportResult.Failed(
                    "The generated sidecar exceeds the local ${MAX_SIDECAR_BYTES / (1024 * 1024)} MB limit."
                )
            }
            val finalFile = nextOutputFile(outputDir, track, format, now)
            if (!installTemporary(temporary, finalFile)) {
                return@withContext MetadataSidecarExportResult.Failed("ClearCut could not finalize the local sidecar.")
            }
            pruneOldSidecars(outputDir)
            MetadataSidecarExportResult.Success(finalFile, format)
        } catch (_: Exception) {
            MetadataSidecarExportResult.Failed("ClearCut could not export this metadata sidecar locally.")
        } finally {
            temporary.delete()
        }
    }

    private fun writeGpsSidecar(
        uri: Uri,
        track: MetadataSidecarTrack,
        format: MetadataSidecarFormat,
        outputFile: File,
    ): Boolean {
        val points = track.location?.let(::listOf) ?: readNmeaPoints(uri, track.trackIndex)
        if (points.isEmpty()) return false
        val content = when (format) {
            MetadataSidecarFormat.CSV -> MetadataSidecarPolicy.formatCsv(points)
            MetadataSidecarFormat.GPX -> MetadataSidecarPolicy.formatGpx(points)
            else -> return false
        }
        outputFile.writeText(content, Charsets.UTF_8)
        return true
    }

    private suspend fun writeSubtitleSidecar(
        uri: Uri,
        track: MetadataSidecarTrack,
        format: MetadataSidecarFormat,
        outputFile: File,
    ): Boolean {
        if (track.trackIndex < 0 || !ffmpegEngine.isAvailable()) return false
        val completed = withTimeoutOrNull(NativeProcessingPolicy.TIMEOUT_METADATA_SIDECAR_MS) {
            ffmpegEngine.extractSubtitleTrack(uri, track.trackIndex, format, outputFile)
        } ?: false
        if (!completed || outputFile.length() <= 0L) return false
        val bytes = readBoundedBytes(outputFile, MAX_SIDECAR_BYTES) ?: return false
        val parsed = CaptionImportEngine.analyze(
            bytes = bytes,
            format = when (format) {
                MetadataSidecarFormat.VTT -> CaptionImportEngine.Format.WEBVTT
                MetadataSidecarFormat.SRT -> CaptionImportEngine.Format.SRT
                else -> return false
            },
        )
        return parsed.isValid
    }

    private fun readNmeaPoints(uri: Uri, trackIndex: Int): List<GpsPoint> {
        if (trackIndex < 0) return emptyList()
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            if (trackIndex >= extractor.trackCount) return emptyList()
            extractor.selectTrack(trackIndex)
            val buffer = ByteBuffer.allocate(NMEA_SAMPLE_BYTES)
            val lines = StringBuilder()
            val points = ArrayList<GpsPoint>()
            var samples = 0
            var bytesRead = 0L
            while (
                samples < MAX_NMEA_SAMPLES &&
                points.size < MAX_GPS_POINTS &&
                bytesRead < MAX_NMEA_BYTES
            ) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                buffer.clear()
                val size = runCatching { extractor.readSampleData(buffer, 0) }.getOrDefault(-1)
                if (size > 0) {
                    if (bytesRead + size > MAX_NMEA_BYTES) break
                    bytesRead += size
                    buffer.position(0)
                    val sample = ByteArray(size)
                    buffer.get(sample)
                    lines.append(String(sample, StandardCharsets.US_ASCII))
                    consumeNmeaLines(lines, sampleTimeUs / 1000L, points)
                }
                samples++
                if (!extractor.advance()) break
            }
            if (lines.isNotBlank()) {
                MetadataSidecarPolicy.parseNmeaSentence(
                    lines.toString(),
                    extractor.sampleTime.takeIf { it >= 0L }?.div(1000L),
                )?.let(points::add)
            }
            points.distinctBy { it.latitude to it.longitude }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun consumeNmeaLines(
        buffer: StringBuilder,
        mediaTimeMs: Long,
        points: MutableList<GpsPoint>,
    ) {
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) break
            val line = buffer.substring(0, newline)
            buffer.delete(0, newline + 1)
            MetadataSidecarPolicy.parseNmeaSentence(line, mediaTimeMs)?.let(points::add)
        }
        if (buffer.length > MAX_NMEA_LINE_CHARS) {
            buffer.delete(0, buffer.length - MAX_NMEA_LINE_CHARS)
        }
    }

    private fun nextOutputFile(
        outputDir: File,
        track: MetadataSidecarTrack,
        format: MetadataSidecarFormat,
        now: Long,
    ): File {
        val base = "metadata-${now.coerceAtLeast(0L)}-track${track.trackIndex.coerceAtLeast(0)}"
        var candidate = File(outputDir, "$base.${format.extension}")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(outputDir, "$base-$suffix.${format.extension}")
            suffix++
        }
        return candidate
    }

    private fun installTemporary(temporary: File, outputFile: File): Boolean = runCatching {
        temporary.copyTo(outputFile, overwrite = false)
        outputFile.isFile && outputFile.length() > 0L
    }.getOrDefault(false)

    private fun readBoundedBytes(file: File, maxBytes: Long): ByteArray? = runCatching {
        if (file.length() > maxBytes) return@runCatching null
        file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream(minOf(file.length(), 64 * 1024L).toInt())
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return@use null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }.getOrNull()

    private fun pruneOldSidecars(outputDir: File) {
        outputDir.listFiles { file ->
            file.isFile && file.name.startsWith("metadata-") &&
                file.extension in MetadataSidecarFormat.entries.map { it.extension }
        }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_RETAINED_SIDECARS)
            ?.forEach { runCatching { it.delete() } }
    }

    companion object {
        const val MAX_SIDECAR_BYTES = 10L * 1024 * 1024
        private const val MAX_NMEA_SAMPLES = 200_000
        private const val MAX_GPS_POINTS = 100_000
        private const val MAX_NMEA_BYTES = 50L * 1024 * 1024
        private const val MAX_NMEA_LINE_CHARS = 16_384
        private const val NMEA_SAMPLE_BYTES = 256 * 1024
        private const val MAX_RETAINED_SIDECARS = 12
    }
}
