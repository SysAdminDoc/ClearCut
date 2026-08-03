package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.novacut.editor.engine.MediaHashWorker
import com.novacut.editor.engine.MediaIngestWorker
import com.novacut.editor.engine.ProxyGenerationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns WorkManager request construction and observation for editor jobs.
 *
 * The ViewModel receives domain callbacks and updates visible state, but it no
 * longer knows WorkManager policies, worker input keys, LiveData observers, or
 * observer cleanup details. This keeps process-restart work separate from the
 * editor's mutation surface.
 */
@Singleton
class EditorBackgroundJobCoordinator internal constructor(
    private val scheduler: WorkScheduler,
    private val idFactory: () -> UUID = UUID::randomUUID,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        scheduler = WorkScheduler.from(WorkManager.getInstance(context)),
    )

    interface WorkObservation {
        fun remove()
    }

    interface WorkScheduler {
        fun enqueue(request: WorkRequest)
        fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest)
        fun cancelWorkById(id: UUID)
        fun observe(id: UUID, observer: Observer<WorkInfo?>): WorkObservation

        companion object {
            fun from(workManager: WorkManager): WorkScheduler = object : WorkScheduler {
                override fun enqueue(request: WorkRequest) {
                    workManager.enqueue(request)
                }

                override fun enqueueUniqueWork(
                    name: String,
                    policy: ExistingWorkPolicy,
                    request: OneTimeWorkRequest,
                ) {
                    workManager.enqueueUniqueWork(name, policy, request)
                }

                override fun cancelWorkById(id: UUID) {
                    workManager.cancelWorkById(id)
                }

                override fun observe(id: UUID, observer: Observer<WorkInfo?>): WorkObservation {
                    val liveData = workManager.getWorkInfoByIdLiveData(id)
                    liveData.observeForever(observer)
                    return object : WorkObservation {
                        override fun remove() {
                            liveData.removeObserver(observer)
                        }
                    }
                }
            }
        }
    }

    data class IngestCallbacks(
        val onQueued: (PendingIngest) -> Unit,
        val onProgress: (String, Float) -> Unit,
        val onSucceeded: (String, Uri, String) -> Unit,
        val onFailed: (String, String?) -> Unit,
        val onCancelled: (String) -> Unit,
    )

    private val observations = ConcurrentHashMap<UUID, WorkObservation>()
    private val terminalBeforeObservationStored = ConcurrentHashMap.newKeySet<UUID>()

    fun enqueueMediaHashJob() {
        scheduler.enqueueUniqueWork(
            MediaHashWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MediaHashWorker>().build(),
        )
    }

    fun enqueueProxyGeneration() {
        scheduler.enqueueUniqueWork(
            ProxyGenerationWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ProxyGenerationWorker>()
                .addTag(ProxyGenerationWorker.TAG)
                .build(),
        )
    }

    fun enqueueMediaIngest(
        sourceUri: Uri,
        mediaType: String,
        displayName: String,
        callbacks: IngestCallbacks,
    ): String {
        val workId = idFactory()
        val request = OneTimeWorkRequestBuilder<MediaIngestWorker>()
            .setInputData(
                workDataOf(
                    MediaIngestWorker.KEY_SOURCE_URI to sourceUri.toString(),
                    MediaIngestWorker.KEY_MEDIA_TYPE to mediaType,
                )
            )
            .addTag(MediaIngestWorker.TAG)
            .setId(workId)
            .build()

        callbacks.onQueued(
            PendingIngest(
                workId = workId.toString(),
                displayName = displayName,
                mediaType = mediaType,
            )
        )
        scheduler.enqueue(request)

        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(info: WorkInfo?) {
                if (info == null) return
                when (info.state) {
                    WorkInfo.State.RUNNING -> callbacks.onProgress(
                        workId.toString(),
                        info.progress.getFloat(MediaIngestWorker.KEY_PROGRESS, 0f),
                    )
                    WorkInfo.State.SUCCEEDED -> {
                        val managedUri = info.outputData.getString(MediaIngestWorker.KEY_MANAGED_URI)
                        val type = info.outputData.getString(MediaIngestWorker.KEY_MEDIA_TYPE) ?: mediaType
                        if (managedUri != null) {
                            callbacks.onSucceeded(workId.toString(), Uri.parse(managedUri), type)
                        } else {
                            callbacks.onFailed(workId.toString(), "Media ingest returned no managed URI")
                        }
                        finishObservation(workId)
                    }
                    WorkInfo.State.FAILED -> {
                        callbacks.onFailed(
                            workId.toString(),
                            info.outputData.getString(MediaIngestWorker.KEY_ERROR),
                        )
                        finishObservation(workId)
                    }
                    WorkInfo.State.CANCELLED -> {
                        callbacks.onCancelled(workId.toString())
                        finishObservation(workId)
                    }
                    else -> Unit
                }
            }
        }
        val observation = scheduler.observe(workId, observer)
        if (terminalBeforeObservationStored.remove(workId)) {
            observation.remove()
        } else {
            observations[workId] = observation
        }
        return workId.toString()
    }

    fun cancelMediaIngest(workId: String, onCancelled: (String) -> Unit) {
        val id = workId.toUuidOrNull() ?: return
        scheduler.cancelWorkById(id)
        removeObservation(id)
        onCancelled(workId)
    }

    fun removeObservers() {
        observations.keys.toList().forEach(::removeObservation)
        terminalBeforeObservationStored.clear()
    }

    private fun removeObservation(id: UUID) {
        observations.remove(id)?.remove()
    }

    private fun finishObservation(id: UUID) {
        if (observations.containsKey(id)) {
            removeObservation(id)
        } else {
            terminalBeforeObservationStored += id
        }
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
