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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal data class MediaPickerBatchImportResult(
    val imported: List<MediaPickerSelection>,
    val insufficientSpace: IngestResult.InsufficientSpace? = null,
)

/**
 * Copies a reviewed batch into managed media, one item at a time, then hands the copies to
 * [adopt].
 *
 * Until [adopt] is called, nothing points at the copies, so a batch that is cancelled or throws
 * before then (while copying, or between the last copy and adoption) deletes every copy it
 * created, sidecars included, before the exception continues. Once [adopt] starts, the copies
 * belong to the editor and are never rolled back: not if [adopt] throws after adding a clip,
 * and not if a cancel lands a moment later, which the caller then sees as a
 * CancellationException. Files that were already managed before the batch are reused rather
 * than copied, and are never deleted here. A batch that runs out of space part way still
 * adopts what it copied.
 *
 * [onProgress] gets the number of finished items before and after each item. [adopt] runs on
 * [adoptContext], the main thread by default.
 */
internal suspend fun importMediaPickerBatch(
    context: Context,
    selections: List<MediaPickerSelection>,
    onProgress: suspend (completed: Int) -> Unit = {},
    adoptContext: CoroutineContext = Dispatchers.Main.immediate,
    adopt: (MediaPickerBatchImportResult) -> Unit = {},
): MediaPickerBatchImportResult {
    val createdCopies = mutableListOf<Uri>()
    var adopted = false
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
        // withContext refuses to start once the batch is cancelled, so a cancel that lands as
        // the last copy finishes still rolls back instead of adopting.
        withContext(adoptContext) {
            adopted = true
            adopt(result)
        }
        return result
    } catch (abort: Throwable) {
        if (!adopted) {
            withContext(NonCancellable + Dispatchers.IO) {
                synchronized(createdCopies) { createdCopies.toList() }.forEach { deleteManagedMediaUri(context, it) }
            }
        }
        throw abort
    }
}
