package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.engine.AppLog
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MediaIngestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing source URI")
        )
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: "video"
        val uri = Uri.parse(uriString)

        AppLog.d(TAG, "Starting ingest for $uriString (type=$mediaType)")

        val result = importUriToManagedMediaWithProgress(
            context = applicationContext,
            uri = uri,
            mediaType = mediaType,
            onProgress = { progress ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            },
            isCancelled = { isStopped }
        )

        return when (result) {
            is IngestResult.Success -> {
                AppLog.d(TAG, "Ingest complete: ${result.managedUri.redacted()}")
                Result.success(
                    workDataOf(
                        KEY_MANAGED_URI to result.managedUri.toString(),
                        KEY_MEDIA_TYPE to mediaType,
                        KEY_PROGRESS to 1f
                    )
                )
            }
            is IngestResult.InsufficientSpace -> {
                AppLog.w(TAG, "Insufficient space: need ${result.requiredBytes}, have ${result.availableBytes}")
                Result.failure(
                    workDataOf(
                        KEY_ERROR to "Insufficient storage space",
                        KEY_REQUIRED_BYTES to result.requiredBytes,
                        KEY_AVAILABLE_BYTES to result.availableBytes
                    )
                )
            }
            is IngestResult.Cancelled -> {
                AppLog.d(TAG, "Ingest cancelled for $uriString")
                Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
            }
            is IngestResult.Failed -> {
                AppLog.w(TAG, "Ingest failed for $uriString: ${result.reason}")
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to result.reason))
                }
            }
        }
    }

    companion object {
        const val TAG = "MediaIngest"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_MANAGED_URI = "managed_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_REQUIRED_BYTES = "required_bytes"
        const val KEY_AVAILABLE_BYTES = "available_bytes"
        private const val MAX_RETRIES = 1
    }
}
