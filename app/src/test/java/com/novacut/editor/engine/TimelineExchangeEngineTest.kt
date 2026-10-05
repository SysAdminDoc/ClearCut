package com.novacut.editor.engine

import android.net.Uri
import android.net.TestUri
import com.novacut.editor.model.BlendMode
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.SpeedPoint
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
    fun editDecisionJsonRoundTripPreservesPerClipAudioSyncOffset() {
        val audio = clip(
            id = "sync-audio",
            uri = "file:///media/sync.wav",
            durationMs = 4_000L,
        ).copy(audioSyncOffsetMs = -3_000L)
        val json = engine.exportToEditDecisionJson(
            tracks = listOf(Track(type = TrackType.AUDIO, index = 0, clips = listOf(audio))),
            projectName = "Per clip sync",
            timebase = TimelineTimebase(30),
        )

        val exportedClip = JSONObject(json)
            .getJSONArray("tracks")
            .getJSONObject(0)
            .getJSONArray("clips")
            .getJSONObject(0)
        assertEquals(-3_000L, exportedClip.getLong("audioSyncOffsetMs"))

        val imported = engine.importFromEditDecisionJson(json, ::testUri)
        assertTrue(imported.warnings.isEmpty())
        assertEquals(-3_000L, imported.tracks.single().clips.single().audioSyncOffsetMs)
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

    @Test
    fun edlExportAtNtsc2997WritesDropFrameTimecodeByteForByte() {
        val edl = engine.exportToEdl(conformTracks(), "Conform Test", TimelineTimebase.NTSC_29_97)

        // 65 s of 29.97 is frame 1948. Non-drop would print 00:01:04:28; drop-frame keeps the clock.
        val expected = listOf(
            "TITLE: Conform Test",
            "FCM: DROP FRAME",
            "",
            "001  VID20001 V     C        00:00:00;00 00:00:10;00 00:00:00;00 00:00:05;00",
            "M2   VID20001       060.0                00:00:00;00",
            "* FROM CLIP NAME: VID_20260105_101500.mp4",
            "",
            "002  SCORE    A     C        00:00:00;00 00:01:05;00 00:00:00;00 00:01:05;00",
            "* FROM CLIP NAME: score.m4a",
            "",
            "003  VID20002 V     D    015 00:00:01;00 00:01:01;00 00:00:05;00 00:01:05;00",
            "* FROM CLIP NAME: VID_20260105_101730.mp4",
            "* EFFECT NAME: Brightness",
            "",
        ).joinToString("\n", postfix = "\n")
        assertEquals(expected, edl)
    }

    @Test
    fun edlExportAt25WritesNonDropTimecodeByteForByte() {
        val edl = engine.exportToEdl(conformTracks(), "Conform Test", TimelineTimebase(25))

        val expected = listOf(
            "TITLE: Conform Test",
            "FCM: NON-DROP FRAME",
            "",
            "001  VID20001 V     C        00:00:00:00 00:00:10:00 00:00:00:00 00:00:05:00",
            "M2   VID20001       050.0                00:00:00:00",
            "* FROM CLIP NAME: VID_20260105_101500.mp4",
            "",
            "002  SCORE    A     C        00:00:00:00 00:01:05:00 00:00:00:00 00:01:05:00",
            "* FROM CLIP NAME: score.m4a",
            "",
            "003  VID20002 V     D    013 00:00:01:00 00:01:01:00 00:00:05:00 00:01:05:00",
            "* FROM CLIP NAME: VID_20260105_101730.mp4",
            "* EFFECT NAME: Brightness",
            "",
        ).joinToString("\n", postfix = "\n")
        assertEquals(expected, edl)
    }

    @Test
    fun edlExportPicksTheFrameCodeModeFromTheTimebase() {
        val at5994 = engine.exportToEdl(conformTracks(), "Rates", TimelineTimebase.NTSC_59_94)
        assertTrue(at5994.lines().contains("FCM: DROP FRAME"))
        assertTrue(at5994.lines().any { it.startsWith("003 ") && it.endsWith("00:00:05;00 00:01:05;00") })

        val at24 = engine.exportToEdl(conformTracks(), "Rates", TimelineTimebase(24))
        assertTrue(at24.lines().contains("FCM: NON-DROP FRAME"))
        assertFalse(at24.contains(';'))

        // 23.976 has no drop-frame form: timecode counts 24 frames a second and runs behind the clock.
        val at23976 = engine.exportToEdl(conformTracks(), "Rates", TimelineTimebase.NTSC_23_976)
        assertTrue(at23976.lines().contains("FCM: NON-DROP FRAME"))
        assertTrue(at23976.lines().any { it.startsWith("003 ") && it.endsWith("00:00:05:00 00:01:04:22") })
    }

    @Test
    fun dropFrameTimecodeSkipsTheDroppedNumbersAtEachMinute() {
        assertEquals("00:00:59;29", EdlTimecode.format(1_799L, 30, dropFrame = true))
        assertEquals("00:01:00;02", EdlTimecode.format(1_800L, 30, dropFrame = true))
        assertEquals("00:09:59;29", EdlTimecode.format(17_981L, 30, dropFrame = true))
        assertEquals("00:10:00;00", EdlTimecode.format(17_982L, 30, dropFrame = true))
        assertEquals("01:00:00;00", EdlTimecode.format(107_892L, 30, dropFrame = true))
        assertEquals("00:01:00;04", EdlTimecode.format(3_600L, 60, dropFrame = true))
        assertEquals("00:10:00;00", EdlTimecode.format(35_964L, 60, dropFrame = true))
        assertEquals("01:00:00;00", EdlTimecode.format(215_784L, 60, dropFrame = true))
        assertEquals("00:01:00:00", EdlTimecode.format(1_800L, 30, dropFrame = false))
        // Drop-frame is meaningless at other rates, so it falls back to non-drop.
        assertEquals("00:01:00:00", EdlTimecode.format(1_500L, 25, dropFrame = true))

        for (fps in listOf(30, 60)) {
            for (frame in 0L..(fps * 1_300L) step 7L) {
                val text = EdlTimecode.format(frame, fps, dropFrame = true)
                val fields = checkNotNull(EdlTimecode.parse(text)) { text }
                assertEquals(text, frame, EdlTimecode.toFrame(fields, fps, dropFrame = true))
            }
        }
        assertEquals(null, EdlTimecode.toFrame(checkNotNull(EdlTimecode.parse("00:01:00;00")), 30, true))
        assertEquals(null, EdlTimecode.toFrame(checkNotNull(EdlTimecode.parse("00:01:00;03")), 60, true))
        assertEquals(17_982L, EdlTimecode.toFrame(checkNotNull(EdlTimecode.parse("00:10:00;00")), 30, true))
    }

    @Test
    fun edlImportReadsDropFrameInEverySeparatorDialect() {
        fun importedStart(header: String, recordIn: String): Long? {
            val edl = "TITLE: Dialect\n$header\n\n" +
                "001  REEL     V     C        $recordIn 00:01:10;00 $recordIn 00:01:10;00\n"
            return engine.importFromEdl(edl, TimelineTimebase(30), ::testUri)
                .tracks.singleOrNull()?.clips?.single()?.timelineStartMs
        }

        // 00:01:00;02 is frame 1800 at 29.97, which is 60.06 s.
        for (recordIn in listOf("00:01:00;02", "00:01:00,02", "00:01:00.02", "00;01;00;02")) {
            assertEquals(recordIn, 60_060L, importedStart("FCM: NON-DROP FRAME", recordIn))
        }
        assertEquals(60_060L, importedStart("FCM: DROP FRAME", "00:01:00:02"))

        val nonDrop = engine.importFromEdl(
            "FCM: NON-DROP FRAME\n001  REEL     V     C        00:01:00:02 00:01:10:00 00:01:00:02 00:01:10:00\n",
            TimelineTimebase(30),
            ::testUri,
        )
        assertEquals(60_067L, nonDrop.tracks.single().clips.single().timelineStartMs)

        val dropped = engine.importFromEdl(
            "FCM: DROP FRAME\n001  REEL     V     C        00:01:00;00 00:01:10;00 00:01:00;00 00:01:10;00\n",
            TimelineTimebase(30),
            ::testUri,
        )
        assertTrue(dropped.tracks.isEmpty())
        assertTrue(dropped.warnings.any { it.contains("invalid timecode") })

        // A frame field past 29 can only be 59.94 drop-frame: 3,645 nominal minus 4 dropped is frame 3,641.
        val at5994 = engine.importFromEdl(
            "001  REEL     V     C        00:01:00;45 00:01:10;00 00:01:00;45 00:01:10;00\n",
            TimelineTimebase(30),
            ::testUri,
        )
        assertEquals(60_744L, at5994.tracks.single().clips.single().timelineStartMs)
    }

    @Test
    fun edlExportFromNtscRoundTripsThroughTheReader() {
        val edl = engine.exportToEdl(conformTracks(), "Round Trip", TimelineTimebase.NTSC_29_97)

        val imported = engine.importFromEdl(edl, TimelineTimebase(30), ::testUri)

        val video = imported.tracks.single { it.type == TrackType.VIDEO }.clips
        assertEquals(2, video.size)
        val second = video[1]
        assertEquals("file:///VID_20260105_101730.mp4", second.sourceUri.toString())
        assertTrue(kotlin.math.abs(second.timelineStartMs - 5_000L) <= 34L)
        assertTrue(kotlin.math.abs(second.trimEndMs - 61_000L) <= 34L)
        assertEquals(2f, video[0].speed, 0.001f)
        val audio = imported.tracks.single { it.type == TrackType.AUDIO }.clips.single()
        assertTrue(kotlin.math.abs(audio.trimEndMs - 65_000L) <= 34L)
    }

    @Test
    fun edlReelsStayWithinEightCharactersAndNeverShareASource() {
        val sources = listOf(
            "file:///dcim/VID_20260105_101500.mp4",
            "file:///dcim/VID_20260105_101730.mp4",
            "file:///dcim/VID_20260105_102000.mp4",
            "file:///dcim/AX.mp4",
            "file:///dcim/Beach.mp4",
            "file:///dcim/___.mp4",
        )
        val track = Track(
            type = TrackType.VIDEO,
            index = 0,
            clips = sources.mapIndexed { i, uri -> clip("c$i", uri, 1_000L, timelineStartMs = i * 1_000L) },
        )

        val edl = engine.exportToEdl(listOf(track), "Reels", TimelineTimebase(30))

        val reels = edl.lines().filter { it.matches(Regex("^\\d{3}  .*")) }.map { it.split(Regex("\\s+"))[1] }
        assertEquals(listOf("VID20001", "VID20002", "VID20003", "AX001", "BEACH", "AX"), reels)
        assertTrue(reels.all { it.length <= 8 })
    }

    @Test
    fun numberedReelsKeepEightCharactersPastTheThousandthCollision() {
        val track = Track(
            type = TrackType.VIDEO,
            index = 0,
            clips = (1..1_001).map { i ->
                clip("c$i", "file:///dcim/VID_20260105_1${"%04d".format(i)}.mp4", 1_000L, timelineStartMs = i * 1_000L)
            },
        )

        val edl = engine.exportToEdl(listOf(track), "Reels", TimelineTimebase(30))

        val reels = edl.lines().filter { it.matches(Regex("^\\d{3,}  .*")) }.map { it.split(Regex("\\s+"))[1] }
        assertEquals(1_001, reels.size)
        assertEquals(1_001, reels.toSet().size)
        assertTrue(reels.filter { it.length > 8 }.toString(), reels.all { it.length <= 8 })
        assertEquals(listOf("VID20999", "VID21000", "VID21001"), reels.takeLast(3))
    }

    @Test
    fun anAudioClipPulledBeforeZeroStartsItsSourceLaterToStayInSync() {
        // Pulled wholly before zero, so it has no event at all.
        val gone = clip("gone", "file:///gone.m4a", 400L).copy(audioSyncOffsetMs = -500L)
        // Sits at 400 ms but plays 900 ms early: -500 ms to 1,500 ms on the timeline.
        val score = clip("score", "file:///score.m4a", 2_000L, timelineStartMs = 400L).copy(audioSyncOffsetMs = -900L)
        val tracks = listOf(Track(type = TrackType.AUDIO, index = 0, clips = listOf(gone, score)))

        val edl = engine.exportToEdl(tracks, "Sync", TimelineTimebase(30))

        // Half a second of source sits before the timeline start, so the event begins 15 frames into it.
        val events = edl.lines().filter { it.matches(Regex("^\\d{3}  .*")) }
        assertEquals(listOf("001  SCORE    A     C        00:00:00:15 00:00:02:00 00:00:00:00 00:00:01:15"), events)
    }

    @Test
    fun aReversedClipPulledBeforeZeroDropsTheTailOfItsSource() {
        // Reversed, the last half second of source is what would have played before zero.
        val score = clip("score", "file:///score.m4a", 2_000L, timelineStartMs = 400L, isReversed = true)
            .copy(audioSyncOffsetMs = -900L)
        val tracks = listOf(Track(type = TrackType.AUDIO, index = 0, clips = listOf(score)))

        val edl = engine.exportToEdl(tracks, "Sync", TimelineTimebase(30))

        val events = edl.lines().filter { it.matches(Regex("^\\d{3}  .*")) }
        assertEquals(listOf("001  SCORE    A     C        00:00:00:00 00:00:01:15 00:00:00:00 00:00:01:15"), events)
    }

    @Test
    fun aSpeedRampPulledBeforeZeroSkipsTheSourceItsRampActuallyPlayed() {
        // Starts at normal speed and ramps to 4x, so its average is far above the speed it
        // plays the first half second at.
        val ramp = clip("ramp", "file:///ramp.m4a", 4_000L, timelineStartMs = 400L).copy(
            audioSyncOffsetMs = -900L,
            speedCurve = SpeedCurve(listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 4f))),
        )
        val tracks = listOf(Track(type = TrackType.AUDIO, index = 0, clips = listOf(ramp)))

        val edl = engine.exportToEdl(tracks, "Ramp", TimelineTimebase(30))

        val sourceInFrames = Math.round(ramp.timelineOffsetToSourceMs(500L) * 30 / 1000.0)
        assertTrue("the ramp plays about 1x at first: $sourceInFrames frames", sourceInFrames in 15L..18L)
        val event = edl.lines().single { it.matches(Regex("^\\d{3}  .*")) }
        assertEquals(EdlTimecode.format(sourceInFrames, 30, false), event.split(Regex(" +"))[4])
    }

    @Test
    fun timecodeShapedTextOutsideTheEventsNeverSetsTheFrameRate() {
        val edl = "TITLE: Party 12.31.20.45\nFCM: DROP FRAME\n* NOTE 01:02:03:59\n\n" +
            "001  REEL     V     C        00:01:00;02 00:01:10;00 00:01:00;02 00:01:10;00\n" +
            "* FROM CLIP NAME: 00.00.00.45.mp4\n"

        val imported = engine.importFromEdl(edl, TimelineTimebase(30), ::testUri)

        // Read at 29.97: frame 1800 is 60.06 s. Read at 59.94 it would be frame 3,598, about 60.03 s.
        assertEquals(60_060L, imported.tracks.single().clips.single().timelineStartMs)
    }

    private fun conformTracks(): List<Track> {
        val fast = Clip(
            id = "fast",
            sourceUri = testUri("file:///VID_20260105_101500.mp4"),
            sourceDurationMs = 20_000L,
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            speed = 2f,
        )
        val graded = Clip(
            id = "graded",
            sourceUri = testUri("file:///VID_20260105_101730.mp4"),
            sourceDurationMs = 90_000L,
            timelineStartMs = 5_000L,
            trimStartMs = 1_000L,
            trimEndMs = 61_000L,
            headTransition = Transition(type = TransitionType.DISSOLVE, durationMs = 500L),
            effects = listOf(Effect(type = EffectType.BRIGHTNESS, params = mapOf("value" to 0.25f))),
        )
        return listOf(
            Track(type = TrackType.VIDEO, index = 0, clips = listOf(fast, graded)),
            Track(type = TrackType.AUDIO, index = 1, clips = listOf(clip("score", "file:///score.m4a", 65_000L))),
            // CMX 3600 carries one picture track; this one must not leak into the list.
            Track(type = TrackType.VIDEO, index = 2, clips = listOf(clip("overlay", "file:///overlay.mp4", 2_000L))),
        )
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
