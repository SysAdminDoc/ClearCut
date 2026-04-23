package com.novacut.editor.engine

import androidx.media3.common.util.UnstableApi

/**
 * Factory for the color-blind preview GlEffect. Wraps the matrix produced by
 * [ColorBlindPreviewEngine] into a `ShaderEffect` the Media3 preview pipeline
 * already knows how to apply.
 *
 * A single shader source is shared across every CVD mode — the 3×3 matrix
 * flows in as nine float uniforms instead of being baked into the source as
 * constants. That lets the driver cache the compiled program and avoids a
 * link-step stall every time the user switches between Deuteranopia /
 * Protanopia / Tritanopia / Achromatopsia.
 *
 * The effect is preview-only — export paths never append it because the
 * transformation would bake the simulated color onto the output file.
 */
object ColorBlindGlEffect {

    @UnstableApi
    fun create(mode: ColorBlindPreviewEngine.Mode): ShaderEffect? {
        if (mode == ColorBlindPreviewEngine.Mode.OFF) return null
        val m = ColorBlindPreviewEngine.matrixFor(mode)
        return ShaderEffect(FRAG, mapOf(
            "uM00" to m[0], "uM01" to m[1], "uM02" to m[2],
            "uM10" to m[3], "uM11" to m[4], "uM12" to m[5],
            "uM20" to m[6], "uM21" to m[7], "uM22" to m[8]
        ))
    }

    /**
     * Single fragment shader used for every mode. GLSL `mat3(...)` takes
     * its nine arguments in column-major order, so the per-column packing
     * below reconstructs the original row-major matrix exactly when the
     * uniforms are written row-by-row.
     */
    private const val FRAG = """#version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexSampler;
        uniform float uM00, uM01, uM02;
        uniform float uM10, uM11, uM12;
        uniform float uM20, uM21, uM22;
        void main() {
            vec4 c = texture(uTexSampler, vTexCoord);
            mat3 M = mat3(
                uM00, uM10, uM20,
                uM01, uM11, uM21,
                uM02, uM12, uM22
            );
            vec3 o = M * c.rgb;
            fragColor = vec4(clamp(o, 0.0, 1.0), c.a);
        }"""
}
