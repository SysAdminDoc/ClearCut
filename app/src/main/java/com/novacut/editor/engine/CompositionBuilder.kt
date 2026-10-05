package com.novacut.editor.engine

import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItemSequence

@UnstableApi
internal data class CompositionBuildRequest(
    val sequences: List<EditedMediaItemSequence>,
    val hasAudioTracks: Boolean,
    val hasEmbeddedVisualAudio: Boolean,
    val targetWidth: Int,
    val targetHeight: Int,
    val hasMultipleVideoSequences: Boolean = false,
    /** From ColorRenderPlanner. Null leaves Media3's default, which only audio-only compositions do. */
    val hdrMode: ColorHdrMode? = null,
    val compositorLayers: List<ClearCutCompositorLayer> = emptyList(),
    val allowAudioTransmux: Boolean = true,
)

/** The only owner of Media3 composition assembly shared by preview and export. */
@UnstableApi
internal object CompositionBuilder {
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
        // Media3 keeps HDR unless told otherwise, so an SDR plan has to ask for tone mapping.
        request.hdrMode?.let { builder.setHdrMode(it.toMedia3()) }
        return builder.build()
    }
}

// Read-as-SDR is Media3's experimental mode; it's the only route on devices that can't tone-map.
@UnstableApi
@androidx.annotation.OptIn(ExperimentalApi::class)
internal fun ColorHdrMode.toMedia3(): Int = when (this) {
    ColorHdrMode.KEEP_HDR -> Composition.HDR_MODE_KEEP_HDR
    ColorHdrMode.TONE_MAP_TO_SDR -> Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
    ColorHdrMode.TONE_MAP_IN_DECODER -> Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
    ColorHdrMode.INTERPRET_HDR_AS_SDR -> Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
}
