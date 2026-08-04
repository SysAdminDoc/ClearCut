package com.novacut.editor.ui.editor

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device contract for the Android 10+ gallery handoff. A real encoded MP4 is
 * published through the production MediaStore writer, then queried from the
 * Video collection so a file/image classification regression cannot hide
 * behind a successful byte copy.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreExportClassificationInstrumentationTest {

    @Test
    fun publishedMp4IsVideoWithDurationAndTimeMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val displayName = "ClearCut-media-store-${System.nanoTime()}.mp4"
        val source = File(context.cacheDir, "media-store-source-${System.nanoTime()}.mp4")
        val export = File(context.cacheDir, displayName)
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        source.copyTo(export)

        try {
            val message = runBlocking {
                withTimeout(15_000L) {
                    saveExportedFileToMediaStore(context, export)
                }
            }
            assertEquals("Saved to gallery: $displayName", message)

            val row = awaitPublishedVideoRow(context.contentResolver, displayName)
            assertNotNull("published export was not visible in the Video collection", row)
            val published = checkNotNull(row)
            assertEquals("video/mp4", published.mimeType)
            assertEquals(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, published.mediaType)
            assertTrue("MediaStore did not index the encoded duration", published.durationMs > 0L)
            assertTrue("MediaStore did not assign DATE_ADDED", published.dateAddedSeconds > 0L)
            assertTrue("MediaStore did not assign DATE_MODIFIED", published.dateModifiedSeconds > 0L)
            assertEquals(
                "${Environment.DIRECTORY_MOVIES}/ClearCut",
                published.relativePath.trimEnd('/'),
            )
        } finally {
            deletePublishedRows(context.contentResolver, displayName)
            source.delete()
            export.delete()
        }
    }

    private fun awaitPublishedVideoRow(
        resolver: ContentResolver,
        displayName: String,
    ): PublishedVideoRow? {
        val deadline = SystemClock.uptimeMillis() + 20_000L
        var latest: PublishedVideoRow? = null
        while (SystemClock.uptimeMillis() < deadline) {
            latest = queryPublishedVideoRow(resolver, displayName)
            if (latest != null && latest.durationMs > 0L &&
                latest.dateAddedSeconds > 0L && latest.dateModifiedSeconds > 0L &&
                latest.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            ) {
                return latest
            }
            SystemClock.sleep(250L)
        }
        return latest
    }

    private fun queryPublishedVideoRow(
        resolver: ContentResolver,
        displayName: String,
    ): PublishedVideoRow? = resolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        VIDEO_PROJECTION,
        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
        arrayOf(displayName),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
        val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        do {
            val relativePath = cursor.getString(relativePathIndex)
            if (relativePath.trimEnd('/') != "${Environment.DIRECTORY_MOVIES}/ClearCut") {
                continue
            }
            val id = cursor.getLong(idIndex)
            return@use PublishedVideoRow(
                mimeType = cursor.getString(mimeIndex),
                relativePath = relativePath,
                mediaType = queryMediaType(resolver, id),
                durationMs = cursor.getLong(durationIndex),
                dateAddedSeconds = cursor.getLong(dateAddedIndex),
                dateModifiedSeconds = cursor.getLong(dateModifiedIndex),
            )
        } while (cursor.moveToNext())
        null
    }

    private fun deletePublishedRows(resolver: ContentResolver, displayName: String) {
        val rows = mutableListOf<Uri>()
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            if (cursor.moveToFirst()) do {
                rows += ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idIndex),
                )
            } while (cursor.moveToNext())
        }
        rows.forEach { uri -> resolver.delete(uri, null, null) }
    }

    private fun queryMediaType(resolver: ContentResolver, id: Long): Int? = resolver.query(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE),
        "${MediaStore.MediaColumns._ID} = ?",
        arrayOf(id.toString()),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
    }

    private data class PublishedVideoRow(
        val mimeType: String,
        val mediaType: Int?,
        val relativePath: String,
        val durationMs: Long,
        val dateAddedSeconds: Long,
        val dateModifiedSeconds: Long,
    )

    private companion object {
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
    }
}
