package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AiToolRequirements
import com.novacut.editor.engine.CaptionTranslationEngine
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ProjectArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDomainStateTest {

    @Test
    fun domainStatesEnumerateEveryTopLevelEditorDomain() {
        val kinds = EditorState().domainStates.asList().map { it.kind }.toSet()

        assertEquals(
            setOf(
                EditorDomainState.Kind.PANEL,
                EditorDomainState.Kind.CAPTION,
                EditorDomainState.Kind.COMPOUND,
                EditorDomainState.Kind.EXPORT,
                EditorDomainState.Kind.AI,
                EditorDomainState.Kind.MEDIA
            ),
            kinds
        )
    }

    @Test
    fun domainProjectionCarriesRepresentativeStateFromEachDomain() {
        val requirement = AiToolRequirements.requirementFor("video_upscale")
        val prompt = AiRequirementPrompt(
            title = "Model required",
            body = "Download before running",
            modelName = "Real-ESRGAN",
            estimatedSize = "17 MB",
            actionLabel = "Review"
        )
        val suggestion = AiSuggestion(
            id = "s1",
            message = "Clean this audio",
            actionId = "denoise"
        )
        val backupFeedback = BackupImportFeedback(
            succeeded = true,
            title = "Backup imported",
            body = "Review media",
            report = ProjectArchive.ImportReport(
                schemaVersion = 1,
                schemaTooNew = false,
                originalProjectId = "original",
                effectiveProjectId = "effective",
                projectIdCollided = false,
                idCollisionPolicy = ProjectArchive.IdCollisionPolicy.REGENERATE,
                mediaTotal = 1,
                mediaResolved = 1,
                unresolvedMediaUris = emptyList(),
                warnings = emptyList(),
                targetDirCreated = false
            )
        )
        val state = EditorState(
            panel = EditorPanelState(
                panels = PanelVisibility(openPanels = setOf(PanelId.AI_TOOLS)),
                selectedEffectId = "effect-1",
                editingTextOverlayId = "text-1"
            ),
            caption = EditorCaptionState(
                sourceLang = "en",
                targetLang = "es",
                quality = CaptionTranslationEngine.LanguagePairQuality.GOOD
            ),
            compound = EditorCompoundState(
                depth = 2,
                breadcrumbText = "Root > Compound"
            ),
            export = EditorExportDomainState(
                progress = 0.42f,
                state = ExportState.EXPORTING,
                lastExportedFilePath = "C:/exports/final.mp4",
                errorMessage = "blocked",
                startTime = 1234L
            ),
            ai = EditorAiState(
                requirementPrompt = prompt,
                modelRequirement = requirement,
                processingTool = "video_upscale",
                suggestion = suggestion,
                isReframing = true,
                isAutoEditing = true,
                isSynthesizingTts = true,
                isTtsAvailable = true,
                isAnalyzingNoise = true,
                noiseAnalysisResult = "Noise profile ready"
            ),
            media = EditorMediaState(
                backupImportFeedback = backupFeedback
            )
        )

        val domains = state.domainStates

        assertTrue(domains.panel.panels.isOpen(PanelId.AI_TOOLS))
        assertEquals("effect-1", domains.panel.selectedEffectId)
        assertEquals("text-1", domains.panel.editingTextOverlayId)
        assertEquals("en", domains.caption.sourceLang)
        assertEquals("es", domains.caption.targetLang)
        assertEquals(CaptionTranslationEngine.LanguagePairQuality.GOOD, domains.caption.quality)
        assertEquals(2, domains.compound.depth)
        assertEquals("Root > Compound", domains.compound.breadcrumbText)
        assertEquals(0.42f, domains.export.progress, 0.0001f)
        assertEquals(ExportState.EXPORTING, domains.export.state)
        assertEquals("C:/exports/final.mp4", domains.export.lastExportedFilePath)
        assertEquals("blocked", domains.export.errorMessage)
        assertEquals(1234L, domains.export.startTime)
        assertSame(prompt, domains.ai.requirementPrompt)
        assertSame(requirement, domains.ai.modelRequirement)
        assertEquals("video_upscale", domains.ai.processingTool)
        assertSame(suggestion, domains.ai.suggestion)
        assertTrue(domains.ai.isReframing)
        assertTrue(domains.ai.isAutoEditing)
        assertTrue(domains.ai.isSynthesizingTts)
        assertTrue(domains.ai.isTtsAvailable)
        assertTrue(domains.ai.isAnalyzingNoise)
        assertEquals("Noise profile ready", domains.ai.noiseAnalysisResult)
        assertSame(backupFeedback, domains.media.backupImportFeedback)
        assertTrue(domains.media.relinkReports.isEmpty())
    }
}
