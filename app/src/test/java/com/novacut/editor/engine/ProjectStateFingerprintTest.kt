package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Project
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.SourceHdrFormat
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProjectStateFingerprintTest {

    @Test
    fun documentFingerprintIgnoresSaveAndDerivedMediaMetadata() {
        val project = project()
        val baseline = state()
        val refreshed = baseline.copy(
            timestamp = 999L,
            playheadMs = 777L,
            mediaAssets = listOf(mediaAsset(lastVerifiedAtEpochMs = 888L)),
            tracks = baseline.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    clip.copy(
                        assetId = "refreshed-asset",
                        proxyUri = FakeUri,
                        sourceColorMetadata = clip.sourceColorMetadata.copy(inspectedAtMs = 555L),
                    )
                })
            },
        )
        val refreshedProject = project.copy(
            updatedAt = 900L,
            durationMs = 1_000L,
            thumbnailUri = "content://thumbnail",
        )

        assertEquals(
            projectStateFingerprint(project, baseline),
            projectStateFingerprint(refreshedProject, refreshed),
        )
    }

    @Test
    fun documentFingerprintChangesForProjectAndTimelineEdits() {
        val project = project()
        val baseline = state()
        val fingerprint = projectStateFingerprint(project, baseline)

        assertNotEquals(fingerprint, projectStateFingerprint(project.copy(name = "Renamed"), baseline))
        assertNotEquals(fingerprint, projectStateFingerprint(project.copy(notes = "Review cut"), baseline))
        assertNotEquals(
            fingerprint,
            projectStateFingerprint(
                project,
                baseline.copy(tracks = baseline.tracks.map { track ->
                    track.copy(clips = track.clips.map { it.copy(trimEndMs = 900L) })
                }),
            ),
        )
    }

    @Test
    fun documentFingerprintCanonicalizesSetAndParameterMapOrder() {
        val project = project()
        val forward = state(
            effectParams = linkedMapOf("amount" to 0.5f, "mix" to 0.8f),
            audioParams = linkedMapOf("low" to -2f, "high" to 1f),
            hdrFormats = linkedSetOf(SourceHdrFormat.HDR10, SourceHdrFormat.HLG),
        )
        val reverse = state(
            effectParams = linkedMapOf("mix" to 0.8f, "amount" to 0.5f),
            audioParams = linkedMapOf("high" to 1f, "low" to -2f),
            hdrFormats = linkedSetOf(SourceHdrFormat.HLG, SourceHdrFormat.HDR10),
        )

        assertEquals(
            projectStateFingerprint(project, forward),
            projectStateFingerprint(project, reverse),
        )
    }

    @Test
    fun persistenceFingerprintIgnoresTimestampButTracksRecoveryContent() {
        val baseline = state()
        val fingerprint = autoSavePersistenceFingerprint(baseline)

        assertEquals(fingerprint, autoSavePersistenceFingerprint(baseline.copy(timestamp = 22L)))
        assertNotEquals(fingerprint, autoSavePersistenceFingerprint(baseline.copy(playheadMs = 300L)))
        assertNotEquals(
            fingerprint,
            autoSavePersistenceFingerprint(baseline.copy(mediaAssets = listOf(mediaAsset()))),
        )
    }

    private fun project() = Project(
        id = "project",
        name = "Cut",
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun state(
        effectParams: Map<String, Float> = mapOf("amount" to 0.5f),
        audioParams: Map<String, Float> = mapOf("low" to -2f),
        hdrFormats: Set<SourceHdrFormat> = setOf(SourceHdrFormat.HDR10),
    ) = AutoSaveState(
        projectId = "project",
        timestamp = 10L,
        playheadMs = 100L,
        tracks = listOf(
            Track(
                id = "video",
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(
                    Clip(
                        id = "clip",
                        assetId = "asset",
                        sourceUri = FakeUri,
                        sourceDurationMs = 1_000L,
                        timelineStartMs = 0L,
                        effects = listOf(
                            Effect(
                                id = "effect",
                                type = EffectType.BRIGHTNESS,
                                params = effectParams,
                            )
                        ),
                        audioEffects = listOf(
                            AudioEffect(
                                id = "audio-effect",
                                type = AudioEffectType.PARAMETRIC_EQ,
                                params = audioParams,
                            )
                        ),
                        sourceColorMetadata = SourceColorMetadata(
                            hdrFormats = hdrFormats,
                            inspectedAtMs = 123L,
                        ),
                    )
                ),
            )
        ),
    )

    private fun mediaAsset(lastVerifiedAtEpochMs: Long = 5L) = ProjectMediaAsset(
        assetId = "asset",
        managedUri = "content://managed",
        originalUri = "content://original",
        displayName = "clip.mp4",
        mediaType = "video",
        mimeType = "video/mp4",
        sizeBytes = 100L,
        durationMs = 1_000L,
        width = 1920,
        height = 1080,
        quickFingerprint = "quick",
        importStatus = "READY",
        lastVerifiedAtEpochMs = lastVerifiedAtEpochMs,
    )
}
