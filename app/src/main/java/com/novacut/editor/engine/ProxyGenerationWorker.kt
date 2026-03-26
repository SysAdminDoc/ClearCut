package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for background proxy generation.
 *
 * Generates low-res proxies (540p H.264) for all registered media
 * that exceeds 1080p. Reports progress via setProgress() so the UI
 * can display a progress indicator.
 *
 * Enqueued by EditorViewModel when proxy editing is enabled and
 * new high-res clips are imported.
 *
 * Dependencies:
 *   - androidx.work:work-runtime-ktx (Tier 4, now activated)
 *   - ProxyWorkflowEngine (@Singleton, injected via Hilt)
 */
@HiltWorker
class ProxyGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val proxyWorkflowEngine: ProxyWorkflowEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting proxy generation for all registered media")

            proxyWorkflowEngine.generateAllProxies { progress ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }

            val storageBytes = proxyWorkflowEngine.getProxyStorageBytes()
            Log.d(TAG, "Proxy generation complete. Storage used: ${storageBytes / 1024}KB")

            Result.success(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STORAGE_BYTES to storageBytes
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Proxy generation failed", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
            }
        }
    }

    companion object {
        const val TAG = "ProxyGeneration"
        const val WORK_NAME = "proxy_generation"
        const val KEY_PROGRESS = "progress"
        const val KEY_STORAGE_BYTES = "storage_bytes"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 2
    }
}
