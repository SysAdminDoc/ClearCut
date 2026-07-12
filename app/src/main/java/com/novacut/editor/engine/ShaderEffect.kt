package com.novacut.editor.engine

import android.content.Context
import android.opengl.GLES30
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.novacut.editor.model.TrackedObject
import com.novacut.editor.model.TransitionEasing
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun easedTransitionPresentationTimeSeconds(
    presentationTimeUs: Long,
    durationUs: Float,
    clipDurationUs: Float?,
    easing: TransitionEasing
): Float {
    if (durationUs <= 0f || easing == TransitionEasing.LINEAR) {
        return presentationTimeUs / 1_000_000f
    }
    val transitionStartUs = clipDurationUs?.let { (it - durationUs).coerceAtLeast(0f) } ?: 0f
    val rawProgress = ((presentationTimeUs - transitionStartUs) / durationUs).coerceIn(0f, 1f)
    val easedProgress = when (easing) {
        TransitionEasing.LINEAR -> rawProgress
        TransitionEasing.EASE_IN -> rawProgress * rawProgress
        TransitionEasing.EASE_OUT -> rawProgress * (2f - rawProgress)
        TransitionEasing.EASE_IN_OUT -> if (rawProgress < 0.5f) {
            2f * rawProgress * rawProgress
        } else {
            -1f + (4f - 2f * rawProgress) * rawProgress
        }
    }
    return (transitionStartUs + easedProgress * durationUs) / 1_000_000f
}

/**
 * Custom GLSL fragment shader effect for Media3 Transformer export.
 * Wraps a GLES 3.0 fragment shader into a GlEffect with fullscreen quad rendering.
 */
@UnstableApi
class ShaderEffect(
    private val fragmentShader: String,
    private val uniforms: Map<String, Float> = emptyMap(),
    private val dynamicUniforms: ((Long) -> Map<String, Float>)? = null,
    private val transitionEasing: TransitionEasing = TransitionEasing.LINEAR
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ShaderProgram(fragmentShader, uniforms, dynamicUniforms, useHdr, transitionEasing)
    }

    fun withEasing(easing: TransitionEasing): ShaderEffect =
        ShaderEffect(fragmentShader, uniforms, dynamicUniforms, easing)
}

@UnstableApi
private class ShaderProgram(
    private val fragmentShaderSource: String,
    private val uniforms: Map<String, Float>,
    private val dynamicUniforms: ((Long) -> Map<String, Float>)?,
    useHdr: Boolean,
    private val transitionEasing: TransitionEasing = TransitionEasing.LINEAR
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram = 0
    private var vao = 0
    private var vbo = 0
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
        uniform1i("uTexSampler", 0)
        // Floor resolution at 1×1 so the several shader programs that compute
        // `1.0 / uResolution` (sharpen, blur, vignette, scanlines, …) can never produce
        // GLSL Infinity if Media3 ever calls drawFrame before configure() set width/height.
        uniform2f("uResolution", width.coerceAtLeast(1).toFloat(), height.coerceAtLeast(1).toFloat())

        val rawTimeSec = presentationTimeUs / 1_000_000f
        val durationUs = uniforms["uDurationUs"]
        if (durationUs != null && durationUs > 0f && transitionEasing != TransitionEasing.LINEAR) {
            uniform1f(
                "uTime",
                easedTransitionPresentationTimeSeconds(
                    presentationTimeUs = presentationTimeUs,
                    durationUs = durationUs,
                    clipDurationUs = uniforms["uClipDurationUs"],
                    easing = transitionEasing
                )
            )
        } else {
            uniform1f("uTime", rawTimeSec)
        }

        for ((name, value) in uniforms) uniform1f(name, value)
        dynamicUniforms?.invoke(presentationTimeUs)?.forEach { (name, value) ->
            uniform1f(name, value)
        }
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    override fun release() {
        super.release()
        if (glProgram != 0) { GLES30.glDeleteProgram(glProgram); glProgram = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
    }

    private fun setupGl() {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERT)
        val fs = try {
            compile(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)
        } catch (e: RuntimeException) {
            android.util.Log.e("ShaderEffect", "Fragment shader compile failed, using passthrough", e)
            compile(GLES30.GL_FRAGMENT_SHADER, FRAG_PASSTHROUGH)
        }
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
            throw RuntimeException("GL program link failed: $log")
        }
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0); vao = vaos[0]
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); vbo = vbos[0]
        val quad = floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(quad).apply { position(0) }
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
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
    private fun uniform2f(n: String, a: Float, b: Float) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform2f(l, a, b)
    }

    companion object {
        private const val VERT = "#version 300 es\n" +
            "in vec4 aPosition;\nin vec2 aTexCoord;\nout vec2 vTexCoord;\n" +
            "void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }"

        private const val FRAG_PASSTHROUGH = "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform sampler2D uTexSampler;\nin vec2 vTexCoord;\nout vec4 fragColor;\n" +
            "void main() { fragColor = texture(uTexSampler, vTexCoord); }"
    }
}

// ─── Factory for all GLSL-based effects ─────────────────────────────────────

@UnstableApi
object EffectShaders {

    fun opacity(opacity: Float) = ShaderEffect(
        FRAG_OPACITY,
        mapOf("uOpacity" to opacity.coerceIn(0f, 1f))
    )

    fun animatedOpacity(opacityAtTimeUs: (Long) -> Float) = ShaderEffect(
        FRAG_OPACITY,
        dynamicUniforms = { presentationTimeUs ->
            mapOf("uOpacity" to opacityAtTimeUs(presentationTimeUs).coerceIn(0f, 1f))
        }
    )

    fun gamma(value: Float) = ShaderEffect(
        FRAG_GAMMA,
        mapOf("uGamma" to value.coerceIn(0.2f, 5f))
    )

    fun highlights(value: Float) = ShaderEffect(
        FRAG_HIGHLIGHTS,
        mapOf("uAmount" to value.coerceIn(-1f, 1f))
    )

    fun shadows(value: Float) = ShaderEffect(
        FRAG_SHADOWS,
        mapOf("uAmount" to value.coerceIn(-1f, 1f))
    )

    fun posterize(levels: Float) = ShaderEffect(
        FRAG_POSTERIZE,
        mapOf("uLevels" to levels.coerceIn(2f, 16f))
    )

    fun vignette(intensity: Float, radius: Float) = ShaderEffect(
        FRAG_VIGNETTE, mapOf("uIntensity" to intensity, "uRadius" to radius)
    )

    fun sharpen(strength: Float) = ShaderEffect(
        FRAG_SHARPEN, mapOf("uAmount" to strength)
    )

    fun filmGrain(intensity: Float) = ShaderEffect(
        FRAG_FILM_GRAIN, mapOf("uIntensity" to intensity)
    )

    fun gaussianBlur(radius: Float) = ShaderEffect(
        FRAG_GAUSSIAN_BLUR, mapOf("uRadius" to radius)
    )

    fun radialBlur(intensity: Float) = ShaderEffect(
        FRAG_RADIAL_BLUR, mapOf("uIntensity" to intensity)
    )

    fun motionBlur(intensity: Float, angle: Float = 0f) = ShaderEffect(
        FRAG_MOTION_BLUR, mapOf("uIntensity" to intensity, "uAngle" to angle)
    )

    fun tiltShift(focusY: Float, width: Float, blur: Float) = ShaderEffect(
        FRAG_TILT_SHIFT, mapOf("uFocusY" to focusY, "uWidth" to width, "uBlur" to blur)
    )

    fun mosaic(size: Float) = ShaderEffect(FRAG_MOSAIC, mapOf("uSize" to size))

    fun trackedMosaic(
        size: Float,
        feather: Float,
        padding: Float,
        trackedObject: TrackedObject,
        sourceTimeOffsetMs: Long
    ) = ShaderEffect(
        FRAG_TRACKED_MOSAIC,
        mapOf(
            "uSize" to size.coerceIn(2f, 50f),
            "uFeather" to feather.coerceIn(0f, 0.15f),
            "uPadding" to padding.coerceIn(0f, 0.2f)
        ),
        dynamicUniforms = { presentationTimeUs ->
            TrackedObjectEffectBinding.uniformsForPresentationTime(
                trackedObject,
                presentationTimeUs,
                sourceTimeOffsetMs
            )
        }
    )

    fun fisheye(intensity: Float) = ShaderEffect(
        FRAG_FISHEYE, mapOf("uIntensity" to intensity)
    )

    fun glitch(intensity: Float) = ShaderEffect(
        FRAG_GLITCH, mapOf("uIntensity" to intensity)
    )

    fun pixelate(size: Float) = ShaderEffect(FRAG_PIXELATE, mapOf("uSize" to size))

    fun wave(amplitude: Float, frequency: Float) = ShaderEffect(
        FRAG_WAVE, mapOf("uAmplitude" to amplitude, "uFrequency" to frequency)
    )

    fun chromaticAberration(intensity: Float) = ShaderEffect(
        FRAG_CHROMATIC_ABERRATION, mapOf("uIntensity" to intensity)
    )

    fun chromaKey(keyR: Float, keyG: Float, keyB: Float, threshold: Float, smoothing: Float, spill: Float = 0.5f) =
        ShaderEffect(FRAG_CHROMA_KEY, mapOf(
            "uKeyR" to keyR.coerceIn(0f, 1f),
            "uKeyG" to keyG.coerceIn(0f, 1f),
            "uKeyB" to keyB.coerceIn(0f, 1f),
            "uThreshold" to threshold.coerceIn(0f, 1f),
            // smoothstep with edge0 == edge1 has undefined behavior in GLSL — floor the window
            // at a hair's width so the alpha ramp never collapses to a 0-wide step.
            "uSmoothing" to smoothing.coerceAtLeast(0.001f),
            "uSpill" to spill.coerceIn(0f, 1f)
        ))

    // ─── Transition shaders (applied to clip start/end) ─────────────────

    fun transitionFadeIn(durationUs: Float, fadeToWhite: Boolean = false) = ShaderEffect(
        if (fadeToWhite) FRAG_FADE_IN_WHITE else FRAG_FADE_IN_BLACK,
        mapOf("uDurationUs" to durationUs)
    )

    fun transitionWipe(durationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_WIPE_IN, mapOf("uDurationUs" to durationUs, "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionZoomIn(durationUs: Float) = ShaderEffect(
        FRAG_ZOOM_IN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionCircleOpen(durationUs: Float) = ShaderEffect(
        FRAG_CIRCLE_OPEN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionZoomOut(durationUs: Float) = ShaderEffect(
        FRAG_ZOOM_OUT, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSpin(durationUs: Float) = ShaderEffect(
        FRAG_SPIN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionFlip(durationUs: Float) = ShaderEffect(
        FRAG_FLIP, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSlideIn(durationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_SLIDE_IN, mapOf("uDurationUs" to durationUs, "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionCube(durationUs: Float) = ShaderEffect(
        FRAG_CUBE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionRipple(durationUs: Float) = ShaderEffect(
        FRAG_RIPPLE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionPixelate(durationUs: Float) = ShaderEffect(
        FRAG_PIXELATE_TRANS, mapOf("uDurationUs" to durationUs)
    )

    fun transitionDirectionalWarp(durationUs: Float) = ShaderEffect(
        FRAG_DIRECTIONAL_WARP, mapOf("uDurationUs" to durationUs)
    )

    fun transitionWind(durationUs: Float) = ShaderEffect(
        FRAG_WIND, mapOf("uDurationUs" to durationUs)
    )

    fun transitionMorph(durationUs: Float) = ShaderEffect(
        FRAG_MORPH, mapOf("uDurationUs" to durationUs)
    )

    fun transitionGlitch(durationUs: Float) = ShaderEffect(
        FRAG_GLITCH_TRANS, mapOf("uDurationUs" to durationUs)
    )

    fun transitionCrossZoom(durationUs: Float) = ShaderEffect(
        FRAG_CROSS_ZOOM, mapOf("uDurationUs" to durationUs)
    )

    fun transitionDreamy(durationUs: Float) = ShaderEffect(
        FRAG_DREAMY, mapOf("uDurationUs" to durationUs)
    )

    fun transitionHeart(durationUs: Float) = ShaderEffect(
        FRAG_HEART, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSwirl(durationUs: Float) = ShaderEffect(
        FRAG_SWIRL, mapOf("uDurationUs" to durationUs)
    )

    fun vhsRetro(intensity: Float) = ShaderEffect(
        FRAG_VHS_RETRO, mapOf("uIntensity" to intensity)
    )

    fun lightLeak(intensity: Float) = ShaderEffect(
        FRAG_LIGHT_LEAK, mapOf("uIntensity" to intensity)
    )

    // ─── New transition factory methods ─────────────────────────────────

    fun transitionDoorOpen(durationUs: Float) = ShaderEffect(
        FRAG_DOOR_OPEN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionBurn(durationUs: Float) = ShaderEffect(
        FRAG_BURN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionRadialWipe(durationUs: Float) = ShaderEffect(
        FRAG_RADIAL_WIPE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionMosaicReveal(durationUs: Float) = ShaderEffect(
        FRAG_MOSAIC_REVEAL, mapOf("uDurationUs" to durationUs)
    )

    fun transitionBounce(durationUs: Float) = ShaderEffect(
        FRAG_BOUNCE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionLensFlare(durationUs: Float) = ShaderEffect(
        FRAG_LENS_FLARE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionPageCurl(durationUs: Float) = ShaderEffect(
        FRAG_PAGE_CURL, mapOf("uDurationUs" to durationUs)
    )

    fun transitionCrossWarp(durationUs: Float) = ShaderEffect(
        FRAG_CROSS_WARP, mapOf("uDurationUs" to durationUs)
    )

    fun transitionAngular(durationUs: Float) = ShaderEffect(
        FRAG_ANGULAR, mapOf("uDurationUs" to durationUs)
    )

    fun transitionKaleidoscope(durationUs: Float) = ShaderEffect(
        FRAG_KALEIDOSCOPE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSquaresWire(durationUs: Float) = ShaderEffect(
        FRAG_SQUARES_WIRE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionColorPhase(durationUs: Float) = ShaderEffect(
        FRAG_COLOR_PHASE, mapOf("uDurationUs" to durationUs)
    )

    // ─── Fragment shader sources ────────────────────────────────────────

    private const val H = "#version 300 es\nprecision mediump float;\n" +
        "uniform sampler2D uTexSampler;\nin vec2 vTexCoord;\nout vec4 fragColor;\n"

    private const val FRAG_OPACITY = H +
        "uniform float uOpacity;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float opacity = clamp(uOpacity, 0.0, 1.0);\n" +
        "  fragColor = vec4(c.rgb * opacity, c.a * opacity);\n}"

    private const val FRAG_GAMMA = H +
        "uniform float uGamma;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 corrected = pow(max(c.rgb, vec3(0.0)), vec3(1.0 / max(uGamma, 0.001)));\n" +
        "  fragColor = vec4(corrected, c.a);\n}"

    private const val FRAG_HIGHLIGHTS = H +
        "uniform float uAmount;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "  float mask = smoothstep(0.45, 1.0, luma);\n" +
        "  vec3 delta = uAmount >= 0.0 ? (vec3(1.0) - c.rgb) : c.rgb;\n" +
        "  fragColor = vec4(clamp(c.rgb + uAmount * mask * delta, 0.0, 1.0), c.a);\n}"

    private const val FRAG_SHADOWS = H +
        "uniform float uAmount;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "  float mask = 1.0 - smoothstep(0.0, 0.55, luma);\n" +
        "  vec3 delta = uAmount >= 0.0 ? (vec3(1.0) - c.rgb) : c.rgb;\n" +
        "  fragColor = vec4(clamp(c.rgb + uAmount * mask * delta, 0.0, 1.0), c.a);\n}"

    private const val FRAG_POSTERIZE = H +
        "uniform float uLevels;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float steps = max(uLevels - 1.0, 1.0);\n" +
        "  fragColor = vec4(floor(c.rgb * steps + 0.5) / steps, c.a);\n}"

    private const val FRAG_VIGNETTE = H +
        "uniform float uIntensity;\nuniform float uRadius;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float d = length(vTexCoord - 0.5) * 1.414;\n" +
        "  float v = 1.0 - smoothstep(uRadius - 0.3, uRadius + 0.4, d * (0.5 + uIntensity * 1.5));\n" +
        "  fragColor = vec4(c.rgb * v, c.a);\n}"

    private const val FRAG_SHARPEN = H +
        "uniform vec2 uResolution;\nuniform float uAmount;\n" +
        "void main() {\n" +
        "  vec2 t = 1.0 / uResolution;\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec4 n = texture(uTexSampler, vTexCoord + vec2(-t.x, 0.0))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(t.x, 0.0))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(0.0, -t.y))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(0.0, t.y));\n" +
        "  fragColor = vec4(clamp(c.rgb + (c.rgb * 4.0 - n.rgb) * uAmount, 0.0, 1.0), c.a);\n}"

    private const val FRAG_FILM_GRAIN = H +
        "uniform float uIntensity;\nuniform float uTime;\n" +
        "float hash(vec2 p) {\n" +
        "  vec3 p3 = fract(vec3(p.xyx) * 0.1031);\n" +
        "  p3 += dot(p3, p3.yzx + 33.33);\n" +
        "  return fract((p3.x + p3.y) * p3.z);\n" +
        "}\n" +
        "float blueNoise(vec2 uv, float t) {\n" +
        "  float n1 = hash(uv + t);\n" +
        "  float n2 = hash(uv * 1.7 + t + 42.0);\n" +
        "  return (n1 + n2) * 0.5 - 0.5;\n" +
        "}\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float lum = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "  float shadowMask = 1.0 - smoothstep(0.0, 0.5, lum);\n" +
        "  float grainStr = mix(0.3, 1.0, shadowMask);\n" +
        "  float noise = blueNoise(vTexCoord * 800.0, fract(uTime * 7.0));\n" +
        "  fragColor = vec4(clamp(c.rgb + noise * uIntensity * 0.3 * grainStr, 0.0, 1.0), c.a);\n}"

    private const val FRAG_GAUSSIAN_BLUR = H +
        "uniform vec2 uResolution;\nuniform float uRadius;\n" +
        "void main() {\n" +
        "  vec2 t = uRadius / uResolution;\n" +
        "  float w0 = 0.2270270270;\n" +
        "  float w1 = 0.1945945946;\n" +
        "  float w2 = 0.1216216216;\n" +
        "  float w3 = 0.0540540541;\n" +
        "  float w4 = 0.0162162162;\n" +
        "  vec4 s = texture(uTexSampler, vTexCoord) * w0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, 0.0)) * w1;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x, 0.0)) * w1;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x * 2.0, 0.0)) * w2;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x * 2.0, 0.0)) * w2;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x * 3.0, 0.0)) * w3;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x * 3.0, 0.0)) * w3;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x * 4.0, 0.0)) * w4;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x * 4.0, 0.0)) * w4;\n" +
        "  vec4 h = s;\n" +
        "  s = h * w0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y)) * w1;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y)) * w1;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y * 2.0)) * w2;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y * 2.0)) * w2;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y * 3.0)) * w3;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y * 3.0)) * w3;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y * 4.0)) * w4;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y * 4.0)) * w4;\n" +
        "  fragColor = mix(h, s, 0.5);\n}"

    private const val FRAG_RADIAL_BLUR = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 dir = vTexCoord - 0.5;\n" +
        "  float str = uIntensity * 0.02;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = 0; i < 10; i++) {\n" +
        "    s += texture(uTexSampler, vTexCoord - dir * float(i) / 10.0 * str);\n" +
        "  }\n" +
        "  fragColor = s / 10.0;\n}"

    private const val FRAG_MOTION_BLUR = H +
        "uniform vec2 uResolution;\nuniform float uIntensity;\nuniform float uAngle;\n" +
        "void main() {\n" +
        "  float rad = uAngle * 3.14159 / 180.0;\n" +
        "  vec2 dir = vec2(cos(rad), sin(rad)) * uIntensity * 5.0 / uResolution;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = -5; i <= 5; i++) {\n" +
        "    s += texture(uTexSampler, vTexCoord + dir * float(i));\n" +
        "  }\n" +
        "  fragColor = s / 11.0;\n}"

    private const val FRAG_TILT_SHIFT = H +
        "uniform vec2 uResolution;\nuniform float uFocusY;\nuniform float uWidth;\nuniform float uBlur;\n" +
        "void main() {\n" +
        "  float dist = abs(vTexCoord.y - uFocusY);\n" +
        "  float blur = smoothstep(uWidth, uWidth + 0.15, dist) * uBlur * 5.0;\n" +
        "  vec2 t = blur / uResolution;\n" +
        "  vec4 s = texture(uTexSampler, vTexCoord) * 4.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + t);\n" +
        "  s += texture(uTexSampler, vTexCoord - t);\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, -t.y));\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(-t.x, t.y));\n" +
        "  vec4 blurred = s / 16.0;\n" +
        "  vec4 orig = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = mix(orig, blurred, smoothstep(uWidth, uWidth + 0.15, dist));\n}"

    private const val FRAG_MOSAIC = H +
        "uniform vec2 uResolution;\nuniform float uSize;\n" +
        "void main() {\n" +
        "  vec2 bs = vec2(uSize) / uResolution;\n" +
        "  vec2 uv = floor(vTexCoord / bs) * bs + bs * 0.5;\n" +
        "  fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_TRACKED_MOSAIC = H +
        "uniform vec2 uResolution;\nuniform float uSize;\n" +
        "uniform float uCenterX;\nuniform float uCenterY;\n" +
        "uniform float uObjectWidth;\nuniform float uObjectHeight;\n" +
        "uniform float uObjectConfidence;\nuniform float uFeather;\nuniform float uPadding;\n" +
        "void main() {\n" +
        "  vec4 original = texture(uTexSampler, vTexCoord);\n" +
        "  vec2 halfBox = max(vec2(uObjectWidth, uObjectHeight) * 0.5 + vec2(uPadding), vec2(0.0001));\n" +
        "  vec2 delta = abs(vTexCoord - vec2(uCenterX, uCenterY)) - halfBox;\n" +
        "  float signedDistance = max(delta.x, delta.y);\n" +
        "  float feather = max(uFeather, 0.0001);\n" +
        "  float confidence = smoothstep(0.2, 0.4, uObjectConfidence);\n" +
        "  float active = step(0.001, uObjectWidth) * step(0.001, uObjectHeight) * confidence;\n" +
        "  float mask = (1.0 - smoothstep(0.0, feather, signedDistance)) * active;\n" +
        "  vec2 bs = max(vec2(uSize), vec2(1.0)) / max(uResolution, vec2(1.0));\n" +
        "  vec2 uv = floor(vTexCoord / bs) * bs + bs * 0.5;\n" +
        "  vec4 mosaic = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = mix(original, mosaic, clamp(mask, 0.0, 1.0));\n}"

    private const val FRAG_FISHEYE = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float power = 1.0 + uIntensity * 3.0;\n" +
        "  if (dist > 0.00001 && dist < 0.5) {\n" +
        "    float f = pow(dist / 0.5, power) * 0.5 / dist;\n" +
        "    uv *= f;\n" +
        "  }\n" +
        "  fragColor = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n}"

    private const val FRAG_GLITCH = H +
        "uniform vec2 uResolution;\nuniform float uIntensity;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  float timeSlice = floor(uTime * 8.0);\n" +
        "  float lineShift = rand(vec2(floor(uv.y * 30.0), timeSlice)) * step(0.8, rand(vec2(timeSlice, 3.0)));\n" +
        "  uv.x += (lineShift - 0.5) * uIntensity * 0.08;\n" +
        "  float blockY = floor(uv.y * 8.0) / 8.0;\n" +
        "  float blockX = floor(uv.x * 8.0) / 8.0;\n" +
        "  float blockRand = rand(vec2(blockX, blockY) + timeSlice);\n" +
        "  float blockActive = step(0.92, blockRand) * uIntensity;\n" +
        "  vec2 blockUv = uv + vec2(rand(vec2(blockY, timeSlice)) - 0.5, 0.0) * blockActive * 0.15;\n" +
        "  float rgbOff = uIntensity * 0.015;\n" +
        "  float r = texture(uTexSampler, clamp(vec2(blockUv.x + rgbOff, blockUv.y), 0.0, 1.0)).r;\n" +
        "  float g = texture(uTexSampler, clamp(blockUv, 0.0, 1.0)).g;\n" +
        "  float b = texture(uTexSampler, clamp(vec2(blockUv.x - rgbOff, blockUv.y + rgbOff * 0.5), 0.0, 1.0)).b;\n" +
        "  float a = texture(uTexSampler, clamp(blockUv, 0.0, 1.0)).a;\n" +
        "  fragColor = vec4(r, g, b, a);\n}"

    private const val FRAG_PIXELATE = H +
        "uniform vec2 uResolution;\nuniform float uSize;\n" +
        "void main() {\n" +
        "  vec2 bs = vec2(uSize) / uResolution;\n" +
        "  vec2 uv = floor(vTexCoord / bs) * bs + bs * 0.5;\n" +
        "  fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_WAVE = H +
        "uniform float uAmplitude;\nuniform float uFrequency;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  uv.x += sin(uv.y * uFrequency + uTime * 3.0) * uAmplitude;\n" +
        "  uv.y += cos(uv.x * uFrequency + uTime * 2.0) * uAmplitude * 0.7;\n" +
        "  fragColor = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n}"

    private const val FRAG_CHROMATIC_ABERRATION = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 dir = (vTexCoord - 0.5) * uIntensity * 0.01;\n" +
        "  float r = texture(uTexSampler, vTexCoord + dir).r;\n" +
        "  float g = texture(uTexSampler, vTexCoord).g;\n" +
        "  float b = texture(uTexSampler, vTexCoord - dir).b;\n" +
        "  float a = texture(uTexSampler, vTexCoord).a;\n" +
        "  fragColor = vec4(r, g, b, a);\n}"

    private const val FRAG_CHROMA_KEY = H +
        "uniform float uKeyR, uKeyG, uKeyB;\n" +
        "uniform float uThreshold, uSmoothing;\n" +
        "uniform float uSpill;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  // Convert key color to YCbCr (Cb, Cr)\n" +
        "  float keyCb = -0.1687 * uKeyR - 0.3313 * uKeyG + 0.5 * uKeyB + 0.5;\n" +
        "  float keyCr =  0.5 * uKeyR - 0.4187 * uKeyG - 0.0813 * uKeyB + 0.5;\n" +
        "  // Convert pixel to YCbCr (Cb, Cr)\n" +
        "  float pixCb = -0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b + 0.5;\n" +
        "  float pixCr =  0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b + 0.5;\n" +
        "  // CbCr distance for chroma keying\n" +
        "  float dist = distance(vec2(pixCb, pixCr), vec2(keyCb, keyCr));\n" +
        "  float alpha = smoothstep(uThreshold, uThreshold + uSmoothing, dist);\n" +
        "  // Green spill suppression\n" +
        "  vec3 color = c.rgb;\n" +
        "  float gSpill = color.g - max(color.r, color.b) * (1.0 - uSpill * 0.5);\n" +
        "  if (gSpill > 0.0 && uKeyG > uKeyR && uKeyG > uKeyB) {\n" +
        "    color.g -= gSpill;\n" +
        "    color.r += gSpill * 0.5;\n" +
        "    color.b += gSpill * 0.5;\n" +
        "  }\n" +
        "  // Blue spill suppression\n" +
        "  float bSpill = color.b - max(color.r, color.g) * (1.0 - uSpill * 0.5);\n" +
        "  if (bSpill > 0.0 && uKeyB > uKeyR && uKeyB > uKeyG) {\n" +
        "    color.b -= bSpill;\n" +
        "    color.r += bSpill * 0.5;\n" +
        "    color.g += bSpill * 0.5;\n" +
        "  }\n" +
        "  fragColor = vec4(color, alpha);\n}"

    // ─── Transition shaders ─────────────────────────────────────────────

    private const val FRAG_FADE_IN_BLACK = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_FADE_IN_WHITE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(vec3(1.0), c.rgb, progress), c.a);\n}"

    private const val FRAG_WIPE_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float pos = vTexCoord.x * uDirX + vTexCoord.y * uDirY;\n" +
        "  float lo = min(uDirX, 0.0) + min(uDirY, 0.0);\n" +
        "  float hi = max(uDirX, 0.0) + max(uDirY, 0.0);\n" +
        "  float edge = (pos - lo) / max(hi - lo, 0.001);\n" +
        "  float p = progress * 1.04 - 0.02;\n" +
        "  float reveal = 1.0 - smoothstep(p - 0.02, p + 0.02, edge);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_ZOOM_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float scale = mix(3.0, 1.0, progress);\n" +
        "  vec2 uv = (vTexCoord - 0.5) * scale + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_CIRCLE_OPEN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float dist = length(vTexCoord - 0.5);\n" +
        "  float radius = progress * 0.75;\n" +
        "  float reveal = smoothstep(radius - 0.02, radius + 0.02, 0.75 - dist);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_ZOOM_OUT = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float scale = mix(0.3, 1.0, progress);\n" +
        "  vec2 uv = (vTexCoord - 0.5) / scale + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_SPIN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float angle = (1.0 - progress) * 6.28318;\n" +
        "  float sc = max(progress, 0.01);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float cs = cos(angle), sn = sin(angle);\n" +
        "  uv = vec2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs) / sc + 0.5;\n" +
        "  vec4 col = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(col.rgb * progress, col.a);\n}"

    private const val FRAG_FLIP = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float xScale = abs(progress * 2.0 - 1.0);\n" +
        "  if (progress < 0.5) { fragColor = vec4(0.0, 0.0, 0.0, 1.0); }\n" +
        "  else {\n" +
        "    vec2 uv = vec2((vTexCoord.x - 0.5) / max(xScale, 0.01) + 0.5, vTexCoord.y);\n" +
        "    fragColor = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  }\n}"

    private const val FRAG_SLIDE_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 offset = vec2(uDirX, uDirY) * (1.0 - progress);\n" +
        "  vec2 uv = vTexCoord + offset;\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)\n" +
        "    fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_CUBE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float angle = (1.0 - progress) * 1.5708;\n" +
        "  float xOff = sin(angle) * 0.5;\n" +
        "  float xSc = cos(angle);\n" +
        "  vec2 uv = vec2((vTexCoord.x - 0.5 + xOff) / max(xSc, 0.01) + 0.5, vTexCoord.y);\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0) fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else {\n" +
        "    vec4 c = texture(uTexSampler, uv);\n" +
        "    fragColor = vec4(c.rgb * (0.5 + 0.5 * xSc), c.a);\n" +
        "  }\n}"

    private const val FRAG_RIPPLE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float wave = sin(dist * 30.0 - progress * 10.0) * (1.0 - progress) * 0.03;\n" +
        "  uv += normalize(uv + 0.001) * wave;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_PIXELATE_TRANS = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blocks = mix(5.0, 200.0, progress * progress);\n" +
        "  vec2 uv = floor(vTexCoord * blocks) / blocks;\n" +
        "  vec4 c = texture(uTexSampler, uv);\n" +
        "  fragColor = vec4(c.rgb * smoothstep(0.0, 0.3, progress), c.a);\n}"

    private const val FRAG_DIRECTIONAL_WARP = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float warp = (1.0 - progress) * 0.3;\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  uv.x += sin(uv.y * 10.0) * warp;\n" +
        "  uv.y += cos(uv.x * 10.0) * warp * 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_WIND = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float wind = 1.0 - progress;\n" +
        "  float offset = wind * (sin(vTexCoord.y * 30.0 + uTime * 5.0) * 0.1 + 0.5);\n" +
        "  vec2 uv = vec2(vTexCoord.x + offset, vTexCoord.y);\n" +
        "  if (uv.x > 1.0) fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else {\n" +
        "    vec4 c = texture(uTexSampler, uv);\n" +
        "    fragColor = vec4(c.rgb * smoothstep(0.0, 0.2, progress), c.a);\n" +
        "  }\n}"

    private const val FRAG_MORPH = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float morph = (1.0 - progress) * 0.15;\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  float noise = sin(uv.x * 20.0) * cos(uv.y * 20.0);\n" +
        "  uv += noise * morph;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_GLITCH_TRANS = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float str = (1.0 - progress) * 0.1;\n" +
        "  float n = rand(vec2(floor(vTexCoord.y * 15.0), floor(uTime * 8.0)));\n" +
        "  float shift = (n - 0.5) * str;\n" +
        "  float r = texture(uTexSampler, vec2(vTexCoord.x + shift, vTexCoord.y)).r;\n" +
        "  float g = texture(uTexSampler, vTexCoord).g;\n" +
        "  float b = texture(uTexSampler, vec2(vTexCoord.x - shift, vTexCoord.y)).b;\n" +
        "  fragColor = vec4(vec3(r, g, b) * progress, 1.0);\n}"

    private const val FRAG_CROSS_ZOOM = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blur = (1.0 - progress) * 0.1;\n" +
        "  vec2 dir = vTexCoord - 0.5;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = 0; i < 8; i++) {\n" +
        "    float t = float(i) / 7.0;\n" +
        "    s += texture(uTexSampler, vTexCoord - dir * blur * t);\n" +
        "  }\n" +
        "  fragColor = vec4((s / 8.0).rgb * progress, 1.0);\n}"

    private const val FRAG_DREAMY = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blur = (1.0 - progress) * 0.01;\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  c += texture(uTexSampler, vTexCoord + vec2(blur, 0.0));\n" +
        "  c += texture(uTexSampler, vTexCoord - vec2(blur, 0.0));\n" +
        "  c += texture(uTexSampler, vTexCoord + vec2(0.0, blur));\n" +
        "  c += texture(uTexSampler, vTexCoord - vec2(0.0, blur));\n" +
        "  c /= 5.0;\n" +
        "  float glow = 1.0 + (1.0 - progress) * 0.5;\n" +
        "  fragColor = vec4(clamp(c.rgb * glow * progress, 0.0, 1.0), 1.0);\n}"

    private const val FRAG_HEART = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 p = (vTexCoord - vec2(0.5, 0.6)) * vec2(2.5, -3.0);\n" +
        "  float h = pow(p.x * p.x + p.y * p.y - 1.0, 3.0) - p.x * p.x * p.y * p.y * p.y;\n" +
        "  float mask = 1.0 - smoothstep(-0.1, 0.1, h + (1.0 - progress) * 2.0);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = vec4(c.rgb * mask, c.a);\n}"

    private const val FRAG_SWIRL = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float angle = (1.0 - progress) * dist * 15.0;\n" +
        "  float cs = cos(angle), sn = sin(angle);\n" +
        "  uv = vec2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs);\n" +
        "  vec4 col = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n" +
        "  fragColor = vec4(col.rgb * progress, col.a);\n}"

    // ─── Color Grading shader ─────────────────────────────────────────

    fun colorGrade(
        liftR: Float, liftG: Float, liftB: Float,
        gammaR: Float, gammaG: Float, gammaB: Float,
        gainR: Float, gainG: Float, gainB: Float,
        offsetR: Float, offsetG: Float, offsetB: Float
    ) = ShaderEffect(
        FRAG_COLOR_GRADE,
        mapOf(
            "uLiftR" to liftR, "uLiftG" to liftG, "uLiftB" to liftB,
            "uGammaR" to gammaR, "uGammaG" to gammaG, "uGammaB" to gammaB,
            "uGainR" to gainR, "uGainG" to gainG, "uGainB" to gainB,
            "uOffsetR" to offsetR, "uOffsetG" to offsetG, "uOffsetB" to offsetB
        )
    )

    fun hslQualify(
        hueCenter: Float, hueWidth: Float,
        satMin: Float, satMax: Float,
        lumMin: Float, lumMax: Float,
        softness: Float,
        adjustHue: Float, adjustSat: Float, adjustLum: Float
    ) = ShaderEffect(
        FRAG_HSL_QUALIFY,
        mapOf(
            "uHueCenter" to hueCenter, "uHueWidth" to hueWidth,
            "uSatMin" to satMin, "uSatMax" to satMax,
            "uLumMin" to lumMin, "uLumMax" to lumMax,
            "uSoftness" to softness,
            "uAdjHue" to adjustHue, "uAdjSat" to adjustSat, "uAdjLum" to adjustLum
        )
    )

    // ─── Blend Mode shaders ──────────────────────────────────────────

    fun blendMode(mode: com.novacut.editor.model.BlendMode, opacity: Float = 1f) = ShaderEffect(
        when (mode) {
            com.novacut.editor.model.BlendMode.MULTIPLY -> FRAG_BLEND_MULTIPLY
            com.novacut.editor.model.BlendMode.SCREEN -> FRAG_BLEND_SCREEN
            com.novacut.editor.model.BlendMode.OVERLAY -> FRAG_BLEND_OVERLAY
            com.novacut.editor.model.BlendMode.DARKEN -> FRAG_BLEND_DARKEN
            com.novacut.editor.model.BlendMode.LIGHTEN -> FRAG_BLEND_LIGHTEN
            com.novacut.editor.model.BlendMode.COLOR_DODGE -> FRAG_BLEND_COLOR_DODGE
            com.novacut.editor.model.BlendMode.COLOR_BURN -> FRAG_BLEND_COLOR_BURN
            com.novacut.editor.model.BlendMode.HARD_LIGHT -> FRAG_BLEND_HARD_LIGHT
            com.novacut.editor.model.BlendMode.SOFT_LIGHT -> FRAG_BLEND_SOFT_LIGHT
            com.novacut.editor.model.BlendMode.DIFFERENCE -> FRAG_BLEND_DIFFERENCE
            com.novacut.editor.model.BlendMode.EXCLUSION -> FRAG_BLEND_EXCLUSION
            com.novacut.editor.model.BlendMode.HUE -> FRAG_BLEND_HUE
            com.novacut.editor.model.BlendMode.SATURATION_BLEND -> FRAG_BLEND_SATURATION
            com.novacut.editor.model.BlendMode.COLOR -> FRAG_BLEND_COLOR
            com.novacut.editor.model.BlendMode.LUMINOSITY -> FRAG_BLEND_LUMINOSITY
            com.novacut.editor.model.BlendMode.ADD -> FRAG_BLEND_ADD
            com.novacut.editor.model.BlendMode.SUBTRACT -> FRAG_BLEND_SUBTRACT
            else -> FRAG_BLEND_NORMAL
        },
        mapOf("uOpacity" to opacity)
    )

    // ─── Mask shader ─────────────────────────────────────────────────

    fun rectangleMask(
        x: Float, y: Float, w: Float, h: Float,
        feather: Float, inverted: Float
    ) = ShaderEffect(
        FRAG_RECT_MASK,
        mapOf(
            "uMaskX" to x, "uMaskY" to y, "uMaskW" to w, "uMaskH" to h,
            "uFeather" to feather, "uInverted" to inverted
        )
    )

    fun ellipseMask(
        cx: Float, cy: Float, rx: Float, ry: Float,
        feather: Float, inverted: Float
    ) = ShaderEffect(
        FRAG_ELLIPSE_MASK,
        mapOf(
            "uCenterX" to cx, "uCenterY" to cy, "uRadiusX" to rx, "uRadiusY" to ry,
            "uFeather" to feather, "uInverted" to inverted
        )
    )

    // ─── Color Grading fragment shaders ──────────────────────────────

    private const val FRAG_COLOR_GRADE = H +
        "uniform float uLiftR, uLiftG, uLiftB;\n" +
        "uniform float uGammaR, uGammaG, uGammaB;\n" +
        "uniform float uGainR, uGainG, uGainB;\n" +
        "uniform float uOffsetR, uOffsetG, uOffsetB;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 lift = vec3(uLiftR, uLiftG, uLiftB);\n" +
        "  vec3 gamma = vec3(uGammaR, uGammaG, uGammaB);\n" +
        "  vec3 gain = vec3(uGainR, uGainG, uGainB);\n" +
        "  vec3 offset = vec3(uOffsetR, uOffsetG, uOffsetB);\n" +
        "  vec3 rgb = c.rgb;\n" +
        "  rgb = rgb * gain + lift * (1.0 - rgb);\n" +
        "  rgb = pow(max(rgb, 0.0), 1.0 / max(gamma, 0.01));\n" +
        "  rgb = clamp(rgb + offset, 0.0, 1.0);\n" +
        "  fragColor = vec4(rgb, c.a);\n}"

    private const val FRAG_HSL_QUALIFY = H +
        "uniform float uHueCenter, uHueWidth;\n" +
        "uniform float uSatMin, uSatMax;\n" +
        "uniform float uLumMin, uLumMax;\n" +
        "uniform float uSoftness;\n" +
        "uniform float uAdjHue, uAdjSat, uAdjLum;\n" +
        "vec3 rgb2hsl(vec3 c) {\n" +
        "  float mx = max(c.r, max(c.g, c.b));\n" +
        "  float mn = min(c.r, min(c.g, c.b));\n" +
        "  float l = (mx + mn) * 0.5;\n" +
        "  if (mx == mn) return vec3(0.0, 0.0, l);\n" +
        "  float d = mx - mn;\n" +
        "  float s = l > 0.5 ? d / (2.0 - mx - mn) : d / (mx + mn);\n" +
        "  float h;\n" +
        "  if (mx == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);\n" +
        "  else if (mx == c.g) h = (c.b - c.r) / d + 2.0;\n" +
        "  else h = (c.r - c.g) / d + 4.0;\n" +
        "  return vec3(h / 6.0, s, l);\n" +
        "}\n" +
        "float hue2rgb(float p, float q, float t) {\n" +
        "  if (t < 0.0) t += 1.0;\n" +
        "  if (t > 1.0) t -= 1.0;\n" +
        "  if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;\n" +
        "  if (t < 1.0/2.0) return q;\n" +
        "  if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;\n" +
        "  return p;\n" +
        "}\n" +
        "vec3 hsl2rgb(vec3 hsl) {\n" +
        "  if (hsl.y == 0.0) return vec3(hsl.z);\n" +
        "  float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;\n" +
        "  float p = 2.0 * hsl.z - q;\n" +
        "  return vec3(hue2rgb(p, q, hsl.x + 1.0/3.0), hue2rgb(p, q, hsl.x), hue2rgb(p, q, hsl.x - 1.0/3.0));\n" +
        "}\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 hsl = rgb2hsl(c.rgb);\n" +
        "  float hueDist = abs(hsl.x - uHueCenter / 360.0);\n" +
        "  hueDist = min(hueDist, 1.0 - hueDist);\n" +
        "  float hueMatch = 1.0 - smoothstep(uHueWidth / 720.0 - uSoftness, uHueWidth / 720.0 + uSoftness, hueDist);\n" +
        "  float satMatch = smoothstep(uSatMin - uSoftness, uSatMin + uSoftness, hsl.y) *\n" +
        "                    (1.0 - smoothstep(uSatMax - uSoftness, uSatMax + uSoftness, hsl.y));\n" +
        "  float lumMatch = smoothstep(uLumMin - uSoftness, uLumMin + uSoftness, hsl.z) *\n" +
        "                    (1.0 - smoothstep(uLumMax - uSoftness, uLumMax + uSoftness, hsl.z));\n" +
        "  float mask = hueMatch * satMatch * lumMatch;\n" +
        "  vec3 adjusted = vec3(hsl.x + uAdjHue / 360.0, clamp(hsl.y + uAdjSat, 0.0, 1.0), clamp(hsl.z + uAdjLum, 0.0, 1.0));\n" +
        "  vec3 result = mix(c.rgb, hsl2rgb(adjusted), mask);\n" +
        "  fragColor = vec4(result, c.a);\n}"

    // ─── Blend Mode fragment shaders ─────────────────────────────────

    private const val BH = H + "uniform float uOpacity;\n"

    private const val FRAG_BLEND_NORMAL = BH +
        "void main() {\n" +
        "  fragColor = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor.a *= uOpacity;\n}"

    // Single-texture blend fallback: preview and per-clip Media3 effects do not
    // expose the already-composited destination texture, so these shaders use
    // virtual reference colors until a programmable multi-input compositor is
    // available for true source-over-destination blend math.

    private const val FRAG_BLEND_MULTIPLY = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = c.rgb * 0.5;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_SCREEN = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = 1.0 - (1.0 - c.rgb) * 0.5;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_OVERLAY = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result;\n" +
        "  result.r = c.r < 0.5 ? 2.0 * c.r * 0.5 : 1.0 - 2.0 * (1.0 - c.r) * 0.5;\n" +
        "  result.g = c.g < 0.5 ? 2.0 * c.g * 0.5 : 1.0 - 2.0 * (1.0 - c.g) * 0.5;\n" +
        "  result.b = c.b < 0.5 ? 2.0 * c.b * 0.5 : 1.0 - 2.0 * (1.0 - c.b) * 0.5;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_DARKEN = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = min(c.rgb, vec3(0.5));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_LIGHTEN = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = max(c.rgb, vec3(0.5));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_COLOR_DODGE = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = clamp(c.rgb / max(vec3(0.5), vec3(0.001)), 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_COLOR_BURN = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = clamp(1.0 - (1.0 - c.rgb) / max(vec3(0.5), vec3(0.001)), 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_HARD_LIGHT = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 blend = vec3(0.5);\n" +
        "  vec3 result;\n" +
        "  result.r = blend.r > 0.5 ? 1.0 - 2.0 * (1.0 - c.r) * (1.0 - blend.r) : 2.0 * c.r * blend.r;\n" +
        "  result.g = blend.g > 0.5 ? 1.0 - 2.0 * (1.0 - c.g) * (1.0 - blend.g) : 2.0 * c.g * blend.g;\n" +
        "  result.b = blend.b > 0.5 ? 1.0 - 2.0 * (1.0 - c.b) * (1.0 - blend.b) : 2.0 * c.b * blend.b;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_SOFT_LIGHT = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 blend = vec3(0.5);\n" +
        "  vec3 result = (1.0 - 2.0 * blend) * c.rgb * c.rgb + 2.0 * blend * c.rgb;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_DIFFERENCE = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = abs(c.rgb - vec3(0.5));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_EXCLUSION = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 blend = vec3(0.5);\n" +
        "  vec3 result = c.rgb + blend - 2.0 * c.rgb * blend;\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val BLEND_HSL_HELPERS =
        "vec3 rgb2hslBlend(vec3 c) {\n" +
        "  float mx = max(c.r, max(c.g, c.b));\n" +
        "  float mn = min(c.r, min(c.g, c.b));\n" +
        "  float l = (mx + mn) * 0.5;\n" +
        "  if (mx == mn) return vec3(0.0, 0.0, l);\n" +
        "  float d = mx - mn;\n" +
        "  float s = l > 0.5 ? d / (2.0 - mx - mn) : d / (mx + mn);\n" +
        "  float h;\n" +
        "  if (mx == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);\n" +
        "  else if (mx == c.g) h = (c.b - c.r) / d + 2.0;\n" +
        "  else h = (c.r - c.g) / d + 4.0;\n" +
        "  return vec3(h / 6.0, s, l);\n" +
        "}\n" +
        "float hue2rgbBlend(float p, float q, float t) {\n" +
        "  if (t < 0.0) t += 1.0;\n" +
        "  if (t > 1.0) t -= 1.0;\n" +
        "  if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;\n" +
        "  if (t < 1.0/2.0) return q;\n" +
        "  if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;\n" +
        "  return p;\n" +
        "}\n" +
        "vec3 hsl2rgbBlend(vec3 hsl) {\n" +
        "  if (hsl.y == 0.0) return vec3(hsl.z);\n" +
        "  float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;\n" +
        "  float p = 2.0 * hsl.z - q;\n" +
        "  return vec3(hue2rgbBlend(p, q, hsl.x + 1.0/3.0), hue2rgbBlend(p, q, hsl.x), hue2rgbBlend(p, q, hsl.x - 1.0/3.0));\n" +
        "}\n"

    private const val FRAG_BLEND_HUE = BH + BLEND_HSL_HELPERS +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 baseHsl = rgb2hslBlend(c.rgb);\n" +
        "  vec3 blendHsl = rgb2hslBlend(vec3(0.85, 0.20, 0.20));\n" +
        "  vec3 result = hsl2rgbBlend(vec3(blendHsl.x, baseHsl.y, baseHsl.z));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_SATURATION = BH + BLEND_HSL_HELPERS +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 baseHsl = rgb2hslBlend(c.rgb);\n" +
        "  vec3 blendHsl = rgb2hslBlend(vec3(0.85, 0.15, 0.15));\n" +
        "  vec3 result = hsl2rgbBlend(vec3(baseHsl.x, blendHsl.y, baseHsl.z));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_COLOR = BH + BLEND_HSL_HELPERS +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 baseHsl = rgb2hslBlend(c.rgb);\n" +
        "  vec3 blendHsl = rgb2hslBlend(vec3(0.20, 0.55, 0.85));\n" +
        "  vec3 result = hsl2rgbBlend(vec3(blendHsl.x, blendHsl.y, baseHsl.z));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_LUMINOSITY = BH + BLEND_HSL_HELPERS +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 baseHsl = rgb2hslBlend(c.rgb);\n" +
        "  vec3 blendHsl = rgb2hslBlend(vec3(0.65));\n" +
        "  vec3 result = hsl2rgbBlend(vec3(baseHsl.x, baseHsl.y, blendHsl.z));\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_ADD = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = clamp(c.rgb + vec3(0.5), 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    private const val FRAG_BLEND_SUBTRACT = BH +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec3 result = clamp(c.rgb - vec3(0.5), 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(c.rgb, result, uOpacity), c.a);\n}"

    // ─── New effect fragment shaders ────────────────────────────────

    private const val FRAG_VHS_RETRO = H +
        "uniform vec2 uResolution;\nuniform float uIntensity;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  float trackOff = sin(uv.y * 40.0 + uTime * 3.0) * 0.002 * uIntensity;\n" +
        "  uv.x += trackOff;\n" +
        "  float chromaOff = uIntensity * 3.0 / uResolution.x;\n" +
        "  float r = texture(uTexSampler, vec2(uv.x + chromaOff, uv.y)).r;\n" +
        "  float g = texture(uTexSampler, uv).g;\n" +
        "  float b = texture(uTexSampler, vec2(uv.x - chromaOff, uv.y)).b;\n" +
        "  vec3 col = vec3(r, g, b);\n" +
        "  float scanline = sin(uv.y * uResolution.y * 3.14159) * 0.5 + 0.5;\n" +
        "  col *= mix(1.0, scanline, 0.15 * uIntensity);\n" +
        "  col = floor(col * (4.0 + (1.0 - uIntensity) * 12.0) + 0.5) / (4.0 + (1.0 - uIntensity) * 12.0);\n" +
        "  float a = texture(uTexSampler, clamp(uv, 0.0, 1.0)).a;\n" +
        "  fragColor = vec4(col, a);\n}"

    private const val FRAG_LIGHT_LEAK = H +
        "uniform float uIntensity;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float cx = 0.3 + 0.4 * sin(uTime * 0.5);\n" +
        "  float cy = 0.3 + 0.4 * cos(uTime * 0.7);\n" +
        "  float dist = length(vTexCoord - vec2(cx, cy));\n" +
        "  float glow = exp(-dist * 2.5) * uIntensity;\n" +
        "  vec3 leak = vec3(1.0, 0.6, 0.2) * glow;\n" +
        "  vec3 result = 1.0 - (1.0 - c.rgb) * (1.0 - leak);\n" +
        "  fragColor = vec4(result, c.a);\n}"

    // ─── New transition fragment shaders ─────────────────────────────

    private const val FRAG_DOOR_OPEN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float half = progress * 0.5;\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float reveal = step(half, abs(vTexCoord.x - 0.5));\n" +
        "  float edge = smoothstep(half - 0.01, half + 0.01, abs(vTexCoord.x - 0.5));\n" +
        "  fragColor = vec4(c.rgb * (1.0 - edge), c.a);\n}"

    private const val FRAG_BURN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float noise = rand(floor(vTexCoord * 50.0));\n" +
        "  float burn = smoothstep(progress - 0.1, progress + 0.1, noise);\n" +
        "  vec3 fire = mix(vec3(1.0, 0.3, 0.0), vec3(1.0, 0.8, 0.0), smoothstep(progress - 0.05, progress, noise));\n" +
        "  float edgeMask = smoothstep(progress - 0.12, progress - 0.02, noise) * (1.0 - smoothstep(progress - 0.02, progress + 0.02, noise));\n" +
        "  vec3 result = mix(c.rgb, fire, edgeMask) * burn;\n" +
        "  fragColor = vec4(result, c.a);\n}"

    private const val FRAG_RADIAL_WIPE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float angle = atan(uv.y, uv.x) / 6.28318 + 0.5;\n" +
        "  float reveal = smoothstep(progress - 0.02, progress + 0.02, angle);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = vec4(c.rgb * (1.0 - reveal), c.a);\n}"

    private const val FRAG_MOSAIC_REVEAL = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float gridSize = 10.0;\n" +
        "  vec2 cell = floor(vTexCoord * gridSize);\n" +
        "  float r = rand(cell);\n" +
        "  float reveal = smoothstep(r - 0.1, r + 0.1, progress);\n" +
        "  float pixSize = mix(gridSize, 200.0, reveal);\n" +
        "  vec2 uv = floor(vTexCoord * pixSize) / pixSize;\n" +
        "  vec4 c = texture(uTexSampler, uv);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_BOUNCE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float t = progress;\n" +
        "  float bounce = abs(sin(t * 3.14159 * 2.5)) * (1.0 - t);\n" +
        "  float scale = mix(0.0, 1.0, t) + bounce * 0.3;\n" +
        "  vec2 uv = (vTexCoord - 0.5) / max(scale, 0.01) + 0.5;\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)\n" +
        "    fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_LENS_FLARE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float flarePos = progress;\n" +
        "  vec2 center = vec2(mix(-0.3, 1.3, flarePos), 0.5);\n" +
        "  float dist = length(vTexCoord - center);\n" +
        "  float flare = exp(-dist * 4.0) * (1.0 - abs(progress - 0.5) * 2.0) * 2.0;\n" +
        "  vec3 flareCol = vec3(1.0, 0.9, 0.7) * flare;\n" +
        "  vec3 result = c.rgb * progress + flareCol;\n" +
        "  fragColor = vec4(clamp(result, 0.0, 1.0), c.a);\n}"

    private const val FRAG_PAGE_CURL = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float curlPos = 1.0 - progress;\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  float curl = curlPos * 1.2;\n" +
        "  if (uv.x > curl) {\n" +
        "    float d = uv.x - curl;\n" +
        "    float r = 0.15;\n" +
        "    float angle = d / r;\n" +
        "    if (angle < 3.14159) {\n" +
        "      vec2 curlUv = vec2(curl - sin(angle) * r, uv.y);\n" +
        "      vec4 c = texture(uTexSampler, clamp(curlUv, 0.0, 1.0));\n" +
        "      float shadow = 1.0 - d * 2.0;\n" +
        "      fragColor = vec4(c.rgb * max(shadow, 0.3), c.a);\n" +
        "    } else { fragColor = vec4(0.0, 0.0, 0.0, 1.0); }\n" +
        "  } else {\n" +
        "    vec4 c = texture(uTexSampler, uv);\n" +
        "    float shadow = smoothstep(curl - 0.1, curl, uv.x);\n" +
        "    fragColor = vec4(c.rgb * (0.7 + 0.3 * (1.0 - shadow * (1.0 - progress))), c.a);\n" +
        "  }\n}"

    private const val FRAG_CROSS_WARP = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float warp = (1.0 - progress) * 0.5;\n" +
        "  vec2 uv = mix(vTexCoord, vec2(0.5), warp);\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_ANGULAR = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float angle = atan(uv.y, uv.x) / 3.14159;\n" +
        "  float sweep = progress * 2.0 - 1.0;\n" +
        "  float reveal = smoothstep(sweep - 0.05, sweep + 0.05, angle);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_KALEIDOSCOPE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float angle = atan(uv.y, uv.x);\n" +
        "  float dist = length(uv);\n" +
        "  float segments = 6.0;\n" +
        "  float kalAngle = (1.0 - progress) * 6.28318;\n" +
        "  angle = mod(angle + kalAngle, 6.28318 / segments);\n" +
        "  angle = abs(angle - 3.14159 / segments);\n" +
        "  vec2 kalUv = vec2(cos(angle), sin(angle)) * dist + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(kalUv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_SQUARES_WIRE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float gridSize = 10.0;\n" +
        "  vec2 cell = fract(vTexCoord * gridSize);\n" +
        "  vec2 cellId = floor(vTexCoord * gridSize);\n" +
        "  float dist = length(cellId / gridSize - 0.5) * 1.414;\n" +
        "  float reveal = smoothstep(dist - 0.1, dist + 0.1, progress * 1.5);\n" +
        "  float wireSize = max(0.0, (1.0 - reveal) * 0.5);\n" +
        "  float wire = step(wireSize, cell.x) * step(wireSize, cell.y) *\n" +
        "               step(wireSize, 1.0 - cell.x) * step(wireSize, 1.0 - cell.y);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = vec4(c.rgb * wire * reveal, c.a);\n}"

    private const val FRAG_COLOR_PHASE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float p2 = progress * progress;\n" +
        "  vec2 uvR = mix(vTexCoord + vec2(0.1, 0.0), vTexCoord, p2);\n" +
        "  vec2 uvG = mix(vTexCoord + vec2(-0.05, 0.1), vTexCoord, p2);\n" +
        "  vec2 uvB = mix(vTexCoord + vec2(0.0, -0.1), vTexCoord, p2);\n" +
        "  float r = texture(uTexSampler, clamp(uvR, 0.0, 1.0)).r;\n" +
        "  float g = texture(uTexSampler, clamp(uvG, 0.0, 1.0)).g;\n" +
        "  float b = texture(uTexSampler, clamp(uvB, 0.0, 1.0)).b;\n" +
        "  fragColor = vec4(vec3(r, g, b) * progress, 1.0);\n}"

    // ─── Transition-OUT shaders (applied at end of outgoing clip) ──

    // Shared transition-out header: includes uClipDurationUs for end-of-clip timing
    private const val HO = "#version 300 es\nprecision mediump float;\n" +
        "uniform sampler2D uTexSampler;\nin vec2 vTexCoord;\nout vec4 fragColor;\n" +
        "uniform float uDurationUs;\nuniform float uClipDurationUs;\nuniform float uTime;\n"

    fun transitionFadeOut(durationUs: Float, clipDurationUs: Float, fadeToWhite: Boolean = false) = ShaderEffect(
        if (fadeToWhite) FRAG_FADE_OUT_WHITE else FRAG_FADE_OUT_BLACK,
        mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs)
    )

    fun transitionWipeOut(durationUs: Float, clipDurationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_WIPE_OUT, mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs,
            "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionSlideOut(durationUs: Float, clipDurationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_SLIDE_OUT, mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs,
            "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionCircleClose(durationUs: Float, clipDurationUs: Float) = ShaderEffect(
        FRAG_CIRCLE_CLOSE, mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs)
    )

    fun transitionZoomOutExit(durationUs: Float, clipDurationUs: Float) = ShaderEffect(
        FRAG_ZOOM_OUT_EXIT, mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs)
    )

    fun transitionSpinOut(durationUs: Float, clipDurationUs: Float) = ShaderEffect(
        FRAG_SPIN_OUT, mapOf("uDurationUs" to durationUs, "uClipDurationUs" to clipDurationUs)
    )

    private const val FRAG_FADE_OUT_BLACK = HO +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = c; return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(c.rgb * (1.0 - progress), c.a);\n}"

    private const val FRAG_FADE_OUT_WHITE = HO +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = c; return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(c.rgb, vec3(1.0), progress), c.a);\n}"

    private const val FRAG_WIPE_OUT = HO +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = c; return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  float pos = vTexCoord.x * uDirX + vTexCoord.y * uDirY;\n" +
        "  float lo = min(uDirX, 0.0) + min(uDirY, 0.0);\n" +
        "  float hi = max(uDirX, 0.0) + max(uDirY, 0.0);\n" +
        "  float edge = (pos - lo) / max(hi - lo, 0.001);\n" +
        "  float p = progress * 1.04 - 0.02;\n" +
        "  float conceal = smoothstep(p - 0.02, p + 0.02, edge);\n" +
        "  fragColor = vec4(c.rgb * conceal, c.a);\n}"

    private const val FRAG_SLIDE_OUT = HO +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = texture(uTexSampler, vTexCoord); return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 offset = vec2(uDirX, uDirY) * progress;\n" +
        "  vec2 uv = vTexCoord + offset;\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)\n" +
        "    fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_CIRCLE_CLOSE = HO +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = c; return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  float dist = length(vTexCoord - 0.5);\n" +
        "  float radius = (1.0 - progress) * 0.75;\n" +
        "  float mask = 1.0 - smoothstep(radius - 0.02, radius + 0.02, dist);\n" +
        "  fragColor = vec4(c.rgb * mask, c.a);\n}"

    private const val FRAG_ZOOM_OUT_EXIT = HO +
        "void main() {\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = texture(uTexSampler, vTexCoord); return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  float scale = mix(1.0, 3.0, progress);\n" +
        "  vec2 uv = (vTexCoord - 0.5) * scale + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * (1.0 - progress), c.a);\n}"

    private const val FRAG_SPIN_OUT = HO +
        "void main() {\n" +
        "  float timeUs = uTime * 1000000.0;\n" +
        "  float transStart = uClipDurationUs - uDurationUs;\n" +
        "  if (timeUs < transStart) { fragColor = texture(uTexSampler, vTexCoord); return; }\n" +
        "  float progress = clamp((timeUs - transStart) / uDurationUs, 0.0, 1.0);\n" +
        "  float angle = progress * 6.28318;\n" +
        "  float sc = max(1.0 - progress, 0.01);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float cs = cos(angle), sn = sin(angle);\n" +
        "  uv = vec2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs) / sc + 0.5;\n" +
        "  vec4 col = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(col.rgb * (1.0 - progress), col.a);\n}"

    // ─── Mask fragment shaders ───────────────────────────────────────

    private const val FRAG_RECT_MASK = H +
        "uniform float uMaskX, uMaskY, uMaskW, uMaskH;\n" +
        "uniform float uFeather, uInverted;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec2 d = abs(vTexCoord - vec2(uMaskX, uMaskY)) - vec2(uMaskW, uMaskH) * 0.5;\n" +
        "  float dist = length(max(d, 0.0));\n" +
        "  float mask = 1.0 - smoothstep(0.0, max(uFeather, 0.001), dist);\n" +
        "  if (uInverted > 0.5) mask = 1.0 - mask;\n" +
        "  fragColor = vec4(c.rgb, c.a * mask);\n}"

    private const val FRAG_ELLIPSE_MASK = H +
        "uniform float uCenterX, uCenterY, uRadiusX, uRadiusY;\n" +
        "uniform float uFeather, uInverted;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec2 d = (vTexCoord - vec2(uCenterX, uCenterY)) / vec2(max(uRadiusX, 0.001), max(uRadiusY, 0.001));\n" +
        "  float dist = length(d) - 1.0;\n" +
        "  float mask = 1.0 - smoothstep(0.0, max(uFeather, 0.001) * 5.0, dist);\n" +
        "  if (uInverted > 0.5) mask = 1.0 - mask;\n" +
        "  fragColor = vec4(c.rgb, c.a * mask);\n}"
}
