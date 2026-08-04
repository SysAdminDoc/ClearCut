package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSidecarEngineTest {

    @Test
    fun classifyTrackOffersSubtitleSidecarsForTextStreams() {
        val track = MetadataSidecarPolicy.classifyTrack(
            trackIndex = 3,
            mimeType = "application/x-subrip; charset=utf-8",
            language = "en",
        )

        requireNotNull(track)
        assertEquals(MetadataSidecarKind.SUBTITLE, track.kind)
        assertEquals("application/x-subrip", track.mimeType)
        assertEquals(setOf(MetadataSidecarFormat.VTT, MetadataSidecarFormat.SRT), track.supportedFormats)
        assertEquals("en", track.language)
    }

    @Test
    fun classifyTrackExplainsUnsupportedTelemetryCodecs() {
        val track = MetadataSidecarPolicy.classifyTrack(
            trackIndex = 4,
            mimeType = "application/x-gpmd",
        )

        requireNotNull(track)
        assertEquals(MetadataSidecarKind.GPS, track.kind)
        assertTrue(track.supportedFormats.isEmpty())
        assertTrue(track.unsupportedReason.orEmpty().contains("GPS telemetry"))
    }

    @Test
    fun parseNmeaSupportsValidRmcAndRejectsNoFix() {
        val point = MetadataSidecarPolicy.parseNmeaSentence(
            "\u0024GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A",
            mediaTimeMs = 2_500L,
        )
        requireNotNull(point)
        assertEquals(48.1173, point.latitude, 0.00001)
        assertEquals(11.5166667, point.longitude, 0.00001)
        assertEquals(2_500L, point.mediaTimeMs)

        assertEquals(
            null,
            MetadataSidecarPolicy.parseNmeaSentence(
                "\u0024GPGGA,123519,4807.038,N,01131.000,E,0,00,0.0,0.0,M,0.0,M,,",
                mediaTimeMs = 1_000L,
            )
        )
    }

    @Test
    fun locationFormattingProducesPortableCsvAndGpx() {
        val points = listOf(
            GpsPoint(48.1173, 11.5166667, mediaTimeMs = 2_500L),
            GpsPoint(-33.8568, 151.2153),
        )

        val csv = MetadataSidecarPolicy.formatCsv(points)
        assertTrue(csv.startsWith("media_time_ms,latitude,longitude\n"))
        assertTrue(csv.contains("2500,48.1173000,11.5166667"))
        assertTrue(csv.contains(",-33.8568000,151.2153000"))

        val gpx = MetadataSidecarPolicy.formatGpx(points)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"48.1173000\" lon=\"11.5166667\""))
        assertTrue(gpx.contains("lat=\"-33.8568000\" lon=\"151.2153000\""))
        assertFalse(gpx.contains("content://"))
    }

    @Test
    fun containerLocationIsExportableAsTwoLocalFormats() {
        val track = MetadataSidecarPolicy.containerLocation(GpsPoint(1.25, 2.5))

        assertEquals(-1, track.trackIndex)
        assertEquals(MetadataSidecarKind.GPS, track.kind)
        assertEquals(setOf(MetadataSidecarFormat.GPX, MetadataSidecarFormat.CSV), track.supportedFormats)
        assertEquals(GpsPoint(1.25, 2.5), track.location)
    }
}
