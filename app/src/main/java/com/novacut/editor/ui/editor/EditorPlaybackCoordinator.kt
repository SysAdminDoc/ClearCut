package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AppLog
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.novacut.editor.engine.VideoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Owns the editor preview session boundary.
 *
 * The ViewModel supplies state callbacks, while this coordinator owns player
 * listeners, frame polling, start recovery, surface recovery, and player
 * controls. The small port keeps those side effects fake-backed in JVM tests.
 */
@Singleton
class EditorPlaybackCoordinator internal constructor(
    private val port: PlaybackPort,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val frameWait: suspend (Long) -> Unit = wait,
    private val playbackStartRecoveryDelayMs: Long = PLAYBACK_START_RECOVERY_DELAY_MS,
    private val playbackStartFailureDelayMs: Long = PLAYBACK_START_FAILURE_DELAY_MS,
    private val surfaceRecoveryDelayMs: Long = PREVIEW_SURFACE_RECOVERY_DELAY_MS,
    private val frameIntervalMs: Long = PLAYBACK_FRAME_INTERVAL_MS,
) {

    @Inject
    constructor(videoEngine: VideoEngine) : this(PlaybackPort.from(videoEngine))

    data class PlaybackSnapshot(
        val playheadMs: Long,
        val totalDurationMs: Long,
        val scrollOffsetMs: Long,
        val zoomLevel: Float,
        val timelineWidthPx: Float,
        val maxTimelineScrollOffsetMs: Long,
        val isPlaybackRequested: Boolean,
    )

    data class PlaybackFrame(
        val positionMs: Long,
        val scrollOffsetMs: Long,
    )

    data class Callbacks(
        val snapshot: () -> PlaybackSnapshot,
        val onPlayingChanged: (Boolean) -> Unit,
        val onPlaybackRequestedChanged: (Boolean) -> Unit,
        val onPlaybackEnded: (Long) -> Unit,
        val onSurfaceRecoveryPosition: (Long) -> Unit,
        val onPlaybackStartFailed: () -> Unit,
        val onUnrecoverableError: (PlaybackException) -> Unit,
        val onFrame: (PlaybackFrame) -> Unit,
    )

    internal interface PlaybackPort {
        fun setPlayerListener(listener: Player.Listener)
        fun removePlayerListener()
        fun isPlaying(): Boolean
        fun getAbsolutePositionMs(): Long
        fun playFromTimelinePosition(positionMs: Long, restartSession: Boolean)
        fun pause()
        fun isPlaybackRequested(): Boolean
        fun isPlaybackEnded(): Boolean
        fun seekTo(positionMs: Long)
        fun setScrubbingMode(enabled: Boolean)
        fun setLooping(enabled: Boolean)

        companion object {
            fun from(videoEngine: VideoEngine): PlaybackPort = object : PlaybackPort {
                override fun setPlayerListener(listener: Player.Listener) {
                    videoEngine.setPlayerListener(listener)
                }

                override fun removePlayerListener() {
                    videoEngine.removePlayerListener()
                }

                override fun isPlaying(): Boolean = videoEngine.isPlaying()

                override fun getAbsolutePositionMs(): Long = videoEngine.getAbsolutePositionMs()

                override fun playFromTimelinePosition(positionMs: Long, restartSession: Boolean) {
                    videoEngine.playFromTimelinePosition(positionMs, restartSession)
                }

                override fun pause() {
                    videoEngine.pause()
                }

                override fun isPlaybackRequested(): Boolean = videoEngine.isPlaybackRequested()

                override fun isPlaybackEnded(): Boolean = videoEngine.isPlaybackEnded()

                override fun seekTo(positionMs: Long) {
                    videoEngine.seekTo(positionMs)
                }

                override fun setScrubbingMode(enabled: Boolean) {
                    videoEngine.setScrubbingMode(enabled)
                }

                override fun setLooping(enabled: Boolean) {
                    videoEngine.getPlayer().repeatMode = if (enabled) {
                        Player.REPEAT_MODE_ALL
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
                }
            }
        }
    }

    private var callbacks: Callbacks? = null
    private var playbackScope: CoroutineScope? = null
    private var frameSyncJob: Job? = null
    private var playbackStartRecoveryJob: Job? = null
    private var surfaceRecoveryJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            callbacks?.onPlayingChanged?.invoke(playing)
            if (playing) playbackStartRecoveryJob?.cancel()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            callbacks?.onPlaybackRequestedChanged?.invoke(playWhenReady)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            cancelRecoveryJobs()
            port.pause()
            callbacks?.let { active ->
                active.onPlaybackEnded(active.snapshot().totalDurationMs)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    fun start(scope: CoroutineScope, callbacks: Callbacks) {
        stop()
        this.playbackScope = scope
        this.callbacks = callbacks
        port.setPlayerListener(playerListener)
        frameSyncJob = scope.launch {
            while (isActive) {
                frameWait(frameIntervalMs)
                if (!port.isPlaying()) continue
                val active = this@EditorPlaybackCoordinator.callbacks ?: break
                val snapshot = active.snapshot()
                val currentMs = port.getAbsolutePositionMs()
                active.onFrame(
                    PlaybackFrame(
                        positionMs = currentMs,
                        scrollOffsetMs = calculateScrollOffset(snapshot, currentMs),
                    )
                )
            }
        }
    }

    fun stop() {
        frameSyncJob?.cancel()
        frameSyncJob = null
        cancelRecoveryJobs()
        port.setScrubbingMode(false)
        port.removePlayerListener()
        callbacks = null
        playbackScope = null
    }

    fun isPlaybackRequested(): Boolean = port.isPlaybackRequested()

    fun isPlaybackEnded(): Boolean = port.isPlaybackEnded()

    fun playFromTimelinePosition(positionMs: Long, restartSession: Boolean) {
        port.playFromTimelinePosition(positionMs, restartSession)
        armPlaybackStartRecovery(positionMs)
    }

    fun pause() {
        cancelRecoveryJobs()
        port.pause()
    }

    fun seekTo(positionMs: Long) {
        port.seekTo(positionMs)
    }

    fun setScrubbingMode(enabled: Boolean) {
        port.setScrubbingMode(enabled)
    }

    fun setLooping(enabled: Boolean) {
        port.setLooping(enabled)
    }

    private fun handlePlayerError(error: PlaybackException) {
        val active = callbacks ?: return
        cancelRecoveryJobs()
        val beforeFailure = active.snapshot()
        port.pause()
        active.onPlayingChanged(false)
        active.onPlaybackRequestedChanged(false)

        if (!isRecoverablePreviewRuntimeFailure(error)) {
            active.onUnrecoverableError(error)
            return
        }

        val currentPositionMs = beforeFailure.playheadMs
        if (isPreviewStuckPlayerFailure(error) &&
            isAtPreviewTimelineEnd(currentPositionMs, beforeFailure.totalDurationMs)
        ) {
            active.onPlaybackEnded(beforeFailure.totalDurationMs)
            AppLog.i(
                "EditorPlaybackCoordinator",
                "Treating a stuck-player signal at timeline end as normal completion",
            )
            return
        }

        AppLog.w(
            "EditorPlaybackCoordinator",
            "Preview runtime stalled; resetting the player without blaming the clip",
            error,
        )
        if (!beforeFailure.isPlaybackRequested) return

        surfaceRecoveryJob = playbackScope?.launch {
            wait(surfaceRecoveryDelayMs)
            val current = this@EditorPlaybackCoordinator.callbacks ?: return@launch
            val snapshot = current.snapshot()
            val recoveryPositionMs = playbackStartPosition(
                snapshot.playheadMs,
                snapshot.totalDurationMs,
            )
            current.onSurfaceRecoveryPosition(recoveryPositionMs)
            port.playFromTimelinePosition(recoveryPositionMs, restartSession = true)
            armPlaybackStartRecovery(recoveryPositionMs)
        }
    }

    private fun armPlaybackStartRecovery(requestedPositionMs: Long) {
        playbackStartRecoveryJob?.cancel()
        val scope = playbackScope ?: return
        playbackStartRecoveryJob = scope.launch {
            wait(playbackStartRecoveryDelayMs)
            val active = this@EditorPlaybackCoordinator.callbacks ?: return@launch
            if (!port.isPlaybackRequested()) return@launch

            val observedPositionMs = port.getAbsolutePositionMs()
            if (hasPreviewPlaybackAdvanced(requestedPositionMs, observedPositionMs)) {
                return@launch
            }

            val recoveryPositionMs = observedPositionMs
            AppLog.w(
                "EditorPlaybackCoordinator",
                "Playback did not advance after request; resetting at $recoveryPositionMs ms",
            )
            port.playFromTimelinePosition(recoveryPositionMs, restartSession = true)
            wait(playbackStartFailureDelayMs)
            val recoveredPositionMs = port.getAbsolutePositionMs()
            if (port.isPlaybackRequested() &&
                !hasPreviewPlaybackAdvanced(recoveryPositionMs, recoveredPositionMs)
            ) {
                port.pause()
                active.onPlayingChanged(false)
                active.onPlaybackRequestedChanged(false)
                active.onPlaybackStartFailed()
            }
        }
    }

    private fun cancelRecoveryJobs() {
        playbackStartRecoveryJob?.cancel()
        playbackStartRecoveryJob = null
        surfaceRecoveryJob?.cancel()
        surfaceRecoveryJob = null
    }

    private fun calculateScrollOffset(snapshot: PlaybackSnapshot, currentMs: Long): Long {
        val pixelsPerMs = snapshot.zoomLevel * TIMELINE_BASE_SCALE
        var newScroll = snapshot.scrollOffsetMs
        if (snapshot.timelineWidthPx > 0f && pixelsPerMs >= 0.001f) {
            val visibleMs = (snapshot.timelineWidthPx / pixelsPerMs).toLong()
            val playheadRelative = currentMs - newScroll
            if (playheadRelative > visibleMs * 0.8f || playheadRelative < 0) {
                val targetScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                newScroll += ((targetScroll - newScroll) * 0.15f).toLong()
            }
        }
        return newScroll.coerceIn(0L, snapshot.maxTimelineScrollOffsetMs.coerceAtLeast(0L))
    }

    private companion object {
        const val TIMELINE_BASE_SCALE = 0.15f
        const val PLAYBACK_FRAME_INTERVAL_MS = 33L
        const val PLAYBACK_START_RECOVERY_DELAY_MS = 3_000L
        const val PLAYBACK_START_FAILURE_DELAY_MS = 7_000L
        const val PREVIEW_SURFACE_RECOVERY_DELAY_MS = 250L
    }
}
