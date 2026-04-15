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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceoverRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    @Synchronized
    fun startRecording(): File? {
        // Release any existing recorder to prevent resource leak on double-start
        if (recorder != null) {
            try { recorder?.stop() } catch (e: Exception) { Log.w("VoiceoverRecorder", "Failed to stop recorder on re-start", e) }
            recorder?.release()
            recorder = null
            _isRecording.value = false
        }

        val dir = File(context.filesDir, "voiceovers").also { it.mkdirs() }
        val file = File(dir, "voiceover_${System.currentTimeMillis()}.m4a")
        outputFile = file

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
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startTime = System.currentTimeMillis()
            _isRecording.value = true
            file
        } catch (e: Exception) {
            rec.release()
            outputFile = null
            null
        }
    }

    @Synchronized
    fun stopRecording(): Uri? {
        val file = outputFile
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            outputFile = null
            _isRecording.value = false
            file?.let { Uri.fromFile(it) }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            // Clean up orphaned incomplete recording file
            file?.let { if (it.exists()) it.delete() }
            outputFile = null
            _isRecording.value = false
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
        try { recorder?.stop() } catch (e: Exception) { Log.w("VoiceoverRecorder", "Failed to stop recorder on release", e) }
        recorder?.release()
        recorder = null
        outputFile = null
        _isRecording.value = false
    }
}
