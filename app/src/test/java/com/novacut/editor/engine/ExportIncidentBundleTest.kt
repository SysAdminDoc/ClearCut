package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExportIncidentBundleTest {

    @Test
    fun roundTripSerializationPreservesAllFields() {
        val bundle = testBundle()
        val json = ExportIncidentStore.toJson(bundle)
        val restored = ExportIncidentStore.fromJson(json)

        assertNotNull(restored)
        assertEquals(bundle.id, restored!!.id)
        assertEquals(bundle.appVersion, restored.appVersion)
        assertEquals(bundle.deviceModel, restored.deviceModel)
        assertEquals(bundle.androidSdk, restored.androidSdk)
        assertEquals(bundle.projectId, restored.projectId)
        assertEquals(bundle.projectName, restored.projectName)
        assertEquals(bundle.failedPhase, restored.failedPhase)
        assertEquals(bundle.errorClass, restored.errorClass)
        assertEquals(bundle.errorMessage, restored.errorMessage)
        assertEquals(bundle.encoderPath, restored.encoderPath)
        assertEquals(bundle.codecLabel, restored.codecLabel)
        assertEquals(bundle.resolutionLabel, restored.resolutionLabel)
        assertEquals(bundle.frameRate, restored.frameRate)
        assertEquals(bundle.exportAudioOnly, restored.exportAudioOnly)
        assertEquals(bundle.hdrRequested, restored.hdrRequested)
        assertEquals(bundle.streamCopyAttempted, restored.streamCopyAttempted)
        assertEquals(bundle.timelineDurationMs, restored.timelineDurationMs)
        assertEquals(bundle.elapsedMs, restored.elapsedMs)
        assertEquals(bundle.progressSamples.size, restored.progressSamples.size)
        assertEquals(bundle.mediaWarningCount, restored.mediaWarningCount)
        assertEquals(bundle.mediaBlockingCount, restored.mediaBlockingCount)
        assertEquals(bundle.mediaHealthSummary, restored.mediaHealthSummary)
        assertEquals(bundle.timestampEpochMs, restored.timestampEpochMs)
    }

    @Test
    fun fromJsonReturnsNullForMissingId() {
        val json = JSONObject().apply {
            put("appVersion", "v1.0")
            put("projectId", "p1")
        }
        assertNull(ExportIncidentStore.fromJson(json))
    }

    @Test
    fun fromJsonToleratesMissingOptionalFields() {
        val json = JSONObject().apply {
            put("id", "abc123")
            put("projectId", "p1")
        }
        val result = ExportIncidentStore.fromJson(json)
        assertNotNull(result)
        assertEquals("abc123", result!!.id)
        assertEquals("unknown", result.appVersion)
        assertEquals(0, result.progressSamples.size)
        assertNull(result.mediaHealthSummary)
    }

    @Test
    fun storeWritesAndReadsIncidents() {
        val dir = Files.createTempDirectory("incident-test-").toFile()
        try {
            val store = ExportIncidentStore(dir)
            val bundle = testBundle()
            store.save(bundle)

            val all = store.readAll()
            assertEquals(1, all.size)
            assertEquals(bundle.id, all[0].id)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun storePrunesOldIncidentsBeyondLimit() {
        val dir = Files.createTempDirectory("incident-prune-").toFile()
        try {
            val store = ExportIncidentStore(dir)
            repeat(15) { i ->
                store.save(testBundle(id = "incident-$i", projectName = "Project $i"))
            }
            val all = store.readAll()
            assertTrue("Expected at most 10 incidents, got ${all.size}", all.size <= 10)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildDiagnosticJsonReturnsNullWhenEmpty() {
        val dir = Files.createTempDirectory("incident-empty-").toFile()
        try {
            val store = ExportIncidentStore(dir)
            assertNull(store.buildDiagnosticJson())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildDiagnosticJsonUsesBundlePseudonymsAndOmitsFreeFormFields() {
        val dir = Files.createTempDirectory("incident-diag-").toFile()
        try {
            val store = ExportIncidentStore(dir)
            val secret = "SECRET-PROJECT-JANE-TRANSCRIPT"
            store.save(
                testBundle(
                    id = "same-a-001",
                    projectId = "private-project-id",
                    projectName = secret,
                    errorMessage = "$secret at /storage/emulated/0/Movies/private.mov",
                    healthSummary = "$secret content://media/video/42",
                )
            )
            store.save(
                testBundle(
                    id = "same-b-002",
                    projectId = "private-project-id",
                    projectName = "Another private name",
                )
            )
            store.save(
                testBundle(
                    id = "other-project",
                    projectId = "different-private-id",
                    projectName = "Different private project",
                )
            )
            val json = requireNotNull(store.buildDiagnosticJson())
            val array = org.json.JSONArray(json)
            assertEquals(3, array.length())

            val byId = (0 until array.length())
                .map { array.getJSONObject(it) }
                .associateBy { it.getString("id") }
            val first = requireNotNull(byId["same-a-001"])
            val second = requireNotNull(byId["same-b-002"])
            val other = requireNotNull(byId["other-project"])

            assertEquals(first.getString("projectPseudonym"), second.getString("projectPseudonym"))
            assertFalse(first.getString("projectPseudonym") == other.getString("projectPseudonym"))
            for (incident in byId.values) {
                assertFalse(incident.has("projectId"))
                assertFalse(incident.has("projectName"))
                assertFalse(incident.has("errorMessage"))
                assertFalse(incident.has("mediaHealthSummary"))
                assertTrue(incident.getString("projectPseudonym").matches(Regex("project-\\d+")))
                assertEquals("encoder", incident.getString("failedPhase"))
                assertEquals(1, incident.getInt("mediaWarningCount"))
                assertEquals(0, incident.getInt("mediaBlockingCount"))
            }
            assertFalse(json.contains(secret))
            assertFalse(json.contains("private-project-id"))
            assertFalse(json.contains("different-private-id"))
            assertFalse(json.contains("/storage/"))
            assertFalse(json.contains("content://"))

            val local = store.readAll().associateBy { it.id }
            assertEquals(secret, local["same-a-001"]?.projectName)
            assertTrue(local["same-a-001"]?.errorMessage.orEmpty().contains(secret))
            assertTrue(local["same-a-001"]?.mediaHealthSummary.orEmpty().contains(secret))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun clearRemovesAllIncidents() {
        val dir = Files.createTempDirectory("incident-clear-").toFile()
        try {
            val store = ExportIncidentStore(dir)
            store.save(testBundle())
            assertEquals(1, store.readAll().size)
            store.clear()
            assertEquals(0, store.readAll().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun progressSamplesBoundedToLimit() {
        val largeSamples = (0..100).map { it / 100f }
        val bundle = ExportIncidentBuilder.build(
            appVersion = "v1.0",
            projectId = "p1",
            projectName = "Test",
            failedPhase = "encoder",
            error = RuntimeException("test"),
            errorMessage = "test error",
            codecLabel = "H.264",
            resolutionLabel = "1080p",
            frameRate = 30,
            exportAudioOnly = false,
            hdrRequested = false,
            streamCopyAttempted = false,
            timelineDurationMs = 60000,
            startedAtMs = 1000,
            finishedAtMs = 2000,
            progressSamples = largeSamples
        )
        assertTrue(
            "Expected at most 40 samples, got ${bundle.progressSamples.size}",
            bundle.progressSamples.size <= 40
        )
    }

    @Test
    fun errorMessageIsRedacted() {
        val bundle = ExportIncidentBuilder.build(
            appVersion = "v1.0",
            projectId = "p1",
            projectName = "Test",
            failedPhase = "encoder",
            error = RuntimeException("Failed reading content://media/video/123"),
            errorMessage = null,
            codecLabel = "H.264",
            resolutionLabel = "1080p",
            frameRate = 30,
            exportAudioOnly = false,
            hdrRequested = false,
            streamCopyAttempted = false,
            timelineDurationMs = 60000,
            startedAtMs = 1000,
            finishedAtMs = 2000
        )
        assertTrue(
            "Error message should be redacted but was: ${bundle.errorMessage}",
            !bundle.errorMessage.contains("content://")
        )
        assertTrue(bundle.errorMessage.contains("<redacted>"))
    }

    @Test
    fun nullHealthSummaryRoundTrips() {
        val bundle = testBundle(healthSummary = null)
        val json = ExportIncidentStore.toJson(bundle)
        val restored = ExportIncidentStore.fromJson(json)
        assertNull(restored!!.mediaHealthSummary)
    }

    private fun testBundle(
        id: String = "test-id-1234",
        projectId: String = "proj-abc",
        projectName: String = "Test Project",
        errorMessage: String = "Encoder failed on frame 42",
        healthSummary: String? = "10 refs, 1 warnings, 0 blocking"
    ) = ExportIncidentBundle(
        id = id,
        appVersion = "v3.74.77",
        deviceModel = "Google Pixel 8",
        androidSdk = 35,
        projectId = projectId,
        projectName = projectName,
        failedPhase = "encoder",
        errorClass = "ExportException",
        errorMessage = errorMessage,
        encoderPath = "hardware: c2.qti.avc.encoder",
        codecLabel = "H.264",
        resolutionLabel = "1080p",
        frameRate = 30,
        exportAudioOnly = false,
        hdrRequested = false,
        streamCopyAttempted = false,
        timelineDurationMs = 120000,
        elapsedMs = 45000,
        progressSamples = listOf(0.1f, 0.3f, 0.5f, 0.7f),
        mediaWarningCount = 1,
        mediaBlockingCount = 0,
        mediaHealthSummary = healthSummary,
        timestampEpochMs = 1718200000000L
    )
}
