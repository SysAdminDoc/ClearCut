package com.novacut.editor.engine

import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ExportQuality
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.TimelineExportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BatchExportPlanStoreTest {

    @Test
    fun roundTripPreservesConfigAndLeavesCompletedWorkOutOfThePlan() {
        val dir = Files.createTempDirectory("batch-plan-round-trip-").toFile()
        try {
            val store = BatchExportPlanStore.forFile(File(dir, "plan.json"))
            val context = BatchExportPlanContext("project-a", "project-fingerprint")
            val config = ExportConfig(
                resolution = Resolution.HD_720P,
                frameRate = 24,
                forceConstantFrameRate = true,
                quality = ExportQuality.MEDIUM,
                aspectRatio = AspectRatio.RATIO_1_1,
                includeChapterMarkers = true,
                chapters = listOf(ChapterMarker(1_250L, "Opening")),
                exportAsContactSheet = true,
                contactSheetColumns = 6,
                timelineRange = TimelineExportRange(30L, 180L),
                filenameTemplate = "{name}-square",
                scrubMetadata = true,
                preserveSourceLocationMetadata = true,
                preserveSourceStreamMetadata = true,
            )
            val failed = BatchExportItem(
                id = "failed-item",
                config = config,
                outputName = "Square",
                projectId = context.projectId,
                projectFingerprint = context.projectFingerprint,
                configFingerprint = exportConfigFingerprint(config),
                status = BatchExportStatus.FAILED,
                progress = 0.42f,
                errorMessage = "Encoder failed",
                createdAtEpochMs = 42L,
            )
            val completed = failed.copy(id = "completed-item", status = BatchExportStatus.COMPLETED)

            store.saveFor(context, listOf(failed, completed))

            val restored = store.readFor(context)
            assertEquals(1, restored.size)
            assertEquals(failed.id, restored.single().id)
            assertEquals(failed.outputName, restored.single().outputName)
            assertEquals(failed.config, restored.single().config)
            assertEquals(failed.status, restored.single().status)
            assertEquals(failed.errorMessage, restored.single().errorMessage)
            assertEquals(failed.createdAtEpochMs, restored.single().createdAtEpochMs)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun inProgressWorkRestoresAsInterruptedWithoutAutoRunning() {
        val dir = Files.createTempDirectory("batch-plan-interrupted-").toFile()
        try {
            val store = BatchExportPlanStore.forFile(File(dir, "plan.json"))
            val context = BatchExportPlanContext("project-a", "fingerprint")
            store.saveFor(
                context,
                listOf(
                    BatchExportItem(
                        id = "active",
                        config = ExportConfig(),
                        outputName = "Active",
                        projectId = context.projectId,
                        projectFingerprint = context.projectFingerprint,
                        configFingerprint = exportConfigFingerprint(ExportConfig()),
                        status = BatchExportStatus.IN_PROGRESS,
                        progress = 0.7f,
                    )
                )
            )

            val restored = store.readFor(context).single()
            assertEquals(BatchExportStatus.INTERRUPTED, restored.status)
            assertEquals(0f, restored.progress)
            assertTrue(restored.errorMessage!!.contains("interrupted", ignoreCase = true))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun changedProjectOrConfigRequiresReview() {
        val dir = Files.createTempDirectory("batch-plan-review-").toFile()
        try {
            val store = BatchExportPlanStore.forFile(File(dir, "plan.json"))
            val originalContext = BatchExportPlanContext("project-a", "old-project")
            val config = ExportConfig()
            store.saveFor(
                originalContext,
                listOf(
                    BatchExportItem(
                        id = "stale",
                        config = config,
                        outputName = "Stale",
                        projectId = originalContext.projectId,
                        projectFingerprint = originalContext.projectFingerprint,
                        configFingerprint = exportConfigFingerprint(config),
                    )
                )
            )

            val restored = store.readFor(
                BatchExportPlanContext("project-a", "new-project")
            ).single()
            assertEquals(BatchExportStatus.REVIEW_REQUIRED, restored.status)
            assertTrue(restored.errorMessage!!.contains("changed", ignoreCase = true))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun countAndByteBoundsProtectTheExistingAtomicPlan() {
        val dir = Files.createTempDirectory("batch-plan-bounds-").toFile()
        try {
            val file = File(dir, "plan.json")
            val store = BatchExportPlanStore.forFile(file, maxItems = 2)
            val context = BatchExportPlanContext("project-a", "fingerprint")
            val stable = BatchExportItem(
                id = "stable",
                config = ExportConfig(),
                outputName = "Stable",
                projectId = context.projectId,
                projectFingerprint = context.projectFingerprint,
                configFingerprint = exportConfigFingerprint(ExportConfig()),
            )
            store.saveFor(context, listOf(stable))
            val before = file.readText(Charsets.UTF_8)

            val oversized = stable.copy(
                id = "oversized",
                config = ExportConfig(
                    chapters = List(500) { ChapterMarker(it.toLong(), "x".repeat(512)) }
                )
            )
            try {
                store.saveFor(context, listOf(stable, oversized))
                throw AssertionError("Expected the bounded plan write to fail")
            } catch (_: IllegalArgumentException) {
                // The old plan must remain intact when the new snapshot is too large.
            }

            assertEquals(before, file.readText(Charsets.UTF_8))
            assertEquals(1, store.readFor(context).size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
