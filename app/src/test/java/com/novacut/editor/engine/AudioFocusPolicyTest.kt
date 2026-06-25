package com.novacut.editor.engine

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusPolicyTest {

    @Test
    fun previewPlayback_usesMediaMovieAttributes() {
        val attributes = ClearCutAudioFocusPolicy.buildPreviewAttributes()

        assertEquals(C.USAGE_MEDIA, ClearCutAudioFocusPolicy.PREVIEW_USAGE)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, ClearCutAudioFocusPolicy.PREVIEW_CONTENT_TYPE)
        assertEquals(C.USAGE_MEDIA, attributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, attributes.contentType)
    }

    @Test
    fun ttsPreview_usesTransientDuckFocus() {
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ClearCutAudioFocusPolicy.TTS_PREVIEW_FOCUS_GAIN,
        )
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            ClearCutAudioFocusPolicy.TTS_PREVIEW_USAGE,
        )
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SPEECH,
            ClearCutAudioFocusPolicy.TTS_PREVIEW_CONTENT_TYPE,
        )
    }

    @Test
    fun voiceover_usesExclusiveTransientFocus() {
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            ClearCutAudioFocusPolicy.VOICEOVER_FOCUS_GAIN,
        )
        assertEquals(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            ClearCutAudioFocusPolicy.VOICEOVER_USAGE,
        )
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SPEECH,
            ClearCutAudioFocusPolicy.VOICEOVER_CONTENT_TYPE,
        )
    }
}
