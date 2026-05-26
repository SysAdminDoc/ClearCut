package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [AiUsageLedger.summarizeForChips] contract that the ExportSheet
 * "AI use" confidence row depends on.
 *
 * The chip row is the pre-export trust moment surfaced in the
 * RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #2 item — every disclosure
 * bucket gets a chip, ordered severity-descending so the most disclosure-
 * bearing entry is visually first. These tests prevent a future re-bucketing
 * from quietly degrading that order.
 */
class AiUsageLedgerChipsTest {

    @Test
    fun summarizeForChips_returnsEmptyWhenLedgerEmpty() {
        assertTrue(AiUsageLedger.summarizeForChips(emptyList()).isEmpty())
    }

    @Test
    fun summarizeForChips_bucketsByEffectKind() {
        val entries = listOf(
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, model = "AnimeGANv2", startMs = 0, endMs = 1_000),
            entry(clipId = "c2", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, model = "FastNST-Mosaic", startMs = 2_000, endMs = 3_000),
            entry(clipId = "c3", kind = AiUsageLedger.EffectKind.TTS_LOCAL, model = "system-tts", startMs = 0, endMs = 5_000),
        )

        val chips = AiUsageLedger.summarizeForChips(entries)

        assertEquals(2, chips.size)
        val byKind = chips.associateBy { it.effectKind }
        assertEquals(2, byKind[AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL]!!.entryCount)
        assertEquals(2, byKind[AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL]!!.clipCount)
        assertEquals(2_000L, byKind[AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL]!!.totalRangeMs)
        assertEquals(1, byKind[AiUsageLedger.EffectKind.TTS_LOCAL]!!.entryCount)
        assertEquals(1, byKind[AiUsageLedger.EffectKind.TTS_LOCAL]!!.clipCount)
        assertEquals(5_000L, byKind[AiUsageLedger.EffectKind.TTS_LOCAL]!!.totalRangeMs)
    }

    @Test
    fun summarizeForChips_ordersDisclosureRequiredFirst() {
        val entries = listOf(
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.TTS_LOCAL),
            entry(clipId = "c2", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL),
            entry(clipId = "c3", kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD),
        )

        val chips = AiUsageLedger.summarizeForChips(entries)

        // DISCLOSURE_REQUIRED → DISCLOSURE_RECOMMENDED → INTERNAL_ONLY
        assertEquals(AiUsageLedger.Severity.DISCLOSURE_REQUIRED, chips[0].severity)
        assertEquals(AiUsageLedger.Severity.DISCLOSURE_RECOMMENDED, chips[1].severity)
        assertEquals(AiUsageLedger.Severity.INTERNAL_ONLY, chips[2].severity)
    }

    @Test
    fun summarizeForChips_ordersWithinSeverityByEffectKindName() {
        // Both INTERNAL_ONLY — alphabetical order on effect-kind name.
        val entries = listOf(
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.TTS_LOCAL),
            entry(clipId = "c2", kind = AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL),
            entry(clipId = "c3", kind = AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL),
        )
        val chips = AiUsageLedger.summarizeForChips(entries)
        assertEquals(
            listOf(
                AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
                AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
                AiUsageLedger.EffectKind.TTS_LOCAL,
            ),
            chips.map { it.effectKind }
        )
    }

    @Test
    fun summarizeForChips_mergesOverlappingRangesBeforeBucketing() {
        // Two records covering the same clip and effect with overlapping
        // ranges should collapse to one chip-row entry, not two.
        val entries = listOf(
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, model = "AnimeGANv2", startMs = 0, endMs = 3_000),
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, model = "AnimeGANv2", startMs = 2_000, endMs = 5_000),
        )
        val chips = AiUsageLedger.summarizeForChips(entries)
        assertEquals(1, chips.size)
        val chip = chips.single()
        assertEquals(1, chip.entryCount)
        assertEquals(1, chip.clipCount)
        // Merged range is 0..5000.
        assertEquals(5_000L, chip.totalRangeMs)
    }

    @Test
    fun summarizeForChips_collectsDistinctModelNames() {
        val entries = listOf(
            entry(clipId = "c1", kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD, model = "Wan 2.2"),
            entry(clipId = "c2", kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD, model = "HunyuanVideo"),
            entry(clipId = "c3", kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD, model = "Wan 2.2"),
        )
        val chips = AiUsageLedger.summarizeForChips(entries)
        assertEquals(1, chips.size)
        assertEquals(
            listOf("HunyuanVideo", "Wan 2.2"),
            chips.single().modelNames
        )
    }

    @Test
    fun chip_describe_rendersHumanReadableLabel() {
        val short = AiUsageLedger.Chip(
            effectKind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
            severity = AiUsageLedger.Severity.DISCLOSURE_RECOMMENDED,
            entryCount = 1,
            clipCount = 1,
            totalRangeMs = 1_500L,
            modelNames = listOf("AnimeGANv2"),
        )
        val long = short.copy(clipCount = 4, totalRangeMs = 91_500L)

        assertEquals("1 clip · 1.5s", short.describe())
        assertEquals("4 clips · 1m 31s", long.describe())
    }

    @Test
    fun chip_effectKindLabel_isLowercaseAndReadable() {
        val chip = AiUsageLedger.Chip(
            effectKind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
            severity = AiUsageLedger.Severity.DISCLOSURE_REQUIRED,
            entryCount = 1,
            clipCount = 1,
            totalRangeMs = 1_000L,
            modelNames = listOf("Wan 2.2"),
        )
        assertEquals("generative video cloud", chip.effectKindLabel)
    }

    private fun entry(
        clipId: String,
        kind: AiUsageLedger.EffectKind,
        model: String = "test-model",
        startMs: Long = 0L,
        endMs: Long = 1_000L,
    ): AiUsageLedger.Entry = AiUsageLedger.Entry(
        clipId = clipId,
        effectKind = kind,
        modelName = model,
        rangeStartMs = startMs,
        rangeEndMs = endMs,
        recordedAtEpochMs = 1_700_000_000_000L,
    )
}
