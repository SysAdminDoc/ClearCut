package com.novacut.editor.engine

/**
 * Runtime policy for Android 15 media hooks.
 *
 * Media3 1.11.0 already owns the platform [android.media.LoudnessCodecController]
 * integration in its API-35-aware [androidx.media3.exoplayer.mediacodec.MediaCodecAudioRenderer].
 * Both CompositionPlayer and Transformer's SequenceAudioRenderer use that renderer for
 * audio decoding, so preview and export input audio get the platform loudness path without
 * the app reaching into a private MediaCodec. Transformer does not expose its codec to the
 * application, and the Android controller is decoder-oriented; creating a second unattached
 * controller here would not change the export.
 *
 * The HDR value is deliberately conservative. It leaves room for HDR highlights while keeping
 * the editor's SDR chrome near the display's SDR white point. SurfaceView has a separate
 * headroom control, so callers should apply this value to both the HDR window and the preview
 * surface when HDR content is active.
 */
internal object Android15MediaPolicy {
    const val ANDROID_15_API = 35
    const val DEFAULT_HDR_HEADROOM_RATIO = 2.0f

    enum class LoudnessIntegration {
        MEDIA3_PLATFORM_CONTROLLER,
        LEGACY_MEDIA3_RENDERER,
    }

    fun loudnessIntegrationForSdk(sdkInt: Int): LoudnessIntegration =
        if (sdkInt >= ANDROID_15_API) {
            LoudnessIntegration.MEDIA3_PLATFORM_CONTROLLER
        } else {
            LoudnessIntegration.LEGACY_MEDIA3_RENDERER
        }

    fun supportsDesiredHdrHeadroom(sdkInt: Int): Boolean = sdkInt >= ANDROID_15_API

    fun desiredHdrHeadroom(sdkInt: Int, hasHdrContent: Boolean): Float =
        if (hasHdrContent && supportsDesiredHdrHeadroom(sdkInt)) {
            DEFAULT_HDR_HEADROOM_RATIO
        } else {
            0.0f
        }
}
