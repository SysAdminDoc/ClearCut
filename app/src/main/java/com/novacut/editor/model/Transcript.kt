package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * Word-level transcript primitives shared across the ASR, text-based editing,
 * auto-chapter, and karaoke-caption pipelines. `WhisperEngine` and
 * `SherpaAsrEngine` both produce these — callers should not depend on the
 * nested types those engines carry for backwards-compat.
 */
@Immutable
data class WordTimestamp(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1f
)

@Immutable
data class Transcript(
    val id: String = UUID.randomUUID().toString(),
    val clipId: String,
    val words: List<WordTimestamp> = emptyList(),
    val language: String = "en"
) {
    val fullText: String get() = words.joinToString(" ") { it.text }
    val durationMs: Long get() = words.lastOrNull()?.endMs ?: 0L
}
