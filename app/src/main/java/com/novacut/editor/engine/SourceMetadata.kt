@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novacut.editor.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Metadata
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4OrientationData
import androidx.media3.container.Mp4TimestampData
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Metadata that can safely be carried from a source into a rendered MP4.
 *
 * The editor renders a composition rather than copying one source track, so
 * arbitrary source tags cannot be copied wholesale. The fields here are
 * deliberately small and typed: timestamp and orientation use Media3's MP4
 * metadata entries, location is opt-in, and stream tags are namespaced mdta
 * entries instead of being mistaken for output-track metadata.
 */
internal data class SourceMediaMetadata(
    val creationTimeEpochMs: Long? = null,
    val rotationDegrees: Int? = null,
    val location: SourceLocation? = null,
    val streamTags: Map<String, String> = emptyMap(),
)

internal data class SourceLocation(
    val latitude: Float,
    val longitude: Float,
)

/**
 * Convert the supported source metadata into Media3 entries.
 *
 * Source metadata is never emitted when [scrubMetadata] is enabled. Location
 * and namespaced stream tags are separate opt-ins because they can disclose
 * more than the creation time and display orientation. The default path
 * carries only the non-sensitive provenance needed for chronological sorting
 * and correct re-import orientation.
 */
internal object SourceMetadataPolicy {
    const val STREAM_TAG_PREFIX = "com.clearcut.source."

    fun entriesFor(
        metadata: SourceMediaMetadata,
        scrubMetadata: Boolean,
        preserveLocation: Boolean,
        preserveStreamTags: Boolean,
    ): List<Metadata.Entry> {
        if (scrubMetadata) return emptyList()

        return buildList {
            metadata.creationTimeEpochMs
                ?.takeIf { it > 0L }
                ?.let { epochMs ->
                    val mp4Seconds = Mp4TimestampData.unixTimeToMp4TimeSeconds(epochMs)
                    add(Mp4TimestampData(mp4Seconds, mp4Seconds))
                }
            metadata.rotationDegrees
                ?.let(::normaliseRotation)
                ?.let(::Mp4OrientationData)
                ?.let(::add)
            if (preserveLocation) {
                metadata.location?.let { location ->
                    add(Mp4LocationData(location.latitude, location.longitude))
                }
            }
            if (preserveStreamTags) {
                metadata.streamTags
                    .asSequence()
                    .mapNotNull { (key, value) ->
                        val safeKey = key
                            .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                            .take(64)
                            .takeIf { it.isNotBlank() }
                        val safeValue = value.trim().take(1024).takeIf { it.isNotBlank() }
                        if (safeKey == null || safeValue == null) null else safeKey to safeValue
                    }
                    .forEach { (key, value) ->
                        add(
                            MdtaMetadataEntry(
                                STREAM_TAG_PREFIX + key,
                                value.toByteArray(StandardCharsets.UTF_8),
                                MdtaMetadataEntry.TYPE_INDICATOR_STRING,
                            )
                        )
                    }
            }
        }
    }

    internal fun normaliseRotation(degrees: Int): Int? {
        val normalised = ((degrees % 360) + 360) % 360
        return normalised.takeIf { it == 0 || it == 90 || it == 180 || it == 270 }
    }
}

/**
 * Probe source metadata at export time. It intentionally does not persist
 * paths or raw tag values in the project document: metadata is read only when
 * the user starts an export and is then filtered by [SourceMetadataPolicy].
 */
internal class SourceMetadataProbe(private val context: Context) {
    private data class Snapshot(
        val metadata: SourceMediaMetadata,
    )

    fun probe(tracks: List<Track>): SourceMediaMetadata {
        val uris = tracks
            .flatMap { it.clips }
            .map { it.sourceUri }
            .distinctBy(Uri::toString)
        val snapshots = uris.mapNotNull(::probeUri)
        if (snapshots.isEmpty()) return SourceMediaMetadata()

        val metadata = snapshots.map { it.metadata }
        val creationTime = metadata.mapNotNull { it.creationTimeEpochMs }.minOrNull()
        val rotations = metadata.mapNotNull { it.rotationDegrees }.distinct()
        val location = metadata.mapNotNull { it.location }.distinct().singleOrNull()
        // A tag from one source is provenance for that source, not necessarily
        // for the rendered composition. Only carry it automatically when the
        // export has one source; multi-source exports still retain timestamp,
        // rotation, and the explicitly requested common location.
        val streamTags = metadata
            .singleOrNull()
            ?.streamTags
            .orEmpty()

        return SourceMediaMetadata(
            creationTimeEpochMs = creationTime,
            rotationDegrees = rotations.singleOrNull(),
            location = location,
            streamTags = streamTags,
        )
    }

    private fun probeUri(uri: Uri): Snapshot? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val tags = linkedMapOf<String, String>()
            metadataTag("title", MediaMetadataRetriever.METADATA_KEY_TITLE, retriever, tags)
            metadataTag("artist", MediaMetadataRetriever.METADATA_KEY_ARTIST, retriever, tags)
            metadataTag("album", MediaMetadataRetriever.METADATA_KEY_ALBUM, retriever, tags)
            metadataTag("author", MediaMetadataRetriever.METADATA_KEY_AUTHOR, retriever, tags)
            metadataTag("composer", MediaMetadataRetriever.METADATA_KEY_COMPOSER, retriever, tags)
            metadataTag("genre", MediaMetadataRetriever.METADATA_KEY_GENRE, retriever, tags)
            metadataTag("writer", MediaMetadataRetriever.METADATA_KEY_WRITER, retriever, tags)
            metadataTag("year", MediaMetadataRetriever.METADATA_KEY_YEAR, retriever, tags)

            SourceMetadataProbe.Snapshot(
                SourceMediaMetadata(
                    creationTimeEpochMs = parseSourceCreationTime(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ),
                    rotationDegrees = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull()
                        ?.let(SourceMetadataPolicy::normaliseRotation),
                    location = parseSourceLocation(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    ),
                    streamTags = tags + probeTrackTags(uri),
                )
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun metadataTag(
        key: String,
        retrieverKey: Int,
        retriever: MediaMetadataRetriever,
        tags: MutableMap<String, String>,
    ) {
        retriever.extractMetadata(retrieverKey)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(1024)
            ?.let { tags[key] = it }
    }

    private fun probeTrackTags(uri: Uri): Map<String, String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            buildMap {
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = runCatching { format.getString(MediaFormat.KEY_MIME) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                    val language = runCatching { format.getString(MediaFormat.KEY_LANGUAGE) }
                        .getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    mime?.let { put("stream.$index.mime", it) }
                    language?.let { put("stream.$index.language", it) }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        } finally {
            runCatching { extractor.release() }
        }
    }
}

/**
 * MediaMetadataRetriever reports both ISO-8601 and camera-style compact
 * timestamps depending on the container. Missing timezone information is
 * interpreted as UTC so a device's local timezone cannot change chronology on
 * re-import.
 */
internal fun parseSourceCreationTime(raw: String?): Long? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    runCatching { Instant.parse(value.replace("UTC", "Z", ignoreCase = true)) }
        .getOrNull()
        ?.toEpochMilli()
        ?.let { return it }

    val offsetFormatters = listOf(
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSX"),
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssX"),
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSXX"),
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssXX"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX"),
    )
    offsetFormatters.forEach { formatter ->
        runCatching { OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli() }
            .getOrNull()
            ?.let { return it }
    }

    val localFormatters = listOf(
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSS"),
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss"),
        DateTimeFormatter.ofPattern("uuuuMMddHHmmss"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
    )
    localFormatters.forEach { formatter ->
        runCatching { LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
            ?.let { return it }
    }

    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd"))
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

internal fun parseSourceLocation(raw: String?): SourceLocation? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val match = Regex(
        "^([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?$"
    ).matchEntire(value) ?: return null
    val latitude = match.groupValues[1].toFloatOrNull() ?: return null
    val longitude = match.groupValues[2].toFloatOrNull() ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90f..90f || longitude !in -180f..180f) return null
    return SourceLocation(latitude, longitude)
}
