package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * R5.7b — Read-only OpenFX effect descriptor.
 *
 * NovaCut does **not** host the full OpenFX runtime on Android (that would
 * mean embedding C++ plugin loading, which is out of scope for mobile). The
 * goal is narrower: define a small JSON descriptor that maps a NovaCut effect's
 * parameters to OpenFX-named parameters so a future NLE round-trip pass
 * (C.14 — FCPXML / OTIO import) can preserve effect intent across imports
 * into DaVinci Resolve / Premiere / Final Cut.
 *
 * The descriptor is *one file per effect*. It is referenced by name from a
 * NovaCut effect chain (`.ncfx`, see [PluginRegistry.Kind.EFFECT_PACK]) and
 * carried alongside it in the same share container.
 *
 * ## File shape (`.ncfxd`)
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "novaCutEffectId": "gaussian_blur",
 *   "openfxId": "uk.co.thefoundry.OfxImageEffectGaussianBlur",
 *   "displayName": "Gaussian Blur",
 *   "parameters": [
 *     { "novaCutName": "radius", "openfxName": "size",
 *       "novaCutRange": [0.0, 50.0], "openfxRange": [0.0, 100.0],
 *       "scale": 2.0, "offset": 0.0, "type": "double" }
 *   ]
 * }
 * ```
 *
 * ## What this class is not
 *
 *  - Not a runtime loader. The actual effect runs through NovaCut's existing
 *    GLSL shader pipeline; the descriptor is metadata only.
 *  - Not a way to install third-party effects. We control the effect set;
 *    descriptors map an existing NovaCut effect to its OpenFX equivalent.
 *  - Not a substitute for full FCPXML compatibility. It complements C.14 by
 *    carrying effect-intent metadata across the round trip.
 */
data class OpenFxDescriptor(
    val schemaVersion: Int,
    val novaCutEffectId: String,
    val openfxId: String,
    val displayName: String,
    val parameters: List<ParameterMapping>,
) {

    /**
     * Maps a single NovaCut effect parameter to its OpenFX equivalent.
     *
     * @param scale linear gain applied to the NovaCut value before passing
     *   to OpenFX: `openfx = novaCut * scale + offset`.
     * @param type OpenFX parameter type: "double", "integer", "boolean",
     *   "rgba", "string", "choice". Mirrors OpenFX kOfxParamTypeXxx constants.
     */
    data class ParameterMapping(
        val novaCutName: String,
        val openfxName: String,
        val novaCutRange: ClosedFloatingPointRange<Double>,
        val openfxRange: ClosedFloatingPointRange<Double>,
        val scale: Double = 1.0,
        val offset: Double = 0.0,
        val type: String = "double",
    ) {
        /** Convert a NovaCut parameter value into the OpenFX equivalent. */
        fun toOpenFx(novaCutValue: Double): Double = novaCutValue * scale + offset

        /** Convert an OpenFX value back into the NovaCut equivalent. */
        fun fromOpenFx(openFxValue: Double): Double =
            if (scale == 0.0) novaCutRange.start else (openFxValue - offset) / scale
    }

    /** Serialize to canonical JSON. */
    fun toJson(): String {
        val root = JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("novaCutEffectId", novaCutEffectId)
            .put("openfxId", openfxId)
            .put("displayName", displayName)
        val params = JSONArray()
        for (p in parameters) {
            params.put(
                JSONObject()
                    .put("novaCutName", p.novaCutName)
                    .put("openfxName", p.openfxName)
                    .put(
                        "novaCutRange",
                        JSONArray().apply {
                            put(p.novaCutRange.start)
                            put(p.novaCutRange.endInclusive)
                        }
                    )
                    .put(
                        "openfxRange",
                        JSONArray().apply {
                            put(p.openfxRange.start)
                            put(p.openfxRange.endInclusive)
                        }
                    )
                    .put("scale", p.scale)
                    .put("offset", p.offset)
                    .put("type", p.type)
            )
        }
        root.put("parameters", params)
        return root.toString(2)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Parse a JSON descriptor. Returns null on malformed input — the
         * caller is responsible for the "this is the wrong file kind"
         * messaging. Permissive: unknown fields are ignored so the format
         * can grow without breaking older readers.
         */
        fun fromJson(json: String): OpenFxDescriptor? {
            return try {
                val root = JSONObject(json)
                val schema = root.optInt("schemaVersion", -1)
                if (schema < 1 || schema > CURRENT_SCHEMA_VERSION) return null
                val novaCutEffectId = root.optString("novaCutEffectId", "").ifBlank { return null }
                val openfxId = root.optString("openfxId", "").ifBlank { return null }
                val displayName = root.optString("displayName", "")
                val paramsArr = root.optJSONArray("parameters") ?: JSONArray()
                val parameters = (0 until paramsArr.length()).mapNotNull { i ->
                    val p = paramsArr.optJSONObject(i) ?: return@mapNotNull null
                    val novaCutName = p.optString("novaCutName").ifBlank { return@mapNotNull null }
                    val openfxName = p.optString("openfxName").ifBlank { return@mapNotNull null }
                    val ncRange = p.optJSONArray("novaCutRange")
                    val ofxRange = p.optJSONArray("openfxRange")
                    if (ncRange == null || ofxRange == null) return@mapNotNull null
                    if (ncRange.length() != 2 || ofxRange.length() != 2) return@mapNotNull null
                    val ncStart = ncRange.optDouble(0, Double.NaN)
                    val ncEnd = ncRange.optDouble(1, Double.NaN)
                    val ofxStart = ofxRange.optDouble(0, Double.NaN)
                    val ofxEnd = ofxRange.optDouble(1, Double.NaN)
                    if (ncStart.isNaN() || ncEnd.isNaN() || ofxStart.isNaN() || ofxEnd.isNaN()) {
                        return@mapNotNull null
                    }
                    if (ncStart > ncEnd || ofxStart > ofxEnd) return@mapNotNull null
                    ParameterMapping(
                        novaCutName = novaCutName,
                        openfxName = openfxName,
                        novaCutRange = ncStart..ncEnd,
                        openfxRange = ofxStart..ofxEnd,
                        scale = p.optDouble("scale", 1.0),
                        offset = p.optDouble("offset", 0.0),
                        type = p.optString("type", "double"),
                    )
                }
                OpenFxDescriptor(
                    schemaVersion = schema,
                    novaCutEffectId = novaCutEffectId,
                    openfxId = openfxId,
                    displayName = displayName,
                    parameters = parameters,
                )
            } catch (_: org.json.JSONException) {
                null
            }
        }
    }
}
