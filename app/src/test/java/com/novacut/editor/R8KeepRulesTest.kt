package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the release shrinker configuration against package-wide keep drift. */
class R8KeepRulesTest {

    @Test
    fun packageWideKeepsAreNotReintroduced() {
        val rules = readRules()
        val forbiddenPatterns = listOf(
            "com.novacut.editor.engine.**",
            "com.novacut.editor.ai.**",
            "dagger.hilt.**",
            "javax.inject.**",
            "androidx.compose.runtime.**",
            "androidx.compose.material3.**",
            "androidx.navigation.**",
            "androidx.lifecycle.**",
            "androidx.media3.**",
            "coil.**",
            "ai.onnxruntime.**",
            "com.google.mediapipe.**",
            "androidx.hilt.work.**",
            "com.airbnb.lottie.**",
            "androidx.datastore.**",
            "okhttp3.**",
        )

        forbiddenPatterns.forEach { pattern ->
            assertFalse(
                "Release rules must not keep the entire $pattern namespace",
                rules.contains("-keep class $pattern")
            )
        }
        assertFalse(
            "Release rules must not retain every annotation-shaped attribute",
            rules.contains("-keepattributes *Annotation*")
        )
    }

    @Test
    fun onlyKnownClassForNameProbesReceiveExactKeeps() {
        val rules = readRules()
        REFLECTIVE_PROBES.forEach { className ->
            assertTrue(
                "The Class.forName probe for $className must retain its original class name",
                rules.contains("-keep,allowoptimization class $className")
            )
        }
    }

    @Test
    fun releaseRulesDocumentConsumerRuleOwnership() {
        val rules = readRules()
        assertTrue(rules.contains("-keep class * extends androidx.room.RoomDatabase { void <init>(); }"))
        assertTrue(rules.contains("-keepattributes AnnotationDefault,EnclosingMethod,InnerClasses"))
        assertTrue(rules.contains("-assumenosideeffects class android.util.Log"))
        assertTrue(
            "The native-method baseline must stay in the default Android optimize rules",
            rules.contains("Native methods are covered by proguard-android-optimize.txt")
        )
    }

    private fun readRules(): String {
        val candidates = listOf(
            File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro"),
            File("../../app/proguard-rules.pro"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Could not locate app/proguard-rules.pro from ${File(".").absoluteFile}")
    }

    companion object {
        private val REFLECTIVE_PROBES = listOf(
            "androidx.camera.video.VideoCapture",
            "org.contentauth.c2pa.Builder",
            "org.proofmode.simplec2pa.Manifest",
            "com.arthenica.ffmpegkit.FFmpegKit",
            "com.rikorose.deepfilternet.NativeDeepFilterNet",
            "com.kaleyra.noise_filter.DeepFilterNet",
            "io.github.thibaultbee.streampack.streamers.SingleStreamer",
            "com.wmspanel.libstream.Streamer",
            "com.haivision.srtkit.Srt",
            "com.google.oboe.MultiChannelResampler",
            "app.rive.runtime.kotlin.RiveAnimationView",
        )
    }
}
