package com.novacut.editor.engine

/**
 * R6.10c migration guard for Media3 frame extraction.
 *
 * Media3 1.10 split frame extraction into `media3-inspector-frame` with the
 * import path [MEDIA3_FRAME_EXTRACTOR_IMPORT]. Cached SDR thumbnails can stay
 * on the platform retriever, while HDR/effect-aware frames should migrate.
 */
object FrameExtractionPolicy {
    const val MEDIA3_INSPECTOR_FRAME_COORDINATE = "androidx.media3:media3-inspector-frame"
    const val MEDIA3_FRAME_EXTRACTOR_IMPORT = "androidx.media3.inspector.frame.FrameExtractor"
    const val OLD_INSPECTOR_FRAME_EXTRACTOR_IMPORT = "androidx.media3.inspector.FrameExtractor"
    const val OLD_TRANSFORMER_FRAME_EXTRACTOR_IMPORT = "androidx.media3.transformer.ExperimentalFrameExtractor"
    const val PLATFORM_RETRIEVER_IMPORT = "android.media.MediaMetadataRetriever"

    enum class UseCase {
        TIMELINE_THUMBNAIL_STRIP,
        CONTACT_SHEET_THUMBNAIL,
        FREEZE_FRAME_EXPORT,
        AI_ANALYSIS_SAMPLE,
        HDR_REVIEW_FRAME,
        EFFECT_AWARE_FRAME,
    }

    enum class Backend {
        PLATFORM_MEDIA_METADATA_RETRIEVER,
        MEDIA3_FRAME_EXTRACTOR,
    }

    enum class Reason {
        SMALL_CACHED_SDR_THUMBNAIL,
        REPRESENTATIVE_AI_SAMPLE,
        USER_VISIBLE_STILL_EXPORT,
        HDR_OR_EFFECT_FIDELITY,
        CUSTOM_DECODER_SELECTION,
    }

    data class Request(
        val useCase: UseCase,
        val requiresHdrFidelity: Boolean = false,
        val requiresEffectStack: Boolean = false,
        val requiresCustomDecoderSelection: Boolean = false,
    )

    data class Decision(
        val backend: Backend,
        val requiredDependency: String?,
        val requiredImport: String,
        val reason: Reason,
    )

    fun chooseBackend(request: Request): Decision {
        if (request.requiresCustomDecoderSelection) {
            return media3Decision(Reason.CUSTOM_DECODER_SELECTION)
        }
        if (
            request.requiresHdrFidelity ||
            request.requiresEffectStack ||
            request.useCase == UseCase.HDR_REVIEW_FRAME ||
            request.useCase == UseCase.EFFECT_AWARE_FRAME
        ) {
            return media3Decision(Reason.HDR_OR_EFFECT_FIDELITY)
        }

        return when (request.useCase) {
            UseCase.FREEZE_FRAME_EXPORT -> platformDecision(Reason.USER_VISIBLE_STILL_EXPORT)
            UseCase.AI_ANALYSIS_SAMPLE -> platformDecision(Reason.REPRESENTATIVE_AI_SAMPLE)
            UseCase.TIMELINE_THUMBNAIL_STRIP,
            UseCase.CONTACT_SHEET_THUMBNAIL -> platformDecision(Reason.SMALL_CACHED_SDR_THUMBNAIL)
            UseCase.HDR_REVIEW_FRAME,
            UseCase.EFFECT_AWARE_FRAME -> media3Decision(Reason.HDR_OR_EFFECT_FIDELITY)
        }
    }

    private fun platformDecision(reason: Reason) = Decision(
        backend = Backend.PLATFORM_MEDIA_METADATA_RETRIEVER,
        requiredDependency = null,
        requiredImport = PLATFORM_RETRIEVER_IMPORT,
        reason = reason,
    )

    private fun media3Decision(reason: Reason) = Decision(
        backend = Backend.MEDIA3_FRAME_EXTRACTOR,
        requiredDependency = MEDIA3_INSPECTOR_FRAME_COORDINATE,
        requiredImport = MEDIA3_FRAME_EXTRACTOR_IMPORT,
        reason = reason,
    )
}
