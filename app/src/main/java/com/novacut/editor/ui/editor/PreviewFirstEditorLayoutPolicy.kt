package com.novacut.editor.ui.editor

object PreviewFirstEditorLayoutPolicy {
    data class Decision(
        val previewCritical: Boolean,
        val lockBottomToolArea: Boolean,
        val bottomToolRailHeightDp: Int,
        val previewMinHeightDp: Int,
        val timelineMinHeightDp: Int,
        val timelineMaxHeightDp: Int
    )

    private val previewCriticalPanels = setOf(
        PanelId.TRANSFORM,
        PanelId.CROP,
        PanelId.COLOR_GRADING,
        PanelId.KEYFRAME_EDITOR,
        PanelId.SPEED_CURVE,
        PanelId.MASK_EDITOR,
        PanelId.BLEND_MODE,
        PanelId.PIP_PRESETS,
        PanelId.CHROMA_KEY,
        PanelId.CAPTION_EDITOR,
        PanelId.SMART_REFRAME,
        PanelId.SPEED_PRESETS
    )

    fun isPreviewCriticalPanel(panel: PanelId): Boolean = panel in previewCriticalPanels

    fun decide(
        paneMode: AdaptiveEditorLayoutPolicy.PaneMode,
        screenHeightDp: Int,
        compactEditorHeight: Boolean,
        clipSelected: Boolean,
        currentTool: EditorTool,
        openPanels: Set<PanelId>,
        selectedEffectActive: Boolean,
        bottomToolPanelExpanded: Boolean,
        timelineEditGestureActive: Boolean
    ): Decision {
        val previewCritical = clipSelected && (
            timelineEditGestureActive ||
                currentTool in previewCriticalTools ||
                openPanels.any(::isPreviewCriticalPanel) ||
                selectedEffectActive
            )
        val singlePane = paneMode == AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE
        val lockBottomToolArea = singlePane && previewCritical
        val effectiveBottomExpanded = bottomToolPanelExpanded && !lockBottomToolArea
        val bottomToolRailHeightDp = if (effectiveBottomExpanded) 244 else 96
        val previewMinHeightDp = previewMinHeightDp(
            paneMode = paneMode,
            screenHeightDp = screenHeightDp,
            compactEditorHeight = compactEditorHeight,
            clipSelected = clipSelected,
            previewCritical = previewCritical
        )
        val timelineBudget = (screenHeightDp - 64 - previewMinHeightDp - bottomToolRailHeightDp)
            .coerceAtLeast(if (previewCritical) 140 else 160)
        val timelineRange = timelineRangeDp(
            paneMode = paneMode,
            compactEditorHeight = compactEditorHeight,
            clipSelected = clipSelected,
            previewCritical = previewCritical,
            bottomToolExpanded = effectiveBottomExpanded
        )
        val timelineMin = timelineRange.first.coerceAtMost(timelineBudget)
        val timelineMax = timelineRange.second.coerceIn(timelineMin, timelineBudget)
        return Decision(
            previewCritical = previewCritical,
            lockBottomToolArea = lockBottomToolArea,
            bottomToolRailHeightDp = bottomToolRailHeightDp,
            previewMinHeightDp = previewMinHeightDp,
            timelineMinHeightDp = timelineMin,
            timelineMaxHeightDp = timelineMax
        )
    }

    private fun previewMinHeightDp(
        paneMode: AdaptiveEditorLayoutPolicy.PaneMode,
        screenHeightDp: Int,
        compactEditorHeight: Boolean,
        clipSelected: Boolean,
        previewCritical: Boolean
    ): Int {
        return when (paneMode) {
            AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT -> 240
            AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE -> 220
            AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE -> 200
            AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE -> when {
                previewCritical && screenHeightDp < 560 -> 200
                previewCritical && screenHeightDp < 720 -> 232
                previewCritical -> 280
                clipSelected && compactEditorHeight -> 210
                clipSelected -> 230
                else -> 180
            }
        }
    }

    private fun timelineRangeDp(
        paneMode: AdaptiveEditorLayoutPolicy.PaneMode,
        compactEditorHeight: Boolean,
        clipSelected: Boolean,
        previewCritical: Boolean,
        bottomToolExpanded: Boolean
    ): Pair<Int, Int> {
        return when (paneMode) {
            AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT -> 280 to 380
            AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE -> 280 to 420
            AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE -> 252 to 360
            AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE -> when {
                previewCritical && compactEditorHeight -> 200 to 260
                previewCritical -> 220 to 300
                clipSelected && bottomToolExpanded -> 220 to 260
                clipSelected && compactEditorHeight -> 220 to 280
                clipSelected -> 280 to 390
                else -> 240 to 330
            }
        }
    }

    private val previewCriticalTools = setOf(
        EditorTool.TRIM,
        EditorTool.SPLIT,
        EditorTool.SPEED,
        EditorTool.TRANSFORM,
        EditorTool.CROP
    )
}
