package com.novacut.editor.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.TextDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 GlEffect that composites a Lottie animation frame over video.
 *
 * Used in the Transformer export pipeline to burn animated titles into video output.
 * The Lottie composition is rendered to a bitmap, uploaded as a GL texture, and
 * alpha-blended over the input video frame on each drawFrame() call.
 *
 * @param lottieEngine Engine instance for rendering frames
 * @param composition Pre-loaded Lottie composition
 * @param overlayStartUs Overlay start time in the video timeline (microseconds)
 * @param overlayDurationUs Overlay duration (microseconds)
 * @param textReplacements Dynamic text substitutions for Lottie text layers
 */
@UnstableApi
class LottieOverlayEffect(
    private val lottieEngine: LottieTemplateEngine,
    private val composition: LottieComposition,
    private val overlayStartUs: Long,
    private val overlayDurationUs: Long,
    private val textReplacements: Map<String, String> = emptyMap()
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LottieOverlayProgram(
            lottieEngine, composition, overlayStartUs, overlayDurationUs, textReplacements, useHdr
        )
    }
}

@UnstableApi
private class LottieOverlayProgram(
    private val lottieEngine: LottieTemplateEngine,
    private val composition: LottieComposition,
    private val overlayStartUs: Long,
    private val overlayDurationUs: Long,
    private val textReplacements: Map<String, String>,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram = 0
    private var vao = 0
    private var vbo = 0
    private var overlayTexId = 0
    private var width = 0
    private var height = 0
    private var lastRenderedFrameMs = -1L

    // Reuse drawable + bitmap across frames for performance
    private val drawable = LottieDrawable().apply {
        this.composition = this@LottieOverlayProgram.composition
        if (textReplacements.isNotEmpty()) {
            val td = TextDelegate(this)
            textReplacements.forEach { (layer, text) -> td.setText(layer, text) }
            setTextDelegate(td)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        if (glProgram == 0) setupGl()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        GLES30.glUseProgram(glProgram)

        // Bind input video as texture 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        val videoLoc = GLES30.glGetUniformLocation(glProgram, "uVideoTex")
        GLES30.glUniform1i(videoLoc, 0)

        // Determine if overlay is visible at this presentation time
        val relativeUs = presentationTimeUs - overlayStartUs
        val isVisible = relativeUs in 0..overlayDurationUs

        val alphaLoc = GLES30.glGetUniformLocation(glProgram, "uOverlayAlpha")

        if (isVisible) {
            val frameTimeMs = (relativeUs / 1000L).coerceAtLeast(0)
            updateOverlayTexture(frameTimeMs)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTexId)
            val overlayLoc = GLES30.glGetUniformLocation(glProgram, "uOverlayTex")
            GLES30.glUniform1i(overlayLoc, 1)
            GLES30.glUniform1f(alphaLoc, 1.0f)
        } else {
            // Bind texture unit 1 to the input video texture as a safe fallback
            // (alpha is 0 so it won't affect output, but avoids undefined texture reads)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
            val overlayLoc = GLES30.glGetUniformLocation(glProgram, "uOverlayTex")
            GLES30.glUniform1i(overlayLoc, 1)
            GLES30.glUniform1f(alphaLoc, 0.0f)
        }

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun updateOverlayTexture(frameTimeMs: Long) {
        // Quantize to ~30fps to avoid re-rendering every microsecond
        val quantizedMs = (frameTimeMs / 33L) * 33L
        if (quantizedMs == lastRenderedFrameMs && overlayTexId != 0) return
        lastRenderedFrameMs = quantizedMs

        val bitmap = lottieEngine.renderFrame(composition, frameTimeMs, width, height, textReplacements)

        if (overlayTexId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            overlayTexId = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bitmap.recycle()
    }

    override fun release() {
        super.release()
        if (glProgram != 0) { GLES30.glDeleteProgram(glProgram); glProgram = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
        if (overlayTexId != 0) { GLES30.glDeleteTextures(1, intArrayOf(overlayTexId), 0); overlayTexId = 0 }
    }

    private fun setupGl() {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAG)
        glProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(glProgram, vs)
        GLES30.glAttachShader(glProgram, fs)
        GLES30.glLinkProgram(glProgram)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        val quadVerts = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        val buf = ByteBuffer.allocateDirect(quadVerts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadVerts); flip() }
        val vaoArr = IntArray(1); GLES30.glGenVertexArrays(1, vaoArr, 0); vao = vaoArr[0]
        val vboArr = IntArray(1); GLES30.glGenBuffers(1, vboArr, 0); vbo = vboArr[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glBindVertexArray(0)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $log")
        }
        return shader
    }

    companion object {
        private const val VERT = """#version 300 es
layout(location=0) in vec2 aPos;
layout(location=1) in vec2 aUV;
out vec2 vUV;
void main() {
    vUV = aUV;
    gl_Position = vec4(aPos, 0.0, 1.0);
}"""

        private const val FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uVideoTex;
uniform sampler2D uOverlayTex;
uniform float uOverlayAlpha;
out vec4 fragColor;
void main() {
    vec4 video = texture(uVideoTex, vUV);
    vec4 overlay = texture(uOverlayTex, vUV);
    // Pre-multiplied alpha compositing
    float a = overlay.a * uOverlayAlpha;
    fragColor = vec4(mix(video.rgb, overlay.rgb, a), 1.0);
}"""
    }
}
