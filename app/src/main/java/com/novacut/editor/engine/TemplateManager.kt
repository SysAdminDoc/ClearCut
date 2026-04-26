package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.BuildConfig
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class UserTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val aspectRatio: AspectRatio,
    val frameRate: Int = 30,
    val resolution: Resolution = Resolution.FHD_1080P,
    val trackTypes: List<TrackType>,
    val textOverlayCount: Int = 0,
    val effectSummary: String = "",
    val compatibility: TemplateCompatibilityMetadata = TemplateCompatibilityMetadata(),
    val createdAt: Long = System.currentTimeMillis(),
    val stateJson: String
)

data class TemplateImportResult(
    val template: UserTemplate? = null,
    val failure: TemplateImportFailure = TemplateImportFailure.NONE,
    val compatibilityReport: TemplateCompatibilityReport? = null
)

enum class TemplateImportFailure {
    NONE,
    UNREADABLE_FILE,
    OVERSIZED_FILE,
    INVALID_JSON,
    INVALID_STATE,
    INCOMPATIBLE,
    WRITE_FAILED
}

@Singleton
class TemplateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val templateDir = File(context.filesDir, "templates")
    private val defaultTemplateTrackTypes = listOf(TrackType.VIDEO, TrackType.AUDIO)
    private val templateSchemaVersion = 1
    private val maxTemplateBytes = 10_000_000L

    fun listTemplates(): List<UserTemplate> {
        if (!templateDir.exists()) return emptyList()
        return templateDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { loadTemplate(it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun getTemplate(templateId: String): UserTemplate? {
        val templateFile = templateFileForId(templateId) ?: return null
        return if (templateFile.exists()) loadTemplate(templateFile) else null
    }

    suspend fun saveTemplate(
        name: String,
        description: String,
        project: Project,
        tracks: List<Track>,
        textOverlays: List<TextOverlay>
    ): UserTemplate = withContext(Dispatchers.IO) {
        templateDir.mkdirs()
        val autoState = AutoSaveState(
            projectId = "template",
            tracks = tracks,
            textOverlays = textOverlays
        )
        val stateJson = autoState.serialize()
        val compatibility = TemplateCompatibilityEngine.createMetadata(
            state = autoState,
            minVersionCode = BuildConfig.VERSION_CODE,
            minVersionName = BuildConfig.VERSION_NAME,
            schemaVersion = templateSchemaVersion
        )

        val effectTypes = tracks.flatMap { it.clips }.flatMap { it.effects }
            .map { it.type.displayName }.distinct().take(3)
        val effectSummary = if (effectTypes.isEmpty()) "No effects"
            else effectTypes.joinToString(", ")

        val template = UserTemplate(
            name = normalizeTemplateName(name),
            description = description.trim(),
            aspectRatio = project.aspectRatio,
            frameRate = project.frameRate,
            resolution = project.resolution,
            trackTypes = tracks.map { it.type }.ifEmpty { defaultTemplateTrackTypes },
            textOverlayCount = textOverlays.size,
            effectSummary = effectSummary,
            compatibility = compatibility,
            stateJson = stateJson
        )

        val templateFile = templateFileForId(template.id)
            ?: throw IllegalStateException("Generated template id was not file-safe")
        writeUtf8TextAtomically(templateFile, templateToJson(template).toString(2))
        template
    }

    fun deleteTemplate(id: String): Boolean {
        return templateFileForId(id)?.delete() == true
    }

    fun loadTemplateState(template: UserTemplate): Pair<List<Track>, List<TextOverlay>>? {
        return try {
            val report = validateTemplateCompatibility(template.compatibility)
            if (!report.canImport) {
                Log.w("TemplateManager", "Template '${template.name}' is not compatible: ${report.issues.joinToString { it.code }}")
                return null
            }
            val state = AutoSaveState.deserialize(template.stateJson)
            state.tracks to state.textOverlays
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to deserialize template '${template.name}'", e)
            null
        }
    }

    suspend fun exportTemplateToFile(templateId: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val template = getTemplate(templateId) ?: return@withContext false
            outputFile.parentFile?.mkdirs()
            writeUtf8TextAtomically(outputFile, templateToJson(template).toString(2))
            true
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to export template '$templateId'", e)
            false
        }
    }

    suspend fun importTemplateFromUri(uri: Uri): UserTemplate? = withContext(Dispatchers.IO) {
        importTemplateFromUriDetailed(uri).template
    }

    suspend fun importTemplateFromUriDetailed(uri: Uri): TemplateImportResult = withContext(Dispatchers.IO) {
        try {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    readUtf8WithByteLimit(stream, maxTemplateBytes)
                } ?: return@withContext TemplateImportResult(failure = TemplateImportFailure.UNREADABLE_FILE)
            } catch (e: IOException) {
                val failure = if (e.message?.contains("byte limit", ignoreCase = true) == true) {
                    TemplateImportFailure.OVERSIZED_FILE
                } else {
                    TemplateImportFailure.UNREADABLE_FILE
                }
                Log.w("TemplateManager", "Template import read failed", e)
                return@withContext TemplateImportResult(failure = failure)
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                Log.w("TemplateManager", "Template import JSON is invalid", e)
                return@withContext TemplateImportResult(failure = TemplateImportFailure.INVALID_JSON)
            }
            val importedTemplate = when (val parsed = parseTemplateJson(
                json = json,
                fallbackId = UUID.randomUUID().toString(),
                defaultCreatedAt = System.currentTimeMillis()
            )) {
                is TemplateParseResult.Success -> parsed.template
                is TemplateParseResult.Failure -> {
                    return@withContext TemplateImportResult(
                        failure = parsed.failure,
                        compatibilityReport = parsed.compatibilityReport
                    )
                }
            }
            val template = normalizeImportedTemplate(importedTemplate, listTemplates())
            templateDir.mkdirs()
            val templateFile = templateFileForId(template.id)
                ?: return@withContext TemplateImportResult(failure = TemplateImportFailure.WRITE_FAILED)
            writeUtf8TextAtomically(templateFile, templateToJson(template).toString(2))
            TemplateImportResult(template = template)
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to import template from URI", e)
            TemplateImportResult(failure = TemplateImportFailure.WRITE_FAILED)
        }
    }

    private fun loadTemplate(file: File): UserTemplate? {
        return try {
            if (file.length() > maxTemplateBytes) {
                Log.w("TemplateManager", "Skipping oversized template ${file.name}")
                return null
            }
            val json = JSONObject(file.inputStream().use { input ->
                readUtf8WithByteLimit(input, maxTemplateBytes)
            })
            when (val parsed = parseTemplateJson(
                json = json,
                fallbackId = file.nameWithoutExtension,
                defaultCreatedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            )) {
                is TemplateParseResult.Success -> parsed.template
                is TemplateParseResult.Failure -> null
            }
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to load template ${file.name}", e)
            null
        }
    }

    private fun parseTemplateJson(
        json: JSONObject,
        fallbackId: String,
        defaultCreatedAt: Long
    ): TemplateParseResult {
        val schemaVersion = json.optInt("novacut_template_version", 1).coerceAtLeast(1)
        if (schemaVersion > templateSchemaVersion) {
            val report = TemplateCompatibilityEngine.validate(
                metadata = TemplateCompatibilityMetadata(schemaVersion = schemaVersion),
                currentSchemaVersion = templateSchemaVersion,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
            Log.w("TemplateManager", "Template schema $schemaVersion is newer than supported $templateSchemaVersion")
            return TemplateParseResult.Failure(
                failure = TemplateImportFailure.INCOMPATIBLE,
                compatibilityReport = report
            )
        }

        val stateJson = json.optString("stateJson", "").trim()
        if (stateJson.isBlank()) {
            Log.w("TemplateManager", "Template JSON missing stateJson payload")
            return TemplateParseResult.Failure(TemplateImportFailure.INVALID_STATE)
        }

        val state = try {
            AutoSaveState.deserialize(stateJson)
        } catch (e: Exception) {
            Log.e("TemplateManager", "Template stateJson is invalid", e)
            return TemplateParseResult.Failure(TemplateImportFailure.INVALID_STATE)
        }

        val inferredCompatibility = TemplateCompatibilityEngine.createMetadata(
            state = state,
            schemaVersion = schemaVersion
        )
        val compatibility = TemplateCompatibilityEngine.merge(
            declared = TemplateCompatibilityEngine.fromJson(json.optJSONObject("compatibility")),
            inferred = inferredCompatibility
        )
        val compatibilityReport = validateTemplateCompatibility(compatibility)
        if (!compatibilityReport.canImport) {
            Log.w("TemplateManager", "Template import blocked: ${compatibilityReport.issues.joinToString { it.code }}")
            return TemplateParseResult.Failure(
                failure = TemplateImportFailure.INCOMPATIBLE,
                compatibilityReport = compatibilityReport
            )
        }

        val trackTypesFromState = state.tracks.map { it.type }
        val normalizedTrackTypes = trackTypesFromState.ifEmpty {
            parseTrackTypes(json.optJSONArray("trackTypes"), defaultTemplateTrackTypes)
        }
        val effectSummary = state.tracks
            .flatMap { it.clips }
            .flatMap { it.effects }
            .map { it.type.displayName }
            .distinct()
            .take(3)
            .joinToString(", ")
            .ifBlank { "No effects" }

        return TemplateParseResult.Success(UserTemplate(
            id = json.optString("id", fallbackId).ifBlank { fallbackId },
            name = normalizeTemplateName(json.optString("name", "Untitled Template")),
            description = json.optString("description", "").trim(),
            aspectRatio = parseAspectRatio(json.optString("aspectRatio", "RATIO_16_9")),
            frameRate = json.optInt("frameRate", 30).coerceIn(1, 240),
            resolution = parseResolution(json.optString("resolution", "FHD_1080P")),
            trackTypes = normalizedTrackTypes,
            textOverlayCount = state.textOverlays.size,
            effectSummary = effectSummary,
            compatibility = compatibility,
            createdAt = json.optLong("createdAt", defaultCreatedAt).takeIf { it > 0L } ?: defaultCreatedAt,
            stateJson = stateJson
        ))
    }

    private fun templateToJson(template: UserTemplate): JSONObject {
        return JSONObject().apply {
            put("novacut_template_version", templateSchemaVersion)
            put("id", template.id)
            put("name", template.name)
            put("description", template.description)
            put("aspectRatio", template.aspectRatio.name)
            put("frameRate", template.frameRate)
            put("resolution", template.resolution.name)
            put("trackTypes", JSONArray(template.trackTypes.map { it.name }))
            put("textOverlayCount", template.textOverlayCount)
            put("effectSummary", template.effectSummary)
            put("compatibility", TemplateCompatibilityEngine.toJson(template.compatibility))
            put("createdAt", template.createdAt)
            put("stateJson", template.stateJson)
        }
    }

    fun validateTemplateCompatibility(template: UserTemplate): TemplateCompatibilityReport {
        return validateTemplateCompatibility(template.compatibility)
    }

    private fun validateTemplateCompatibility(metadata: TemplateCompatibilityMetadata): TemplateCompatibilityReport {
        return TemplateCompatibilityEngine.validate(
            metadata = metadata,
            currentSchemaVersion = templateSchemaVersion,
            currentVersionCode = BuildConfig.VERSION_CODE
        )
    }

    private fun normalizeImportedTemplate(
        template: UserTemplate,
        existingTemplates: List<UserTemplate>
    ): UserTemplate {
        val existingIds = existingTemplates.asSequence().map { it.id }.toHashSet()
        val existingNames = existingTemplates.asSequence().map { it.name.lowercase() }.toHashSet()
        // Sanitize the imported template id BEFORE the collision check, otherwise an id like
        // "../../etc/passwd" from a hostile .novacut-template would land in the file system as
        // `templateDir/../../etc/passwd.json` (path traversal). Allow only [A-Za-z0-9_-]; if
        // sanitization changes anything, mint a fresh UUID rather than collide silently.
        val sanitizedId = sanitizeFilenameSafe(template.id)
        val safeId = if (sanitizedId.isEmpty() || sanitizedId != template.id) {
            UUID.randomUUID().toString()
        } else {
            template.id
        }
        val resolvedId = if (safeId in existingIds) UUID.randomUUID().toString() else safeId
        val resolvedName = ensureUniqueImportedName(template.name, existingNames)

        return if (resolvedId == template.id && resolvedName == template.name) {
            template
        } else {
            template.copy(id = resolvedId, name = resolvedName)
        }
    }

    private fun sanitizeFilenameSafe(value: String): String {
        // Keep only filename-safe characters; everything else (slashes, dots, control chars,
        // unicode separators, reserved Windows characters) is dropped. The caller decides
        // what to do if the result differs from the input.
        return value.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
    }

    private fun templateFileForId(id: String): File? {
        val sanitizedId = sanitizeFilenameSafe(id)
        if (sanitizedId.isEmpty() || sanitizedId != id) {
            Log.w("TemplateManager", "Rejected unsafe template id: $id")
            return null
        }
        return File(templateDir, "$sanitizedId.json")
    }

    private fun ensureUniqueImportedName(name: String, existingNames: Set<String>): String {
        if (name.lowercase() !in existingNames) return name

        val baseName = name.trim().ifBlank { "Untitled Template" }
        var candidate = "$baseName (Imported)"
        var counter = 2
        while (candidate.lowercase() in existingNames) {
            candidate = "$baseName (Imported $counter)"
            counter++
        }
        return candidate
    }

    private fun parseTrackTypes(jsonArray: JSONArray?, fallback: List<TrackType>): List<TrackType> {
        return jsonArray?.let { arr ->
            (0 until arr.length()).mapNotNull { index ->
                try {
                    TrackType.valueOf(arr.getString(index))
                } catch (_: Exception) {
                    null
                }
            }.ifEmpty { fallback }
        } ?: fallback
    }

    private fun parseAspectRatio(raw: String): AspectRatio {
        return try {
            AspectRatio.valueOf(raw)
        } catch (_: Exception) {
            AspectRatio.RATIO_16_9
        }
    }

    private fun parseResolution(raw: String): Resolution {
        return try {
            Resolution.valueOf(raw)
        } catch (_: Exception) {
            Resolution.FHD_1080P
        }
    }

    private fun normalizeTemplateName(raw: String): String {
        return raw.trim().ifBlank { "Untitled Template" }
    }

    private sealed class TemplateParseResult {
        data class Success(val template: UserTemplate) : TemplateParseResult()
        data class Failure(
            val failure: TemplateImportFailure,
            val compatibilityReport: TemplateCompatibilityReport? = null
        ) : TemplateParseResult()
    }

}
