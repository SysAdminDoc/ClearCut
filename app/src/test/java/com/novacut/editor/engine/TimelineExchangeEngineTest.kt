package com.novacut.editor.engine

import android.net.Uri
import android.net.TestUri
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineExchangeEngineTest {

    private val engine = TimelineExchangeEngine(null)

    @Test
    fun interchangeContractsDeclareTheSupportedAdapterRange() {
        val otio = TimelineExchangeEngine.TimelineExchangeFormat.OTIO
        assertEquals("0.15", otio.contract?.schema)
        assertEquals("0.15-0.16", otio.contract?.adapterRange)
        assertEquals("1.11", TimelineExchangeEngine.TimelineExchangeFormat.FCPXML.contract?.schema)
        assertEquals("CMX 3600", TimelineExchangeEngine.TimelineExchangeFormat.EDL_CMX3600.contract?.schema)
    }

    @Test
    fun otioRoundTripPreservesRationalTimingMetadataTransitionsAndNestedClips() {
        val nested = clip(
            id = "nested",
            uri = "file:///media/nested.mp4",
            durationMs = 500L,
            name = "Nested shot",
        )
        val first = clip(
            id = "first",
            uri = "file:///media/M%26M%20%3Cdraft%3E.mp4",
            durationMs = 1_000L,
            name = "Opening & title",
            headTransition = Transition(TransitionType.WIPE_LEFT, durationMs = 250L),
            tailTransition = Transition(TransitionType.DISSOLVE, durationMs = 300L),
            isReversed = true,
            effects = listOf(Effect(type = EffectType.BRIGHTNESS, params = mapOf("value" to 0.25f))),
            compoundClips = listOf(nested),
        )
        val second = clip(
            id = "second",
            uri = "file:///media/second.mp4",
            durationMs = 750L,
            timelineStartMs = 5_000L,
            name = "Second shot",
        )
        val tracks = listOf(
            Track(
                id = "video-track",
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(first, second),
                isLocked = true,
                isVisible = false,
                isMuted = true,
                isSolo = true,
                volume = 0.75f,
                pan = -0.25f,
                opacity = 0.8f,
                blendMode = BlendMode.SCREEN,
            )
        )
        val overlays = listOf(
            TextOverlay(
                id = "title-overlay",
                text = "M&M <draft>",
                startTimeMs = 2_000L,
                endTimeMs = 3_250L,
            )
        )

        val json = engine.exportToOtio(
            tracks = tracks,
            textOverlays = overlays,
            projectName = "Hostile & nested",
            timebase = TimelineTimebase.NTSC_23_976,
        )
        val root = JSONObject(json)
        assertEquals("Timeline.1", root.getString("OTIO_SCHEMA"))
        assertEquals("0.15", root.getJSONObject("metadata").getString("clearcut_otio_schema_version"))
        assertEquals("0.15-0.16", root.getJSONObject("metadata").getString("clearcut_otio_adapter_range"))
        assertEquals(24_000, root.getJSONObject("metadata").getInt("clearcut_timebase_numerator"))
        assertEquals(1_001, root.getJSONObject("metadata").getInt("clearcut_timebase_denominator"))
        assertTrue(json.contains("TimeRange.1"))
        assertTrue(json.contains("Transition.1"))
        assertTrue(json.contains("M&M <draft>"))

        val imported = engine.importFromOtio(json, ::testUri)
        assertTrue(imported.warnings.joinToString().isBlank())
        assertTrue(imported.unresolvedMediaUris.isEmpty())
        assertEquals(0, imported.droppedEffects)
        assertEquals(1, imported.tracks.size)
        assertEquals(1, imported.textOverlays.size)

        val importedTrack = imported.tracks.single()
        assertEquals("video-track", importedTrack.id)
        assertTrue(importedTrack.isLocked)
        assertFalse(importedTrack.isVisible)
        assertTrue(importedTrack.isMuted)
        assertTrue(importedTrack.isSolo)
        assertEquals(BlendMode.SCREEN, importedTrack.blendMode)
        assertEquals(2, importedTrack.clips.size)

        val importedFirst = importedTrack.clips[0]
        assertEquals("first", importedFirst.id)
        assertEquals("Opening & title", importedFirst.name)
        assertTrue(importedFirst.isReversed)
        assertEquals(TransitionType.WIPE_LEFT, importedFirst.headTransition?.type)
        assertEquals(TransitionType.DISSOLVE, importedFirst.tailTransition?.type)
        assertEquals(1, importedFirst.compoundClips.size)
        assertEquals("nested", importedFirst.compoundClips.single().id)
        assertEquals(1, importedFirst.effects.size)
        assertEquals(EffectType.BRIGHTNESS, importedFirst.effects.single().type)
        assertEquals("file:///media/M%26M%20%3Cdraft%3E.mp4", importedFirst.sourceUri.toString())

        val importedSecond = importedTrack.clips[1]
        // 5,000 ms is not an exact 23.976 frame boundary; compare within one
        // frame rather than demanding a wall-clock millisecond identity.
        assertTrue(kotlin.math.abs(importedSecond.timelineStartMs - 5_000L) <= 42L)
        assertTrue(
            "overlay end=${imported.textOverlays.single().endTimeMs}",
            kotlin.math.abs(imported.textOverlays.single().endTimeMs - 3_250L) <= 4L
        )
    }

    @Test
    fun otioRoundTripUsesExactNtsc2997Metadata() {
        val json = engine.exportToOtio(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip("c", "file:///c.mp4", 2_000L)))),
            textOverlays = emptyList(),
            projectName = "29.97",
            timebase = TimelineTimebase.NTSC_29_97,
        )
        val metadata = JSONObject(json).getJSONObject("metadata")
        assertEquals(30_000, metadata.getInt("clearcut_timebase_numerator"))
        assertEquals(1_001, metadata.getInt("clearcut_timebase_denominator"))
        val rate = JSONObject(json)
            .getJSONObject("tracks")
            .getJSONArray("children")
            .getJSONObject(0)
            .getJSONArray("children")
            .getJSONObject(0)
            .getJSONObject("source_range")
            .getJSONObject("duration")
            .getDouble("rate")
        assertTrue(kotlin.math.abs(rate - (30_000.0 / 1_001.0)) < 0.0001)
        assertEquals(1, engine.importFromOtio(json, ::testUri).tracks.single().clips.size)
    }

    @Test
    fun otioImportReportsHostileUriWithoutExecutingOrCrashing() {
        val hostile = JSONObject()
            .put("OTIO_SCHEMA", "Timeline.1")
            .put("metadata", JSONObject())
            .put("name", "hostile")
            .put("tracks", JSONObject()
                .put("OTIO_SCHEMA", "Stack.1")
                .put("children", org.json.JSONArray().put(
                    JSONObject()
                        .put("OTIO_SCHEMA", "Track.1")
                        .put("kind", "Video")
                        .put("children", org.json.JSONArray().put(
                            JSONObject()
                                .put("OTIO_SCHEMA", "Clip.1")
                                .put("source_range", timeRange(0, 30, 30.0))
                                .put("media_reference", JSONObject()
                                    .put("OTIO_SCHEMA", "ExternalReference.1")
                                    .put("target_url", "javascript:alert('x')"))
                        ))
                )))

        val result = engine.importFromOtio(hostile.toString(), ::testUri)

        assertEquals(1, result.tracks.single().clips.size)
        assertEquals(listOf("javascript:alert('x')"), result.unresolvedMediaUris)
        assertTrue(result.warnings.any { it.contains("unsupported media URI scheme") })
    }

    @Test
    fun fcpxmlRoundTripParsesRationalFrameDurationAndEscapedMedia() {
        val source = clip(
            id = "fcpxml-clip",
            uri = "file:///media/M%26M%20%3Cdraft%3E.mp4",
            durationMs = 2_000L,
            timelineStartMs = 1_000L,
            name = "M&M <draft>",
        )
        val xml = engine.exportToFcpxml(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(source))),
            projectName = "M&M <draft>",
            timebase = TimelineTimebase.NTSC_29_97,
        )
        assertTrue(xml.contains("frameDuration=\"1001/30000s\""))
        assertTrue(xml.contains("M&amp;M &lt;draft&gt;"))

        val imported = engine.importFromFcpxml(xml, ::testUri)

        assertTrue(imported.warnings.isEmpty())
        assertEquals(1, imported.tracks.single().clips.size)
        val clip = imported.tracks.single().clips.single()
        assertEquals("M&M <draft>", clip.name)
        assertEquals("file:///media/M%26M%20%3Cdraft%3E.mp4", clip.sourceUri.toString())
        assertTrue(kotlin.math.abs(clip.timelineStartMs - 1_000L) <= 34L)
    }

    @Test
    fun editDecisionJsonRoundTripMapsClipsMarkersCaptionsAndTimebase() {
        val source = clip(
            id = "decision-clip",
            uri = "file:///media/decision.mp4",
            durationMs = 4_000L,
            timelineStartMs = 750L,
            name = "Decision shot",
        ).copy(
            trimStartMs = 500L,
            trimEndMs = 3_500L,
            flipHorizontal = true,
            flipVertical = true,
            captions = listOf(
                Caption(
                    id = "caption",
                    text = "Keep this line",
                    startTimeMs = 600L,
                    endTimeMs = 1_200L,
                    style = com.novacut.editor.model.CaptionStyle(type = CaptionStyleType.KARAOKE),
                )
            ),
        )
        val marker = TimelineMarker(
            id = "marker",
            timeMs = 1_000L,
            label = "Hook",
        )
        val json = engine.exportToEditDecisionJson(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(source))),
            textOverlays = listOf(TextOverlay(id = "title", text = "Review")),
            timelineMarkers = listOf(marker),
            projectName = "Portable decisions",
            timebase = TimelineTimebase.NTSC_29_97,
        )

        val root = JSONObject(json)
        assertEquals("com.clearcut.edit-decision", root.getString("schema"))
        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals(30_000, root.getJSONObject("project").getInt("frameRateNumerator"))
        assertEquals("decision-clip", root.getJSONArray("tracks")
            .getJSONObject(0).getJSONArray("clips").getJSONObject(0).getString("id"))
        val exportedClip = root.getJSONArray("tracks")
            .getJSONObject(0).getJSONArray("clips").getJSONObject(0)
        assertTrue(exportedClip.getBoolean("flipHorizontal"))
        assertTrue(exportedClip.getBoolean("flipVertical"))

        val imported = engine.importFromEditDecisionJson(json, ::testUri)

        assertTrue(imported.warnings.isEmpty())
        assertEquals(1, imported.tracks.single().clips.size)
        assertEquals("decision-clip", imported.tracks.single().clips.single().id)
        assertEquals(750L, imported.tracks.single().clips.single().timelineStartMs)
        assertTrue(imported.tracks.single().clips.single().flipHorizontal)
        assertTrue(imported.tracks.single().clips.single().flipVertical)
        assertEquals("Keep this line", imported.tracks.single().clips.single().captions.single().text)
        assertEquals(CaptionStyleType.KARAOKE, imported.tracks.single().clips.single().captions.single().style.type)
        assertEquals(listOf(marker), imported.timelineMarkers)
        assertEquals("Review", imported.textOverlays.single().text)
    }

    @Test
    fun editDecisionJsonRejectsSchemaThatIsNewerThanTheSupportedVersion() {
        val future = JSONObject()
            .put("schema", "com.clearcut.edit-decision")
            .put("schemaVersion", 2)
            .put("tracks", org.json.JSONArray().put(JSONObject().put("type", "VIDEO")))

        val result = engine.importFromEditDecisionJson(future.toString(), ::testUri)

        assertTrue(result.schemaTooNew)
        assertEquals(2, result.schemaVersion)
        assertTrue(result.tracks.isEmpty())
        assertTrue(result.warnings.single().contains("newer"))
    }

    @Test
    fun editDecisionJsonReportsUnprobeableSourceForRelinkPreview() {
        val result = engine.importFromEditDecisionJson(
            engine.exportToEditDecisionJson(
                tracks = listOf(
                    Track(
                        type = TrackType.VIDEO,
                        index = 0,
                        clips = listOf(clip("hostile", "javascript:alert('x')", 1_000L)),
                    )
                ),
                projectName = "Relink",
            ),
            ::testUri,
        )

        assertEquals(listOf("javascript:alert('x')"), result.unresolvedMediaUris)
        assertTrue(result.warnings.any { it.contains("relink", ignoreCase = true) })
    }

    @Test
    fun otio015FixtureIsAcceptedByTheDeclaredContract() {
        val imported = engine.importFromOtio(fixture("otio-0.15-supported.otio"), ::testUri)

        assertTrue(imported.warnings.isEmpty())
        assertTrue(imported.unresolvedMediaUris.isEmpty())
        assertEquals(0, imported.droppedEffects)
        assertEquals("otio-015-clip", imported.tracks.single().clips.single().id)
        assertEquals(2_000L, imported.tracks.single().clips.single().trimEndMs)
    }

    @Test
    fun otio016FixturePreservesTimebaseAndLossReports() {
        val imported = engine.importFromOtio(fixture("otio-0.16-lossy.otio"), ::testUri)

        val clip = imported.tracks.single().clips.single()
        assertEquals("otio-016-clip", clip.id)
        assertEquals(1_001L, clip.trimEndMs)
        assertTrue(imported.unresolvedMediaUris.contains("javascript:alert('x')"))
        assertTrue(imported.unresolvedMediaUris.contains("<missing:missing-media>"))
        assertTrue(imported.droppedEffects >= 1)
        assertTrue(imported.warnings.any { it.contains("Unsupported OTIO schema in track") })
        assertTrue(imported.warnings.any { it.contains("Unsupported effect") })
    }

    @Test
    fun otioInvalidTimebaseFixtureReportsAnActionableFallback() {
        val imported = engine.importFromOtio(fixture("otio-invalid-timebase.otio"), ::testUri)

        assertTrue(imported.warnings.any { it.contains("timebase", ignoreCase = true) })
        assertTrue(imported.warnings.any { it.contains("30 fps") })
    }

    @Test
    fun otioFutureFixtureIsRejectedBeforeParsingTracks() {
        val imported = engine.importFromOtio(fixture("otio-future.otio"), ::testUri)

        assertTrue(imported.schemaTooNew)
        assertEquals(2, imported.schemaVersion)
        assertTrue(imported.tracks.isEmpty())
        assertTrue(imported.warnings.single().contains("nothing was imported"))
    }

    @Test
    fun futureClearCutOtioMetadataIsRejectedWithItsVersionCode() {
        val future = JSONObject(fixture("otio-0.15-supported.otio")).apply {
            getJSONObject("metadata").put("clearcut_otio_schema_version", "0.17")
        }

        val imported = engine.importFromOtio(future.toString(), ::testUri)

        assertTrue(imported.schemaTooNew)
        assertEquals(17, imported.schemaVersion)
        assertTrue(imported.warnings.single().contains("0.17"))
        assertTrue(imported.warnings.single().contains("nothing was imported"))
    }

    @Test
    fun fcpxmlFixturePreservesMissingMediaAndTransitionWarnings() {
        val imported = engine.importFromFcpxml(fixture("fcpxml-lossy.fcpxml"), ::testUri)

        assertEquals(1, imported.tracks.single().clips.size)
        assertEquals("file:///media/opening.mp4", imported.tracks.single().clips.single().sourceUri.toString())
        assertEquals(listOf("missing-asset"), imported.unresolvedMediaUris)
        assertTrue(imported.warnings.any { it.contains("transition", ignoreCase = true) })
        assertTrue(imported.warnings.any { it.contains("no resolvable media reference") })
    }

    @Test
    fun edlFixturePreservesTimingAndDroppedEffectReport() {
        val imported = engine.importFromEdl(
            edl = fixture("edl-lossy.edl"),
            timebase = TimelineTimebase(30),
            uriParser = ::testUri,
        )

        val clip = imported.tracks.single().clips.single()
        assertEquals("file:///media/source.mp4", clip.sourceUri.toString())
        assertEquals(TransitionType.DISSOLVE, clip.headTransition?.type)
        assertEquals(100L, clip.headTransition?.durationMs)
        assertEquals(2f, clip.speed, 0.001f)
        assertEquals(1, imported.droppedEffects)
        assertTrue(imported.warnings.any { it.contains("invalid timecode") })
        assertTrue(imported.warnings.any { it.contains("effect comment") })
    }

    @Test
    fun edlImportPreservesCutTimingTransitionSourceCommentAndSpeed() {
        val edl = """
            TITLE: Conform
            FCM: NON-DROP FRAME

            001  REEL     V  D  003 00:00:00:00 00:00:02:00 00:00:05:00 00:00:07:00
            M2   REEL     60.0  00:00:00:00
            * FROM CLIP NAME: file:///media/source.mp4
            * EFFECT NAME: Unsupported grade
        """.trimIndent()

        val imported = engine.importFromEdl(
            edl = edl,
            timebase = TimelineTimebase(30),
            uriParser = ::testUri,
        )

        assertEquals(1, imported.tracks.single().clips.size)
        val clip = imported.tracks.single().clips.single()
        assertEquals("file:///media/source.mp4", clip.sourceUri.toString())
        assertEquals(TransitionType.DISSOLVE, clip.headTransition?.type)
        assertEquals(100L, clip.headTransition?.durationMs)
        assertEquals(2f, clip.speed, 0.001f)
        assertEquals(1, imported.droppedEffects)
        assertTrue(imported.warnings.any { it.contains("effect comment") })
    }

    private fun clip(
        id: String,
        uri: String,
        durationMs: Long,
        timelineStartMs: Long = 0L,
        name: String? = null,
        headTransition: Transition? = null,
        tailTransition: Transition? = null,
        isReversed: Boolean = false,
        effects: List<Effect> = emptyList(),
        compoundClips: List<Clip> = emptyList(),
    ): Clip = Clip(
        id = id,
        sourceUri = testUri(uri),
        sourceDurationMs = durationMs,
        timelineStartMs = timelineStartMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        name = name,
        headTransition = headTransition,
        tailTransition = tailTransition,
        isReversed = isReversed,
        effects = effects,
        isCompound = compoundClips.isNotEmpty(),
        compoundClips = compoundClips,
    )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/interchange/$name")) {
            "Missing interchange fixture: $name"
        }.readText()

    private fun testUri(raw: String): Uri {
        val scheme = raw.substringBefore(':', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
        return TestUri(raw = raw, schemeValue = scheme ?: "", segment = raw.substringAfterLast('/'))
    }

    private fun timeRange(start: Long, duration: Long, rate: Double): JSONObject = JSONObject()
        .put("OTIO_SCHEMA", "TimeRange.1")
        .put("start_time", JSONObject()
            .put("OTIO_SCHEMA", "RationalTime.1")
            .put("value", start)
            .put("rate", rate))
        .put("duration", JSONObject()
            .put("OTIO_SCHEMA", "RationalTime.1")
            .put("value", duration)
            .put("rate", rate))
}
