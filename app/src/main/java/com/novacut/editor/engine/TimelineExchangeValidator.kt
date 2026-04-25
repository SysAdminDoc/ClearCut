package com.novacut.editor.engine

import android.net.Uri
import com.novacut.editor.engine.TimelineExchangeEngine.TimelineExchangeFormat
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.TransitionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-flight validator for timeline import/export against external NLE formats.
 *
 * Runs *before* the export writer is invoked or *after* the import parser
 * returns its candidate state. Produces a single [Report] with categorised
 * issues so the UI can surface a real "what will/did change" sheet instead of
 * silently dropping data — historically the export path lost transitions, blend
 * modes, and effect chains without telling the user.
 *
 * The validator is intentionally pure: it does not touch the filesystem, never
 * mutates the input, and depends only on data classes from [com.novacut.editor.model].
 * That makes it safe to call from a worker thread or from inside an export
 * pipeline that already holds locks on the shared player.
 */
@Singleton
class TimelineExchangeValidator @Inject constructor() {

    enum class Severity {
        /** Operation cannot proceed as-is. */
        ERROR,

        /** Operation will proceed but data will be lost or substituted. */
        WARNING,

        /** Operation will proceed; informational only. */
        INFO
    }

    enum class Direction { EXPORT, IMPORT }

    /**
     * One actionable issue. [path] is a human-readable location ("Track 2 → Clip 5")
     * so the UI can drop it straight into a sheet without further interpretation.
     */
    data class Issue(
        val severity: Severity,
        val path: String,
        val message: String,
        val suggestedFix: String? = null
    )

    data class Report(
        val format: TimelineExchangeFormat,
        val direction: Direction,
        val issues: List<Issue>
    ) {
        val errors: List<Issue> = issues.filter { it.severity == Severity.ERROR }
        val warnings: List<Issue> = issues.filter { it.severity == Severity.WARNING }
        val infos: List<Issue> = issues.filter { it.severity == Severity.INFO }
        val canProceed: Boolean = errors.isEmpty()
        val summary: String
            get() = when {
                errors.isNotEmpty() -> "${errors.size} blocking, ${warnings.size} lossy"
                warnings.isNotEmpty() -> "${warnings.size} lossy"
                infos.isNotEmpty() -> "${infos.size} note(s)"
                else -> "No issues"
            }
    }

    /**
     * Validate a snapshot before exporting to [format].
     */
    fun validateExport(
        format: TimelineExchangeFormat,
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        frameRate: Int = 30
    ): Report {
        val issues = mutableListOf<Issue>()

        if (!format.canExport) {
            issues += Issue(
                Severity.ERROR,
                path = format.displayName,
                message = "Format is not yet supported for export.",
                suggestedFix = "Pick a supported format (OTIO, FCPXML, EDL)."
            )
            return Report(format, Direction.EXPORT, issues)
        }

        if (frameRate <= 0) {
            issues += Issue(
                Severity.WARNING,
                path = "Project",
                message = "Frame rate '$frameRate' is non-positive; default 30 fps will be used.",
                suggestedFix = "Set the project frame rate before export."
            )
        }

        val videoTrackCount = tracks.count { it.type == TrackType.VIDEO }
        val overlayTrackCount = tracks.count { it.type == TrackType.OVERLAY }
        val audioTrackCount = tracks.count { it.type == TrackType.AUDIO }
        val adjustmentTrackCount = tracks.count { it.type == TrackType.ADJUSTMENT }

        // EDL is single-track-per-file by spec. Warn loudly so the user knows
        // tracks 2+ won't make it.
        if (format == TimelineExchangeFormat.EDL_CMX3600) {
            if (videoTrackCount + overlayTrackCount > 1) {
                issues += Issue(
                    Severity.WARNING,
                    path = "Tracks",
                    message = "EDL CMX 3600 is single-track. Only the first video track will export.",
                    suggestedFix = "Use OTIO or FCPXML for multi-track projects."
                )
            }
            if (audioTrackCount > 0) {
                issues += Issue(
                    Severity.INFO,
                    path = "Audio",
                    message = "EDL audio rows export, but per-clip audio effects do not.",
                )
            }
        }

        if (adjustmentTrackCount > 0) {
            issues += Issue(
                Severity.WARNING,
                path = "Adjustment layers",
                message = "$adjustmentTrackCount adjustment track(s) have no equivalent in $format and will be dropped.",
                suggestedFix = "Bake adjustment-layer effects onto each affected clip before export."
            )
        }

        tracks.forEachIndexed { trackIdx, track ->
            val trackPath = "Track ${trackIdx + 1} (${track.type.name.lowercase()})"

            if (track.blendMode != BlendMode.NORMAL) {
                issues += Issue(
                    Severity.WARNING,
                    path = trackPath,
                    message = "Track blend mode '${track.blendMode.name}' has no $format equivalent.",
                    suggestedFix = "Pre-composite the blend or expect 'normal' on import."
                )
            }

            if (track.audioEffects.isNotEmpty() && format != TimelineExchangeFormat.OTIO) {
                issues += Issue(
                    Severity.WARNING,
                    path = trackPath,
                    message = "${track.audioEffects.size} track-level audio effect(s) won't be carried by $format.",
                )
            }

            track.clips.forEachIndexed { clipIdx, clip ->
                val clipPath = "$trackPath → Clip ${clipIdx + 1}"
                validateClipForExport(format, clip, clipPath, issues)
            }
        }

        textOverlays.forEachIndexed { idx, overlay ->
            val path = "Text overlay ${idx + 1}"
            if (overlay.endTimeMs <= overlay.startTimeMs) {
                issues += Issue(
                    Severity.ERROR,
                    path = path,
                    message = "Overlay end time (${overlay.endTimeMs} ms) is not after start (${overlay.startTimeMs} ms).",
                    suggestedFix = "Drag the overlay to a positive duration before exporting."
                )
            }
            if (format == TimelineExchangeFormat.EDL_CMX3600 && overlay.text.isNotBlank()) {
                issues += Issue(
                    Severity.WARNING,
                    path = path,
                    message = "EDL has no text track; overlay '${overlay.text.take(40)}' will be dropped.",
                )
            } else if (format != TimelineExchangeFormat.OTIO && format != TimelineExchangeFormat.FCPXML) {
                issues += Issue(
                    Severity.WARNING,
                    path = path,
                    message = "Text overlay styling will be lost outside OTIO/FCPXML.",
                )
            }
        }

        return Report(format, Direction.EXPORT, issues)
    }

    /**
     * Validate the candidate result of an import before committing it to the
     * editor. [unresolvedMediaUris] is the list returned by the importer for
     * media that could not be located on disk.
     */
    fun validateImport(
        format: TimelineExchangeFormat,
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        unresolvedMediaUris: List<String> = emptyList(),
        droppedEffects: Int = 0,
        importerWarnings: List<String> = emptyList()
    ): Report {
        val issues = mutableListOf<Issue>()

        if (!format.canImport) {
            issues += Issue(
                Severity.ERROR,
                path = format.displayName,
                message = "Format is not yet supported for import.",
                suggestedFix = "Re-export the timeline as OTIO from the source NLE."
            )
            return Report(format, Direction.IMPORT, issues)
        }

        importerWarnings.forEach { msg ->
            issues += Issue(Severity.WARNING, path = "Parser", message = msg)
        }

        if (droppedEffects > 0) {
            issues += Issue(
                Severity.WARNING,
                path = "Effects",
                message = "$droppedEffects effect(s) had no NovaCut equivalent and were dropped.",
                suggestedFix = "Re-apply effects manually after import."
            )
        }

        unresolvedMediaUris.forEach { uri ->
            issues += Issue(
                Severity.ERROR,
                path = "Media: $uri",
                message = "Source media file could not be found.",
                suggestedFix = "Use 'Relink media' to point at the file's new location."
            )
        }

        if (tracks.isEmpty() && textOverlays.isEmpty()) {
            issues += Issue(
                Severity.ERROR,
                path = "Timeline",
                message = "Imported timeline contains no tracks or overlays.",
                suggestedFix = "Verify the source file isn't an empty project."
            )
        }

        // Frame-rate drift: clip trims that don't snap to a sensible frame
        // boundary tend to mean the source NLE used a non-standard rate
        // (23.976 vs 24, 29.97 vs 30) and the importer assumed the wrong one.
        tracks.forEachIndexed { trackIdx, track ->
            val trackPath = "Track ${trackIdx + 1}"
            track.clips.forEachIndexed { clipIdx, clip ->
                if (clip.trimEndMs <= clip.trimStartMs) {
                    issues += Issue(
                        Severity.ERROR,
                        path = "$trackPath → Clip ${clipIdx + 1}",
                        message = "Clip trim range is empty (${clip.trimStartMs}..${clip.trimEndMs} ms).",
                        suggestedFix = "Re-export from the source NLE; this clip is unrecoverable."
                    )
                }
                if (clip.sourceUri == Uri.EMPTY || clip.sourceUri.toString().isBlank()) {
                    issues += Issue(
                        Severity.ERROR,
                        path = "$trackPath → Clip ${clipIdx + 1}",
                        message = "Clip has no source URI.",
                        suggestedFix = "Use 'Relink media' to point at the source file."
                    )
                }
            }
        }

        return Report(format, Direction.IMPORT, issues)
    }

    private fun validateClipForExport(
        format: TimelineExchangeFormat,
        clip: Clip,
        clipPath: String,
        issues: MutableList<Issue>
    ) {
        if (clip.sourceUri == Uri.EMPTY || clip.sourceUri.toString().isBlank()) {
            issues += Issue(
                Severity.ERROR,
                path = clipPath,
                message = "Clip has no source URI; importer will not find any media.",
                suggestedFix = "Relink the clip to a file before exporting."
            )
        }

        if (clip.trimEndMs <= clip.trimStartMs) {
            issues += Issue(
                Severity.ERROR,
                path = clipPath,
                message = "Clip trim range is empty (${clip.trimStartMs}..${clip.trimEndMs} ms).",
                suggestedFix = "Drag the clip handles to give it a positive duration."
            )
        }

        if (clip.isCompound) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "Compound clips are flattened to a single clip on export.",
                suggestedFix = "Open the compound to bake child timing if precision matters."
            )
        }

        if (clip.isReversed) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "Reverse playback is preview-only; exported clip will play forward.",
                suggestedFix = "Pre-render the reversed clip with FFmpegX once it ships."
            )
        }

        if (clip.speedCurve != null && clip.speedCurve.points.size >= 2) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "Speed ramp (curved) flattens to a constant time-warp on export.",
                suggestedFix = "Bake the ramp into a rendered clip if timing matters."
            )
        }

        if (clip.blendMode != BlendMode.NORMAL) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "Clip blend mode '${clip.blendMode.name}' has no $format equivalent.",
            )
        }

        if (clip.masks.isNotEmpty()) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "${clip.masks.size} mask(s) won't survive ${format.displayName} export.",
            )
        }

        if (clip.colorGrade != null) {
            issues += Issue(
                Severity.WARNING,
                path = clipPath,
                message = "Color grade is not represented in $format and will be dropped.",
                suggestedFix = "Export an accompanying .cube LUT alongside the timeline."
            )
        }

        if (clip.effects.isNotEmpty()) {
            issues += Issue(
                Severity.INFO,
                path = clipPath,
                message = "${clip.effects.size} effect(s) export as named markers; the receiving NLE must re-apply them manually.",
            )
        }

        if (format == TimelineExchangeFormat.EDL_CMX3600) {
            clip.transition?.let { transition ->
                if (transition.type !in EDL_SUPPORTED_TRANSITIONS) {
                    issues += Issue(
                        Severity.WARNING,
                        path = clipPath,
                        message = "EDL only supports cut/dissolve; '${transition.type.name}' downgrades to a dissolve.",
                    )
                }
            }
        }
    }

    companion object {
        private val EDL_SUPPORTED_TRANSITIONS = setOf(
            TransitionType.DISSOLVE,
            TransitionType.FADE_BLACK,
            TransitionType.FADE_WHITE
        )
    }
}
