package com.novacut.editor.ui.settings

import android.content.Context
import com.novacut.editor.engine.DiagnosticExportEngine
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.engine.whisper.WhisperEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the diagnostic ZIP from the same inputs wherever it's asked for, so the
 * Settings export and the crash report on the projects screen carry the same
 * model, permission and timeline details under the same privacy settings.
 */
@Singleton
class DiagnosticBundleBuilder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepo: SettingsRepository,
    private val whisperEngine: WhisperEngine,
    private val segmentationEngine: SegmentationEngine,
    private val diagnosticExportEngine: DiagnosticExportEngine,
    private val projectDao: ProjectDao,
    private val autoSave: ProjectAutoSave,
) {
    class Bundle(
        val file: File,
        val timelineShapeRequested: Boolean,
        val timelineShapeIncluded: Boolean,
    )

    suspend fun build(issueBody: String? = null): Bundle {
        val modelRegistry = withContext(Dispatchers.IO) {
            val whisperBytes = whisperEngine.getModelSizeBytes()
            val segmentationBytes = segmentationEngine.getModelSizeBytes()
            listOf(
                DiagnosticExportEngine.ModelSnapshot(
                    id = "whisper-onnx",
                    installed = whisperBytes > 0L,
                    sizeBytes = whisperBytes
                ),
                DiagnosticExportEngine.ModelSnapshot(
                    id = "segmentation-mediapipe",
                    installed = segmentationBytes > 0L,
                    sizeBytes = segmentationBytes
                )
            )
        }
        val settings = settingsRepo.settings.first()
        val timelineShape = if (settings.includeDiagnosticTimelineShape) {
            withContext(Dispatchers.IO) { latestTimelineShape() }
        } else {
            null
        }
        val file = diagnosticExportEngine.exportDiagnosticBundle(
            modelRegistry = modelRegistry,
            timelineShape = timelineShape,
            permissionSnapshots = DiagnosticExportEngine.collectRuntimePermissionSnapshots(appContext),
            includeRawExportErrorText = settings.includeDiagnosticRawErrorText,
            issueBody = issueBody,
            includeHeapDump = settings.includeDiagnosticHeapDump,
        )
        return Bundle(
            file = file,
            timelineShapeRequested = settings.includeDiagnosticTimelineShape,
            timelineShapeIncluded = timelineShape != null,
        )
    }

    private suspend fun latestTimelineShape(): DiagnosticExportEngine.TimelineShape? {
        val latestProject = projectDao.getAllProjectsSnapshot().firstOrNull() ?: return null
        val outcome = autoSave.loadRecoveryDataWithOutcome(latestProject.id)
        val state = (outcome as? ProjectAutoSave.LoadOutcome.Loaded)?.state ?: return null
        return DiagnosticExportEngine.summarizeTimelineShape(state.tracks)
    }
}
