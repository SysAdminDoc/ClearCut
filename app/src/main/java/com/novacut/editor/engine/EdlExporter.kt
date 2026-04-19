package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EdlExporter"

/**
 * Exports NovaCut project timeline as industry-standard EDL (CMX 3600)
 * for import into desktop editors (Premiere, Resolve, FCPX).
 * FCPXML export is handled by [TimelineExchangeEngine].
 */
@Singleton
class EdlExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ExportFormat(val extension: String, val displayName: String) {
        EDL("edl", "EDL (CMX 3600)")
    }

    /**
     * Export timeline as EDL file.
     */
    suspend fun exportEdl(
        projectName: String,
        tracks: List<Track>,
        frameRate: Int = 30
    ): File? = withContext(Dispatchers.IO) {
        try {
            val safeFrameRate = frameRate.coerceIn(1, 240)
            val sb = StringBuilder()
            sb.appendLine("TITLE: ${edlSafeText(projectName, fallback = "NovaCut Project")}")
            sb.appendLine("FCM: NON-DROP FRAME")
            sb.appendLine()

            val videoClips = tracks
                .filter { it.type == TrackType.VIDEO && it.isVisible }
                .flatMap { it.clips }
                .sortedBy { it.timelineStartMs }
            val exportLocale = Locale.US

            for ((index, clip) in videoClips.withIndex()) {
                val editNum = String.format(exportLocale, "%03d", index + 1)
                val reelName = clip.sourceUri.lastPathSegment
                    ?.replace(Regex("[^A-Za-z0-9]"), "")
                    ?.take(8)?.uppercase(Locale.ROOT)?.ifEmpty { "AX" }
                    ?.padEnd(8) ?: "AX      "

                val srcIn = msToTimecode(clip.trimStartMs, safeFrameRate)
                val srcOut = msToTimecode(clip.trimEndMs, safeFrameRate)
                val recIn = msToTimecode(clip.timelineStartMs, safeFrameRate)
                val recOut = msToTimecode(clip.timelineEndMs, safeFrameRate)

                // Transition
                val transition = if (clip.transition != null) {
                    val frames = (clip.transition.durationMs.toDouble() * safeFrameRate / 1000.0)
                        .toInt()
                        .coerceAtLeast(0)
                    "D ${String.format(exportLocale, "%03d", frames)}"
                } else {
                    "C"
                }

                sb.appendLine(
                    "$editNum  $reelName  V  $transition  $srcIn $srcOut $recIn $recOut"
                )

                // Speed effect
                val clipSpeed = safeEdlSpeed(
                    clip.speedCurve?.averageSpeed((clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1L))
                        ?: clip.speed
                )
                if (kotlin.math.abs(clipSpeed - 1.0f) > 0.001f) {
                    val effectiveFps = safeFrameRate * clipSpeed
                    sb.appendLine(
                        "M2   $reelName  ${String.format(exportLocale, "%.1f", effectiveFps)}  $srcIn"
                    )
                }

                // Source file comment
                sb.appendLine(
                    "* FROM CLIP NAME: ${edlSafeText(clip.sourceUri.lastPathSegment ?: "unknown", fallback = "unknown")}"
                )

                // Effects as comments
                for (effect in clip.effects.filter { it.enabled }) {
                    sb.appendLine("* EFFECT NAME: ${edlSafeText(effect.type.displayName, fallback = "Effect")}")
                }

                sb.appendLine()
            }

            val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
            outputDir.mkdirs()
            val sanitized = sanitizeFileName(projectName, fallback = "NovaCut", maxLength = 50)
            val file = File(outputDir, "${sanitized}.edl")
            writeUtf8TextAtomically(file, sb.toString())
            Log.d(TAG, "EDL exported: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "EDL export failed", e)
            null
        }
    }

    private fun msToTimecode(ms: Long, fps: Int): String {
        val totalFrames = (ms * fps + 500) / 1000
        val frames = (totalFrames % fps).toInt()
        val totalSeconds = totalFrames / fps
        val seconds = (totalSeconds % 60).toInt()
        val minutes = ((totalSeconds / 60) % 60).toInt()
        val hours = (totalSeconds / 3600).toInt()
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }

    private fun safeEdlSpeed(speed: Float): Float {
        return if (speed.isFinite() && speed > 0f) speed.coerceIn(0.01f, 100f) else 1f
    }

    private fun edlSafeText(value: String, fallback: String): String {
        return value
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .ifBlank { fallback }
            .take(120)
    }
}
