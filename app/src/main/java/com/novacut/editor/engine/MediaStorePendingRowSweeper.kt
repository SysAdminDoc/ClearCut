package com.novacut.editor.engine

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.novacut.editor.engine.AppLog
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaStorePendingRows"

internal object MediaStorePendingRowPolicy {
    const val ORPHAN_AGE_MS = 24L * 60L * 60L * 1_000L

    private val ownedRelativePaths = setOf(
        "Movies/ClearCut",
        "Music/ClearCut",
        "Pictures/ClearCut",
        "Download/ClearCut",
    )

    fun shouldDelete(
        relativePath: String?,
        dateAddedSeconds: Long,
        nowSeconds: Long,
    ): Boolean {
        val normalizedPath = relativePath?.trim()?.trimEnd('/') ?: return false
        if (normalizedPath !in ownedRelativePaths || dateAddedSeconds <= 0L) return false
        return dateAddedSeconds <= nowSeconds - (ORPHAN_AGE_MS / 1_000L)
    }
}

/**
 * Removes only old, still-pending MediaStore rows that this app created in its
 * own public output directories. A process kill between insert(IS_PENDING=1)
 * and publish(IS_PENDING=0) otherwise leaves an invisible row indefinitely.
 */
@Singleton
class MediaStorePendingRowSweeper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun sweep(nowMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext 0

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val nowSeconds = nowMs / 1_000L
        val cutoffSeconds = nowSeconds - (MediaStorePendingRowPolicy.ORPHAN_AGE_MS / 1_000L)
        val selection = buildString {
            append("${MediaStore.MediaColumns.IS_PENDING} = 1")
            append(" AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            append(" AND ${MediaStore.MediaColumns.DATE_ADDED} <= ?")
        }
        val selectionArgs = arrayOf(context.packageName, cutoffSeconds.toString())
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )

        var deleted = 0
        runCatching {
            queryPendingRows(
                resolver = resolver,
                collection = collection,
                projection = projection,
                selection = selection,
                selectionArgs = selectionArgs,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val relativePath = cursor.getString(pathIndex)
                    val dateAddedSeconds = cursor.getLong(addedIndex)
                    if (!MediaStorePendingRowPolicy.shouldDelete(relativePath, dateAddedSeconds, nowSeconds)) {
                        continue
                    }
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (resolver.delete(uri, null, null) == 1) {
                        deleted++
                        AppLog.i(TAG, "Removed orphaned pending row ${cursor.getString(nameIndex)}")
                    }
                }
            }
        }.onFailure { error ->
            AppLog.w(TAG, "Pending MediaStore cleanup skipped", error)
        }
        deleted
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryPendingRows(
        resolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
    ): Cursor? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            return resolver.query(collection, projection, queryArgs, null)
        }

        @Suppress("DEPRECATION")
        return resolver.query(
            MediaStore.setIncludePending(collection),
            projection,
            selection,
            selectionArgs,
            null,
        )
    }
}
