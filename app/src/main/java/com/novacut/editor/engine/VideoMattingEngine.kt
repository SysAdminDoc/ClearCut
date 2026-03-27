package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stub engine — requires RVM ONNX Runtime dependency. See ROADMAP.md Tier 2. */
@Singleton
class VideoMattingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VideoMattingEngine"
    }

    /**
     * Configuration for video matting operations.
     *
     * @param quality Preview mode uses lower resolution (faster); Export uses full resolution.
     * @param backgroundMode How to handle the removed background.
     * @param backgroundColor Solid color for [BackgroundMode.COLOR] mode (default: green).
     * @param backgroundImageUri URI to replacement background image for [BackgroundMode.IMAGE] mode.
     * @param blurRadius Blur radius for [BackgroundMode.BLUR] mode (default: 25).
     * @param downsampleRatio Processing resolution as fraction of input (0.25 = quarter res).
     */
    data class MattingConfig(
        val quality: Quality = Quality.PREVIEW,
        val backgroundMode: BackgroundMode = BackgroundMode.TRANSPARENT,
        val backgroundColor: Int = Color.GREEN,
        val backgroundImageUri: Uri? = null,
        val blurRadius: Int = 25,
        val downsampleRatio: Float = 0.5f
    ) {
        enum class Quality {
            /** 512x288, faster processing. Good for real-time preview. */
            PREVIEW,
            /** Full resolution, slower. Used for final export with alpha preservation. */
            EXPORT
        }

        enum class BackgroundMode {
            /** Output RGBA with transparent background (for compositing). */
            TRANSPARENT,
            /** Gaussian blur of the original background. */
            BLUR,
            /** Replace with a user-provided image. */
            IMAGE,
            /** Replace with a solid color. */
            COLOR
        }
    }

    /**
     * Result of processing a single frame through RVM.
     *
     * @param alphaMatte Grayscale bitmap where white = foreground, black = background.
     * @param compositedFrame The frame with background replaced according to config.
     * @param processingTimeMs Inference time in milliseconds
     */
    data class MattingResult(
        val alphaMatte: Bitmap,
        val compositedFrame: Bitmap?,
        val processingTimeMs: Long
    )

    /**
     * Result of processing a full video through RVM.
     *
     * @param outputUri URI to the output video
     * @param framesProcessed Total frames processed
     * @param totalProcessingTimeMs Wall-clock time
     * @param averageFps Average processing speed in frames per second
     */
    data class VideoMattingResult(
        val outputUri: Uri,
        val framesProcessed: Int,
        val totalProcessingTimeMs: Long,
        val averageFps: Float
    )

    /** Whether the RVM model is downloaded and ready for inference. */
    fun isModelReady(): Boolean {
        Log.d(TAG, "isModelReady: stub — requires RVM ONNX model")
        return false
    }

    /**
     * Download the RVM MobileNetV3 ONNX model.
     *
     * @param onProgress Progress callback in [0.0, 1.0]
     */
    suspend fun downloadModel(
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub — requires RVM ONNX model")
        false
    }

    /**
     * Process a single frame through RVM to extract an alpha matte.
     *
     * @param bitmap Input frame
     * @param config Matting configuration
     * @return MattingResult with alpha matte and optional composited frame, or null on failure
     */
    suspend fun processFrame(
        bitmap: Bitmap,
        config: MattingConfig = MattingConfig()
    ): MattingResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "processFrame: stub — requires RVM ONNX model")
        null
    }

    /**
     * Process a full video through RVM with background replacement.
     *
     * @param uri Source video URI
     * @param outputUri Destination URI for the matted video
     * @param config Matting configuration (background mode, quality, etc.)
     * @param onProgress Progress callback in [0.0, 1.0]
     * @return VideoMattingResult, or null on failure
     */
    suspend fun processVideo(
        uri: Uri,
        outputUri: Uri,
        config: MattingConfig = MattingConfig(),
        onProgress: (Float) -> Unit = {}
    ): VideoMattingResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "processVideo: stub — requires RVM ONNX model")
        null
    }
}
