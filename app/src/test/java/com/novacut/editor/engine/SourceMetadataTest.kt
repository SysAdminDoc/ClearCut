package com.novacut.editor.engine

import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class SourceMetadataTest {

    @Test
    fun preservesTimestampAndRotationWithoutPrivacyScrub() {
        val entries = SourceMetadataPolicy.entriesFor(
            metadata = SourceMediaMetadata(
                creationTimeEpochMs = 1_700_000_000_000L,
                rotationDegrees = 90,
                location = SourceLocation(40.7f, -74.0f),
                streamTags = mapOf("title" to "Morning", "stream.0.language" to "en"),
            ),
            scrubMetadata = false,
            preserveLocation = false,
            preserveStreamTags = false,
        )

        assertEquals(2, entries.size)
        assertTrue(entries[0] is androidx.media3.container.Mp4TimestampData)
        assertTrue(entries[1] is androidx.media3.container.Mp4OrientationData)
        assertEquals(
            1_700_000_000L + 2_082_844_800L,
            (entries[0] as androidx.media3.container.Mp4TimestampData).creationTimestampSeconds,
        )
        assertEquals(90, (entries[1] as androidx.media3.container.Mp4OrientationData).orientation)
    }

    @Test
    fun scrubSuppressesTimestampRotationLocationAndStreamTags() {
        val entries = SourceMetadataPolicy.entriesFor(
            metadata = SourceMediaMetadata(
                creationTimeEpochMs = 1_700_000_000_000L,
                rotationDegrees = 180,
                location = SourceLocation(1f, 2f),
                streamTags = mapOf("title" to "Private"),
            ),
            scrubMetadata = true,
            preserveLocation = true,
            preserveStreamTags = true,
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun locationAndStreamTagsRequireExplicitOptIn() {
        val entries = SourceMetadataPolicy.entriesFor(
            metadata = SourceMediaMetadata(
                location = SourceLocation(51.5f, -0.1f),
                streamTags = mapOf("title" to "News", "stream.0.language" to "en"),
            ),
            scrubMetadata = false,
            preserveLocation = true,
            preserveStreamTags = true,
        )

        assertEquals(3, entries.size)
        assertTrue(entries.any { it is androidx.media3.container.Mp4LocationData })
        val tagEntries = entries.filterIsInstance<androidx.media3.container.MdtaMetadataEntry>()
        assertEquals(2, tagEntries.size)
        assertTrue(tagEntries.all { it.key.startsWith(SourceMetadataPolicy.STREAM_TAG_PREFIX) })
    }

    @Test
    fun malformedSourceDatesAndLocationsAreRejected() {
        assertEquals(null, parseSourceCreationTime("not-a-date"))
        assertEquals(1_700_000_000_000L, parseSourceCreationTime("2023-11-14T22:13:20Z"))
        assertEquals(SourceLocation(40.7f, -74.0f), parseSourceLocation("+40.7-074.0/"))
        assertEquals(null, parseSourceLocation("+95.0-074.0/"))
    }

    @Test
    fun muxerFactoryAddsMetadataOnlyToMp4Outputs() {
        val factory = RecordingMuxerFactory()
        val entry = androidx.media3.container.Mp4OrientationData(90)
        val wrapped = MetadataPreservingMuxerFactory(factory, listOf(entry))

        wrapped.create("output.mp4")
        assertEquals(listOf<Metadata.Entry>(entry), factory.last.metadata)

        factory.last.metadata.clear()
        wrapped.create("output.webm")
        assertFalse(factory.last.metadata.contains(entry))
    }

    @Test
    fun muxerFactoryFiltersMetadataForwardedByTransformer() {
        val factory = RecordingMuxerFactory()
        val allowed = androidx.media3.container.Mp4OrientationData(90)
        val forwarded = androidx.media3.container.Mp4LocationData(40.7f, -74.0f)
        val wrapped = MetadataPreservingMuxerFactory(factory, listOf(allowed))

        val muxer = wrapped.create("output.mp4")
        muxer.addMetadataEntry(forwarded)

        assertEquals(listOf<Metadata.Entry>(allowed), factory.last.metadata)
    }

    private class RecordingMuxerFactory : Muxer.Factory {
        val last = RecordingMuxer()

        override fun create(path: String): Muxer = last

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            ImmutableList.of()
    }

    private class RecordingMuxer : Muxer {
        val metadata = mutableListOf<Metadata.Entry>()

        override fun addTrack(format: Format): Int = 0

        override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) = Unit

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            metadata += metadataEntry
        }

        override fun close() = Unit
    }
}
