package com.novacut.editor.engine

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusPolicyTest {

    @Test
    fun previewPlayback_usesMediaMovieAttributes() {
        val attributes = NovaCutAudioFocusPolicy.buildPreviewAttributes()

        assertEquals(C.USAGE_MEDIA, NovaCutAudioFocusPolicy.PREVIEW_USAGE)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, NovaCutAudioFocusPolicy.PREVIEW_CONTENT_TYPE)
        assertEquals(C.USAGE_MEDIA, attributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, attributes.contentType)
    }

    @Test
    fun ttsPreview_usesTransientDuckFocus() {
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            NovaCutAudioFocusPolicy.TTS_PREVIEW_FOCUS_GAIN,
        )
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            NovaCutAudioFocusPolicy.TTS_PREVIEW_USAGE,
        )
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SPEECH,
            NovaCutAudioFocusPolicy.TTS_PREVIEW_CONTENT_TYPE,
        )
    }

    @Test
    fun voiceover_usesExclusiveTransientFocus() {
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            NovaCutAudioFocusPolicy.VOICEOVER_FOCUS_GAIN,
        )
        assertEquals(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            NovaCutAudioFocusPolicy.VOICEOVER_USAGE,
        )
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SPEECH,
            NovaCutAudioFocusPolicy.VOICEOVER_CONTENT_TYPE,
        )
    }
}
