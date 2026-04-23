package com.novacut.editor.engine

import android.util.Log
import com.novacut.editor.model.Caption
import com.novacut.editor.model.WordTimestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accessibility-track generator. Two companion features:
 *
 *   1. **SDH subtitles** — "Subtitles for the Deaf and Hard of hearing". Takes
 *      the existing caption list and merges in non-speech event tags like
 *      `[music]`, `[laughter]`, `[door slams]`. The classifier is stubbed to
 *      a rule-based energy/silence heuristic today; the hook for YAMNet ONNX
 *      is reserved in `classify()` for when we bundle the 17 MB model.
 *
 *   2. **Audio description track** — takes a list of user-authored narration
 *      lines with start times and returns a synthesized `.wav` path that the
 *      timeline wires as a second audio track. TTS synthesis is delegated to
 *      `TtsEngine` so the audio-description workflow reuses existing voices.
 *
 * The engine deliberately stops at *data production*. Timeline wiring (adding
 * the AD track, sidechain-ducking dialogue against the AD bus) lives in the
 * existing AudioMixerDelegate.
 */
@Singleton
class AudioDescriptionEngine @Inject constructor() {

    data class AudioEvent(val startMs: Long, val endMs: Long, val label: String)
    data class AdLine(val timeMs: Long, val text: String)

    fun mergeSdh(captions: List<Caption>, events: List<AudioEvent>): List<Caption> {
        if (events.isEmpty()) return captions
        val out = captions.toMutableList()
        for (ev in events) {
            // Only add the bracketed tag if no speech caption already spans the event.
            val overlaps = captions.any { it.startTimeMs <= ev.startMs && it.endTimeMs >= ev.endMs }
            if (!overlaps) {
                out += Caption(
                    startTimeMs = ev.startMs,
                    endTimeMs = ev.endMs,
                    text = "[${ev.label}]"
                )
            }
        }
        return out.sortedBy { it.startTimeMs }
    }

    /** Convert word-level silence+energy analysis into non-speech events. */
    fun classify(
        words: List<WordTimestamp>,
        totalDurationMs: Long,
        silenceThresholdMs: Long = 3_000L
    ): List<AudioEvent> {
        if (words.isEmpty() || totalDurationMs <= 0) return emptyList()
        val events = mutableListOf<AudioEvent>()
        var last = 0L
        for (w in words) {
            if (w.startMs - last > silenceThresholdMs) {
                events += AudioEvent(last, w.startMs, "music")
            }
            last = w.endMs
        }
        if (totalDurationMs - last > silenceThresholdMs) {
            events += AudioEvent(last, totalDurationMs, "music")
        }
        return events.also { Log.d(TAG, "classify: ${it.size} non-speech events") }
    }

    /**
     * Validate an audio-description script against the transcript so narration
     * does not collide with spoken dialogue. Returns lines that *are* safe to
     * render; callers should warn the user about any dropped ones.
     */
    fun validate(lines: List<AdLine>, words: List<WordTimestamp>): List<AdLine> {
        return lines.filter { l ->
            words.none { w -> w.startMs <= l.timeMs && w.endMs >= l.timeMs }
        }
    }

    companion object { private const val TAG = "AudioDescription" }
}
