package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine for AI style transfer. See ROADMAP.md Tier A.11.
 *
 * Targets two model families:
 *  - **AnimeGANv2** for cartoon / anime stylization
 *    (https://github.com/TachibanaYoshino/AnimeGANv2). ~9 MB per variant.
 *  - **Fast Neural Style Transfer** (Johnson 2016) for painterly transfers
 *    (https://github.com/yakhyo/fast-neural-style-transfer). 6-7 MB per style.
 *
 * Both run through the ONNX Runtime that already ships with ClearCut. Each
 * style is an opt-in download via `ModelDownloadManager`; users tap a style
 * card → "Download ~9 MB?" sheet → model is fetched and the engine is
 * activated for that style only.
 *
 * ## Activation path
 *
 *   1. Host each model on a stable URL (or use the upstream Hugging Face
 *      mirrors); record SHA-256 in [docs/models.md](../../../../../../docs/models.md) §1.
 *   2. Wire `prepareStyle(StylePreset)` to download via
 *      `ModelDownloadManager`, with the size cost surfaced to the user
 *      ahead of the download.
 *   3. Implement [stylizeBitmap] with `OrtSession.run(...)` — input tensor
 *      `image` (NCHW, BGR normalized to model-specific mean / std), read
 *      back the stylized tensor, denormalize.
 *   4. AnimeGAN expects 256×256 fixed input; tile-and-blend for larger
 *      frames, identical pattern to InpaintingEngine.
 *   5. Add a clip-level "Style Transfer" effect entry so the export pipeline
 *      reuses the same ONNX session across frames in a clip range.
 *
 * ## License
 *
 * - AnimeGANv2 source: Apache-2.0. **Some pretrained model variants
 *   carry research-only clauses — audit per variant before pinning.**
 * - Fast Neural Style Transfer source: MIT. Released style weights are
 *   typically redistributable but verify per artist.
 */
@Singleton
class StyleTransferEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StyleTransferEngine"
        const val TARGET_ANIMEGAN_SOURCE_URL = "https://github.com/TachibanaYoshino/AnimeGANv2"
        const val TARGET_FAST_NST_SOURCE_URL = "https://github.com/yakhyo/fast-neural-style-transfer"
        const val ANIMEGAN_INPUT_SIZE_PX = 256
        const val FAST_NST_INPUT_SIZE_PX = 480
    }

    /**
     * Available style presets with their associated model information.
     *
     * @param displayName Human-readable name for UI
     * @param modelFilename ONNX model filename
     * @param modelSizeBytes Approximate model size for download progress
     * @param family Which model family this style belongs to
     * @param description Brief description of the visual effect
     */
    enum class StylePreset(
        val displayName: String,
        val modelFilename: String,
        val modelSizeBytes: Long,
        val family: ModelFamily,
        val description: String
    ) {
        /** Hayao Miyazaki (Studio Ghibli) anime style. Soft colors, painterly. */
        ANIME_HAYAO(
            "Anime (Hayao)", "animegan2_hayao.onnx", 8_600_000L,
            ModelFamily.ANIME_GAN, "Studio Ghibli-inspired anime style"
        ),
        /** Makoto Shinkai (Your Name) anime style. Vivid skies, saturated colors. */
        ANIME_SHINKAI(
            "Anime (Shinkai)", "animegan2_shinkai.onnx", 8_600_000L,
            ModelFamily.ANIME_GAN, "Vivid colors, dramatic skies"
        ),
        /** Satoshi Kon (Paprika) anime style. Surreal, detailed. */
        ANIME_PAPRIKA(
            "Anime (Paprika)", "animegan2_paprika.onnx", 8_600_000L,
            ModelFamily.ANIME_GAN, "Surreal, detailed animation style"
        ),
        /** Roman mosaic tile pattern. */
        MOSAIC(
            "Mosaic", "fast_nst_mosaic.onnx", 6_500_000L,
            ModelFamily.FAST_NST, "Roman mosaic tile pattern"
        ),
        /** Van Gogh's Starry Night painting style. */
        STARRY_NIGHT(
            "Starry Night", "fast_nst_starry_night.onnx", 6_500_000L,
            ModelFamily.FAST_NST, "Van Gogh swirling brushstrokes"
        ),
        /** Candy — bright, colorful abstract. */
        CANDY(
            "Candy", "fast_nst_candy.onnx", 6_500_000L,
            ModelFamily.FAST_NST, "Bright, colorful abstract style"
        ),
        /** Udnie — cubist abstract (Francis Picabia). */
        UDNIE(
            "Udnie", "fast_nst_udnie.onnx", 7_000_000L,
            ModelFamily.FAST_NST, "Cubist abstract art style"
        ),
        /** Rain Princess — impressionist rain scene. */
        RAIN_PRINCESS(
            "Rain Princess", "fast_nst_rain_princess.onnx", 6_500_000L,
            ModelFamily.FAST_NST, "Impressionist rain scene style"
        ),
        /** Pencil sketch effect. No ML model required — uses edge detection. */
        PENCIL_SKETCH(
            "Pencil Sketch", "", 0L,
            ModelFamily.OPENCV, "Black and white pencil sketch"
        );

        /** Whether this style requires a downloaded ML model. */
        val requiresModel: Boolean get() = modelSizeBytes > 0
    }

    enum class ModelFamily {
        /** AnimeGANv2 models. Input: [0,1], Output: [-1,1]. */
        ANIME_GAN,
        /** Fast Neural Style Transfer models. Input: [0,255], Output: [0,255]. */
        FAST_NST,
        /** OpenCV-based effects (no ML model needed). */
        OPENCV
    }

    /**
     * Result of applying a style to a single frame.
     *
     * @param outputBitmap The stylized frame
     * @param style Which style was applied
     * @param processingTimeMs Inference time in milliseconds
     */
    data class StyleResult(
        val outputBitmap: Bitmap,
        val style: StylePreset,
        val processingTimeMs: Long
    )

    /**
     * Result of applying a style to an entire video.
     *
     * @param outputUri URI to the stylized video
     * @param style Which style was applied
     * @param framesProcessed Total frames processed
     * @param totalProcessingTimeMs Wall-clock time
     * @param averageFps Average processing speed
     */
    data class VideoStyleResult(
        val outputUri: Uri,
        val style: StylePreset,
        val framesProcessed: Int,
        val totalProcessingTimeMs: Long,
        val averageFps: Float
    )

    /** Whether a given style's model is downloaded and ready. */
    fun isStyleReady(style: StylePreset): Boolean {
        if (!style.requiresModel) return true
        Log.d(TAG, "isStyleReady: stub — per-style model not yet pinned")
        return false
    }

    /** Get list of all styles that are ready to use (downloaded or model-free). */
    fun getAvailableStyles(): List<StylePreset> {
        return StylePreset.entries.filter { isStyleReady(it) }
    }

    /** Get list of all styles that need to be downloaded. */
    fun getDownloadableStyles(): List<StylePreset> {
        return StylePreset.entries.filter { it.requiresModel && !isStyleReady(it) }
    }

    /**
     * Download the model for a specific style preset.
     *
     * @param style Which style to download
     * @param onProgress Progress callback in [0.0, 1.0]
     */
    suspend fun downloadStyle(
        style: StylePreset,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!style.requiresModel) return@withContext true
        Log.d(TAG, "downloadStyle: stub — per-style model not yet pinned")
        false
    }

    /**
     * Apply an artistic style to a single frame.
     *
     * @param bitmap Input frame
     * @param style Which style preset to apply
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return StyleResult with the stylized bitmap, or null on failure
     */
    suspend fun applyStyle(
        bitmap: Bitmap,
        style: StylePreset,
        onProgress: (Float) -> Unit = {}
    ): StyleResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "applyStyle: stub — per-style model not yet pinned")
        null
    }

    /**
     * Apply an artistic style to an entire video.
     *
     * @param uri Source video URI
     * @param style Which style preset to apply
     * @param outputUri Destination URI for the stylized video
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return VideoStyleResult, or null on failure
     */
    suspend fun applyStyleToVideo(
        uri: Uri,
        style: StylePreset,
        outputUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): VideoStyleResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "applyStyleToVideo: stub — per-style model not yet pinned")
        null
    }
}
