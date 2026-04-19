package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectKeyframe
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSaveStateTest {

    @Test
    fun deserialize_coercesTrackFieldsToModelInvariants() {
        val state = AutoSaveState.deserialize(
            """
            {
              "version": 1,
              "projectId": "project",
              "tracks": [
                {
                  "id": "track",
                  "type": "VIDEO",
                  "index": -4,
                  "pan": 9.0,
                  "volume": 9.0,
                  "opacity": -3.0,
                  "trackHeight": 1,
                  "clips": []
                }
              ],
              "textOverlays": []
            }
            """.trimIndent()
        )

        val track = state.tracks.single()
        assertEquals(0, track.index)
        assertEquals(1f, track.pan, 0.0001f)
        assertEquals(2f, track.volume, 0.0001f)
        assertEquals(0f, track.opacity, 0.0001f)
        assertEquals(32, track.trackHeight)
    }

    @Test
    fun deserialize_preservesTextOverlayWhenRecoveredNumbersAreInvalid() {
        val state = AutoSaveState.deserialize(
            """
            {
              "version": 1,
              "projectId": "project",
              "tracks": [],
              "textOverlays": [
                {
                  "id": "title",
                  "text": "Recovered title",
                  "fontSize": 0,
                  "positionX": 999,
                  "positionY": -999,
                  "startTimeMs": 5000,
                  "endTimeMs": 1000,
                  "scaleX": 0,
                  "scaleY": -1,
                  "shadowBlur": -10,
                  "glowRadius": -10,
                  "lineHeight": 0
                }
              ]
            }
            """.trimIndent()
        )

        val overlay = state.textOverlays.single()
        assertEquals("Recovered title", overlay.text)
        assertTrue(overlay.fontSize > 0f)
        assertEquals(5_000L, overlay.startTimeMs)
        assertEquals(5_001L, overlay.endTimeMs)
        assertEquals(5f, overlay.positionX, 0.0001f)
        assertEquals(-5f, overlay.positionY, 0.0001f)
        assertTrue(overlay.scaleX > 0f)
        assertTrue(overlay.scaleY > 0f)
        assertEquals(0f, overlay.shadowBlur, 0.0001f)
        assertEquals(0f, overlay.glowRadius, 0.0001f)
        assertTrue(overlay.lineHeight > 0f)
    }

    @Test
    fun serialize_replacesNonFiniteFloatsBeforeWritingJson() {
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    volume = Float.NaN,
                    opacity = Float.POSITIVE_INFINITY,
                    audioEffects = listOf(
                        AudioEffect(
                            type = AudioEffectType.PARAMETRIC_EQ,
                            params = mapOf("band1_gain" to Float.NEGATIVE_INFINITY)
                        )
                    ),
                    clips = listOf(
                        Clip(
                            sourceUri = FakeUri,
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L,
                            trimStartMs = 0L,
                            trimEndMs = 1_000L,
                            speed = Float.POSITIVE_INFINITY,
                            rotation = Float.NaN,
                            scaleX = Float.POSITIVE_INFINITY,
                            effects = listOf(
                                Effect(
                                    type = EffectType.BRIGHTNESS,
                                    params = mapOf("amount" to Float.NaN),
                                    keyframes = listOf(
                                        EffectKeyframe(
                                            timeOffsetMs = 100L,
                                            paramName = "amount",
                                            value = Float.POSITIVE_INFINITY,
                                            handleInX = Float.NaN
                                        )
                                    )
                                )
                            ),
                            keyframes = listOf(
                                Keyframe(
                                    timeOffsetMs = 200L,
                                    property = KeyframeProperty.OPACITY,
                                    value = Float.NaN,
                                    handleOutY = Float.POSITIVE_INFINITY
                                )
                            ),
                            speedCurve = SpeedCurve(
                                listOf(
                                    SpeedPoint(Float.NaN, Float.POSITIVE_INFINITY),
                                    SpeedPoint(1f, 1f, handleInY = Float.NaN)
                                )
                            )
                        )
                    )
                )
            ),
            textOverlays = listOf(
                TextOverlay(
                    text = "Title",
                    fontSize = Float.POSITIVE_INFINITY,
                    positionX = Float.NaN,
                    lineHeight = Float.NEGATIVE_INFINITY
                )
            ),
            drawingPaths = listOf(
                com.novacut.editor.model.DrawingPath(
                    points = listOf(Float.NaN to Float.POSITIVE_INFINITY, 1f to 1f),
                    color = 0xFFFFFFFF,
                    strokeWidth = Float.NaN
                )
            )
        )

        val serialized = state.serialize()

        assertFalse(serialized.contains("NaN"))
        assertFalse(serialized.contains("Infinity"))

        val root = JSONObject(serialized)
        val track = root.getJSONArray("tracks").getJSONObject(0)
        val clip = track.getJSONArray("clips").getJSONObject(0)
        val effect = clip.getJSONArray("effects").getJSONObject(0)
        val overlay = root.getJSONArray("textOverlays").getJSONObject(0)
        val drawingPath = root.getJSONArray("drawingPaths").getJSONObject(0)

        assertEquals(1.0, track.getDouble("volume"), 0.0001)
        assertEquals(1.0, track.getDouble("opacity"), 0.0001)
        assertEquals(0.0, track.getJSONArray("audioEffects").getJSONObject(0).getJSONObject("params").getDouble("band1_gain"), 0.0001)
        assertEquals(1.0, clip.getDouble("speed"), 0.0001)
        assertEquals(0.0, clip.getDouble("rotation"), 0.0001)
        assertEquals(1.0, clip.getDouble("scaleX"), 0.0001)
        assertEquals(0.0, effect.getJSONObject("params").getDouble("amount"), 0.0001)
        assertEquals(1.0, effect.getJSONArray("keyframes").getJSONObject(0).getDouble("value"), 0.0001)
        assertEquals(1.0, clip.getJSONArray("keyframes").getJSONObject(0).getDouble("value"), 0.0001)
        assertEquals(48.0, overlay.getDouble("fontSize"), 0.0001)
        assertEquals(0.5, overlay.getDouble("positionX"), 0.0001)
        assertEquals(1.2, overlay.getDouble("lineHeight"), 0.0001)
        assertEquals(4.0, drawingPath.getDouble("strokeWidth"), 0.0001)
    }
}
