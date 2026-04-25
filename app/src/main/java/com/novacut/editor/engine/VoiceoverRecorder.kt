package com.novacut.editor.engine

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceoverRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var partialOutputFile: File? = null
    private var startTime: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    @Synchronized
    fun startRecording(): File? {
        discardActiveRecording("re-start")

        val dir = File(context.filesDir, VOICEOVER_DIR_NAME).also { it.mkdirs() }
        sweepAbandonedVoiceoverPartials(dir)
        val fileId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val file = File(dir, "${VOICEOVER_FILE_PREFIX}${fileId}.m4a")
        val partialFile = File(dir, "${VOICEOVER_FILE_PREFIX}${fileId}${VOICEOVER_PARTIAL_SUFFIX}")

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(256000)
                setAudioChannels(1)
                setOutputFile(partialFile.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = file
            partialOutputFile = partialFile
            startTime = System.currentTimeMillis()
            _isRecording.value = true
            file
        } catch (e: Exception) {
            Log.w("VoiceoverRecorder", "Failed to start voiceover recording", e)
            runCatching { rec.release() }
            partialFile.delete()
            file.delete()
            outputFile = null
            partialOutputFile = null
            _isRecording.value = false
            null
        }
    }

    @Synchronized
    fun stopRecording(): Uri? {
        val file = outputFile
        val partialFile = partialOutputFile
        return try {
            val activeRecorder = recorder
            if (activeRecorder == null) {
                cleanupVoiceoverFiles(partialFile, file)
                clearActiveRecorder()
                null
            } else {
                activeRecorder.stop()
                activeRecorder.release()
                clearActiveRecorder()
                finalizeRecordedVoiceoverFile(partialFile, file)?.let { Uri.fromFile(it) }
            }
        } catch (e: Exception) {
            Log.w("VoiceoverRecorder", "Failed to stop voiceover recording", e)
            runCatching { recorder?.release() }
            cleanupVoiceoverFiles(partialFile, file)
            clearActiveRecorder()
            null
        }
    }

    fun getRecordingDurationMs(): Long {
        return if (_isRecording.value) {
            System.currentTimeMillis() - startTime
        } else 0L
    }

    @Synchronized
    fun release() {
        discardActiveRecording("release")
    }

    private fun discardActiveRecording(reason: String) {
        val activeRecorder = recorder
        val file = outputFile
        val partialFile = partialOutputFile
        if (activeRecorder != null) {
            try { activeRecorder.stop() } catch (e: Exception) { Log.w("VoiceoverRecorder", "Failed to stop recorder on $reason", e) }
            runCatching { activeRecorder.release() }
        }
        cleanupVoiceoverFiles(partialFile, file)
        clearActiveRecorder()
    }

    private fun clearActiveRecorder() {
        recorder = null
        outputFile = null
        partialOutputFile = null
        startTime = 0L
        _isRecording.value = false
    }
}

private const val VOICEOVER_DIR_NAME = "voiceovers"
private const val VOICEOVER_FILE_PREFIX = "voiceover_"
private const val VOICEOVER_PARTIAL_SUFFIX = ".partial.m4a"
private const val ABANDONED_VOICEOVER_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

private fun cleanupVoiceoverFiles(partialFile: File?, outputFile: File?) {
    partialFile?.delete()
    outputFile?.delete()
}

private fun sweepAbandonedVoiceoverPartials(dir: File) {
    val cutoff = System.currentTimeMillis() - ABANDONED_VOICEOVER_PARTIAL_MAX_AGE_MS
    dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(VOICEOVER_PARTIAL_SUFFIX) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

internal fun finalizeRecordedVoiceoverFile(partialFile: File?, outputFile: File?): File? {
    if (partialFile == null || outputFile == null) {
        cleanupVoiceoverFiles(partialFile, outputFile)
        return null
    }
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        cleanupVoiceoverFiles(partialFile, outputFile)
        return null
    }
    moveFileReplacing(partialFile, outputFile)
    return if (outputFile.isFile && outputFile.length() > 0L) {
        outputFile
    } else {
        outputFile.delete()
        null
    }
}
