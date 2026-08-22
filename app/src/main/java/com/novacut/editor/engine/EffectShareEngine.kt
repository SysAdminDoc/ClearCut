package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.novacut.editor.BuildConfig
import com.novacut.editor.engine.AppLog
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
    companion object {
        private const val TAG = "EffectShareEngine"
        /** Maximum serialized .ncfx size, including any embedded LUT payload. */
        const val MAX_EFFECT_SHARE_BYTES = 8_000_000L
        private const val MAX_EMBEDDED_LUT_BYTES = 5_000_000L
        private const val MAX_EMBEDDED_LUT_BASE64_CHARS = 6_666_668
        private val ALLOWED_LUT_EXTENSIONS = setOf("cube", "3dl")
        private val BASE64_PATTERN = Regex("[A-Za-z0-9+/]*={0,2}")
    }

    data class EmbeddedLut(
        val fileName: String,
        val bytes: ByteArray,
    )

    private data class EmbeddedLutParse(
        val payload: EmbeddedLut? = null,
        val invalid: Boolean = false,
    )

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
            val embeddedLut = colorGrade
                ?.takeIf { it.enabled }
                ?.lutPath
                ?.let(::readLutPayload)
            val json = JSONObject().apply {
                put("name", name)
                put("version", 1)
                put("type", "clearcut_effects")
                put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
                put("packType", DeclarativePackKind.EFFECT.wireName)
                put("minAppVersion", BuildConfig.VERSION_NAME)
                put("requiredCapabilities", JSONArray().apply {
                    put(DeclarativePackContract.EFFECT_PACK_CAPABILITY)
                    if (audioEffects.isNotEmpty()) {
                        put(DeclarativePackContract.AUDIO_EFFECTS_CAPABILITY)
                    }
                    if (embeddedLut != null) {
                        put(DeclarativePackContract.EMBEDDED_LUT_CAPABILITY)
                    }
                })
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
                        embeddedLut?.let { lut ->
                            put("lutFileName", lut.fileName)
                            put("lutBase64", Base64.encodeToString(lut.bytes, Base64.NO_WRAP))
                        }
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

            val serialized = json.toString(2)
            if (serialized.toByteArray(Charsets.UTF_8).size.toLong() > MAX_EFFECT_SHARE_BYTES) {
                AppLog.w(TAG, "Effect share exceeds ${MAX_EFFECT_SHARE_BYTES / 1_000_000}MB limit")
                return@withContext null
            }
            val sanitized = sanitizeFileName(name, fallback = "effects", maxLength = 50)
            val file = File(shareDir, "${sanitized}_${System.currentTimeMillis()}.ncfx")
            writeUtf8TextAtomically(file, serialized)
            file
        } catch (e: Exception) {
            AppLog.e(TAG, "Export effects failed", e)
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
        MISSING_MANIFEST_FIELDS,
        UNKNOWN_REQUIRED_CAPABILITY,
        INCOMPATIBLE_APP_VERSION,
        UNSAFE_CONTENT,
        MISSING_CONTENT_HASH,
        HASH_MISMATCH,
        INVALID_ENTRY,
        INVALID_LUT,
    }

    data class EffectPackValidation(
        val imported: ImportedEffects? = null,
        val failure: EffectPackFailure = EffectPackFailure.NONE,
        val schemaVersion: Int = DeclarativePackContract.LEGACY_SCHEMA_VERSION,
        val contentHash: String? = null,
        val provenanceSource: String? = null,
        val minAppVersion: String? = null,
        val requiredCapabilities: Set<String> = emptySet(),
        val warnings: List<String> = emptyList(),
        val reasonCode: String = failure.name,
    )

    /** Read-only validation for the Projects import preview. */
    suspend fun validateEffects(uri: Uri): EffectPackValidation = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                readUtf8WithByteLimit(stream, MAX_EFFECT_SHARE_BYTES)
            } ?: return@withContext EffectPackValidation(failure = EffectPackFailure.UNREADABLE)
            validateEffectsJson(json)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to validate effect pack", e)
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
            validateEffectsJson(json).imported?.let(::installEmbeddedLut)
        } catch (e: Exception) {
            AppLog.e(TAG, "Import effects failed", e)
            null
        }
    }

    /**
     * Import effects from a .ncfx file.
     */
    suspend fun importEffects(file: File): ImportedEffects? = withContext(Dispatchers.IO) {
        try {
            if (file.length() > MAX_EFFECT_SHARE_BYTES) {
                AppLog.w(TAG, "Effect share file exceeds ${MAX_EFFECT_SHARE_BYTES / 1_000_000}MB limit")
                return@withContext null
            }
            validateEffectsJson(file.inputStream().use { input ->
                readUtf8WithByteLimit(input, MAX_EFFECT_SHARE_BYTES)
            }).imported?.let(::installEmbeddedLut)
        } catch (e: Exception) {
            AppLog.e(TAG, "Import effects failed", e)
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
                DeclarativePackIssue.MISSING_MANIFEST_FIELDS -> EffectPackFailure.MISSING_MANIFEST_FIELDS
                DeclarativePackIssue.UNKNOWN_REQUIRED_CAPABILITY -> EffectPackFailure.UNKNOWN_REQUIRED_CAPABILITY
                DeclarativePackIssue.INCOMPATIBLE_APP_VERSION -> EffectPackFailure.INCOMPATIBLE_APP_VERSION
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
                    minAppVersion = envelope.minAppVersion,
                    requiredCapabilities = envelope.requiredCapabilities,
                    warnings = envelope.warnings,
                    reasonCode = envelope.reasonCode,
                )
            }
            if (json.optString("type") != "clearcut_effects") {
                return EffectPackValidation(
                    failure = EffectPackFailure.WRONG_KIND,
                    schemaVersion = envelope.schemaVersion,
                    contentHash = envelope.contentHash,
                    provenanceSource = envelope.source,
                    minAppVersion = envelope.minAppVersion,
                    requiredCapabilities = envelope.requiredCapabilities,
                    warnings = listOf("This JSON document is not a ClearCut effect pack."),
                    reasonCode = EffectPackFailure.WRONG_KIND.name,
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
                        AppLog.w(TAG, "Unknown effect type in JSON", e)
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
            val embeddedLutParse = cg?.let(::parseEmbeddedLut) ?: EmbeddedLutParse()
            if (embeddedLutParse.invalid) {
                return EffectPackValidation(
                    failure = EffectPackFailure.INVALID_LUT,
                    schemaVersion = envelope.schemaVersion,
                    contentHash = envelope.contentHash,
                    provenanceSource = envelope.source,
                    minAppVersion = envelope.minAppVersion,
                    requiredCapabilities = envelope.requiredCapabilities,
                    warnings = listOf("Embedded LUT is malformed, oversized, or uses an unsupported format."),
                )
            }
            val embeddedLut = embeddedLutParse.payload
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
                    // Embedded LUTs are installed after validation/import. Legacy
                    // filename-only references are accepted only when that exact
                    // filename already exists in the app-local LUT registry.
                    lutPath = if (embeddedLut != null) {
                        null
                    } else {
                        cg.optString("lutFileName", "")
                            .ifEmpty { cg.optString("lutPath", "") }
                            .ifEmpty { null }
                            ?.let(::normalizeImportedLutPath)
                            ?.takeIf { it.isFile }
                            ?.absolutePath
                    },
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
                        AppLog.w(TAG, "Unknown audio effect type in JSON", e)
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
                    minAppVersion = envelope.minAppVersion,
                    requiredCapabilities = envelope.requiredCapabilities,
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
                embeddedLut = embeddedLut,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash.orEmpty(),
                provenanceSource = envelope.source,
                minAppVersion = envelope.minAppVersion,
                requiredCapabilities = envelope.requiredCapabilities,
            )
            EffectPackValidation(
                imported = imported,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash,
                provenanceSource = envelope.source,
                minAppVersion = envelope.minAppVersion,
                requiredCapabilities = envelope.requiredCapabilities,
                warnings = warnings,
                reasonCode = envelope.reasonCode,
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Parse failed", e)
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

    private fun readLutPayload(rawPath: String): EmbeddedLut? {
        val source = File(rawPath)
        val fileName = normalizeLutFileName(source.name) ?: return null
        if (!source.isFile || source.length() <= 0L || source.length() > MAX_EMBEDDED_LUT_BYTES) return null
        val bytes = runCatching {
            ByteArrayOutputStream(source.length().toInt()).also { output ->
                source.inputStream().use { input ->
                    copyWithLimit(input, output, MAX_EMBEDDED_LUT_BYTES)
                }
            }.toByteArray()
        }.getOrNull() ?: return null
        val payload = EmbeddedLut(fileName = fileName, bytes = bytes)
        return payload.takeIf { parseLut(source) != null }
    }

    private fun parseEmbeddedLut(colorGrade: JSONObject): EmbeddedLutParse {
        if (!colorGrade.has("lutBase64")) return EmbeddedLutParse()
        val rawBase64 = colorGrade.opt("lutBase64") as? String ?: return EmbeddedLutParse(invalid = true)
        val compactBase64 = rawBase64.filterNot { it.isWhitespace() }
        val fileName = normalizeLutFileName(colorGrade.optString("lutFileName", ""))
            ?: return EmbeddedLutParse(invalid = true)
        if (
            compactBase64.isEmpty() ||
            compactBase64.length > MAX_EMBEDDED_LUT_BASE64_CHARS ||
            !BASE64_PATTERN.matches(compactBase64)
        ) {
            return EmbeddedLutParse(invalid = true)
        }
        val bytes = runCatching { Base64.decode(compactBase64, Base64.DEFAULT) }.getOrNull()
            ?: return EmbeddedLutParse(invalid = true)
        if (
            bytes.isEmpty() ||
            bytes.size.toLong() > MAX_EMBEDDED_LUT_BYTES ||
            Base64.encodeToString(bytes, Base64.NO_WRAP) != compactBase64
        ) {
            return EmbeddedLutParse(invalid = true)
        }
        val payload = EmbeddedLut(fileName = fileName, bytes = bytes)
        return if (isValidLutPayload(payload)) {
            EmbeddedLutParse(payload = payload)
        } else {
            EmbeddedLutParse(invalid = true)
        }
    }

    private fun isValidLutPayload(payload: EmbeddedLut): Boolean {
        val extension = payload.fileName.substringAfterLast('.', "").lowercase()
        val validationDir = File(context.cacheDir, "effect-lut-validation").also { it.mkdirs() }
        val temporary = runCatching {
            File.createTempFile("ncfx-lut-", ".${extension}", validationDir)
        }.getOrNull() ?: return false
        return try {
            temporary.outputStream().use { it.write(payload.bytes) }
            parseLut(temporary) != null
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    private fun installEmbeddedLut(imported: ImportedEffects): ImportedEffects? {
        val embeddedLut = imported.embeddedLut ?: return imported
        val grade = imported.colorGrade ?: return null
        val digest = DeclarativePackContract.sha256Hex(embeddedLut.bytes).take(16)
        val targetName = sanitizeFileNamePreservingExtension(
            raw = "ncfx_${digest}_${embeddedLut.fileName}",
            fallbackStem = "embedded_lut",
            maxLength = 80,
        )
        val lutDir = File(context.filesDir, "luts").also { it.mkdirs() }
        val target = File(lutDir, targetName)
        var replaced = false
        val installed = runCatching {
            writeFileAtomically(target, requireNonEmpty = true) { temporary ->
                temporary.outputStream().use { it.write(embeddedLut.bytes) }
            }
            replaced = true
            parseLut(target) != null
        }.getOrDefault(false)
        if (!installed && replaced) {
            target.delete()
            return null
        }
        if (!installed) return null
        return imported.copy(
            colorGrade = grade.copy(lutPath = target.absolutePath),
            embeddedLut = null,
        )
    }

    private fun parseLut(file: File): LutEngine.Lut3D? = when (file.extension.lowercase()) {
        "cube" -> LutEngine.parseCube(file)
        "3dl" -> LutEngine.parse3dl(file)
        else -> null
    }

    private fun normalizeLutFileName(rawPath: String): String? {
        val safeName = sanitizeFileNamePreservingExtension(
            raw = File(rawPath).name,
            fallbackStem = "lut",
            maxLength = 80,
        )
        return safeName.takeIf { it.substringAfterLast('.', "").lowercase() in ALLOWED_LUT_EXTENSIONS }
    }

    private fun normalizeImportedLutPath(rawPath: String): File? {
        return normalizeLutFileName(rawPath)?.let { File(File(context.filesDir, "luts"), it) }
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
    val embeddedLut: EffectShareEngine.EmbeddedLut? = null,
    val schemaVersion: Int = DeclarativePackContract.LEGACY_SCHEMA_VERSION,
    val contentHash: String = "",
    val provenanceSource: String? = null,
    val minAppVersion: String? = null,
    val requiredCapabilities: Set<String> = emptySet(),
)
