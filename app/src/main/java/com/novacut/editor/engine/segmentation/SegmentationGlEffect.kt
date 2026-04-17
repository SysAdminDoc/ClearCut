package com.novacut.editor.engine.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 GlEffect that applies per-frame MediaPipe selfie segmentation.
 * Copies the input texture to an intermediate texture for readback (avoids feedback loop),
 * runs segmentation on CPU, uploads the mask as a texture, then renders with alpha compositing.
 */
@UnstableApi
class SegmentationGlEffect(
    private val engine: SegmentationEngine,
    private val threshold: Float = 0.5f
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return SegmentationShaderProgram(engine, threshold, useHdr)
    }
}

@UnstableApi
private class SegmentationShaderProgram(
    private val engine: SegmentationEngine,
    private val threshold: Float,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram = 0
    private var copyProgram = 0
    private var vao = 0
    private var vbo = 0
    private var maskTexture = 0
    private var copyTexture = 0
    private var readbackFbo = 0
    private var width = 0
    private var height = 0
    private var pixelBuffer: ByteBuffer? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        if (glProgram == 0) setupGl()
        pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
        // Create intermediate texture for readback (avoids input texture feedback loop)
        if (copyTexture == 0) {
            val texs = IntArray(1); GLES30.glGenTextures(1, texs, 0); copyTexture = texs[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, copyTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // Save the current FBO (BaseGlShaderProgram's output FBO)
        val savedFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, savedFbo, 0)

        // Step 1: Copy input texture to intermediate texture via FBO blit
        copyInputToIntermediate(inputTexId)

        // Step 2: Read intermediate texture to Bitmap
        val bitmap = readCopyTextureToBitmap()

        // Restore the output FBO before rendering
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, savedFbo[0])

        if (bitmap != null) {
            // Step 3: Run segmentation (downscale for speed)
            val segScale = 256f / maxOf(bitmap.width, bitmap.height)
            val segW = (bitmap.width * segScale).toInt().coerceAtLeast(1)
            val segH = (bitmap.height * segScale).toInt().coerceAtLeast(1)
            val segBitmap = Bitmap.createScaledBitmap(bitmap, segW, segH, true)
            bitmap.recycle()

            // Wrap in try/finally so a MediaPipe segmenter exception (bad-input frame, model
            // tensor mismatch) can't leak segBitmap. This effect runs once per export frame,
            // so a per-frame leak under sustained errors would exhaust GPU/native bitmap heap.
            val result = try {
                engine.segment(segBitmap)
            } catch (e: Exception) {
                android.util.Log.w("SegmentationGlEffect", "segment() failed for export frame", e)
                null
            } finally {
                try { segBitmap.recycle() } catch (_: Exception) { /* already recycled */ }
            }

            if (result != null) {
                uploadMaskTexture(result)
            } else {
                uploadFallbackMask()
            }
        } else {
            uploadFallbackMask()
        }

        // Step 4: Render with mask shader (into BaseGlShaderProgram's output FBO, already bound)
        GLES30.glUseProgram(glProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        uniform1i("uTexSampler", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        uniform1i("uMaskSampler", 1)
        uniform1f("uThreshold", threshold)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /**
     * Copy input texture to intermediate copyTexture using a simple pass-through shader.
     * This avoids the feedback loop of reading and sampling from the same texture.
     */
    private fun copyInputToIntermediate(inputTexId: Int) {
        if (readbackFbo == 0) {
            val fbos = IntArray(1)
            GLES30.glGenFramebuffers(1, fbos, 0)
            readbackFbo = fbos[0]
        }

        // Bind FBO with copyTexture as color attachment
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, readbackFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, copyTexture, 0
        )

        // Render input texture into copyTexture via pass-through shader
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(copyProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        val loc = GLES30.glGetUniformLocation(copyProgram, "uTexSampler")
        if (loc >= 0) GLES30.glUniform1i(loc, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Read the intermediate copyTexture (attached to readbackFbo) into a Bitmap.
     */
    private fun readCopyTextureToBitmap(): Bitmap? {
        val buf = pixelBuffer ?: return null
        buf.clear()

        // readbackFbo is already bound with copyTexture as attachment from copyInputToIntermediate
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)

        buf.rewind()
        var bitmap: Bitmap? = null
        return try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buf)
            // GL readback is bottom-up, flip vertically
            val matrix = android.graphics.Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
            val flipped = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
            bitmap.recycle()
            flipped
        } catch (e: Exception) {
            bitmap?.recycle()
            null
        }
    }

    private fun uploadMaskTexture(result: SegmentationResult) {
        val maskW = result.width
        val maskH = result.height
        val pixels = IntArray(maskW * maskH)
        for (i in result.mask.indices) {
            val v = result.mask[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val maskBitmap = Bitmap.createBitmap(pixels, maskW, maskH, Bitmap.Config.ARGB_8888)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, maskBitmap, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        maskBitmap.recycle()
    }

    private fun uploadFallbackMask() {
        val white = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        white.setPixel(0, 0, 0xFFFFFFFF.toInt())
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, white, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        white.recycle()
    }

    override fun release() {
        super.release()
        if (glProgram != 0) { GLES30.glDeleteProgram(glProgram); glProgram = 0 }
        if (copyProgram != 0) { GLES30.glDeleteProgram(copyProgram); copyProgram = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
        if (maskTexture != 0) { GLES30.glDeleteTextures(1, intArrayOf(maskTexture), 0); maskTexture = 0 }
        if (copyTexture != 0) { GLES30.glDeleteTextures(1, intArrayOf(copyTexture), 0); copyTexture = 0 }
        if (readbackFbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(readbackFbo), 0); readbackFbo = 0 }
        pixelBuffer = null
    }

    private fun setupGl() {
        // Main segmentation shader
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAG_SEGMENTATION)
        glProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(glProgram, vs)
        GLES30.glAttachShader(glProgram, fs)
        GLES30.glLinkProgram(glProgram)
        GLES30.glDeleteShader(fs)
        val s = IntArray(1)
        GLES30.glGetProgramiv(glProgram, GLES30.GL_LINK_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(glProgram)
            GLES30.glDeleteProgram(glProgram); glProgram = 0
            GLES30.glDeleteShader(vs)
            throw RuntimeException("GL program link failed: $log")
        }

        // Pass-through copy shader (for copying input to intermediate texture)
        val copyFs = compile(GLES30.GL_FRAGMENT_SHADER, FRAG_COPY)
        copyProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(copyProgram, vs)
        GLES30.glAttachShader(copyProgram, copyFs)
        GLES30.glLinkProgram(copyProgram)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(copyFs)
        GLES30.glGetProgramiv(copyProgram, GLES30.GL_LINK_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(copyProgram)
            GLES30.glDeleteProgram(copyProgram); copyProgram = 0
            throw RuntimeException("Copy shader link failed: $log")
        }

        // VAO/VBO setup (shared between both programs)
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0); vao = vaos[0]
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); vbo = vbos[0]
        val quad = floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(quad).apply { position(0) }
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
        // Guard against -1 locations — see LutEngine.kt for full rationale. This is the
        // same pattern: some drivers corrupt GL state when attrib -1 is enabled, which
        // visually manifests as the segmented frame rendering black during export.
        val p = GLES30.glGetAttribLocation(glProgram, "aPosition")
        if (p >= 0) {
            GLES30.glEnableVertexAttribArray(p)
            GLES30.glVertexAttribPointer(p, 2, GLES30.GL_FLOAT, false, 16, 0)
        }
        val t = GLES30.glGetAttribLocation(glProgram, "aTexCoord")
        if (t >= 0) {
            GLES30.glEnableVertexAttribArray(t)
            GLES30.glVertexAttribPointer(t, 2, GLES30.GL_FLOAT, false, 16, 8)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // Mask texture — allocate 1x1 R8 storage up front so the first uploadFallbackMask /
        // uploadMask call never binds an uninitialized texture. Some drivers mark the texture
        // "incomplete" if its storage is never defined, which causes the sampler to return zero
        // (fully masked-out frame) or hard-fails the draw entirely.
        val texs = IntArray(1); GLES30.glGenTextures(1, texs, 0); maskTexture = texs[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val seedByte = java.nio.ByteBuffer.allocateDirect(1).order(java.nio.ByteOrder.nativeOrder())
            .put(0xFF.toByte()).apply { position(0) }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 1, 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, seedByte
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val s = IntArray(1); GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh); GLES30.glDeleteShader(sh)
            throw RuntimeException("Shader compile failed: $log")
        }
        return sh
    }

    private fun uniform1i(n: String, v: Int) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform1i(l, v)
    }
    private fun uniform1f(n: String, v: Float) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform1f(l, v)
    }

    companion object {
        private const val VERT = """#version 300 es
in vec2 aPosition;
in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}"""

        private const val FRAG_COPY = """#version 300 es
precision mediump float;
uniform sampler2D uTexSampler;
in vec2 vTexCoord;
out vec4 outColor;
void main() {
    outColor = texture(uTexSampler, vTexCoord);
}"""

        private const val FRAG_SEGMENTATION = """#version 300 es
precision mediump float;
uniform sampler2D uTexSampler;
uniform sampler2D uMaskSampler;
uniform float uThreshold;
in vec2 vTexCoord;
out vec4 outColor;
void main() {
    vec4 color = texture(uTexSampler, vTexCoord);
    float mask = texture(uMaskSampler, vTexCoord).r;
    float alpha = smoothstep(uThreshold - 0.1, uThreshold + 0.1, mask);
    outColor = vec4(color.rgb * alpha, alpha);
}"""
    }
}
