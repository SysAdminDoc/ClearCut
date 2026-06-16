package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app recorder stub -- requires androidx.camera + teleprompter UI. See ROADMAP.md Tier C.8.
 *
 * The shipped capture button uses an external camera-app handoff through
 * ActivityResultContracts.CaptureVideo. This engine models the separate future
 * in-app recorder, where NovaCut would own CameraX, runtime CAMERA permission,
 * and optional scrolling teleprompter overlay.
 *
 * Dependencies to add when wiring the real UI:
 *   implementation("androidx.camera:camera-core:1.4.+")
 *   implementation("androidx.camera:camera-camera2:1.4.+")
 *   implementation("androidx.camera:camera-lifecycle:1.4.+")
 *   implementation("androidx.camera:camera-video:1.4.+")
 *   implementation("androidx.camera:camera-view:1.4.+")
 */
@Singleton
class CameraCaptureEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class CaptureConfig(
        val resolution: Resolution = Resolution.UHD_4K,
        val frameRate: Int = 30,
        val useFrontCamera: Boolean = false,
        val stabilizationEnabled: Boolean = true,
        val hdrEnabled: Boolean = false,
        val teleprompter: TeleprompterConfig? = null
    ) {
        enum class Resolution(val widthPx: Int, val heightPx: Int) {
            HD_720P(1280, 720),
            FHD_1080P(1920, 1080),
            UHD_4K(3840, 2160)
        }
    }

    data class TeleprompterConfig(
        val text: String,
        val scrollSpeedWordsPerMin: Int = 150,
        val fontSizeSp: Int = 26,
        val mirrorText: Boolean = false,
        val backgroundAlpha: Float = 0.6f
    )

    data class CaptureResult(
        val outputUri: Uri,
        val durationMs: Long,
        val widthPx: Int,
        val heightPx: Int,
        val frameRate: Int
    )

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    enum class RecordingState { IDLE, PREPARING, RECORDING, PAUSED, STOPPING }

    fun captureCapability(): CameraCaptureCapability = cameraCaptureCapability(isCameraAvailable())

    /**
     * Reflection probe for the CameraX VideoCapture entry point. Flips
     * automatically when the camera-video dep is added.
     */
    fun isCameraAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val available = try {
            Class.forName("androidx.camera.video.VideoCapture")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "CameraCaptureEngine availability probe threw an unexpected error", e)
            false
        }
        cachedAvailability = available
        if (!available) Log.d(TAG, "isCameraAvailable: CameraX not on classpath")
        return available
    }

    @Volatile private var cachedAvailability: Boolean? = null

    /**
     * Compute the number of words the teleprompter should keep on-screen at
     * the configured scroll speed, so the renderer can size the visible
     * window without re-querying CameraX. Pure helper available today.
     *
     * Default visibleSeconds = 6.0 means "show roughly 6 seconds of speech
     * worth of words" — the prompter should fade out words after they
     * scroll off the top.
     */
    fun teleprompterVisibleWordCount(
        config: TeleprompterConfig,
        visibleSeconds: Float = 6f,
    ): Int {
        require(visibleSeconds > 0f) { "visibleSeconds must be > 0: $visibleSeconds" }
        require(config.scrollSpeedWordsPerMin > 0) {
            "scrollSpeedWordsPerMin must be > 0: ${config.scrollSpeedWordsPerMin}"
        }
        val wordsPerSec = config.scrollSpeedWordsPerMin / 60f
        return (wordsPerSec * visibleSeconds).toInt().coerceAtLeast(1)
    }

    suspend fun startRecording(
        config: CaptureConfig = CaptureConfig(),
        outputUri: Uri
    ): Boolean = withContext(Dispatchers.Main) {
        Log.d(TAG, "startRecording: stub -- CameraX not wired")
        false
    }

    suspend fun stopRecording(): CaptureResult? = withContext(Dispatchers.Main) {
        Log.d(TAG, "stopRecording: stub -- CameraX not wired")
        null
    }

    fun pauseRecording() { Log.d(TAG, "pauseRecording: stub") }
    fun resumeRecording() { Log.d(TAG, "resumeRecording: stub") }

    companion object {
        private const val TAG = "CameraCapture"
    }
}

data class CameraCaptureCapability(
    val externalHandoff: ExternalCameraHandoffCapability,
    val inAppRecorder: InAppCameraRecorderCapability
)

data class ExternalCameraHandoffCapability(
    val available: Boolean,
    val label: String,
    val requiresNovaCutCameraPermission: Boolean
)

data class InAppCameraRecorderCapability(
    val available: Boolean,
    val unavailableReason: String?,
    val requiresRuntimeCameraPermission: Boolean
)

internal fun cameraCaptureCapability(cameraXAvailable: Boolean): CameraCaptureCapability {
    return CameraCaptureCapability(
        externalHandoff = ExternalCameraHandoffCapability(
            available = true,
            label = "Open camera app",
            requiresNovaCutCameraPermission = false
        ),
        inAppRecorder = InAppCameraRecorderCapability(
            available = cameraXAvailable,
            unavailableReason = if (cameraXAvailable) {
                null
            } else {
                "CameraX VideoCapture is not bundled; use the external camera-app handoff."
            },
            requiresRuntimeCameraPermission = true
        )
    )
}
