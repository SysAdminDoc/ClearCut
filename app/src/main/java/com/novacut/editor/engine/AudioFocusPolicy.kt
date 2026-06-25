package com.novacut.editor.engine

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes as Media3AudioAttributes

internal object ClearCutAudioFocusPolicy {
    const val PREVIEW_USAGE: Int = C.USAGE_MEDIA
    const val PREVIEW_CONTENT_TYPE: Int = C.AUDIO_CONTENT_TYPE_MOVIE
    const val TTS_PREVIEW_FOCUS_GAIN: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    const val TTS_PREVIEW_USAGE: Int = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
    const val TTS_PREVIEW_CONTENT_TYPE: Int = AudioAttributes.CONTENT_TYPE_SPEECH
    const val VOICEOVER_FOCUS_GAIN: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
    const val VOICEOVER_USAGE: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION
    const val VOICEOVER_CONTENT_TYPE: Int = AudioAttributes.CONTENT_TYPE_SPEECH

    fun buildPreviewAttributes(): Media3AudioAttributes {
        return Media3AudioAttributes.Builder()
            .setUsage(PREVIEW_USAGE)
            .setContentType(PREVIEW_CONTENT_TYPE)
            .build()
    }

    fun buildSpeechAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(TTS_PREVIEW_USAGE)
            .setContentType(TTS_PREVIEW_CONTENT_TYPE)
            .build()
    }

    fun buildVoiceoverCaptureAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(VOICEOVER_USAGE)
            .setContentType(VOICEOVER_CONTENT_TYPE)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildFocusRequest(
        gainType: Int,
        attributes: AudioAttributes,
        listener: AudioManager.OnAudioFocusChangeListener,
    ): AudioFocusRequest {
        return AudioFocusRequest.Builder(gainType)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener)
            .build()
    }
}
