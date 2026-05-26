package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * R8.9 — In-memory + autosave-persisted ledger of AI-assisted edits for a
 * project. Records every clip range that touched a generative cloud effect,
 * a substantial AI inpainting pass, AI lip-sync, AI auto-edit, etc.
 *
 * Drives:
 * - the export sheet's "Disclose AI use" toggle default (on whenever the
 *   ledger holds at least one disclosure-bearing entry)
 * - the C2PA manifest assertions written by `C2paExportEngine` (R8.2) once
 *   that engine is wired
 * - the AI-use sidecar JSON written alongside MP4 export
 * - the Privacy Dashboard (R5.5c) "AI ledger" row that lets users review
 *   and clear per-project AI usage
 *
 * Pure-Kotlin value types + math only — no Android dependencies, so this is
 * fully unit-testable on the JVM. Mutable state lives in
 * `EditorViewModel` / autosave; this file only owns the data model and the
 * pure helpers around it.
 *
 * Background and regulatory context for the default-severity table:
 * - EU AI Act Article 50 (effective 2026-08-02) requires machine-readable
 *   labels for AI-generated content.
 * - FTC AI policy statement (2026-03) covers deepfakes, synthetic
 *   endorsements, and deceptive AI imagery.
 * - The federal Protecting Consumers From Deceptive AI Act
 *   (introduced 2026-04-24) directs NIST + FTC to set audio/visual
 *   AI-content labeling standards.
 * - TikTok and YouTube already require labels for realistic AI-generated
 *   content.
 *
 * See [GenerativeVideoPolicy] for the provider-side consent gate; this
 * ledger records the *usage* of those (and other AI features) on the
 * project after the user has cleared the consent gate.
 */
object AiUsageLedger {

    /**
     * Source effect categories the ledger tracks. The boundaries are drawn
     * by *disclosure relevance*, not by engine package — translation /
     * background removal / TTS are local + low-stakes and stay at
     * [Severity.INTERNAL_ONLY] by default, while cloud generative video and
     * the AI Auto-Edit composer pipeline are treated as
     * [Severity.DISCLOSURE_REQUIRED].
     */
    enum class EffectKind {
        GENERATIVE_VIDEO_CLOUD,       // Wan, HunyuanVideo, VideoCrafter2
        LIP_SYNC_CLOUD,               // MuseTalk, LatentSync, Wav2Lip
        GENERATIVE_FILL_CLOUD,        // Cloud image-in/image-out fills
        INPAINTING_CLOUD,             // A.12 ProPainter cloud variant
        AUTO_EDIT_LOCAL,              // R6.13 AI Auto-Edit composer
        INPAINTING_LOCAL_LARGE,       // LaMa multi-second range
        STYLE_TRANSFER_LOCAL,         // A.11 AnimeGAN / Fast NST
        UPSCALING_LOCAL,              // A.5 Real-ESRGAN
        FRAME_INTERPOLATION_LOCAL,    // A.4 RIFE
        VOICE_CLONE_LOCAL,            // C.3 XTTS v2 (consent-bearing)
        CAPTION_TRANSLATION_LOCAL,    // MADLAD / Bergamot translation
        BACKGROUND_REMOVAL_LOCAL,     // RVM / MediaPipe selfie segmenter
        TTS_LOCAL                     // A.8 Piper / system TTS
    }

    enum class Severity {
        /** EU AI Act / FTC / platform deepfake-class label required. */
        DISCLOSURE_REQUIRED,
        /** Substantial AI edit but not deceptive-class. Recommend label. */
        DISCLOSURE_RECOMMENDED,
        /** Low-stakes assistive AI — translation, TTS, mask helpers. */
        INTERNAL_ONLY
    }

    /**
     * One AI usage record. Ranges are timeline coordinates in milliseconds.
     */
    data class Entry(
        val clipId: String,
        val effectKind: EffectKind,
        val modelName: String,
        val rangeStartMs: Long,
        val rangeEndMs: Long,
        val recordedAtEpochMs: Long
    ) {
        init {
            require(clipId.isNotBlank()) { "clipId must not be blank" }
            require(modelName.isNotBlank()) { "modelName must not be blank" }
            require(rangeStartMs >= 0L) { "rangeStartMs must be >= 0" }
            require(rangeEndMs >= rangeStartMs) { "rangeEndMs must be >= rangeStartMs" }
            require(recordedAtEpochMs >= 0L) { "recordedAtEpochMs must be >= 0" }
        }

        val rangeDurationMs: Long get() = rangeEndMs - rangeStartMs
    }

    /**
     * Default disclosure severity for each [EffectKind]. Conservative on
     * the cloud + composer side, lenient on local helpers.
     */
    fun defaultSeverity(kind: EffectKind): Severity = when (kind) {
        EffectKind.GENERATIVE_VIDEO_CLOUD,
        EffectKind.LIP_SYNC_CLOUD,
        EffectKind.GENERATIVE_FILL_CLOUD,
        EffectKind.INPAINTING_CLOUD,
        EffectKind.AUTO_EDIT_LOCAL -> Severity.DISCLOSURE_REQUIRED

        EffectKind.INPAINTING_LOCAL_LARGE,
        EffectKind.STYLE_TRANSFER_LOCAL,
        EffectKind.UPSCALING_LOCAL,
        EffectKind.FRAME_INTERPOLATION_LOCAL,
        EffectKind.VOICE_CLONE_LOCAL -> Severity.DISCLOSURE_RECOMMENDED

        EffectKind.CAPTION_TRANSLATION_LOCAL,
        EffectKind.BACKGROUND_REMOVAL_LOCAL,
        EffectKind.TTS_LOCAL -> Severity.INTERNAL_ONLY
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.DISCLOSURE_REQUIRED -> 2
        Severity.DISCLOSURE_RECOMMENDED -> 1
        Severity.INTERNAL_ONLY -> 0
    }

    /** Aggregate severity across all entries (maximum). */
    fun aggregateSeverity(entries: List<Entry>): Severity {
        if (entries.isEmpty()) return Severity.INTERNAL_ONLY
        return entries
            .map { defaultSeverity(it.effectKind) }
            .maxByOrNull(::severityRank)
            ?: Severity.INTERNAL_ONLY
    }

    /**
     * Whether the export sheet's "Disclose AI use" toggle should default to
     * on for the given ledger. R8.9 requires the default for any non-empty
     * ledger entry so users can consciously turn disclosure off instead of
     * NovaCut silently hiding low-stakes AI assistance.
     */
    fun discloseToggleDefaultOn(entries: List<Entry>): Boolean = entries.isNotEmpty()

    /**
     * Merge overlapping ranges per `(clipId, effectKind)` so disclosure
     * copy doesn't double-count a re-applied effect on the same range.
     * Returns a stable order: by clipId, then by effectKind name, then by
     * rangeStartMs.
     */
    fun mergeOverlaps(entries: List<Entry>): List<Entry> {
        if (entries.size <= 1) return entries
        return entries
            .groupBy { it.clipId to it.effectKind }
            .toSortedMap(compareBy({ it.first }, { it.second.name }))
            .flatMap { (_, group) ->
                val sorted = group.sortedBy { it.rangeStartMs }
                val merged = mutableListOf<Entry>()
                var current = sorted.first()
                for (next in sorted.drop(1)) {
                    if (next.rangeStartMs <= current.rangeEndMs) {
                        // Overlap or contact — merge.
                        current = current.copy(
                            rangeEndMs = maxOf(current.rangeEndMs, next.rangeEndMs),
                            modelName = if (next.recordedAtEpochMs > current.recordedAtEpochMs) next.modelName else current.modelName,
                            recordedAtEpochMs = maxOf(current.recordedAtEpochMs, next.recordedAtEpochMs)
                        )
                    } else {
                        merged.add(current)
                        current = next
                    }
                }
                merged.add(current)
                merged
            }
    }

    /**
     * Render a one-line human-readable summary for the disclosure copy.
     * Sorted by effect-kind name for deterministic output.
     */
    fun summaryLine(entries: List<Entry>): String {
        val merged = mergeOverlaps(entries)
        if (merged.isEmpty()) return "No AI assistance recorded for this project."
        val byKind = merged.groupBy { it.effectKind }
            .toSortedMap(compareBy { it.name })
        val parts = byKind.map { (kind, list) ->
            val modelSet = list.map { it.modelName }.toSortedSet()
            val kindLabel = kind.name.lowercase().replace('_', ' ')
            "${list.size} × $kindLabel (${modelSet.joinToString(", ")})"
        }
        return "AI assistance recorded: ${parts.joinToString("; ")}."
    }

    /**
     * Serialize the ledger to a stable JSON array for autosave persistence.
     */
    fun toJson(entries: List<Entry>): String = toJsonArray(entries).toString()

    fun toJsonArray(entries: List<Entry>): JSONArray {
        return JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("clipId", entry.clipId)
                    put("effectKind", entry.effectKind.name)
                    put("modelName", entry.modelName)
                    put("rangeStartMs", entry.rangeStartMs)
                    put("rangeEndMs", entry.rangeEndMs)
                    put("recordedAtEpochMs", entry.recordedAtEpochMs)
                })
            }
        }
    }

    /**
     * Pre-export chip-row data for the ExportSheet "AI use" confidence row.
     *
     * Buckets entries by [EffectKind], computes count + total range duration +
     * default severity per bucket, and returns a stable-ordered list:
     * 1. Severity descending (`DISCLOSURE_REQUIRED` first so the visual stack
     *    leads with the strongest disclosure signal).
     * 2. Then effect-kind name ascending so the order is reproducible across
     *    builds and tests.
     *
     * Pure data — no Compose, no string resources, no Android. The UI layer
     * maps [Chip.effectKind] to a localized label and [Chip.severity] to a
     * colour token. The data here is enough to drive the chip row, the
     * expanded per-clip detail list, and an at-a-glance count badge.
     *
     * `mergeOverlaps` is applied first so a re-applied effect on the same
     * clip range counts once.
     */
    fun summarizeForChips(entries: List<Entry>): List<Chip> {
        val merged = mergeOverlaps(entries)
        if (merged.isEmpty()) return emptyList()
        val buckets = merged.groupBy { it.effectKind }
        return buckets.map { (kind, list) ->
            val totalDurationMs = list.sumOf { it.rangeDurationMs }
            val clipCount = list.map { it.clipId }.distinct().size
            val severity = defaultSeverity(kind)
            Chip(
                effectKind = kind,
                severity = severity,
                entryCount = list.size,
                clipCount = clipCount,
                totalRangeMs = totalDurationMs,
                modelNames = list.map { it.modelName }.toSortedSet().toList(),
            )
        }.sortedWith(
            compareByDescending<Chip> { severityRank(it.severity) }
                .thenBy { it.effectKind.name }
        )
    }

    /**
     * Compact chip-row entry. One per effect-kind bucket after merge.
     *
     * @property effectKind Source bucket.
     * @property severity Default disclosure severity for the bucket.
     * @property entryCount Merged entry count for this bucket.
     * @property clipCount Distinct clip IDs touched by this bucket.
     * @property totalRangeMs Sum of merged range durations across the bucket.
     * @property modelNames Distinct model names involved, sorted for stability.
     */
    data class Chip(
        val effectKind: EffectKind,
        val severity: Severity,
        val entryCount: Int,
        val clipCount: Int,
        val totalRangeMs: Long,
        val modelNames: List<String>,
    ) {
        /**
         * Effect-kind label suitable for an English chip body.
         * Lowercased + underscores stripped — caller wraps in any title-case
         * convention they want.
         */
        val effectKindLabel: String
            get() = effectKind.name.lowercase().replace('_', ' ')

        /** Human-readable bucket total (e.g. "4 clips · 12.3s") for chip detail rows. */
        fun describe(): String {
            val secs = totalRangeMs / 1000.0
            val secsLabel = if (secs >= 60.0) {
                val m = (secs / 60).toInt()
                val s = (secs % 60).toInt()
                "${m}m ${s}s"
            } else {
                "%.1fs".format(secs)
            }
            val clipsLabel = if (clipCount == 1) "1 clip" else "$clipCount clips"
            return "$clipsLabel · $secsLabel"
        }
    }

    fun toDisclosureDeclaration(
        entries: List<Entry>,
        projectName: String,
        exportedFileName: String,
        generatedAtEpochMs: Long
    ): JSONObject {
        val merged = mergeOverlaps(entries)
        return JSONObject().apply {
            put("schema", "com.novacut.ai-use.v1")
            put("projectName", projectName.trim().take(MAX_PROJECT_NAME_CHARS))
            put("exportedFileName", exportedFileName.trim().take(MAX_FILE_NAME_CHARS))
            put("generatedAtEpochMs", generatedAtEpochMs)
            put("aggregateSeverity", aggregateSeverity(merged).name)
            put("disclosureRecommended", discloseToggleDefaultOn(merged))
            put("summary", summaryLine(merged))
            put("entries", toJsonArray(merged))
        }
    }

    fun fromJson(raw: String, maxEntries: Int = DEFAULT_MAX_ENTRIES): List<Entry> {
        return try {
            fromJsonArray(JSONArray(raw), maxEntries)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fromJsonArray(array: JSONArray?, maxEntries: Int = DEFAULT_MAX_ENTRIES): List<Entry> {
        if (array == null || maxEntries <= 0) return emptyList()
        val limit = minOf(array.length(), maxEntries)
        return (0 until limit).mapNotNull { index ->
            try {
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val clipId = obj.optString("clipId", "").trim().take(MAX_ID_CHARS)
                val modelName = obj.optString("modelName", "").trim().take(MAX_MODEL_NAME_CHARS)
                if (clipId.isBlank() || modelName.isBlank()) return@mapNotNull null
                val start = obj.optLong("rangeStartMs", -1L)
                val end = obj.optLong("rangeEndMs", -1L)
                val recordedAt = obj.optLong("recordedAtEpochMs", -1L)
                if (start < 0L || end < start || recordedAt < 0L) return@mapNotNull null
                Entry(
                    clipId = clipId,
                    effectKind = enumValueOf<EffectKind>(obj.optString("effectKind")),
                    modelName = modelName,
                    rangeStartMs = start,
                    rangeEndMs = end,
                    recordedAtEpochMs = recordedAt
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private const val DEFAULT_MAX_ENTRIES = 2_000
    private const val MAX_ID_CHARS = 128
    private const val MAX_MODEL_NAME_CHARS = 160
    private const val MAX_PROJECT_NAME_CHARS = 160
    private const val MAX_FILE_NAME_CHARS = 220
}
