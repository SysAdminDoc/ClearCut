@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novacut.editor.engine

import androidx.media3.common.Metadata
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import java.io.File
import java.nio.ByteBuffer

/**
 * Adds a filtered set of typed metadata entries to Media3's normal muxer.
 *
 * Media3's default MP4 muxer understands [Metadata.Entry] values such as
 * `Mp4TimestampData`, `Mp4OrientationData`, and `Mp4LocationData`, but the
 * public Transformer builder only accepts a `Muxer.Factory`. This small
 * delegating factory keeps the normal muxer selection and MIME support while
 * making the source-metadata policy explicit at the one place where the file
 * is finalized. Transformer forwards source [androidx.media3.common.Format]
 * metadata to the muxer as well, so the wrapper drops that implicit path and
 * admits only the policy entries supplied here.
 */
internal class MetadataPreservingMuxerFactory(
    private val delegate: Muxer.Factory,
    private val entries: List<Metadata.Entry>,
) : Muxer.Factory {
    override fun create(path: String): Muxer {
        val muxer = delegate.create(path)
        return if (isMp4Path(path)) {
            MetadataAppendingMuxer(muxer, entries)
        } else {
            muxer
        }
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        delegate.getSupportedSampleMimeTypes(trackType)

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean =
        delegate.supportsWritingNegativeTimestampsInEditList()

    private fun isMp4Path(path: String): Boolean =
        File(path).extension.lowercase() in setOf("mp4", "m4a")
}

private class MetadataAppendingMuxer(
    private val delegate: Muxer,
    entries: List<Metadata.Entry>,
) : Muxer {
    init {
        entries.forEach(delegate::addMetadataEntry)
    }

    override fun addTrack(format: androidx.media3.common.Format): Int = delegate.addTrack(format)

    override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
        delegate.writeSampleData(trackId, byteBuffer, bufferInfo)
    }

    // MuxerWrapper forwards each input Format.metadata entry here. Those
    // entries are intentionally filtered so opt-in GPS/stream tags cannot be
    // bypassed by a source container and scrubbed exports remain metadata-free.
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit

    override fun close() {
        delegate.close()
    }
}
