package com.novacut.editor.ui.editor

import android.content.Context
import android.util.Log
import com.novacut.editor.engine.AiThumbnailEngine
import com.novacut.editor.engine.AudioDescriptionEngine
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoChapterEngine
import com.novacut.editor.engine.ColorBlindPreviewEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.ContentIdEngine
import com.novacut.editor.engine.DirectPublishEngine
import com.novacut.editor.engine.FlashSafetyEngine
import com.novacut.editor.engine.KaraokeCaptionEngine
import com.novacut.editor.engine.StreamCopyExportEngine
import com.novacut.editor.engine.StylusMidiEngine
import com.novacut.editor.engine.TalkingHeadFramingEngine
import com.novacut.editor.engine.TextBasedEditEngine
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.Transcript
import com.novacut.editor.model.WordTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Wires the 15-feature v3.69 wave into the ViewModel without growing the main
 * EditorViewModel body. Follows the existing delegate pattern: takes the
 * shared state flow and a handful of ViewModel callbacks, owns any coroutine
 * jobs it spawns, and emits state updates via the same CAS-loop extension
 * (`MutableStateFlow.update`) the rest of the app uses.
 */
class V369Delegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val saveProject: () -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val recalculateDuration: (EditorState) -> EditorState,
    // Engines
    val textBased: TextBasedEditEngine,
    val autoChapter: AutoChapterEngine,
    val talkingHead: TalkingHeadFramingEngine,
    val karaoke: KaraokeCaptionEngine,
    val streamCopy: StreamCopyExportEngine,
    val contentId: ContentIdEngine,
    val publish: DirectPublishEngine,
    val flashSafety: FlashSafetyEngine,
    val colorBlind: ColorBlindPreviewEngine,
    val thumbnail: AiThumbnailEngine,
    val audioDescription: AudioDescriptionEngine,
    val stylusMidi: StylusMidiEngine,
    private val audioEngine: AudioEngine,
    private val videoEngine: VideoEngine
) {

    private val jobs = mutableListOf<Job>()

    fun cancelAll() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    // ---- Text-based editing ---------------------------------------------

    fun setTranscript(transcript: Transcript?) {
        stateFlow.update {
            it.copy(v369 = it.v369.copy(transcript = transcript, selectedWordIndices = emptySet()))
        }
        // Persist through the normal auto-save path so text-based editing
        // survives app restart without the user having to re-transcribe.
        saveProject()
    }

    fun toggleWordSelection(index: Int) {
        stateFlow.update {
            val sel = it.v369.selectedWordIndices.toMutableSet()
            if (index in sel) sel.remove(index) else sel.add(index)
            it.copy(v369 = it.v369.copy(selectedWordIndices = sel))
        }
    }

    fun selectFillerWords() {
        val t = stateFlow.value.v369.transcript ?: return
        val idx = textBased.fillerWordIndices(t.words)
        stateFlow.update { it.copy(v369 = it.v369.copy(selectedWordIndices = idx)) }
        showToast("${idx.size} filler word${if (idx.size == 1) "" else "s"} selected")
    }

    /**
     * Apply text-based deletions. Splits the target clip around the cut ranges,
     * preserves the clip's inbound transition on the first surviving segment,
     * and *ripples* every downstream clip on the same track so there is no
     * silent gap where the deleted words used to live. Other tracks are
     * unaffected — matches the behaviour users expect from Descript-style text
     * editing, which always works "inside the video", not across the project.
     */
    fun applyDeletions(clipId: String) {
        val t = stateFlow.value.v369.transcript ?: return
        val selected = stateFlow.value.v369.selectedWordIndices
        if (selected.isEmpty()) { showToast("No words selected"); return }
        val state = stateFlow.value
        val target = state.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        jobs += scope.launch {
            val ranges = textBased.computeCutRanges(target, t.words, selected)
            if (ranges.isEmpty()) { showToast("Nothing to cut"); return@launch }
            saveUndoState("Text-based edit")
            val originalDuration = target.durationMs
            val segments = splitAroundRanges(target, ranges)
            val newDuration = segments.sumOf { it.durationMs }
            val rippleMs = originalDuration - newDuration
            stateFlow.update { s ->
                val tracks = s.tracks.map { track ->
                    if (track.clips.none { it.id == clipId }) track
                    else rippleTrack(track, target, segments, rippleMs)
                }
                recalculateDuration(
                    s.copy(tracks = tracks, v369 = s.v369.copy(selectedWordIndices = emptySet()))
                )
            }
            rebuildPlayerTimeline()
            saveProject()
            showToast("Removed ${ranges.size} segment${if (ranges.size == 1) "" else "s"} (-${rippleMs / 1000f}s)")
        }
    }

    /**
     * Produce the surviving clip segments for a single source clip given a
     * sorted list of cut ranges (in source-time). Segments inherit every
     * editable field from the source via `copy`; only the id, trim window,
     * transition (first segment keeps it, the rest go null), keyframes
     * (remapped per segment), and timelineStart are specialised.
     *
     * Keyframe remap rule: every keyframe on the source clip has a clip-local
     * `timeOffsetMs`. We translate that to the source-time via the original
     * clip's `timelineOffsetToSourceMs`, and — if the source time falls inside
     * a segment's trim window — translate it back to the segment's local time
     * via the segment's `sourceTimeToTimelineOffsetMs`. Keyframes whose source
     * time lands outside every kept segment are dropped, which is the right
     * answer: they reference source frames that no longer exist in the edit.
     */
    private fun splitAroundRanges(
        clip: Clip,
        ranges: List<TextBasedEditEngine.CutRange>
    ): List<Clip> {
        val out = mutableListOf<Clip>()
        var cursor = clip.trimStartMs
        var first = true
        for (r in ranges) {
            if (r.startSrcMs > cursor) {
                out += buildSegment(clip, cursor, r.startSrcMs, first)
                first = false
            }
            cursor = r.endSrcMs.coerceAtLeast(cursor)
        }
        if (cursor < clip.trimEndMs) {
            out += buildSegment(clip, cursor, clip.trimEndMs, first)
        }
        // Reflow so segments are contiguous starting at the original clip's
        // timeline start. Use Clip.durationMs so speed-curves are honoured.
        var t = clip.timelineStartMs
        return out.map { seg ->
            val shifted = seg.copy(timelineStartMs = t)
            t += shifted.durationMs
            shifted
        }
    }

    private fun buildSegment(
        original: Clip,
        newTrimStart: Long,
        newTrimEnd: Long,
        isFirst: Boolean
    ): Clip {
        val draft = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            trimStartMs = newTrimStart,
            trimEndMs = newTrimEnd,
            transition = if (isFirst) original.transition else null,
            // Speed-curve restricted to the sub-range so preview + export
            // time-stretching stay consistent with the segment's trim window.
            speedCurve = original.speedCurve?.let { curve ->
                val origRange = (original.trimEndMs - original.trimStartMs).toFloat().coerceAtLeast(1f)
                val s = ((newTrimStart - original.trimStartMs).toFloat() / origRange).coerceIn(0f, 1f)
                val e = ((newTrimEnd - original.trimStartMs).toFloat() / origRange).coerceIn(0f, 1f)
                curve.restrictTo(s, e, origRange.toLong())
            }
        )
        // Remap keyframes to the new segment's clip-local time.
        val remapped = original.keyframes.mapNotNull { kf ->
            val sourceT = original.timelineOffsetToSourceMs(kf.timeOffsetMs)
            if (sourceT < newTrimStart || sourceT > newTrimEnd) return@mapNotNull null
            val newOffset = draft.sourceTimeToTimelineOffsetMs(sourceT) ?: return@mapNotNull null
            kf.copy(timeOffsetMs = newOffset)
        }
        return draft.copy(keyframes = remapped)
    }

    /**
     * Rebuild a track's clip list, replacing the target clip with the produced
     * segments and shifting every clip that started AFTER the target back by
     * `rippleMs`. Clips on the same track that happened to start earlier than
     * the target (e.g. on a multi-track mix) are left untouched.
     */
    private fun rippleTrack(
        track: Track,
        target: Clip,
        segments: List<Clip>,
        rippleMs: Long
    ): Track {
        val originalEnd = target.timelineStartMs + target.durationMs
        val rebuilt = mutableListOf<Clip>()
        for (c in track.clips) {
            when {
                c.id == target.id -> rebuilt += segments
                c.timelineStartMs >= originalEnd && rippleMs != 0L -> {
                    val shifted = (c.timelineStartMs - rippleMs).coerceAtLeast(0L)
                    rebuilt += c.copy(timelineStartMs = shifted)
                }
                else -> rebuilt += c
            }
        }
        return track.copy(clips = rebuilt)
    }

    // ---- Auto-chapter ----------------------------------------------------

    fun generateChapters(words: List<WordTimestamp>) {
        stateFlow.update { it.copy(v369 = it.v369.copy(isGeneratingChapters = true)) }
        jobs += scope.launch {
            val cands = autoChapter.detect(words)
            stateFlow.update {
                it.copy(v369 = it.v369.copy(isGeneratingChapters = false, chapterCandidates = cands))
            }
            showToast(
                if (cands.isEmpty()) "No chapter boundaries detected (try a longer transcript)"
                else "${cands.size} chapter${if (cands.size == 1) "" else "s"} detected"
            )
        }
    }

    fun applyChaptersToProject() {
        val cands = stateFlow.value.v369.chapterCandidates
        if (cands.isEmpty()) return
        saveUndoState("Apply auto-chapters")
        stateFlow.update {
            val markers = cands.map { ChapterMarker(timeMs = it.timeMs, title = it.title) }
            it.copy(chapterMarkers = markers)
        }
        saveProject()
        showToast("${cands.size} chapters added")
    }

    fun youtubeChapterClipboard(): String =
        autoChapter.formatYouTubeClipboard(stateFlow.value.chapterMarkers)

    // ---- Talking-head framing -------------------------------------------

    fun trackTalkingHead(clipId: String) {
        val clip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        stateFlow.update { it.copy(v369 = it.v369.copy(isTrackingFaces = true)) }
        jobs += scope.launch {
            val centers = talkingHead.trackFaceCenter(clip.sourceUri, clip.durationMs)
            val kfs = talkingHead.toKeyframes(centers, clip.durationMs)
            stateFlow.update { state ->
                val tracks = state.tracks.map { track ->
                    track.copy(clips = track.clips.map { c ->
                        if (c.id == clipId) c.copy(keyframes = c.keyframes + kfs) else c
                    })
                }
                state.copy(tracks = tracks, v369 = state.v369.copy(isTrackingFaces = false))
            }
            saveProject()
            showToast(
                if (kfs.isEmpty()) "No face detected — framing unchanged"
                else "Face-framing applied — ${centers.size} samples"
            )
        }
    }

    // ---- Karaoke captions -----------------------------------------------

    fun setKaraokeStyle(style: KaraokeCaptionEngine.KaraokeStyle) {
        stateFlow.update { it.copy(v369 = it.v369.copy(karaokeStyle = style)) }
    }

    fun generateKaraokeCaptions() {
        val t = stateFlow.value.v369.transcript
        if (t == null || t.words.isEmpty()) {
            showToast("Transcribe audio first (AI Tools → Auto Captions)")
            return
        }
        saveUndoState("Karaoke captions")
        val existing = stateFlow.value.textOverlays
        val overlays: List<TextOverlay> = karaoke.generate(t.words, stateFlow.value.v369.karaokeStyle)
        if (overlays.isEmpty()) { showToast("No captions generated"); return }
        stateFlow.update { it.copy(textOverlays = existing + overlays) }
        saveProject()
        showToast("${overlays.size} caption cue${if (overlays.size == 1) "" else "s"} added")
    }

    // ---- Stream-copy eligibility ----------------------------------------

    fun checkStreamCopyEligibility() {
        val state = stateFlow.value
        val hasOverlays = state.textOverlays.isNotEmpty() || state.imageOverlays.isNotEmpty()
        val result = streamCopy.analyze(state.tracks, hasOverlays)
        stateFlow.update { it.copy(v369 = it.v369.copy(streamCopyEligibility = result)) }
        showToast(
            if (result.eligible) "Stream-copy eligible — 50× faster export"
            else "Re-encode required (${result.reason})"
        )
    }

    // ---- Flash safety ----------------------------------------------------

    fun analyzeFlashSafety(clipId: String) {
        val clip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        stateFlow.update { it.copy(v369 = it.v369.copy(isAnalyzingFlashes = true)) }
        jobs += scope.launch {
            val warnings = flashSafety.analyze(clip.sourceUri, clip.durationMs)
            stateFlow.update {
                it.copy(v369 = it.v369.copy(isAnalyzingFlashes = false, flashWarnings = warnings))
            }
            showToast(
                if (warnings.isEmpty()) "No flash risk detected"
                else "${warnings.size} flash warning${if (warnings.size == 1) "" else "s"}"
            )
        }
    }

    // ---- Color-blind preview --------------------------------------------

    fun setColorBlindMode(mode: ColorBlindPreviewEngine.Mode) {
        stateFlow.update { it.copy(v369 = it.v369.copy(colorBlindMode = mode)) }
        videoEngine.setColorBlindMode(mode)
    }

    // ---- AI thumbnail picker --------------------------------------------

    fun scoreThumbnails(clipId: String) {
        val clip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        stateFlow.update { it.copy(v369 = it.v369.copy(isScoringThumbnails = true)) }
        jobs += scope.launch {
            val cands = thumbnail.score(clip.sourceUri, clip.durationMs)
            stateFlow.update {
                it.copy(v369 = it.v369.copy(isScoringThumbnails = false, thumbnailCandidates = cands))
            }
            showToast(
                if (cands.isEmpty()) "No thumbnail candidates produced"
                else "${cands.size} top candidate${if (cands.size == 1) "" else "s"}"
            )
        }
    }

    suspend fun saveThumbnailAt(index: Int, outputPath: String): Boolean {
        val cands = stateFlow.value.v369.thumbnailCandidates
        val cand = cands.getOrNull(index) ?: return false
        val bmp = cand.bitmap ?: return false
        return thumbnail.saveThumbnail(bmp, java.io.File(outputPath))
    }

    // ---- Content-ID ------------------------------------------------------

    /**
     * Fingerprint the audio of the most recent export. We decode the exported
     * file's audio track to PCM via the existing AudioEngine and hand it to
     * ContentIdEngine. AcoustID lookup runs only when an API key is supplied
     * via `settingsRepo`/user input; without one the engine still returns the
     * local fingerprint hash so the UI can show something meaningful.
     */
    fun runContentIdOnLastExport(apiKey: String? = null) {
        val path = stateFlow.value.lastExportedFilePath
        if (path == null) { showToast("Export something first"); return }
        jobs += scope.launch {
            val pcm = try {
                audioEngine.decodeToPCM(android.net.Uri.fromFile(java.io.File(path)))
            } catch (e: Exception) {
                Log.w(TAG, "pcm decode failed", e); null
            }
            if (pcm == null || pcm.isEmpty()) {
                showToast("Could not decode exported audio")
                return@launch
            }
            val match = contentId.analyze(pcm, apiKey)
            stateFlow.update { it.copy(v369 = it.v369.copy(contentIdResult = match)) }
            showToast(
                if (match.matchedTitle != null) "Match: ${match.matchedTitle}"
                else "No copyright match detected"
            )
        }
    }

    // ---- Direct publish --------------------------------------------------

    fun publishLastExport(target: DirectPublishEngine.Target, title: String, description: String) {
        val path = stateFlow.value.lastExportedFilePath
        if (path == null) { showToast("Export something first"); return }
        jobs += scope.launch {
            val meta = DirectPublishEngine.PublishMeta(
                title = title, description = description, tags = emptyList()
            )
            val result = publish.publish(path, target, meta)
            val intent = result.intent
            if (intent == null) {
                showToast(result.message)
            } else {
                try {
                    appContext.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "publish intent failed", e)
                    showToast("Unable to open ${target.displayName}")
                }
            }
        }
    }

    companion object { private const val TAG = "V369Delegate" }
}
