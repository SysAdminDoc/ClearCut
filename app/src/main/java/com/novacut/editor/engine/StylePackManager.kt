package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.CaptionAccessibilityPreset
import com.novacut.editor.model.CaptionStyleTemplate
import com.novacut.editor.model.CaptionTemplateType
import com.novacut.editor.model.TextAnimation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StylePack(
    val id: String,
    val name: String,
    val version: Int,
    val author: String,
    val license: String,
    val minAppVersion: String,
    val styles: List<CaptionStyleTemplate>,
)

data class StylePackImportResult(
    val pack: StylePack? = null,
    val failure: StylePackFailure = StylePackFailure.NONE,
    val warnings: List<String> = emptyList(),
)

enum class StylePackFailure {
    NONE,
    UNREADABLE,
    INVALID_JSON,
    MISSING_REQUIRED_FIELDS,
    INCOMPATIBLE_VERSION,
    EMPTY_STYLES,
    DUPLICATE_ID,
    OVERSIZED,
    WRITE_FAILED,
}

@Singleton
class StylePackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StylePackManager"
        private const val MAX_STYLE_PACK_BYTES = 1_000_000L
        private const val SCHEMA_VERSION = 1
        private const val MAX_STYLES_PER_PACK = 50
        private const val MAX_PACK_ID_CHARS = 128
    }

    private val packsDir: File
        get() = File(context.filesDir, "style_packs").also { it.mkdirs() }

    /**
     * Resolve the on-disk file for a pack id, returning null for any id that is
     * not strictly filename-safe. Untrusted `.stylepack` JSON supplies the id,
     * so an unsanitized value like "../../databases/room-projects" would escape
     * `style_packs/` and clobber app-private files.
     */
    private fun packFileForId(id: String): File? {
        val sanitized = id.asSequence()
            .filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
            .take(MAX_PACK_ID_CHARS)
            .joinToString("")
        if (sanitized.isEmpty() || sanitized != id) {
            Log.w(TAG, "Rejected unsafe style pack id")
            return null
        }
        return File(packsDir, "$sanitized.json")
    }

    suspend fun importFromUri(uri: Uri): StylePackImportResult = withContext(Dispatchers.IO) {
        val json = readPackJson(uri) ?: return@withContext StylePackImportResult(failure = StylePackFailure.UNREADABLE)
        importFromJson(json)
    }

    /**
     * Read-only validation of a style pack document. Nothing is written to disk,
     * so this is safe to call from a preview surface and is idempotent.
     */
    suspend fun validateFromUri(uri: Uri): StylePackImportResult = withContext(Dispatchers.IO) {
        val json = readPackJson(uri) ?: return@withContext StylePackImportResult(failure = StylePackFailure.UNREADABLE)
        validateFromJson(json)
    }

    fun validateFromJson(json: String): StylePackImportResult {
        val root = parseRoot(json) ?: return StylePackImportResult(failure = StylePackFailure.INVALID_JSON)
        return validate(root).result
    }

    fun importFromJson(json: String): StylePackImportResult {
        val root = parseRoot(json) ?: return StylePackImportResult(failure = StylePackFailure.INVALID_JSON)
        val validation = validate(root)
        val validated = validation.validated ?: return validation.result
        return install(validated)
    }

    private fun readPackJson(uri: Uri): String? {
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readUtf8WithByteLimit(stream, MAX_STYLE_PACK_BYTES)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read style pack", e)
            return null
        }
        return json?.takeIf { it.isNotBlank() }
    }

    private fun parseRoot(json: String): JSONObject? {
        return try {
            JSONObject(json)
        } catch (e: Exception) {
            Log.w(TAG, "Style pack is not valid JSON", e)
            null
        }
    }

    fun listInstalledPacks(): List<StylePack> {
        val dir = packsDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val root = JSONObject(file.readText())
                    parsePack(root)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read installed pack: ${file.name}", e)
                    null
                }
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun listInstalledStyles(): List<CaptionStyleTemplate> {
        return listInstalledPacks().flatMap { it.styles }
    }

    fun removePack(packId: String): Boolean {
        val file = packFileForId(packId) ?: return false
        return if (file.exists()) {
            file.delete().also { ok ->
                if (ok) Log.d(TAG, "Removed style pack: $packId")
                else Log.w(TAG, "Failed to delete style pack file: $packId")
            }
        } else false
    }

    fun isInstalled(packId: String): Boolean =
        packFileForId(packId)?.exists() == true

    /** A pack that passed every validation rule and is ready for [install]. */
    private data class ValidatedPack(
        val pack: StylePack,
        val root: JSONObject,
        val targetFile: File,
        val warnings: List<String>,
    )

    private data class Validation(
        val result: StylePackImportResult,
        val validated: ValidatedPack? = null,
    )

    private fun validate(root: JSONObject): Validation {
        val pack = parsePack(root)
            ?: return Validation(StylePackImportResult(failure = StylePackFailure.MISSING_REQUIRED_FIELDS))

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion > SCHEMA_VERSION) {
            return Validation(
                StylePackImportResult(
                    failure = StylePackFailure.INCOMPATIBLE_VERSION,
                    warnings = listOf("Pack requires schema version $schemaVersion, this app supports up to $SCHEMA_VERSION.")
                )
            )
        }

        if (pack.styles.isEmpty()) {
            return Validation(StylePackImportResult(failure = StylePackFailure.EMPTY_STYLES))
        }

        val warnings = mutableListOf<String>()
        if (pack.styles.size > MAX_STYLES_PER_PACK) {
            warnings.add("Pack contains ${pack.styles.size} styles; only the first $MAX_STYLES_PER_PACK will be imported.")
        }

        val styleIds = pack.styles.map { it.id }
        if (styleIds.size != styleIds.toSet().size) {
            return Validation(StylePackImportResult(failure = StylePackFailure.DUPLICATE_ID))
        }

        val file = packFileForId(pack.id)
            ?: return Validation(StylePackImportResult(failure = StylePackFailure.MISSING_REQUIRED_FIELDS))

        if (file.exists()) {
            warnings.add("Installing will replace the previously installed pack \"${pack.name}\".")
        }

        return Validation(
            result = StylePackImportResult(pack = pack, warnings = warnings),
            validated = ValidatedPack(pack = pack, root = root, targetFile = file, warnings = warnings)
        )
    }

    private fun install(validated: ValidatedPack): StylePackImportResult {
        return try {
            // Atomic: a crash mid-write leaves the previously installed pack (or no
            // pack at all) rather than a truncated file the gallery would fail to load.
            writeUtf8TextAtomically(validated.targetFile, validated.root.toString(2))
            Log.d(
                TAG,
                "Installed style pack: ${validated.pack.id} " +
                    "(${validated.pack.name}, ${validated.pack.styles.size} styles)"
            )
            StylePackImportResult(pack = validated.pack, warnings = validated.warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write style pack", e)
            StylePackImportResult(failure = StylePackFailure.WRITE_FAILED)
        }
    }

    private fun parsePack(root: JSONObject): StylePack? {
        val id = root.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val name = root.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        val version = root.optInt("version", 1)
        val author = root.optString("author", "")
        val license = root.optString("license", "")
        val minAppVersion = root.optString("minAppVersion", "")
        val stylesArray = root.optJSONArray("styles") ?: return null
        val styles = parseStyles(stylesArray).take(MAX_STYLES_PER_PACK)
        return StylePack(
            id = id,
            name = name,
            version = version,
            author = author,
            license = license,
            minAppVersion = minAppVersion,
            styles = styles,
        )
    }

    private fun parseStyles(array: JSONArray): List<CaptionStyleTemplate> {
        val result = mutableListOf<CaptionStyleTemplate>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val style = parseStyle(obj) ?: continue
            result.add(style)
        }
        return result
    }

    private fun parseStyle(obj: JSONObject): CaptionStyleTemplate? {
        val typeStr = obj.optString("type", "").uppercase()
        val type = try {
            CaptionTemplateType.valueOf(typeStr)
        } catch (_: Exception) {
            CaptionTemplateType.CLASSIC
        }
        val animStr = obj.optString("animation", "FADE").uppercase()
        val animation = try {
            TextAnimation.valueOf(animStr)
        } catch (_: Exception) {
            TextAnimation.FADE
        }
        val accessStr = obj.optString("accessibilityPreset", "STANDARD").uppercase()
        val accessibility = try {
            CaptionAccessibilityPreset.valueOf(accessStr)
        } catch (_: Exception) {
            CaptionAccessibilityPreset.STANDARD
        }
        return CaptionStyleTemplate(
            id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: return null,
            type = type,
            fontFamily = obj.optString("fontFamily", "sans-serif"),
            fontSize = obj.optDouble("fontSize", 24.0).toFloat().coerceIn(8f, 200f),
            textColor = parseColorLong(obj.optString("textColor", ""), 0xFFFFFFFF),
            backgroundColor = parseColorLong(obj.optString("backgroundColor", ""), 0x80000000),
            outlineColor = parseColorLong(obj.optString("outlineColor", ""), 0xFF000000),
            outlineWidth = obj.optDouble("outlineWidth", 0.0).toFloat().coerceIn(0f, 20f),
            shadowColor = parseColorLong(obj.optString("shadowColor", ""), 0x80000000),
            shadowOffsetX = obj.optDouble("shadowOffsetX", 2.0).toFloat().coerceIn(-20f, 20f),
            shadowOffsetY = obj.optDouble("shadowOffsetY", 2.0).toFloat().coerceIn(-20f, 20f),
            positionY = obj.optDouble("positionY", 0.85).toFloat().coerceIn(0f, 1f),
            animation = animation,
            highlightColor = parseColorLong(obj.optString("highlightColor", ""), 0xFFFFD700),
            wordByWord = obj.optBoolean("wordByWord", false),
            accessibilityPreset = accessibility,
        )
    }

    private fun parseColorLong(hex: String, default: Long): Long {
        if (hex.isBlank()) return default
        val digits = hex.removePrefix("#").removePrefix("0x")
        // Reject anything wider than 8 hex digits so a hostile pack cannot smuggle
        // 64-bit garbage that silently truncates to an unintended ARGB color.
        if (digits.length > 8) return default
        return try {
            java.lang.Long.parseUnsignedLong(digits, 16) and 0xFFFFFFFFL
        } catch (_: Exception) {
            default
        }
    }
}
