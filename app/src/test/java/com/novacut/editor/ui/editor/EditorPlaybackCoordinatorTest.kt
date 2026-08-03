package com.novacut.editor.ui.editor

import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPlaybackCoordinatorTest {

    @Test
    fun playerEventsAreProjectedAndEndStopsAtTheTimelineDuration() = runBlocking {
        val port = FakePlaybackPort()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            port = port,
        )

        coordinator.start(this, callbacks(
            events = events,
            snapshot = { snapshot(totalDurationMs = 5_000L) },
        ))
        port.emitPlaying(true)
        port.emitPlayWhenReady(true)
        port.emitEnded()
        coordinator.stop()

        assertEquals(listOf("playing:true", "requested:true", "ended:5000"), events)
        assertEquals(1, port.pauseCount)
        assertFalse(port.listenerAttached)
    }

    @Test
    fun stalledPlaybackIsRestartedThenReportsAStartFailure() = runBlocking {
        val port = FakePlaybackPort()
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            port = port,
        )

        coordinator.start(this, callbacks(
            events = events,
            snapshot = { snapshot(playheadMs = 1_000L, totalDurationMs = 5_000L) },
        ))
        coordinator.playFromTimelinePosition(1_000L, restartSession = false)
        yield()
        coordinator.stop()

        assertEquals(2, port.playRequests)
        assertEquals(1, port.pauseCount)
        assertEquals(listOf("playing:false", "requested:false", "start-failed"), events)
    }

    @Test
    fun frameSyncCalculatesSmoothScrollAndStopsWithTheSession() = runBlocking {
        val port = FakePlaybackPort().apply {
            playing = true
            positionMs = 8_000L
        }
        val frames = mutableListOf<EditorPlaybackCoordinator.PlaybackFrame>()
        val coordinator = EditorPlaybackCoordinator(
            port = port,
            wait = {},
            frameWait = { delay(1L) },
            frameIntervalMs = 1L,
        )
        val callbacks = callbacks(
            events = mutableListOf(),
            snapshot = {
                snapshot(
                    scrollOffsetMs = 0L,
                    zoomLevel = 1f,
                    timelineWidthPx = 1_000f,
                    maxTimelineScrollOffsetMs = 10_000L,
                )
            },
            onFrame = { frames += it },
        )

        coordinator.start(this, callbacks)
        delay(12L)
        coordinator.stop()

        assertTrue(frames.isNotEmpty())
        assertEquals(8_000L, frames.last().positionMs)
        assertTrue(frames.last().scrollOffsetMs > 0L)
        assertFalse(port.listenerAttached)
    }

    private fun coordinator(
        port: FakePlaybackPort,
    ) = EditorPlaybackCoordinator(
        port = port,
        wait = {},
        frameWait = { delay(60_000L) },
        playbackStartRecoveryDelayMs = 1L,
        playbackStartFailureDelayMs = 1L,
        surfaceRecoveryDelayMs = 1L,
    )

    private fun callbacks(
        events: MutableList<String>,
        snapshot: () -> EditorPlaybackCoordinator.PlaybackSnapshot,
        onFrame: (EditorPlaybackCoordinator.PlaybackFrame) -> Unit = {},
    ) = EditorPlaybackCoordinator.Callbacks(
        snapshot = snapshot,
        onPlayingChanged = { events += "playing:$it" },
        onPlaybackRequestedChanged = { events += "requested:$it" },
        onPlaybackEnded = { events += "ended:$it" },
        onSurfaceRecoveryPosition = { events += "surface:$it" },
        onPlaybackStartFailed = { events += "start-failed" },
        onUnrecoverableError = { events += "error" },
        onFrame = onFrame,
    )

    private fun snapshot(
        playheadMs: Long = 0L,
        totalDurationMs: Long = 10_000L,
        scrollOffsetMs: Long = 0L,
        zoomLevel: Float = 1f,
        timelineWidthPx: Float = 0f,
        maxTimelineScrollOffsetMs: Long = 10_000L,
        isPlaybackRequested: Boolean = true,
    ) = EditorPlaybackCoordinator.PlaybackSnapshot(
        playheadMs = playheadMs,
        totalDurationMs = totalDurationMs,
        scrollOffsetMs = scrollOffsetMs,
        zoomLevel = zoomLevel,
        timelineWidthPx = timelineWidthPx,
        maxTimelineScrollOffsetMs = maxTimelineScrollOffsetMs,
        isPlaybackRequested = isPlaybackRequested,
    )

    private class FakePlaybackPort : EditorPlaybackCoordinator.PlaybackPort {
        var listenerAttached = false
        var playing = false
        var requested = false
        var ended = false
        var positionMs = 0L
        var playRequests = 0
        var pauseCount = 0
        var scrubbing = false
        var loopingEnabled = false
        private var listener: Player.Listener? = null

        override fun setPlayerListener(listener: Player.Listener) {
            this.listener = listener
            listenerAttached = true
        }

        override fun removePlayerListener() {
            listener = null
            listenerAttached = false
        }

        override fun isPlaying(): Boolean = playing

        override fun getAbsolutePositionMs(): Long = positionMs

        override fun playFromTimelinePosition(positionMs: Long, restartSession: Boolean) {
            this.positionMs = positionMs
            requested = true
            ended = false
            playRequests++
        }

        override fun pause() {
            playing = false
            requested = false
            pauseCount++
        }

        override fun isPlaybackRequested(): Boolean = requested

        override fun isPlaybackEnded(): Boolean = ended

        override fun seekTo(positionMs: Long) {
            this.positionMs = positionMs
        }

        override fun setScrubbingMode(enabled: Boolean) {
            scrubbing = enabled
        }

        override fun setLooping(enabled: Boolean) {
            loopingEnabled = enabled
        }

        fun emitPlaying(playing: Boolean) {
            this.playing = playing
            listener?.onIsPlayingChanged(playing)
        }

        fun emitPlayWhenReady(requested: Boolean) {
            this.requested = requested
            listener?.onPlayWhenReadyChanged(requested, 0)
        }

        fun emitEnded() {
            ended = true
            listener?.onPlaybackStateChanged(Player.STATE_ENDED)
        }
    }
}
