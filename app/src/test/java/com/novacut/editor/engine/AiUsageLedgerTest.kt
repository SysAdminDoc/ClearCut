package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUsageLedgerTest {

    @Test
    fun defaultSeverity_isConservativeForDisclosureBearingEffects() {
        assertEquals(
            AiUsageLedger.Severity.DISCLOSURE_REQUIRED,
            AiUsageLedger.defaultSeverity(AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD)
        )
        assertEquals(
            AiUsageLedger.Severity.DISCLOSURE_RECOMMENDED,
            AiUsageLedger.defaultSeverity(AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE)
        )
        assertEquals(
            AiUsageLedger.Severity.INTERNAL_ONLY,
            AiUsageLedger.defaultSeverity(AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL)
        )
    }

    @Test
    fun discloseToggleDefaultOn_defaultsOnForAnyLedgerEntry() {
        val internal = listOf(
            entry(
                kind = AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
                model = "Bergamot en-es"
            )
        )
        val recommended = internal + entry(
            kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
            model = "AnimeGANv2"
        )

        assertFalse(AiUsageLedger.discloseToggleDefaultOn(emptyList()))
        assertTrue(AiUsageLedger.discloseToggleDefaultOn(internal))
        assertTrue(AiUsageLedger.discloseToggleDefaultOn(recommended))
    }

    @Test
    fun mergeOverlaps_coalescesPerClipAndEffectOnly() {
        val entries = listOf(
            entry(clip = "b", kind = AiUsageLedger.EffectKind.INPAINTING_CLOUD, start = 10, end = 20, recorded = 100, model = "old"),
            entry(clip = "a", kind = AiUsageLedger.EffectKind.INPAINTING_CLOUD, start = 0, end = 10, recorded = 100, model = "lama"),
            entry(clip = "a", kind = AiUsageLedger.EffectKind.INPAINTING_CLOUD, start = 10, end = 18, recorded = 200, model = "lama-v2"),
            entry(clip = "a", kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, start = 8, end = 16, recorded = 150, model = "style"),
            entry(clip = "a", kind = AiUsageLedger.EffectKind.INPAINTING_CLOUD, start = 40, end = 50, recorded = 250, model = "later")
        )

        val merged = AiUsageLedger.mergeOverlaps(entries)

        assertEquals(4, merged.size)
        assertEquals("a", merged[0].clipId)
        assertEquals(AiUsageLedger.EffectKind.INPAINTING_CLOUD, merged[0].effectKind)
        assertEquals(0L, merged[0].rangeStartMs)
        assertEquals(18L, merged[0].rangeEndMs)
        assertEquals("lama-v2", merged[0].modelName)
        assertEquals(200L, merged[0].recordedAtEpochMs)
        assertEquals(AiUsageLedger.EffectKind.INPAINTING_CLOUD, merged[1].effectKind)
        assertEquals(40L, merged[1].rangeStartMs)
        assertEquals(AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, merged[2].effectKind)
        assertEquals("b", merged[3].clipId)
    }

    @Test
    fun jsonRoundTrip_preservesEscapedTextAndSkipsInvalidRows() {
        val rows = listOf(
            entry(clip = "clip\"1", model = "Model\nOne", kind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL),
            entry(clip = "clip2", model = "Model Two", kind = AiUsageLedger.EffectKind.TTS_LOCAL)
        )
        val json = AiUsageLedger.toJson(rows)
        val parsed = AiUsageLedger.fromJson(json)

        assertEquals(rows, parsed)

        val malformed = JSONArray().apply {
            put(AiUsageLedger.toJsonArray(rows).getJSONObject(0))
            put(JSONObject().put("clipId", "bad").put("effectKind", "UNKNOWN").put("modelName", "x"))
            put(JSONObject().put("clipId", "bad-range").put("effectKind", AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL.name).put("modelName", "x").put("rangeStartMs", 10).put("rangeEndMs", 5).put("recordedAtEpochMs", 1))
        }
        assertEquals(1, AiUsageLedger.fromJsonArray(malformed).size)
        assertEquals(emptyList<AiUsageLedger.Entry>(), AiUsageLedger.fromJson("not json"))
    }

    @Test
    fun disclosureDeclaration_isStableAndIncludesMergedEntries() {
        val declaration = AiUsageLedger.toDisclosureDeclaration(
            entries = listOf(
                entry(kind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL, start = 0, end = 500, model = "Auto Edit"),
                entry(kind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL, start = 400, end = 1_000, model = "Auto Edit v2")
            ),
            projectName = "Launch cut",
            exportedFileName = "launch.mp4",
            generatedAtEpochMs = 123L
        )

        assertEquals("com.novacut.ai-use.v2", declaration.getString("schema"))
        assertEquals("Launch cut", declaration.getString("projectName"))
        assertEquals("launch.mp4", declaration.getString("exportedFileName"))
        assertEquals(123L, declaration.getLong("generatedAtEpochMs"))
        assertEquals(AiUsageLedger.Severity.DISCLOSURE_REQUIRED.name, declaration.getString("aggregateSeverity"))
        assertTrue(declaration.getBoolean("disclosureRecommended"))
        assertTrue(declaration.getBoolean("article50InScope"))
        assertTrue(declaration.has("iptcDigitSourceType"))
        assertEquals(1, declaration.getJSONArray("entries").length())
        assertTrue(declaration.getString("summary").contains("auto edit local"))
    }

    @Test
    fun autoSaveState_persistsAiUsageLedger() {
        val ledger = listOf(
            entry(kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD, model = "Wan 2.2"),
            entry(clip = "clip2", kind = AiUsageLedger.EffectKind.TTS_LOCAL, model = "Piper")
        )
        val state = AutoSaveState(
            projectId = "project",
            aiUsageLedger = ledger
        )

        val serialized = state.serialize()
        val root = JSONObject(serialized)
        assertEquals(2, root.getJSONArray("aiUsageLedger").length())

        val restored = AutoSaveState.deserialize(serialized)
        assertEquals(ledger, restored.aiUsageLedger)
        assertTrue(AiUsageLedger.discloseToggleDefaultOn(restored.aiUsageLedger))
    }

    private fun entry(
        clip: String = "clip1",
        kind: AiUsageLedger.EffectKind = AiUsageLedger.EffectKind.INPAINTING_CLOUD,
        model: String = "model",
        start: Long = 0L,
        end: Long = 1_000L,
        recorded: Long = 42L
    ): AiUsageLedger.Entry {
        return AiUsageLedger.Entry(
            clipId = clip,
            effectKind = kind,
            modelName = model,
            rangeStartMs = start,
            rangeEndMs = end,
            recordedAtEpochMs = recorded
        )
    }
}
