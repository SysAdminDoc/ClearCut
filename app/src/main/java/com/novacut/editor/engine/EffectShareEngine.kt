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
 * as shareable .ncfx (ClearCut Effects) JSON files.
 */
@Singleton
class EffectShareEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val TAG = "EffectShareEngine"
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
                put("type", "clearcut_effects")
                put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
                put("packType", DeclarativePackKind.EFFECT.wireName)
                put("provenance", JSONObject().put("source", "ClearCut effect export"))

                // Effects
                val effectsArr = JSONArray()
                for (effect in effects) {
                    effectsArr.put(JSONObject().apply {
                        put("type", effect.type.name)
                        put("enabled", effect.enabled)
                        val params = JSONObject()
                        effect.params.forEach { (k, v) -> params.putSafeFloat(k, v) }
                        put("params", params)
                    })
                }
                put("effects", effectsArr)

                // Color grade
                if (colorGrade != null && colorGrade.enabled) {
                    put("colorGrade", JSONObject().apply {
                        putSafeFloat("liftR", colorGrade.liftR)
                        putSafeFloat("liftG", colorGrade.liftG)
                        putSafeFloat("liftB", colorGrade.liftB)
                        putSafeFloat("gammaR", colorGrade.gammaR, default = 1f)
                        putSafeFloat("gammaG", colorGrade.gammaG, default = 1f)
                        putSafeFloat("gammaB", colorGrade.gammaB, default = 1f)
                        putSafeFloat("gainR", colorGrade.gainR, default = 1f)
                        putSafeFloat("gainG", colorGrade.gainG, default = 1f)
                        putSafeFloat("gainB", colorGrade.gainB, default = 1f)
                        putSafeFloat("offsetR", colorGrade.offsetR)
                        putSafeFloat("offsetG", colorGrade.offsetG)
                        putSafeFloat("offsetB", colorGrade.offsetB)
                        colorGrade.lutPath?.let { put("lutFileName", java.io.File(it).name) }
                        putSafeFloat("lutIntensity", colorGrade.lutIntensity, default = 1f)
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
                            ae.params.forEach { (k, v) -> params.putSafeFloat(k, v) }
                            put("params", params)
                        })
                    }
                    put("audioEffects", audioArr)
                }
                put("contentHash", DeclarativePackContract.contentHash(this))
            }

            val sanitized = sanitizeFileName(name, fallback = "effects", maxLength = 50)
            val file = File(shareDir, "${sanitized}_${System.currentTimeMillis()}.ncfx")
            writeUtf8TextAtomically(file, json.toString(2))
            file
        } catch (e: Exception) {
            Log.e(TAG, "Export effects failed", e)
            null
        }
    }

    enum class EffectPackFailure {
        NONE,
        UNREADABLE,
        INVALID_JSON,
        INVALID_SCHEMA,
        WRONG_KIND,
        INCOMPATIBLE_VERSION,
        UNSAFE_CONTENT,
        MISSING_CONTENT_HASH,
        HASH_MISMATCH,
        INVALID_ENTRY,
    }

    data class EffectPackValidation(
        val imported: ImportedEffects? = null,
        val failure: EffectPackFailure = EffectPackFailure.NONE,
        val schemaVersion: Int = DeclarativePackContract.LEGACY_SCHEMA_VERSION,
        val contentHash: String? = null,
        val provenanceSource: String? = null,
        val warnings: List<String> = emptyList(),
    )

    /** Read-only validation for the Projects import preview. */
    suspend fun validateEffects(uri: Uri): EffectPackValidation = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                readUtf8WithByteLimit(stream, MAX_EFFECT_SHARE_BYTES)
            } ?: return@withContext EffectPackValidation(failure = EffectPackFailure.UNREADABLE)
            validateEffectsJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to validate effect pack", e)
            EffectPackValidation(failure = EffectPackFailure.UNREADABLE)
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
            validateEffectsJson(json).imported
        } catch (e: Exception) {
            Log.e(TAG, "Import effects failed", e)
            null
        }
    }

    /**
     * Import effects from a .ncfx file.
     */
    suspend fun importEffects(file: File): ImportedEffects? = withContext(Dispatchers.IO) {
        try {
            if (file.length() > MAX_EFFECT_SHARE_BYTES) {
                Log.w(TAG, "Effect share file exceeds 1MB limit")
                return@withContext null
            }
            validateEffectsJson(file.inputStream().use { input ->
                readUtf8WithByteLimit(input, MAX_EFFECT_SHARE_BYTES)
            }).imported
        } catch (e: Exception) {
            Log.e(TAG, "Import effects failed", e)
            null
        }
    }

    fun validateEffectsJson(jsonStr: String): EffectPackValidation {
        return try {
            val json = JSONObject(jsonStr)
            val envelope = DeclarativePackContract.inspect(json, DeclarativePackKind.EFFECT)
            val contractFailure = when (envelope.issue) {
                DeclarativePackIssue.NONE -> null
                DeclarativePackIssue.INVALID_SCHEMA -> EffectPackFailure.INVALID_SCHEMA
                DeclarativePackIssue.FUTURE_SCHEMA -> EffectPackFailure.INCOMPATIBLE_VERSION
                DeclarativePackIssue.WRONG_KIND -> EffectPackFailure.WRONG_KIND
                DeclarativePackIssue.MISSING_CONTENT_HASH -> EffectPackFailure.MISSING_CONTENT_HASH
                DeclarativePackIssue.HASH_MISMATCH -> EffectPackFailure.HASH_MISMATCH
                DeclarativePackIssue.EXECUTABLE_CONTENT -> EffectPackFailure.UNSAFE_CONTENT
            }
            if (contractFailure != null) {
                return EffectPackValidation(
                    failure = contractFailure,
                    schemaVersion = envelope.schemaVersion,
                    contentHash = envelope.contentHash,
                    provenanceSource = envelope.source,
                    warnings = envelope.warnings,
                )
            }
            if (json.optString("type") != "clearcut_effects") {
                return EffectPackValidation(
                    failure = EffectPackFailure.WRONG_KIND,
                    schemaVersion = envelope.schemaVersion,
                    contentHash = envelope.contentHash,
                    provenanceSource = envelope.source,
                    warnings = listOf("This JSON document is not a ClearCut effect pack."),
                )
            }

            val name = json.optString("name", "Imported")
            val strictEntries = envelope.schemaVersion >= DeclarativePackContract.CURRENT_SCHEMA_VERSION
            val warnings = envelope.warnings.toMutableList()
            var invalidEntries = 0

            // Parse effects
            val effects = mutableListOf<Effect>()
            val effectsArr = json.optJSONArray("effects")
            if (effectsArr != null) {
                for (i in 0 until effectsArr.length()) {
                    val eo = effectsArr.optJSONObject(i)
                    if (eo == null) {
                        invalidEntries++
                        continue
                    }
                    val type = try {
                        EffectType.valueOf(eo.getString("type"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Unknown effect type in JSON", e)
                        invalidEntries++
                        continue
                    }
                    val params = mutableMapOf<String, Float>()
                    val po = eo.optJSONObject("params")
                    if (po != null) {
                        po.keys().forEach { k ->
                            params[k] = safeFloat(po.optDouble(k, 0.0), default = 0f)
                        }
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
                    liftR = safeFloat(cg.optDouble("liftR", 0.0), default = 0f),
                    liftG = safeFloat(cg.optDouble("liftG", 0.0), default = 0f),
                    liftB = safeFloat(cg.optDouble("liftB", 0.0), default = 0f),
                    gammaR = safeFloat(cg.optDouble("gammaR", 1.0), default = 1f),
                    gammaG = safeFloat(cg.optDouble("gammaG", 1.0), default = 1f),
                    gammaB = safeFloat(cg.optDouble("gammaB", 1.0), default = 1f),
                    gainR = safeFloat(cg.optDouble("gainR", 1.0), default = 1f),
                    gainG = safeFloat(cg.optDouble("gainG", 1.0), default = 1f),
                    gainB = safeFloat(cg.optDouble("gainB", 1.0), default = 1f),
                    offsetR = safeFloat(cg.optDouble("offsetR", 0.0), default = 0f),
                    offsetG = safeFloat(cg.optDouble("offsetG", 0.0), default = 0f),
                    offsetB = safeFloat(cg.optDouble("offsetB", 0.0), default = 0f),
                    // .ncfx carries only the LUT *filename*, not its bytes, so a
                    // referenced LUT usually does not exist in luts/. Only keep the
                    // path when the file is actually present — otherwise a filename
                    // collision could apply an unrelated project's LUT.
                    lutPath = cg.optString("lutFileName", "")
                        .ifEmpty { cg.optString("lutPath", "") }
                        .ifEmpty { null }
                        ?.let(::normalizeImportedLutPath)
                        ?.takeIf { it.isFile }
                        ?.absolutePath,
                    lutIntensity = safeFloat(cg.optDouble("lutIntensity", 1.0), default = 1f).coerceIn(0f, 1f)
                )
            }

            // Parse audio effects
            val audioEffects = mutableListOf<AudioEffect>()
            val audioArr = json.optJSONArray("audioEffects")
            if (audioArr != null) {
                for (i in 0 until audioArr.length()) {
                    val ao = audioArr.optJSONObject(i)
                    if (ao == null) {
                        invalidEntries++
                        continue
                    }
                    val type = try {
                        AudioEffectType.valueOf(ao.getString("type"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Unknown audio effect type in JSON", e)
                        invalidEntries++
                        continue
                    }
                    val params = mutableMapOf<String, Float>()
                    val po = ao.optJSONObject("params")
                    if (po != null) {
                        po.keys().forEach { k ->
                            params[k] = safeFloat(po.optDouble(k, 0.0), default = 0f)
                        }
                    }
                    audioEffects.add(AudioEffect(type = type, enabled = ao.optBoolean("enabled", true), params = params))
                }
            }

            if (invalidEntries > 0 && strictEntries) {
                return EffectPackValidation(
                    failure = EffectPackFailure.INVALID_ENTRY,
                    schemaVersion = envelope.schemaVersion,
                    contentHash = envelope.contentHash,
                    provenanceSource = envelope.source,
                    warnings = listOf(
                        "Effect pack contains $invalidEntries unsupported or invalid effect entr${if (invalidEntries == 1) "y" else "ies"}."
                    ),
                )
            }
            if (invalidEntries > 0) {
                warnings.add("Skipped $invalidEntries unsupported legacy effect entr${if (invalidEntries == 1) "y" else "ies"}.")
            }
            val imported = ImportedEffects(
                name = name,
                effects = effects,
                colorGrade = colorGrade,
                audioEffects = audioEffects,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash.orEmpty(),
                provenanceSource = envelope.source,
            )
            EffectPackValidation(
                imported = imported,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash,
                provenanceSource = envelope.source,
                warnings = warnings,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed", e)
            EffectPackValidation(failure = EffectPackFailure.INVALID_JSON)
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
    fun deleteSavedEffects(file: File): Boolean {
        val canonicalShareDir = runCatching { shareDir.canonicalFile }.getOrNull() ?: return false
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (!canonicalFile.toPath().startsWith(canonicalShareDir.toPath())) return false
        if (canonicalFile.extension != "ncfx") return false
        return canonicalFile.delete()
    }

    private fun normalizeImportedLutPath(rawPath: String): File? {
        val safeName = sanitizeFileNamePreservingExtension(
            raw = File(rawPath).name,
            fallbackStem = "lut",
            maxLength = 80
        )
        return safeName.takeIf { it.contains('.') }?.let { File(File(context.filesDir, "luts"), it) }
    }

    private fun safeFloat(value: Double, default: Float): Float {
        val asFloat = value.toFloat()
        return if (asFloat.isFinite()) asFloat else default
    }

    private fun JSONObject.putSafeFloat(name: String, value: Float, default: Float = 0f): JSONObject {
        val fallback = if (default.isFinite()) default else 0f
        return put(name, (if (value.isFinite()) value else fallback).toDouble())
    }
}

data class ImportedEffects(
    val name: String,
    val effects: List<Effect>,
    val colorGrade: ColorGrade?,
    val audioEffects: List<AudioEffect>,
    val schemaVersion: Int = DeclarativePackContract.LEGACY_SCHEMA_VERSION,
    val contentHash: String = "",
    val provenanceSource: String? = null,
)
