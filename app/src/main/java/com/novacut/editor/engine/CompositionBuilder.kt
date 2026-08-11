package com.novacut.editor.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItemSequence
import com.novacut.editor.engine.AppLog

@UnstableApi
internal data class CompositionBuildRequest(
    val sequences: List<EditedMediaItemSequence>,
    val hasAudioTracks: Boolean,
    val hasEmbeddedVisualAudio: Boolean,
    val targetWidth: Int,
    val targetHeight: Int,
    val hasMultipleVideoSequences: Boolean = false,
    val preserveHdr: Boolean = false,
    val compositorLayers: List<ClearCutCompositorLayer> = emptyList(),
    val allowAudioTransmux: Boolean = true,
)

/** The only owner of Media3 composition assembly shared by preview and export. */
@UnstableApi
internal object CompositionBuilder {
    private const val TAG = "CompositionBuilder"

    fun build(request: CompositionBuildRequest): Composition {
        val builder = Composition.Builder(request.sequences)
            .setTransmuxAudio(
                request.allowAudioTransmux && !request.hasAudioTracks &&
                    request.hasEmbeddedVisualAudio && !request.hasMultipleVideoSequences
            )
        if (request.hasMultipleVideoSequences) {
            builder.setVideoCompositorSettings(
                ClearCutVideoCompositorSettings(
                    outputWidth = request.targetWidth,
                    outputHeight = request.targetHeight,
                    layers = request.compositorLayers,
                )
            )
        }
        if (request.preserveHdr) {
            // Keep the same defensive fallback as the old VideoEngine facade:
            // older Media3 builds may not expose HDR mode at runtime.
            try {
                builder.setHdrMode(Composition.HDR_MODE_KEEP_HDR)
            } catch (e: Throwable) {
                AppLog.w(TAG, "setHdrMode unavailable on this Media3 build", e)
            }
        }
        return builder.build()
    }
}
