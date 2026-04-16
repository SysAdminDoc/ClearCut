package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine for exporting and importing effect chains, color grades, and LUTs
 * as shareable .ncfx (NovaCut Effects) JSON files.
 */
@Singleton
class EffectShareEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val MAX_EFFECT_SHARE_BYTES = 1_000_000L
    }

    private val shareDir = File(context.filesDir, "shared_effects").also { it.mkdirs() }

    /**
     * Export a clip's effects + color grade as a shareable .ncfx file.
     */
    suspend fun exportEffects(
        name: String,
        effects: List<Effect>,
        colorGrade: ColorGrade?,
        audioEffects: List<AudioEffect> = emptyList()
    ): File? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("version", 1)
                put("type", "novacut_effects")

                // Effects
                val effectsArr = JSONArray()
                for (effect in effects) {
                    effectsArr.put(JSONObject().apply {
                        put("type", effect.type.name)
                        put("enabled", effect.enabled)
                        val params = JSONObject()
                        effect.params.forEach { (k, v) -> params.put(k, v.toDouble()) }
                        put("params", params)
                    })
                }
                put("effects", effectsArr)

                // Color grade
                if (colorGrade != null && colorGrade.enabled) {
                    put("colorGrade", JSONObject().apply {
                        put("liftR", colorGrade.liftR.toDouble())
                        put("liftG", colorGrade.liftG.toDouble())
                        put("liftB", colorGrade.liftB.toDouble())
                        put("gammaR", colorGrade.gammaR.toDouble())
                        put("gammaG", colorGrade.gammaG.toDouble())
                        put("gammaB", colorGrade.gammaB.toDouble())
                        put("gainR", colorGrade.gainR.toDouble())
                        put("gainG", colorGrade.gainG.toDouble())
                        put("gainB", colorGrade.gainB.toDouble())
                        put("offsetR", colorGrade.offsetR.toDouble())
                        put("offsetG", colorGrade.offsetG.toDouble())
                        put("offsetB", colorGrade.offsetB.toDouble())
                        colorGrade.lutPath?.let { put("lutFileName", java.io.File(it).name) }
                        put("lutIntensity", colorGrade.lutIntensity.toDouble())
                    })
                }

                // Audio effects
                if (audioEffects.isNotEmpty()) {
                    val audioArr = JSONArray()
                    for (ae in audioEffects) {
                        audioArr.put(JSONObject().apply {
                            put("type", ae.type.name)
                            put("enabled", ae.enabled)
                            val params = JSONObject()
                            ae.params.forEach { (k, v) -> params.put(k, v.toDouble()) }
                            put("params", params)
                        })
                    }
                    put("audioEffects", audioArr)
                }
            }

            val sanitized = sanitizeFileName(name, fallback = "effects", maxLength = 50)
            val file = File(shareDir, "${sanitized}_${System.currentTimeMillis()}.ncfx")
            file.writeText(json.toString(2), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            Log.e("EffectShareEngine", "Export effects failed", e)
            null
        }
    }

    /**
     * Import effects from a .ncfx file URI.
     */
    suspend fun importEffects(uri: Uri): ImportedEffects? = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                readUtf8WithByteLimit(stream, MAX_EFFECT_SHARE_BYTES)
            } ?: return@withContext null
            parseEffectsJson(json)
        } catch (e: Exception) {
            Log.e("EffectShareEngine", "Import effects failed", e)
            null
        }
    }

    /**
     * Import effects from a .ncfx file.
     */
    suspend fun importEffects(file: File): ImportedEffects? = withContext(Dispatchers.IO) {
        try {
            if (file.length() > MAX_EFFECT_SHARE_BYTES) {
                Log.w("EffectShareEngine", "Effect share file exceeds 1MB limit")
                return@withContext null
            }
            parseEffectsJson(file.inputStream().use { input ->
                readUtf8WithByteLimit(input, MAX_EFFECT_SHARE_BYTES)
            })
        } catch (e: Exception) {
            Log.e("EffectShareEngine", "Import effects failed", e)
            null
        }
    }

    private fun parseEffectsJson(jsonStr: String): ImportedEffects? {
        return try {
            val json = JSONObject(jsonStr)
            if (json.optString("type") != "novacut_effects") return null

            val name = json.optString("name", "Imported")

            // Parse effects
            val effects = mutableListOf<Effect>()
            val effectsArr = json.optJSONArray("effects")
            if (effectsArr != null) {
                for (i in 0 until effectsArr.length()) {
                    val eo = effectsArr.getJSONObject(i)
                    val type = try { EffectType.valueOf(eo.getString("type")) } catch (e: Exception) { Log.w("EffectShareEngine", "Unknown effect type in JSON", e); continue }
                    val params = mutableMapOf<String, Float>()
                    val po = eo.optJSONObject("params")
                    if (po != null) {
                        po.keys().forEach { k -> params[k] = po.getDouble(k).toFloat() }
                    }
                    effects.add(Effect(type = type, enabled = eo.optBoolean("enabled", true), params = params))
                }
            }

            // Parse color grade
            var colorGrade: ColorGrade? = null
            val cg = json.optJSONObject("colorGrade")
            if (cg != null) {
                colorGrade = ColorGrade(
                    enabled = true,
                    liftR = cg.optDouble("liftR", 0.0).toFloat(),
                    liftG = cg.optDouble("liftG", 0.0).toFloat(),
                    liftB = cg.optDouble("liftB", 0.0).toFloat(),
                    gammaR = cg.optDouble("gammaR", 1.0).toFloat(),
                    gammaG = cg.optDouble("gammaG", 1.0).toFloat(),
                    gammaB = cg.optDouble("gammaB", 1.0).toFloat(),
                    gainR = cg.optDouble("gainR", 1.0).toFloat(),
                    gainG = cg.optDouble("gainG", 1.0).toFloat(),
                    gainB = cg.optDouble("gainB", 1.0).toFloat(),
                    offsetR = cg.optDouble("offsetR", 0.0).toFloat(),
                    offsetG = cg.optDouble("offsetG", 0.0).toFloat(),
                    offsetB = cg.optDouble("offsetB", 0.0).toFloat(),
                    lutPath = cg.optString("lutFileName", "")
                        .ifEmpty { cg.optString("lutPath", "") }
                        .ifEmpty { null }
                        ?.let(::normalizeImportedLutPath)
                        ?.absolutePath,
                    lutIntensity = cg.optDouble("lutIntensity", 1.0).toFloat()
                )
            }

            // Parse audio effects
            val audioEffects = mutableListOf<AudioEffect>()
            val audioArr = json.optJSONArray("audioEffects")
            if (audioArr != null) {
                for (i in 0 until audioArr.length()) {
                    val ao = audioArr.getJSONObject(i)
                    val type = try { AudioEffectType.valueOf(ao.getString("type")) } catch (e: Exception) { Log.w("EffectShareEngine", "Unknown audio effect type in JSON", e); continue }
                    val params = mutableMapOf<String, Float>()
                    val po = ao.optJSONObject("params")
                    if (po != null) {
                        po.keys().forEach { k -> params[k] = po.getDouble(k).toFloat() }
                    }
                    audioEffects.add(AudioEffect(type = type, enabled = ao.optBoolean("enabled", true), params = params))
                }
            }

            ImportedEffects(name, effects, colorGrade, audioEffects)
        } catch (e: Exception) {
            Log.e("EffectShareEngine", "Parse failed", e)
            null
        }
    }

    /**
     * List all locally saved .ncfx files.
     */
    fun listSavedEffects(): List<File> {
        return shareDir.listFiles()?.filter { it.extension == "ncfx" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Delete a saved effects file.
     */
    fun deleteSavedEffects(file: File): Boolean = file.delete()

    private fun normalizeImportedLutPath(rawPath: String): File? {
        val safeName = sanitizeFileNamePreservingExtension(
            raw = File(rawPath).name,
            fallbackStem = "lut",
            maxLength = 80
        )
        return safeName.takeIf { it.contains('.') }?.let { File(File(context.filesDir, "luts"), it) }
    }
}

data class ImportedEffects(
    val name: String,
    val effects: List<Effect>,
    val colorGrade: ColorGrade?,
    val audioEffects: List<AudioEffect>
)
