package com.novacut.editor.ui.editor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composition boundary for editor system coordinators.
 *
 * Keeping these Hilt-owned collaborators together leaves the ViewModel with a
 * single system boundary while preserving the smaller, independently tested
 * contracts behind it.
 */
@Singleton
class EditorCoordinatorSet @Inject constructor(
    val document: EditorDocumentCoordinator,
    val projectTransfer: EditorProjectTransferCoordinator,
    val backgroundJobs: EditorBackgroundJobCoordinator,
    val playback: EditorPlaybackCoordinator,
)
