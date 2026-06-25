package com.novacut.editor.engine

import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-tap audio mastering chains. See ROADMAP.md Tier C.6.
 *
 * Pre-configured signal chains (EQ + compressor + limiter + optional denoise) tuned
 * for common distribution targets. Each preset returns a [MasteringChain] the
 * [AudioEffectsEngine] applies in order during export.
 *
 * These are not stubs -- the underlying DSP already exists in ClearCut. The value
 * this engine adds is the curated chain recipes plus the [buildEffectChain]
 * adapter that converts a [MasteringChain] into the [AudioEffect] list a track
 * can apply directly.
 */
@Singleton
class AudioMasteringEngine @Inject constructor() {

    data class EqBand(val frequencyHz: Float, val gainDb: Float, val q: Float = 0.7f)

    data class MasteringChain(
        val id: String,
        val displayName: String,
        val description: String,
        val highPassHz: Float? = null,
        val eqBands: List<EqBand> = emptyList(),
        val compressorThresholdDb: Float = -18f,
        val compressorRatio: Float = 3f,
        val compressorAttackMs: Float = 10f,
        val compressorReleaseMs: Float = 120f,
        val deEsserAmount: Float = 0f,
        val noiseReductionMode: Int = 0, // 0 = off, matches NoiseReductionEngine enum order
        val targetLufs: Float = -14f,
        val truePeakDb: Float = -1f
    )

    fun getPresets(): List<MasteringChain> = PRESETS

    fun getPreset(id: String): MasteringChain? = PRESETS.firstOrNull { it.id == id }

    /**
     * Convert a mastering preset into the ordered [AudioEffect] chain that the
     * track-level audio effect pipeline can apply directly. Order is:
     *   HighPass → ParametricEQ → De-esser → Compressor → Limiter
     *
     * Slots are skipped when the preset has no value for them (e.g. no
     * `deEsserAmount` and no `eqBands` produces a HighPass + Compressor + Limiter
     * chain). The DeepFilterNet (R6.6) noise-reduction slot is *not* added to
     * the per-track chain — noise reduction lives at the clip / file pre-process
     * stage via [NoiseReductionEngine] and is keyed off the preset's
     * `noiseReductionMode` separately.
     */
    fun buildEffectChain(preset: MasteringChain): List<AudioEffect> {
        val chain = mutableListOf<AudioEffect>()
        preset.highPassHz?.let { hp ->
            chain += AudioEffect(
                type = AudioEffectType.HIGH_PASS,
                params = mapOf("frequency" to hp, "resonance" to 0.7f)
            )
        }
        if (preset.eqBands.isNotEmpty()) {
            // PARAMETRIC_EQ exposes 5 bands. Map up to 5 preset bands into them
            // and zero-gain unused slots so the EQ contributes nothing there.
            val bands = preset.eqBands.take(5)
            val eqParams = mutableMapOf<String, Float>()
            for (i in 0 until 5) {
                val idx = i + 1
                val band = bands.getOrNull(i)
                eqParams["band${idx}_freq"] = band?.frequencyHz
                    ?: defaultEqBandFrequencyForSlot(i)
                eqParams["band${idx}_gain"] = band?.gainDb ?: 0f
                eqParams["band${idx}_q"] = band?.q ?: 1f
            }
            chain += AudioEffect(type = AudioEffectType.PARAMETRIC_EQ, params = eqParams)
        }
        if (preset.deEsserAmount > 0f) {
            // De-esser threshold scales with the amount slider:
            // amount=0 → -10 dB, amount=1 → -30 dB (more aggressive).
            val threshold = -10f - (preset.deEsserAmount.coerceIn(0f, 1f) * 20f)
            chain += AudioEffect(
                type = AudioEffectType.DE_ESSER,
                params = mapOf(
                    "frequency" to 6000f,
                    "threshold" to threshold,
                    "ratio" to 3f
                )
            )
        }
        chain += AudioEffect(
            type = AudioEffectType.COMPRESSOR,
            params = mapOf(
                "threshold" to preset.compressorThresholdDb,
                "ratio" to preset.compressorRatio,
                "attack" to preset.compressorAttackMs,
                "release" to preset.compressorReleaseMs,
                "knee" to 6f,
                "makeupGain" to 0f
            )
        )
        chain += AudioEffect(
            type = AudioEffectType.LIMITER,
            params = mapOf("ceiling" to preset.truePeakDb, "release" to 50f)
        )
        return chain.toList()
    }

    private fun defaultEqBandFrequencyForSlot(slotIndex: Int): Float = when (slotIndex) {
        0 -> 80f
        1 -> 250f
        2 -> 1000f
        3 -> 4000f
        else -> 12000f
    }

    companion object {
        val PRESETS = listOf(
            MasteringChain(
                id = "podcast_voice",
                displayName = "Podcast Voice",
                description = "Warm, close-mic talk. Rolls off rumble, tames sibilance, bus-compressed for consistent level.",
                highPassHz = 80f,
                eqBands = listOf(
                    EqBand(180f, -2f, 1.2f),   // mud cut
                    EqBand(3200f, 2f, 0.8f),   // presence boost
                    EqBand(9000f, 1.5f, 0.6f)  // air
                ),
                compressorThresholdDb = -20f,
                compressorRatio = 3.5f,
                compressorAttackMs = 15f,
                compressorReleaseMs = 140f,
                deEsserAmount = 0.35f,
                noiseReductionMode = 2, // moderate
                targetLufs = -16f
            ),
            MasteringChain(
                id = "music_master",
                displayName = "Music Master",
                description = "Balanced master for music-only tracks. Gentle bus compression, target streaming loudness.",
                highPassHz = 25f,
                eqBands = listOf(
                    EqBand(60f, 1f, 0.8f),
                    EqBand(8500f, 1f, 0.6f)
                ),
                compressorThresholdDb = -12f,
                compressorRatio = 2f,
                compressorAttackMs = 30f,
                compressorReleaseMs = 200f,
                targetLufs = -14f
            ),
            MasteringChain(
                id = "dialogue_clean",
                displayName = "Dialogue Clean",
                description = "Film/vlog dialogue. Aggressive noise reduction, broadcast EBU R128 target.",
                highPassHz = 100f,
                eqBands = listOf(
                    EqBand(300f, -1.5f, 1.4f),
                    EqBand(4500f, 1.5f, 0.8f)
                ),
                compressorThresholdDb = -22f,
                compressorRatio = 4f,
                compressorAttackMs = 8f,
                compressorReleaseMs = 110f,
                deEsserAmount = 0.4f,
                noiseReductionMode = 3, // aggressive
                targetLufs = -23f
            ),
            MasteringChain(
                id = "asmr",
                displayName = "ASMR",
                description = "Close-mic whisper content. Preserve dynamic range, zero denoise, target quiet playback.",
                highPassHz = 40f,
                eqBands = listOf(
                    EqBand(5000f, -1f, 0.9f),  // tame mouth sounds
                    EqBand(12000f, 2f, 0.5f)   // air / ticks
                ),
                compressorThresholdDb = -28f,
                compressorRatio = 1.8f,
                compressorAttackMs = 25f,
                compressorReleaseMs = 250f,
                deEsserAmount = 0.2f,
                noiseReductionMode = 0, // off
                targetLufs = -24f
            ),
            MasteringChain(
                id = "social_loud",
                displayName = "Social Loud",
                description = "TikTok / Reels. Loud, punchy, compressed for phone speaker playback.",
                highPassHz = 60f,
                eqBands = listOf(
                    EqBand(80f, 2f, 0.8f),
                    EqBand(2500f, 1.5f, 0.9f),
                    EqBand(10000f, 2f, 0.5f)
                ),
                compressorThresholdDb = -16f,
                compressorRatio = 4f,
                compressorAttackMs = 5f,
                compressorReleaseMs = 90f,
                targetLufs = -9f
            )
        )
    }
}
