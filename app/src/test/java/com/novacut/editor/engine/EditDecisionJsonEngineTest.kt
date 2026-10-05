package com.novacut.editor.engine

import android.net.Uri
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.model.CaptionWord
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ClipLabel
import com.novacut.editor.model.ColorCurves
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.CurvePoint
import com.novacut.editor.model.Easing
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectKeyframe
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.HslQualifier
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeInterpolation
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskKeyframe
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TextPath
import com.novacut.editor.model.TextPathType
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionEasing
import com.novacut.editor.model.TransitionType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for a real Uri: the JVM stub's equals is always false, so clips could never compare equal.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditDecisionJsonEngineTest {

    private fun uri(raw: String): Uri = Uri.parse(raw)

    private val corner = MaskPoint(0.1f, 0.2f, handleInX = 0.05f, handleInY = 0.15f, handleOutX = 0.12f, handleOutY = 0.25f)

    private val graded = Clip(
        id = "graded",
        sourceUri = uri("file:///media/interview.mp4"),
        sourceDurationMs = 20_000L,
        timelineStartMs = 0L,
        trimStartMs = 1_000L,
        trimEndMs = 9_000L,
        effects = listOf(
            Effect(
                id = "warmth",
                type = EffectType.TEMPERATURE,
                params = mapOf("amount" to 0.35f, "tint" to -0.1f),
                keyframes = listOf(
                    EffectKeyframe(0L, "amount", 0.1f),
                    EffectKeyframe(2_000L, "amount", 0.6f, easing = Easing.EASE_OUT, handleOutX = 0.3f, handleOutY = 0.4f),
                ),
                targetTrackedObjectId = "face-1",
            ),
            Effect(id = "off", type = EffectType.CONTRAST, params = mapOf("amount" to 1.2f), enabled = false),
        ),
        headTransition = Transition(TransitionType.DISSOLVE, durationMs = 750L, easing = TransitionEasing.EASE_IN),
        tailTransition = Transition(TransitionType.FADE_BLACK, durationMs = 400L),
        volume = 0.8f,
        opacity = 0.9f,
        rotation = 12.5f,
        scaleX = 1.25f,
        scaleY = 1.1f,
        flipHorizontal = true,
        positionX = -0.2f,
        positionY = 0.15f,
        anchorX = 0.4f,
        anchorY = 0.6f,
        fadeInMs = 250L,
        fadeOutMs = 500L,
        keyframes = listOf(
            Keyframe(0L, KeyframeProperty.SCALE_X, 1f),
            Keyframe(3_000L, KeyframeProperty.SCALE_X, 1.5f, easing = Easing.EASE_IN_OUT, handleInX = 0.2f, handleInY = 0.1f),
            Keyframe(1_500L, KeyframeProperty.OPACITY, 0.5f, interpolation = KeyframeInterpolation.HOLD),
        ),
        blendMode = BlendMode.SCREEN,
        speedCurve = SpeedCurve(
            listOf(SpeedPoint(0f, 1f), SpeedPoint(0.5f, 2.5f, handleInY = 2f, handleOutY = 3f), SpeedPoint(1f, 0.5f)),
        ),
        colorGrade = ColorGrade(
            liftR = 0.02f,
            gammaG = 1.1f,
            gainB = 0.95f,
            offsetR = -0.01f,
            curves = ColorCurves(master = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.5f, 0.55f), CurvePoint(1f, 0.95f))),
            hslQualifier = HslQualifier(hueCenter = 30f, hueWidth = 20f, adjustSat = 0.2f),
            lutIntensity = 0.7f,
        ),
        masks = listOf(
            Mask(
                id = "window",
                type = MaskType.FREEHAND,
                points = listOf(corner, MaskPoint(0.9f, 0.2f), MaskPoint(0.5f, 0.8f)),
                feather = 0.05f,
                opacity = 0.8f,
                inverted = true,
                expansion = 0.02f,
                keyframes = listOf(
                    MaskKeyframe(1_000L, listOf(MaskPoint(0.2f, 0.3f), MaskPoint(0.8f, 0.3f), MaskPoint(0.5f, 0.9f)), Easing.EASE_IN),
                ),
                trackToMotion = true,
            ),
        ),
        audioEffects = listOf(AudioEffect(id = "eq", type = AudioEffectType.PARAMETRIC_EQ, params = mapOf("gain" to 3f, "freq" to 2_000f))),
        captions = listOf(
            Caption(
                id = "line",
                text = "Keep this line",
                startTimeMs = 0L,
                endTimeMs = 1_200L,
                words = listOf(CaptionWord("Keep", 0L, 300L, 0.9f), CaptionWord("this", 300L, 600L)),
                style = CaptionStyle(type = CaptionStyleType.KARAOKE, fontSize = 40f),
            ),
        ),
        clipLabel = ClipLabel.GREEN,
        name = "Interview",
    )

    private val compound = Clip(
        id = "nest",
        sourceUri = uri("file:///media/nest.mp4"),
        sourceDurationMs = 4_000L,
        timelineStartMs = 8_000L,
        isCompound = true,
        compoundClips = listOf(
            Clip(id = "inner-a", sourceUri = uri("file:///media/a.mp4"), sourceDurationMs = 2_000L, timelineStartMs = 0L),
            Clip(
                id = "inner-b",
                sourceUri = uri("file:///media/b.mp4"),
                sourceDurationMs = 2_000L,
                timelineStartMs = 2_000L,
                keyframes = listOf(Keyframe(0L, KeyframeProperty.ROTATION, 15f)),
            ),
        ),
    )

    private val voice = Clip(
        id = "voice",
        sourceUri = uri("file:///media/voice.wav"),
        sourceDurationMs = 12_000L,
        timelineStartMs = 500L,
        audioSyncOffsetMs = -1_250L,
        linkedClipId = "graded",
        groupId = "take-3",
    )

    private val tracks = listOf(
        Track(id = "v1", type = TrackType.VIDEO, index = 0, clips = listOf(graded, compound), opacity = 0.95f),
        Track(
            id = "a1",
            type = TrackType.AUDIO,
            index = 1,
            clips = listOf(voice),
            timelineOffsetMs = 120L,
            volume = 1.4f,
            pan = -0.3f,
            isSolo = true,
            audioEffects = listOf(AudioEffect(id = "comp", type = AudioEffectType.COMPRESSOR, params = mapOf("ratio" to 4f))),
        ),
    )

    private val overlays = listOf(
        TextOverlay(
            id = "title",
            text = "Chapter one",
            fontSize = 64f,
            bold = true,
            alignment = TextAlignment.LEFT,
            startTimeMs = 0L,
            endTimeMs = 4_000L,
            animationIn = TextAnimation.FADE,
            glowColor = 0xFF89B4FA,
            glowRadius = 6f,
            textPath = TextPath(TextPathType.CURVED, listOf(MaskPoint(0f, 0.5f), MaskPoint(1f, 0.4f)), progress = 0.75f),
            keyframes = listOf(Keyframe(0L, KeyframeProperty.POSITION_Y, 0.2f), Keyframe(1_000L, KeyframeProperty.POSITION_Y, 0.5f)),
            gradientStartColor = 0xFFF38BA8,
            gradientEndColor = 0xFFFAB387,
            gradientAngle = 45f,
            wordStaggerMs = 80L,
        ),
    )

    private val markers = listOf(
        TimelineMarker(id = "hook", timeMs = 1_000L, label = "Hook", color = MarkerColor.ORANGE, notes = "tighten"),
    )

    private fun export(
        tracks: List<Track> = this.tracks,
        textOverlays: List<TextOverlay> = overlays,
        timelineMarkers: List<TimelineMarker> = markers,
    ): String = EditDecisionJsonEngine.export(
        tracks = tracks,
        textOverlays = textOverlays,
        timelineMarkers = timelineMarkers,
        projectName = "Round trip",
        timebase = TimelineTimebase.NTSC_29_97,
    )

    @Test
    fun aFullProjectRoundTripsAndReexportsToTheSameDocument() {
        val json = export()

        val imported = EditDecisionJsonEngine.import(json, ::uri)

        assertEquals(emptyList<String>(), imported.warnings)
        assertEquals(tracks.size, imported.tracks.size)
        tracks.zip(imported.tracks).forEach { (expected, actual) ->
            assertEquals(expected.copy(clips = emptyList()), actual.copy(clips = emptyList()))
            assertEquals(expected.clips.size, actual.clips.size)
            expected.clips.zip(actual.clips).forEach { (want, got) -> assertEquals(want, got) }
        }
        assertEquals(overlays, imported.textOverlays)
        assertEquals(markers, imported.timelineMarkers)
        assertEquals(json, export(imported.tracks, imported.textOverlays, imported.timelineMarkers))
    }

    @Test
    fun aSpeedCurveSurvivesSoTheClipKeepsItsTimelineLength() {
        val imported = EditDecisionJsonEngine.import(export(), ::uri)

        val clip = imported.tracks.first().clips.first()
        assertEquals(graded.durationMs, clip.durationMs)
        assertTrue("the curve changed the duration", clip.durationMs != graded.trimEndMs - graded.trimStartMs)
    }

    @Test
    fun anUnknownEffectIsDroppedWithAWarningAndTheRestImports() {
        val root = JSONObject(export())
        val effects = root.getJSONArray("tracks").getJSONObject(0)
            .getJSONArray("clips").getJSONObject(0).getJSONArray("effects")
        effects.put(JSONObject().put("type", "FROM_THE_FUTURE"))

        val imported = EditDecisionJsonEngine.import(root.toString(), ::uri)

        assertEquals(graded.effects, imported.tracks.first().clips.first().effects)
        assertTrue(imported.warnings.single().contains("Unknown effect"))
    }

    @Test
    fun aMalformedMaskIsLeftOutWithAWarning() {
        val root = JSONObject(export())
        val clip = root.getJSONArray("tracks").getJSONObject(0).getJSONArray("clips").getJSONObject(0)
        clip.getJSONArray("masks").getJSONObject(0).put("points", JSONArray().put("not a point"))

        val imported = EditDecisionJsonEngine.import(root.toString(), ::uri)

        assertEquals(emptyList<Mask>(), imported.tracks.first().clips.first().masks)
        assertTrue(imported.warnings.toString(), imported.warnings.single().contains("mask"))
        assertEquals(graded.keyframes, imported.tracks.first().clips.first().keyframes)
    }

    @Test
    fun aMalformedMaskKeyframeIsReportedRatherThanDroppedSilently() {
        val root = JSONObject(export())
        val mask = root.getJSONArray("tracks").getJSONObject(0).getJSONArray("clips").getJSONObject(0)
            .getJSONArray("masks").getJSONObject(0)
        mask.getJSONArray("keyframes").put("not a keyframe")

        val imported = EditDecisionJsonEngine.import(root.toString(), ::uri)

        assertEquals(graded.masks, imported.tracks.first().clips.first().masks)
        assertTrue(imported.warnings.toString(), imported.warnings.single().contains("mask keyframe"))
    }

    @Test
    fun entriesThatArentObjectsAreReportedRatherThanDroppedSilently() {
        val root = JSONObject(export())
        val clip = root.getJSONArray("tracks").getJSONObject(0).getJSONArray("clips").getJSONObject(0)
        for (list in listOf("effects", "keyframes", "masks", "audioEffects", "captions")) {
            (clip.optJSONArray(list) ?: JSONArray().also { clip.put(list, it) }).put("not an object")
        }
        clip.put("colorGrade", 7)
        root.getJSONArray("markers").put(3)
        root.getJSONArray("textOverlays").put(false)

        val imported = EditDecisionJsonEngine.import(root.toString(), ::uri)

        val importedClip = imported.tracks.first().clips.first()
        assertEquals(graded.effects, importedClip.effects)
        assertEquals(graded.keyframes, importedClip.keyframes)
        assertEquals(graded.masks, importedClip.masks)
        assertEquals(null, importedClip.colorGrade)
        assertEquals(markers, imported.timelineMarkers)
        val notObjects = imported.warnings.filter { it.contains("isn't an object") }
        assertEquals(imported.warnings.toString(), 8, notObjects.size)
        for (what in listOf("effect", "keyframe", "mask", "audio effect", "caption", "colorGrade", "marker", "text overlay")) {
            assertTrue("$what in $notObjects", notObjects.any { it.startsWith("Skipped $what at") })
        }
    }

    @Test
    fun aSchemaNewerThanThisBuildIsRejectedBeforeAnyTimelineIsBuilt() {
        val root = JSONObject(export()).put("schemaVersion", EditDecisionJsonEngine.SCHEMA_VERSION + 1)

        val result = EditDecisionJsonEngine.import(root.toString(), ::uri)

        assertTrue(result.schemaTooNew)
        assertEquals(emptyList<Track>(), result.tracks)
        assertEquals(emptyList<TextOverlay>(), result.textOverlays)
        assertEquals(emptyList<TimelineMarker>(), result.timelineMarkers)
    }
}
