package com.novacut.editor.engine

import android.net.Uri
import android.util.Log
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LosslessCut-style fast-trim eligibility detector.
 *
 * Two shapes of eligible timeline are handled:
 *   * **Single clip** — head/tail trims on one unmodified source. Classic
 *     fast-trim. Muxed via [StreamCopyMuxer.trim].
 *   * **Multi-clip same source** — several clips that all trim the same
 *     source URI with only head/tail cuts. Muxed via
 *     [StreamCopyMuxer.concat] which produces a single output concatenating
 *     each keeper range. All clips must live on the same visible VIDEO track,
 *     sorted by timelineStartMs, with no gaps requiring black or overlays.
 *
 * Stream-copy is never used when any clip has effects, colour grade,
 * transform, speed change, opacity, audio fades/volume, etc. — the full
 * `firstDisqualifier` list applies to every candidate clip.
 */
@Singleton
class StreamCopyExportEngine @Inject constructor(
    private val streamCopyMuxer: StreamCopyMuxer
) {

    data class Eligibility(
        val eligible: Boolean,
        val reason: String,
        val sourceUri: Uri? = null,
        val ranges: List<StreamCopyMuxer.Range> = emptyList()
    ) {
        val startMs: Long get() = ranges.firstOrNull()?.startMs ?: 0L
        val endMs: Long get() = ranges.lastOrNull()?.endMs ?: 0L
    }

    fun analyze(tracks: List<Track>, hasEffectsOrOverlays: Boolean): Eligibility {
        if (hasEffectsOrOverlays) return Eligibility(false, "effects or overlays present")
        val videoTracks = tracks.filter { it.type == TrackType.VIDEO && it.isVisible }
        if (videoTracks.size != 1) return Eligibility(false, "multi-track video")
        val audioTracks = tracks.filter { it.type == TrackType.AUDIO && it.isVisible && it.clips.isNotEmpty() }
        if (audioTracks.isNotEmpty()) return Eligibility(false, "additional audio tracks")
        val videoTrack = videoTracks[0]
        if (videoTrack.isMuted || videoTrack.opacity != 1f || videoTrack.volume != 1f ||
            videoTrack.pan != 0f || videoTrack.blendMode != BlendMode.NORMAL ||
            videoTrack.audioEffects.isNotEmpty()
        ) {
            return Eligibility(false, "video track has non-default mix")
        }
        val clips = videoTrack.clips.sortedBy { it.timelineStartMs }
        if (clips.isEmpty()) return Eligibility(false, "no clips")
        // Every clip must target the same source; otherwise concat would
        // interleave two different codecs which MediaMuxer can't mix. We
        // compare by `.toString()` because Android's `Uri.equals` is
        // content:// scheme-aware and a Robolectric/JVM unit test
        // environment returns the default (false) for un-mocked framework
        // calls, which would silently disqualify every single-clip timeline.
        val firstSource = clips[0].sourceUri
        val firstSourceStr = firstSource.toString()
        if (clips.any { it.sourceUri.toString() != firstSourceStr }) {
            return Eligibility(false, "multiple source files")
        }
        for (c in clips) {
            val reason = c.firstDisqualifier()
            if (reason != null) return Eligibility(false, reason)
        }
        val ranges = clips.map { StreamCopyMuxer.Range(it.trimStartMs, it.trimEndMs) }
        return Eligibility(true, "eligible", firstSource, ranges)
    }

    suspend fun execute(
        e: Eligibility,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val src = e.sourceUri
        if (!e.eligible || src == null || e.ranges.isEmpty()) return false
        Log.d(TAG, "stream-copy export ${e.ranges.size} range(s) from $src -> $outputPath")
        return if (e.ranges.size == 1) {
            streamCopyMuxer.trim(src, e.ranges[0].startMs, e.ranges[0].endMs, outputPath, onProgress)
        } else {
            streamCopyMuxer.concat(src, e.ranges, outputPath, onProgress)
        }
    }

    /**
     * Return the first field of the clip that would force a decode-modify-encode
     * round-trip, or null when every field is pass-through-safe. Returning the
     * specific reason lets the UI surface WHY an export had to transcode.
     */
    private fun Clip.firstDisqualifier(): String? = when {
        effects.isNotEmpty() -> "clip has effects"
        colorGrade != null -> "clip has color grade"
        speed != 1f -> "clip speed ≠ 1×"
        speedCurve != null -> "clip has speed curve"
        isReversed -> "clip is reversed"
        keyframes.isNotEmpty() -> "clip has keyframes"
        positionX != 0f || positionY != 0f -> "clip is translated"
        scaleX != 1f || scaleY != 1f -> "clip is scaled"
        rotation != 0f -> "clip is rotated"
        opacity != 1f -> "clip opacity < 1"
        anchorX != 0.5f || anchorY != 0.5f -> "clip anchor moved"
        blendMode != BlendMode.NORMAL -> "clip uses blend mode"
        transition != null -> "clip has transition"
        masks.isNotEmpty() -> "clip has mask"
        fadeInMs > 0L -> "clip has audio fade-in"
        fadeOutMs > 0L -> "clip has audio fade-out"
        volume != 1f -> "clip volume ≠ 1×"
        audioEffects.isNotEmpty() -> "clip has audio effects"
        motionTrackingData != null -> "clip has motion tracking"
        captions.isNotEmpty() -> "clip has captions"
        isCompound -> "clip is a compound"
        else -> null
    }

    companion object { private const val TAG = "StreamCopyExport" }
}
