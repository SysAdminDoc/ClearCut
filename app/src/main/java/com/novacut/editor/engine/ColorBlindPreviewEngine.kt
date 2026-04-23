package com.novacut.editor.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Color-blind preview simulation matrices (Brettel/Viénot/Mollon).
 *
 * Ships as a singleton for DI convenience, but every method is pure — the
 * companion object exposes identical entry points so callers that do not
 * have the injected instance (for example the `VideoEngine` preview chain)
 * can build shader sources directly.
 *
 * The matrices are approximate sRGB-linearised forms of Brettel 1997. They
 * are intended for creator self-check preview; they are **not** a clinically
 * accurate simulation. Never apply these at export time — the transform is
 * destructive.
 */
@Singleton
class ColorBlindPreviewEngine @Inject constructor() {

    enum class Mode(val displayName: String) {
        OFF("Off"),
        DEUTERANOPIA("Deuteranopia"),
        PROTANOPIA("Protanopia"),
        TRITANOPIA("Tritanopia"),
        ACHROMATOPSIA("Achromatopsia")
    }

    fun matrix(mode: Mode): FloatArray = matrixFor(mode)

    /**
     * Legacy shim kept for downstream callers that generated a bake-constants
     * fragment — new code should go through [ColorBlindGlEffect.create] which
     * uses uniform inputs and shares a single compiled shader across modes.
     */
    fun glslFragment(mode: Mode): String = fragmentSource(mode)

    companion object {
        /** Row-major 3×3 color transform. Row 0 = (m[0], m[1], m[2]) etc. */
        fun matrixFor(mode: Mode): FloatArray = when (mode) {
            Mode.OFF -> floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
            Mode.DEUTERANOPIA -> floatArrayOf(
                0.43f, 0.72f, -0.15f,
                0.34f, 0.57f, 0.09f,
                -0.02f, 0.03f, 1.00f
            )
            Mode.PROTANOPIA -> floatArrayOf(
                0.17f, 0.83f, 0.00f,
                0.17f, 0.83f, 0.00f,
                0.01f, -0.01f, 1.00f
            )
            Mode.TRITANOPIA -> floatArrayOf(
                1.00f, 0.13f, -0.13f,
                0.00f, 0.87f, 0.13f,
                0.00f, 0.69f, 0.31f
            )
            Mode.ACHROMATOPSIA -> floatArrayOf(
                0.299f, 0.587f, 0.114f,
                0.299f, 0.587f, 0.114f,
                0.299f, 0.587f, 0.114f
            )
        }

        private fun fragmentSource(mode: Mode): String {
            val m = matrixFor(mode)
            return """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D uTexSampler;
                void main() {
                    vec4 c = texture(uTexSampler, vTexCoord);
                    mat3 M = mat3(
                        ${m[0]}, ${m[3]}, ${m[6]},
                        ${m[1]}, ${m[4]}, ${m[7]},
                        ${m[2]}, ${m[5]}, ${m[8]}
                    );
                    vec3 o = M * c.rgb;
                    fragColor = vec4(clamp(o, 0.0, 1.0), c.a);
                }
            """.trimIndent()
        }
    }
}
