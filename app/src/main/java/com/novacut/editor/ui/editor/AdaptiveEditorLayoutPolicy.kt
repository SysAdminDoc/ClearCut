package com.novacut.editor.ui.editor

/**
 * R8.8 adaptive layout policy for Android 16 forced-resizable screens.
 *
 * Kept Android-free so tablet/foldable breakpoints are unit-testable without
 * Compose or WindowManager. Platform posture detection feeds [isTabletop]
 * from WindowManager at the activity boundary.
 */
object AdaptiveEditorLayoutPolicy {
    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
    enum class HeightClass { COMPACT, MEDIUM, EXPANDED }
    enum class PaneMode { SINGLE_PANE, TWO_PANE, THREE_PANE, TABLETOP_SPLIT }

    data class Decision(
        val widthClass: WidthClass,
        val heightClass: HeightClass,
        val paneMode: PaneMode,
        val useSidebar: Boolean,
        val compactTimeline: Boolean,
        val preferEmbeddedExportPane: Boolean,
    )

    fun widthClass(widthDp: Int): WidthClass = when {
        widthDp < 600 -> WidthClass.COMPACT
        widthDp < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }

    fun heightClass(heightDp: Int): HeightClass = when {
        heightDp < 480 -> HeightClass.COMPACT
        heightDp < 900 -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }

    fun decide(
        widthDp: Int,
        heightDp: Int,
        isTabletop: Boolean = false,
        desktopLike: Boolean = false
    ): Decision {
        val width = widthClass(widthDp)
        val height = heightClass(heightDp)
        val paneMode = when {
            isTabletop && width != WidthClass.COMPACT -> PaneMode.TABLETOP_SPLIT
            desktopLike || width == WidthClass.EXPANDED -> PaneMode.THREE_PANE
            width == WidthClass.MEDIUM -> PaneMode.TWO_PANE
            else -> PaneMode.SINGLE_PANE
        }
        return Decision(
            widthClass = width,
            heightClass = height,
            paneMode = paneMode,
            useSidebar = paneMode == PaneMode.THREE_PANE,
            compactTimeline = height == HeightClass.COMPACT || width == WidthClass.COMPACT,
            preferEmbeddedExportPane = paneMode == PaneMode.THREE_PANE && widthDp >= 1000 && height != HeightClass.COMPACT
        )
    }
}
