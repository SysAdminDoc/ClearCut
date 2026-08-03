package com.novacut.editor.ui.editor

import android.net.FakeUri
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkRequest
import com.novacut.editor.engine.MediaIngestWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EditorBackgroundJobCoordinatorTest {

    @Test
    fun ingestRequestIsBoundedToWorkerInputsAndReportsQueuedState() {
        val scheduler = FakeScheduler()
        val queued = mutableListOf<String>()
        val coordinator = EditorBackgroundJobCoordinator(
            scheduler = scheduler,
            idFactory = { UUID.fromString("00000000-0000-0000-0000-000000000123") },
        )

        val workId = coordinator.enqueueMediaIngest(
            sourceUri = FakeUri,
            mediaType = "video/mp4",
            displayName = "clip.mp4",
            callbacks = EditorBackgroundJobCoordinator.IngestCallbacks(
                onQueued = { queued += "${it.workId}:${it.displayName}:${it.mediaType}" },
                onProgress = { _, _ -> },
                onSucceeded = { _, _, _ -> },
                onFailed = { _, _ -> },
                onCancelled = { _ -> },
            ),
        )

        val request = scheduler.enqueued.single() as OneTimeWorkRequest
        assertEquals("00000000-0000-0000-0000-000000000123", workId)
        assertEquals(
            listOf("00000000-0000-0000-0000-000000000123:clip.mp4:video/mp4"),
            queued,
        )
        assertEquals(FakeUri.toString(), request.workSpec.input.getString(MediaIngestWorker.KEY_SOURCE_URI))
        assertEquals("video/mp4", request.workSpec.input.getString(MediaIngestWorker.KEY_MEDIA_TYPE))
        assertNotNull(scheduler.observers[UUID.fromString(workId)])
    }

    @Test
    fun cancelRemovesTheObserverAndEmitsOneCancellationCallback() {
        val scheduler = FakeScheduler()
        var cancelled = 0
        val coordinator = EditorBackgroundJobCoordinator(
            scheduler = scheduler,
            idFactory = { UUID.fromString("00000000-0000-0000-0000-000000000124") },
        )
        val workId = coordinator.enqueueMediaIngest(
            sourceUri = FakeUri,
            mediaType = "audio/m4a",
            displayName = "voice.m4a",
            callbacks = EditorBackgroundJobCoordinator.IngestCallbacks(
                onQueued = {},
                onProgress = { _, _ -> },
                onSucceeded = { _, _, _ -> },
                onFailed = { _, _ -> },
                onCancelled = { cancelled++ },
            ),
        )

        coordinator.cancelMediaIngest(workId) { cancelled++ }

        assertEquals(UUID.fromString(workId), scheduler.cancelled.single())
        assertEquals(1, scheduler.removed)
        assertEquals(1, cancelled)
    }

    @Test
    fun recurringJobsUseKeepPolicy() {
        val scheduler = FakeScheduler()
        val coordinator = EditorBackgroundJobCoordinator(scheduler)

        coordinator.enqueueMediaHashJob()
        coordinator.enqueueProxyGeneration()

        assertEquals(2, scheduler.unique.size)
        assertTrue(scheduler.unique.all { it.policy == ExistingWorkPolicy.KEEP })
    }

    private class FakeScheduler : EditorBackgroundJobCoordinator.WorkScheduler {
        data class Unique(
            val name: String,
            val policy: ExistingWorkPolicy,
            val request: OneTimeWorkRequest,
        )

        val enqueued = mutableListOf<WorkRequest>()
        val unique = mutableListOf<Unique>()
        val cancelled = mutableListOf<UUID>()
        val observers = mutableMapOf<UUID, Observer<WorkInfo?>>()
        var removed = 0

        override fun enqueue(request: WorkRequest) {
            enqueued += request
        }

        override fun enqueueUniqueWork(
            name: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ) {
            unique += Unique(name, policy, request)
        }

        override fun cancelWorkById(id: UUID) {
            cancelled += id
        }

        override fun observe(id: UUID, observer: Observer<WorkInfo?>): EditorBackgroundJobCoordinator.WorkObservation {
            observers[id] = observer
            return object : EditorBackgroundJobCoordinator.WorkObservation {
                override fun remove() {
                    removed++
                    observers.remove(id)
                }
            }
        }
    }
}
