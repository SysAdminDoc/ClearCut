package com.novacut.editor.ui.mediapicker

import android.content.Context
import android.net.Uri
import com.novacut.editor.engine.IngestResult
import com.novacut.editor.engine.deleteManagedMediaUri
import com.novacut.editor.engine.importUriToManagedMediaWithProgress
import com.novacut.editor.engine.insufficientSpaceFor
import com.novacut.editor.engine.querySourceSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class MediaPickerBatchImportResult(
    val imported: List<MediaPickerSelection>,
    val insufficientSpace: IngestResult.InsufficientSpace? = null,
)

/**
 * Copies a reviewed batch into managed media, one item at a time.
 *
 * Nothing reaches the editor until the whole batch returns, so a batch that is cancelled or
 * throws part way would otherwise leave every copy it already made in app storage with no
 * project pointing at it. On any abort this deletes the copies the batch created, sidecars
 * included, before the exception continues. Files that were already managed before the batch
 * are reused rather than copied, and are never deleted here. A batch that runs out of space
 * part way still returns what it copied, for the caller to adopt.
 *
 * [onProgress] gets the number of finished items before and after each item.
 */
internal suspend fun importMediaPickerBatch(
    context: Context,
    selections: List<MediaPickerSelection>,
    onProgress: suspend (completed: Int) -> Unit = {},
): MediaPickerBatchImportResult {
    val createdCopies = mutableListOf<Uri>()
    try {
        val result = withContext(Dispatchers.IO) {
            val totalSize = selections.sumOf { querySourceSize(context, it.uri).coerceAtLeast(0L) }
            insufficientSpaceFor(context, totalSize)?.let { failure ->
                return@withContext MediaPickerBatchImportResult(imported = emptyList(), insufficientSpace = failure)
            }
            val operationContext = currentCoroutineContext()
            val imported = mutableListOf<MediaPickerSelection>()
            for ((index, selection) in selections.withIndex()) {
                if (!operationContext.isActive) throw CancellationException("Media import cancelled")
                onProgress(index)
                when (
                    val ingest = importUriToManagedMediaWithProgress(
                        context = context,
                        uri = selection.uri,
                        mediaType = selection.mediaType,
                        isCancelled = { !operationContext.isActive },
                    )
                ) {
                    is IngestResult.Success -> {
                        if (ingest.createdNewCopy) synchronized(createdCopies) { createdCopies += ingest.managedUri }
                        imported += selection.copy(uri = ingest.managedUri)
                    }
                    is IngestResult.InsufficientSpace ->
                        return@withContext MediaPickerBatchImportResult(imported.toList(), insufficientSpace = ingest)
                    is IngestResult.Cancelled -> throw CancellationException("Media import cancelled")
                    is IngestResult.Failed -> Unit
                }
                onProgress(index + 1)
            }
            MediaPickerBatchImportResult(imported = imported)
        }
        // A cancel that lands as the last copy finishes still means the user backed out.
        currentCoroutineContext().ensureActive()
        return result
    } catch (abort: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            synchronized(createdCopies) { createdCopies.toList() }.forEach { deleteManagedMediaUri(context, it) }
        }
        throw abort
    }
}
