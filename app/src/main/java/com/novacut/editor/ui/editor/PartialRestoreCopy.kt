package com.novacut.editor.ui.editor

import android.content.res.Resources
import com.novacut.editor.R
import com.novacut.editor.engine.ProjectRestoreReport
import com.novacut.editor.engine.RestoreElementKind

/** Render restore losses with locale-aware quantities instead of persisted log labels. */
internal fun partialRestoreSummary(
    resources: Resources,
    report: ProjectRestoreReport,
): String = partialRestoreLosses(resources, report).joinToString()

internal fun partialRestoreBulletList(
    resources: Resources,
    report: ProjectRestoreReport,
): String = partialRestoreLosses(resources, report).joinToString("\n") { "• $it" }

private fun partialRestoreLosses(
    resources: Resources,
    report: ProjectRestoreReport,
): List<String> = report.countsByRestoreKind().map { (kind, count) ->
    resources.getQuantityString(kind.quantityResId(), count, count)
}

private fun RestoreElementKind.quantityResId(): Int = when (this) {
    RestoreElementKind.CLIPS -> R.plurals.partial_restore_clips
    RestoreElementKind.TRACKS -> R.plurals.partial_restore_tracks
    RestoreElementKind.EFFECTS -> R.plurals.partial_restore_effects
    RestoreElementKind.KEYFRAMES -> R.plurals.partial_restore_keyframes
    RestoreElementKind.CHAPTER_MARKERS -> R.plurals.partial_restore_chapter_markers
    RestoreElementKind.TIMELINE_MARKERS -> R.plurals.partial_restore_timeline_markers
    RestoreElementKind.BEAT_MARKERS -> R.plurals.partial_restore_beat_markers
    RestoreElementKind.IMAGE_OVERLAYS -> R.plurals.partial_restore_image_overlays
    RestoreElementKind.TEXT_OVERLAYS -> R.plurals.partial_restore_text_overlays
    RestoreElementKind.WATERMARKS -> R.plurals.partial_restore_watermarks
    RestoreElementKind.TRANSITIONS -> R.plurals.partial_restore_transitions
    RestoreElementKind.DRAWING_PATHS -> R.plurals.partial_restore_drawing_paths
    RestoreElementKind.POINTS -> R.plurals.partial_restore_points
    RestoreElementKind.TRANSCRIPTS -> R.plurals.partial_restore_transcripts
    RestoreElementKind.TRACKED_OBJECTS -> R.plurals.partial_restore_tracked_objects
    RestoreElementKind.STORYBOARD_CARDS -> R.plurals.partial_restore_storyboard_cards
    RestoreElementKind.MEDIA_ASSETS -> R.plurals.partial_restore_media_assets
    RestoreElementKind.CAPTIONS -> R.plurals.partial_restore_captions
    RestoreElementKind.CAPTION_WORDS -> R.plurals.partial_restore_caption_words
    RestoreElementKind.OTHER -> R.plurals.partial_restore_other_elements
}
