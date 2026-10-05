package com.novacut.editor.engine

import android.content.Context
import androidx.media3.common.ColorInfo
import androidx.media3.common.DebugViewProvider
import androidx.media3.common.VideoGraph
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executor

/**
 * Records when a graph built by [delegate] is told one of its inputs has ended.
 *
 * Media3 1.11's DefaultVideoCompositor refuses frames on an input for good once it
 * has ended, and neither a seek nor a flush clears that. A preview that reached the
 * end of a sequence (playing through, looping, or opening with the playhead parked
 * at the end) failed on its next frame (issue #54). [inputEnded] tells the owner the
 * player has to be replaced before it is asked to render again. It flips on the
 * playback thread.
 */
@UnstableApi
internal class EndOfInputWatchingVideoGraphFactory(
    private val delegate: VideoGraph.Factory,
) : VideoGraph.Factory by delegate {

    @Volatile
    var inputEnded = false

    override fun create(
        context: Context,
        outputColorInfo: ColorInfo,
        debugViewProvider: DebugViewProvider,
        listener: VideoGraph.Listener,
        listenerExecutor: Executor,
        initialTimestampOffsetUs: Long,
        renderFramesAutomatically: Boolean,
    ): VideoGraph {
        val graph = delegate.create(
            context,
            outputColorInfo,
            debugViewProvider,
            listener,
            listenerExecutor,
            initialTimestampOffsetUs,
            renderFramesAutomatically,
        )
        return object : VideoGraph by graph {
            override fun signalEndOfInput(inputIndex: Int) {
                inputEnded = true
                graph.signalEndOfInput(inputIndex)
            }
        }
    }
}
