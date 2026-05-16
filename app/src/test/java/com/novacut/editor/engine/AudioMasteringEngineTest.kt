package com.novacut.editor.engine

import com.novacut.editor.model.AudioEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMasteringEngineTest {

    private val engine = AudioMasteringEngine()

    @Test
    fun presets_idsAreUnique() {
        val ids = engine.getPresets().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun presets_nonEmptyDisplayNames() {
        engine.getPresets().forEach {
            assertTrue("Empty displayName for ${it.id}", it.displayName.isNotBlank())
            assertTrue("Empty description for ${it.id}", it.description.isNotBlank())
        }
    }

    @Test
    fun presets_eqGainsWithinRange() {
        // Mastering chains should not have wild gain values -- cap at ±12 dB
        // which is the max a reasonable post-tuning chain would apply.
        engine.getPresets().forEach { chain ->
            chain.eqBands.forEach { band ->
                assertTrue(
                    "EQ gain out of range in ${chain.id}: ${band.gainDb} dB",
                    band.gainDb in -12f..12f
                )
                assertTrue(
                    "EQ frequency out of audible range in ${chain.id}: ${band.frequencyHz} Hz",
                    band.frequencyHz in 20f..20_000f
                )
                assertTrue(
                    "EQ Q out of reasonable range in ${chain.id}: ${band.q}",
                    band.q in 0.1f..10f
                )
            }
        }
    }

    @Test
    fun presets_lufsTargetsWithinRange() {
        engine.getPresets().forEach { chain ->
            assertTrue(
                "LUFS target unrealistic in ${chain.id}: ${chain.targetLufs}",
                chain.targetLufs in -30f..-6f
            )
            assertTrue(
                "True peak above 0 dBFS in ${chain.id}: ${chain.truePeakDb}",
                chain.truePeakDb <= 0f
            )
        }
    }

    @Test
    fun presets_compressorRatiosReasonable() {
        engine.getPresets().forEach { chain ->
            assertTrue(
                "Compressor ratio unrealistic in ${chain.id}: ${chain.compressorRatio}",
                chain.compressorRatio in 1f..20f
            )
        }
    }

    @Test
    fun getPreset_knownId_returnsChain() {
        assertNotNull(engine.getPreset("podcast_voice"))
    }

    @Test
    fun getPreset_unknownId_returnsNull() {
        assertNull(engine.getPreset("nonexistent-preset"))
    }

    // --- C.6: buildEffectChain converts presets into AudioEffect lists ---

    @Test
    fun buildEffectChain_podcastVoice_emitsAllStages() {
        val preset = engine.getPreset("podcast_voice")!!
        val chain = engine.buildEffectChain(preset)
        // Order: HighPass → EQ → DeEsser → Compressor → Limiter
        val types = chain.map { it.type }
        assertEquals(
            listOf(
                AudioEffectType.HIGH_PASS,
                AudioEffectType.PARAMETRIC_EQ,
                AudioEffectType.DE_ESSER,
                AudioEffectType.COMPRESSOR,
                AudioEffectType.LIMITER
            ),
            types
        )
    }

    @Test
    fun buildEffectChain_skipsHighPassWhenAbsent() {
        // Construct an in-memory preset with no high-pass to exercise the skip.
        val preset = AudioMasteringEngine.MasteringChain(
            id = "test_no_hp",
            displayName = "No HP",
            description = "Test",
            highPassHz = null,
            eqBands = emptyList(),
            deEsserAmount = 0f
        )
        val chain = engine.buildEffectChain(preset)
        val types = chain.map { it.type }
        // Compressor + Limiter only when EQ and HP are absent and deEsser is 0.
        assertEquals(
            listOf(AudioEffectType.COMPRESSOR, AudioEffectType.LIMITER),
            types
        )
        assertFalse(types.contains(AudioEffectType.HIGH_PASS))
        assertFalse(types.contains(AudioEffectType.PARAMETRIC_EQ))
        assertFalse(types.contains(AudioEffectType.DE_ESSER))
    }

    @Test
    fun buildEffectChain_eqMaps5SlotsWithZeroFillForUnusedBands() {
        // Use a preset known to have only 3 EQ bands (Podcast Voice).
        val preset = engine.getPreset("podcast_voice")!!
        assertEquals(3, preset.eqBands.size)
        val chain = engine.buildEffectChain(preset)
        val eq = chain.first { it.type == AudioEffectType.PARAMETRIC_EQ }
        // All 5 slots present in the parametric EQ params.
        for (i in 1..5) {
            assertNotNull(eq.params["band${i}_freq"])
            assertNotNull(eq.params["band${i}_gain"])
            assertNotNull(eq.params["band${i}_q"])
        }
        // Unused slots (4 and 5) gain-zero.
        assertEquals(0f, eq.params["band4_gain"])
        assertEquals(0f, eq.params["band5_gain"])
    }

    @Test
    fun buildEffectChain_deEsserAmountScalesThreshold() {
        // amount = 0 → threshold = -10 dB. amount = 1 → threshold = -30 dB.
        val light = AudioMasteringEngine.MasteringChain(
            id = "t1", displayName = "x", description = "x",
            highPassHz = null, eqBands = emptyList(),
            deEsserAmount = 0.5f
        )
        val deEsser = engine.buildEffectChain(light)
            .first { it.type == AudioEffectType.DE_ESSER }
        // 0.5 → -10 + (-0.5 * 20) = -20 dB
        assertEquals(-20f, deEsser.params["threshold"])
    }

    @Test
    fun buildEffectChain_limiterCeilingTracksTruePeak() {
        val preset = engine.getPreset("social_loud")!!
        val limiter = engine.buildEffectChain(preset)
            .first { it.type == AudioEffectType.LIMITER }
        assertEquals(preset.truePeakDb, limiter.params["ceiling"])
    }

    @Test
    fun buildEffectChain_compressorParamsRoundTripFromPreset() {
        val preset = engine.getPreset("dialogue_clean")!!
        val comp = engine.buildEffectChain(preset)
            .first { it.type == AudioEffectType.COMPRESSOR }
        assertEquals(preset.compressorThresholdDb, comp.params["threshold"])
        assertEquals(preset.compressorRatio, comp.params["ratio"])
        assertEquals(preset.compressorAttackMs, comp.params["attack"])
        assertEquals(preset.compressorReleaseMs, comp.params["release"])
    }
}
