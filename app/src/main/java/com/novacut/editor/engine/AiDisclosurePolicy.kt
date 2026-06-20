package com.novacut.editor.engine

/**
 * EU AI Act Article 50 disclosure classification for NovaCut's AI tools.
 *
 * Article 50 (effective 2026-08-02) requires:
 * (a) users must be clearly informed they are interacting with an AI system
 *     BEFORE interaction begins,
 * (b) synthetic or AI-modified audio/video/image content must be labeled in
 *     a machine-readable format detectable by downstream platforms.
 *
 * Not all AI features trigger Article 50. The regulation explicitly exempts
 * "minor" assistive functions such as colour correction, noise reduction,
 * cropping, and standard editing aids. Only features that substantively alter
 * content (background removal, style transfer, object removal, generative
 * fill) or produce synthetic content (text-to-video, voice cloning) are
 * in scope.
 *
 * Pure-Kotlin value types — no Android dependencies, fully JVM-testable.
 */
object AiDisclosurePolicy {

    enum class Scope {
        /** Substantive AI alteration or synthetic content — Article 50 applies. */
        IN_SCOPE,
        /** Assistive or minor adjustment — Article 50 does not apply. */
        EXEMPT
    }

    /**
     * Classify an AI tool ID as Article 50 in-scope or exempt.
     *
     * In-scope tools:
     * - Background removal / replacement (substantive scene alteration)
     * - Object removal / inpainting (substantive content removal)
     * - Style transfer (semantic visual transformation)
     * - Video upscaling with AI super-resolution (substantive enhancement)
     * - Frame interpolation (AI-generated intermediate frames)
     * - AI auto-edit / highlight reel (AI-selected content composition)
     * - Voice cloning (synthetic voice)
     * - Generative video / fill (synthetic content creation)
     * - Lip sync (synthetic facial manipulation)
     * - Smart reframe with AI face tracking (AI-driven crop decisions)
     *
     * Exempt tools:
     * - Auto captions / speech-to-text (assistive transcription)
     * - Scene detection (content analysis, no alteration)
     * - Auto colour correction (minor colour adjustment)
     * - Audio denoise (noise reduction — explicitly exempt)
     * - Motion tracking (analysis, no alteration)
     * - Smart crop / saliency analysis (analysis-driven crop)
     * - TTS / text-to-speech (assistive narration, not deceptive)
     * - Caption translation (text transformation, not media alteration)
     * - Beat sync (timing analysis, no content alteration)
     * - Cut assistant / filler removal (editing assistance)
     */
    fun classify(toolId: String): Scope = when (toolId) {
        "remove_bg",
        "ai_background",
        "bg_replace",
        "object_remove",
        "inpaint",
        "ai_style_transfer",
        "style_transfer",
        "video_upscale",
        "frame_interp",
        "auto_edit",
        "voice_clone",
        "generative_video",
        "generative_fill",
        "lip_sync",
        "smart_reframe" -> Scope.IN_SCOPE

        "auto_captions",
        "scene_detect",
        "auto_color",
        "denoise",
        "track_motion",
        "smart_crop",
        "stabilize",
        "ai_stabilize",
        "tts",
        "caption_translate",
        "beat_sync",
        "cut_assistant",
        "filler_removal",
        "noise_reduction" -> Scope.EXEMPT

        else -> Scope.EXEMPT
    }

    fun isInScope(toolId: String): Boolean = classify(toolId) == Scope.IN_SCOPE

    /**
     * Classify an [AiUsageLedger.EffectKind] for export-time disclosure.
     */
    fun classify(kind: AiUsageLedger.EffectKind): Scope = when (kind) {
        AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
        AiUsageLedger.EffectKind.LIP_SYNC_CLOUD,
        AiUsageLedger.EffectKind.GENERATIVE_FILL_CLOUD,
        AiUsageLedger.EffectKind.INPAINTING_CLOUD,
        AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE,
        AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
        AiUsageLedger.EffectKind.UPSCALING_LOCAL,
        AiUsageLedger.EffectKind.FRAME_INTERPOLATION_LOCAL,
        AiUsageLedger.EffectKind.VOICE_CLONE_LOCAL,
        AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL -> Scope.IN_SCOPE

        AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL -> Scope.IN_SCOPE

        AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
        AiUsageLedger.EffectKind.TTS_LOCAL -> Scope.EXEMPT
    }

    /**
     * Whether any entries in the ledger contain Article 50 in-scope effects,
     * meaning the export must carry machine-readable AI disclosure metadata.
     */
    fun requiresMachineReadableLabel(entries: List<AiUsageLedger.Entry>): Boolean =
        entries.any { classify(it.effectKind) == Scope.IN_SCOPE }

    /**
     * IPTC digitSourceType value for AI-modified content.
     * See IPTC Photo Metadata Standard 2025.1.
     */
    const val IPTC_DIGIT_SOURCE_TYPE_COMPOSITE_WITH_AI =
        "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia"

    const val IPTC_DIGIT_SOURCE_TYPE_TRAINED_ALGORITHMIC =
        "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"
}
