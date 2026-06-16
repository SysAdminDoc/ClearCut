package com.novacut.editor.engine

import android.opengl.GLES20
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.common.util.Size
import com.novacut.editor.model.GlobalTransition
import com.novacut.editor.model.GlobalTransitionType

@UnstableApi
internal class GlobalTransitionEffect(
    private val transitions: List<GlobalTransition>,
    private val clipTimelineStartMs: Long
) : GlEffect {

    override fun toGlShaderProgram(
        context: android.content.Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return GlobalTransitionShaderProgram(transitions, clipTimelineStartMs)
    }

    companion object {
        fun forClip(
            globalTransitions: List<GlobalTransition>,
            clipTimelineStartMs: Long,
            clipTimelineEndMs: Long
        ): GlobalTransitionEffect? {
            val overlapping = globalTransitions.filter { gt ->
                gt.timelineAnchorMs < clipTimelineEndMs && gt.endMs > clipTimelineStartMs
            }
            return if (overlapping.isNotEmpty()) {
                GlobalTransitionEffect(overlapping, clipTimelineStartMs)
            } else null
        }
    }
}

@UnstableApi
private class GlobalTransitionShaderProgram(
    private val transitions: List<GlobalTransition>,
    private val clipTimelineStartMs: Long
) : BaseGlShaderProgram(false, 1) {

    private var programId = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexSamplerLoc = 0
    private var uAlphaLoc = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val vertSrc = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragSrc = """
            #version 100
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexSampler;
            uniform float uAlpha;
            void main() {
                vec4 c = texture2D(uTexSampler, vTexCoord);
                gl_FragColor = vec4(c.rgb * uAlpha, c.a * uAlpha);
            }
        """.trimIndent()

        val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertShader)
        GLES20.glAttachShader(programId, fragShader)
        GLES20.glLinkProgram(programId)

        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uTexSamplerLoc = GLES20.glGetUniformLocation(programId, "uTexSampler")
        uAlphaLoc = GLES20.glGetUniformLocation(programId, "uAlpha")

        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val timeMs = clipTimelineStartMs + presentationTimeUs / 1000L
        var alpha = 1f
        for (gt in transitions) {
            if (timeMs < gt.timelineAnchorMs || timeMs > gt.endMs) continue
            val progress = ((timeMs - gt.timelineAnchorMs).toFloat() / gt.durationMs).coerceIn(0f, 1f)
            alpha *= when (gt.type) {
                GlobalTransitionType.FADE_FROM_BLACK, GlobalTransitionType.FADE_FROM_WHITE -> progress
                GlobalTransitionType.FADE_TO_BLACK, GlobalTransitionType.FADE_TO_WHITE -> 1f - progress
            }
        }

        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexId)
        GLES20.glUniform1i(uTexSamplerLoc, 0)
        GLES20.glUniform1f(uAlphaLoc, alpha)

        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        val buffer = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .position(0) as java.nio.FloatBuffer

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, buffer)
        buffer.position(2)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    override fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
