package com.novacut.editor.engine

import android.content.Context
import android.opengl.GLES30
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * LUT (Look-Up Table) engine for color grading.
 * Supports .cube and .3dl format parsing and GPU-based LUT application.
 */
object LutEngine {

    data class Lut3D(
        val size: Int,
        val data: FloatArray // RGB triplets, size^3 * 3
    )

    /**
     * Parse a .cube LUT file.
     */
    fun parseCube(file: File): Lut3D? {
        return try {
            val lines = file.readLines()
            var size = 0
            val data = mutableListOf<Float>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("TITLE")) continue

                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    val parsedSize = trimmed.substringAfter("LUT_3D_SIZE").trim().toInt()
                    // Bound LUT size to a sane range. A malicious .cube file declaring
                    // "LUT_3D_SIZE 1000" would otherwise need a 1000^3 * 3 = 3 billion
                    // float allocation (12 GB) which OOMs before the size mismatch check
                    // could reject it. Common LUT sizes are 17, 32, 33, 64.
                    if (parsedSize !in 2..256) {
                        Log.w("LutEngine", "LUT size $parsedSize outside supported range [2..256]")
                        throw IllegalArgumentException("LUT size out of range")
                    }
                    size = parsedSize
                    continue
                }
                if (trimmed.startsWith("DOMAIN_MIN") || trimmed.startsWith("DOMAIN_MAX")) continue

                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    // Clamp to [0, 1] — out-of-range LUT entries produce wild GPU colors
                    // (negative -> wrap, >1 -> blow out highlights). Standard .cube spec
                    // is 0..1 normalized; defensive clamping protects the shader.
                    data.add(parts[0].toFloat().coerceIn(0f, 1f))
                    data.add(parts[1].toFloat().coerceIn(0f, 1f))
                    data.add(parts[2].toFloat().coerceIn(0f, 1f))
                }
            }

            if (size > 0 && data.size == size * size * size * 3) {
                Lut3D(size, data.toFloatArray())
            } else null
        } catch (e: Exception) {
            Log.w("LutEngine", "LUT parse failed", e)
            null
        }
    }

    /**
     * Parse a .3dl LUT file.
     */
    fun parse3dl(file: File): Lut3D? {
        return try {
            val lines = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            if (lines.isEmpty()) return null

            // First line may be mesh points
            val firstParts = lines[0].trim().split("\\s+".toRegex())
            val startIdx = if (firstParts.size <= 3 && firstParts.all { it.toIntOrNull() != null }) 1 else 0

            // Determine scale from global max across all data lines
            var globalMax = 0f
            val rawLines = mutableListOf<List<Float>>()
            for (i in startIdx until lines.size) {
                val parts = lines[i].trim().split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val vals = listOf(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
                    globalMax = maxOf(globalMax, vals.max())
                    rawLines.add(vals)
                }
            }
            val scale = if (globalMax > 1f) (if (globalMax > 1023f) 4095f else 1023f) else 1f
            val data = mutableListOf<Float>()
            for (vals in rawLines) {
                data.add((vals[0] / scale).coerceIn(0f, 1f))
                data.add((vals[1] / scale).coerceIn(0f, 1f))
                data.add((vals[2] / scale).coerceIn(0f, 1f))
            }

            val entryCount = data.size / 3
            val size = Math.round(Math.cbrt(entryCount.toDouble())).toInt()
            if (size !in 2..256) {
                Log.w("LutEngine", "3DL inferred size $size outside supported range [2..256]")
                null
            } else if (size * size * size == entryCount) {
                Lut3D(size, data.toFloatArray())
            } else null
        } catch (e: Exception) {
            Log.w("LutEngine", "LUT parse failed", e)
            null
        }
    }

    /**
     * Create a GlEffect that applies a 3D LUT via texture lookup.
     */
    @UnstableApi
    fun createLutEffect(lut: Lut3D, intensity: Float = 1f): GlEffect {
        return LutGlEffect(lut, intensity)
    }
}

@UnstableApi
private class LutGlEffect(
    private val lut: LutEngine.Lut3D,
    private val intensity: Float
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LutShaderProgram(lut, intensity, useHdr)
    }
}

@UnstableApi
private class LutShaderProgram(
    private val lut: LutEngine.Lut3D,
    private val intensity: Float,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram = 0
    private var vao = 0
    private var vbo = 0
    private var lutTexture = 0
    private var width = 0
    private var height = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        if (glProgram == 0) setupGl()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        GLES30.glUseProgram(glProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        val texLoc = GLES30.glGetUniformLocation(glProgram, "uTexSampler")
        if (texLoc >= 0) GLES30.glUniform1i(texLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        val lutLoc = GLES30.glGetUniformLocation(glProgram, "uLutSampler")
        if (lutLoc >= 0) GLES30.glUniform1i(lutLoc, 1)

        val intLoc = GLES30.glGetUniformLocation(glProgram, "uIntensity")
        if (intLoc >= 0) GLES30.glUniform1f(intLoc, intensity)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    override fun release() {
        super.release()
        if (glProgram != 0) { GLES30.glDeleteProgram(glProgram); glProgram = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
        if (lutTexture != 0) { GLES30.glDeleteTextures(1, intArrayOf(lutTexture), 0); lutTexture = 0 }
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

        val s = IntArray(1)
        GLES30.glGetProgramiv(glProgram, GLES30.GL_LINK_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(glProgram)
            GLES30.glDeleteProgram(glProgram); glProgram = 0
            throw RuntimeException("LUT shader link failed: $log")
        }

        // Setup quad
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0); vao = vaos[0]
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); vbo = vbos[0]
        val quad = floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(quad).apply { position(0) }
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
        val p = GLES30.glGetAttribLocation(glProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(p)
        GLES30.glVertexAttribPointer(p, 2, GLES30.GL_FLOAT, false, 16, 0)
        val t = GLES30.glGetAttribLocation(glProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(t)
        GLES30.glVertexAttribPointer(t, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glBindVertexArray(0)

        // Upload 3D LUT texture
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        lutTexture = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        // Pad RGB data to RGBA for guaranteed filterability on GLES 3.0
        val rgbaData = FloatArray(lut.size * lut.size * lut.size * 4)
        for (i in 0 until lut.size * lut.size * lut.size) {
            rgbaData[i * 4] = lut.data[i * 3]
            rgbaData[i * 4 + 1] = lut.data[i * 3 + 1]
            rgbaData[i * 4 + 2] = lut.data[i * 3 + 2]
            rgbaData[i * 4 + 3] = 1.0f
        }
        val lutBuf = ByteBuffer.allocateDirect(rgbaData.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(rgbaData).apply { position(0) }
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F,
            lut.size, lut.size, lut.size, 0,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, lutBuf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val s = IntArray(1); GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh); GLES30.glDeleteShader(sh)
            throw RuntimeException("LUT shader compile failed: $log")
        }
        return sh
    }

    companion object {
        private const val VERT = "#version 300 es\n" +
            "in vec4 aPosition;\nin vec2 aTexCoord;\nout vec2 vTexCoord;\n" +
            "void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }"

        private const val FRAG = "#version 300 es\n" +
            "precision mediump float;\n" +
            "precision mediump sampler3D;\n" +
            "uniform sampler2D uTexSampler;\n" +
            "uniform sampler3D uLutSampler;\n" +
            "uniform float uIntensity;\n" +
            "in vec2 vTexCoord;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
            "  vec3 lutCoord = clamp(c.rgb, 0.0, 1.0);\n" +
            "  vec3 graded = texture(uLutSampler, lutCoord).rgb;\n" +
            "  fragColor = vec4(mix(c.rgb, graded, uIntensity), c.a);\n" +
            "}"
    }
}
