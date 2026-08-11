package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.engine.AppLog
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
    val frameRateNumerator: Int = frameRate,
    val frameRateDenominator: Int = 1,
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
    val compatibilityReport: TemplateCompatibilityReport? = null,
    val restoreReport: ProjectRestoreReport = ProjectRestoreReport.EMPTY,
)

data class TemplateStateLoadResult(
    val tracks: List<Track>,
    val textOverlays: List<TextOverlay>,
    val restoreReport: ProjectRestoreReport = ProjectRestoreReport.EMPTY,
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
    private companion object {
        private const val MAX_TEMPLATE_ID_CHARS = 128
        private const val MAX_TEMPLATE_NAME_CHARS = 80
        private const val MAX_TEMPLATE_DESCRIPTION_CHARS = 2_000
        private const val MAX_TEMPLATE_TRACK_TYPES = 16
    }

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
        val templateId = UUID.randomUUID().toString()
        val document = ProjectDocumentApplicator.capture(
            project = project.copy(id = templateId),
            state = AutoSaveState(
                projectId = templateId,
                tracks = tracks,
                textOverlays = textOverlays
            )
        )
        val stateJson = ProjectDocumentApplicator.encode(document)
        val autoState = document.state
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
            id = templateId,
            name = normalizeTemplateName(name),
            description = normalizeTemplateDescription(description),
            aspectRatio = project.aspectRatio,
            frameRate = project.frameRate,
            frameRateNumerator = project.frameRateNumerator,
            frameRateDenominator = project.frameRateDenominator,
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

    /**
     * Move a template to the trash instead of destroying it.
     *
     * A user template is work the user authored, and deletion had no undo, no restore
     * and no second copy — the confirm dialog was the only thing standing between a
     * mis-tap and permanent loss. The file is now moved aside so [restoreTemplate] can
     * put it back; [listTemplates] never looks in the trash, so it disappears from the
     * UI exactly as before.
     */
    fun deleteTemplate(id: String): Boolean {
        val templateFile = templateFileForId(id) ?: return false
        if (!templateFile.exists()) return false
        val trashFile = trashFileForId(id) ?: return templateFile.delete()
        trashFile.parentFile?.mkdirs()
        if (trashFile.exists()) trashFile.delete()
        // A failed rename must not silently leave the template in place while the UI
        // reports it gone, so fall back to the destructive delete rather than lying.
        return templateFile.renameTo(trashFile) || templateFile.delete()
    }

    /** Put a trashed template back. Returns false when nothing is there to restore. */
    fun restoreTemplate(id: String): Boolean {
        val trashFile = trashFileForId(id) ?: return false
        if (!trashFile.exists()) return false
        val templateFile = templateFileForId(id) ?: return false
        templateFile.parentFile?.mkdirs()
        return trashFile.renameTo(templateFile)
    }

    /** True when [id] is sitting in the trash and can still be restored. */
    fun isTemplateRestorable(id: String): Boolean = trashFileForId(id)?.exists() == true

    private fun trashFileForId(id: String): File? {
        val templateFile = templateFileForId(id) ?: return null
        return File(File(templateDir, "trash"), templateFile.name)
    }

    fun loadTemplateState(template: UserTemplate): TemplateStateLoadResult? {
        return try {
            val report = validateTemplateCompatibility(template.compatibility)
            if (!report.canImport) {
                AppLog.w("TemplateManager", "Template '${template.name}' is not compatible: ${report.issues.joinToString { it.code }}")
                return null
            }
            when (val decoded = ProjectDocumentApplicator.read(template.stateJson)) {
                is ProjectDocumentReadResult.Loaded -> {
                    decoded.warnings.forEach { warning ->
                        AppLog.w("TemplateManager", "Template '${template.name}': $warning")
                    }
                    TemplateStateLoadResult(
                        tracks = decoded.document.state.tracks,
                        textOverlays = decoded.document.state.textOverlays,
                        restoreReport = decoded.report,
                    )
                }
                else -> return null
            }
        } catch (e: Exception) {
            AppLog.e("TemplateManager", "Failed to deserialize template '${template.name}'", e)
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
            AppLog.e("TemplateManager", "Failed to export template '$templateId'", e)
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
                AppLog.w("TemplateManager", "Template import read failed", e)
                return@withContext TemplateImportResult(failure = failure)
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                AppLog.w("TemplateManager", "Template import JSON is invalid", e)
                return@withContext TemplateImportResult(failure = TemplateImportFailure.INVALID_JSON)
            }
            val parsed = parseTemplateJson(
                json = json,
                fallbackId = UUID.randomUUID().toString(),
                defaultCreatedAt = System.currentTimeMillis()
            )
            val parsedTemplate = when (parsed) {
                is TemplateParseResult.Success -> parsed
                is TemplateParseResult.Failure -> {
                    return@withContext TemplateImportResult(
                        failure = parsed.failure,
                        compatibilityReport = parsed.compatibilityReport
                    )
                }
            }
            val template = normalizeImportedTemplate(parsedTemplate.template, listTemplates())
            templateDir.mkdirs()
            val templateFile = templateFileForId(template.id)
                ?: return@withContext TemplateImportResult(failure = TemplateImportFailure.WRITE_FAILED)
            writeUtf8TextAtomically(templateFile, templateToJson(template).toString(2))
            TemplateImportResult(template = template, restoreReport = parsedTemplate.restoreReport)
        } catch (e: Exception) {
            AppLog.e("TemplateManager", "Failed to import template from URI", e)
            TemplateImportResult(failure = TemplateImportFailure.WRITE_FAILED)
        }
    }

    private fun loadTemplate(file: File): UserTemplate? {
        return try {
            if (file.length() > maxTemplateBytes) {
                AppLog.w("TemplateManager", "Skipping oversized template ${file.redacted()}")
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
            AppLog.e("TemplateManager", "Failed to load template ${file.redacted()}", e)
            null
        }
    }

    private fun parseTemplateJson(
        json: JSONObject,
        fallbackId: String,
        defaultCreatedAt: Long
    ): TemplateParseResult {
        val schemaVersion = json.optInt("clearcut_template_version", 1).coerceAtLeast(1)
        if (schemaVersion > templateSchemaVersion) {
            val report = TemplateCompatibilityEngine.validate(
                metadata = TemplateCompatibilityMetadata(schemaVersion = schemaVersion),
                currentSchemaVersion = templateSchemaVersion,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
            AppLog.w("TemplateManager", "Template schema $schemaVersion is newer than supported $templateSchemaVersion")
            return TemplateParseResult.Failure(
                failure = TemplateImportFailure.INCOMPATIBLE,
                compatibilityReport = report
            )
        }

        val stateJson = json.optString("stateJson", "").trim()
        if (stateJson.isBlank()) {
            AppLog.w("TemplateManager", "Template JSON missing stateJson payload")
            return TemplateParseResult.Failure(TemplateImportFailure.INVALID_STATE)
        }

        val decoded = ProjectDocumentApplicator.read(stateJson)
        val loaded = when (decoded) {
            is ProjectDocumentReadResult.Loaded -> {
                decoded.warnings.forEach { warning ->
                    AppLog.w("TemplateManager", "Imported template document: $warning")
                }
                decoded
            }
            is ProjectDocumentReadResult.FutureSchema -> {
                AppLog.w("TemplateManager", "Template state uses a newer project document schema")
                return TemplateParseResult.Failure(TemplateImportFailure.INCOMPATIBLE)
            }
            is ProjectDocumentReadResult.Corrupt -> {
                AppLog.e("TemplateManager", "Template stateJson is invalid", decoded.cause)
                return TemplateParseResult.Failure(TemplateImportFailure.INVALID_STATE)
            }
        }
        val state = loaded.document.state

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
            AppLog.w("TemplateManager", "Template import blocked: ${compatibilityReport.issues.joinToString { it.code }}")
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

        return TemplateParseResult.Success(
            template = UserTemplate(
                id = json.optString("id", fallbackId).ifBlank { fallbackId },
                name = normalizeTemplateName(json.optString("name", "Untitled Template")),
                description = normalizeTemplateDescription(json.optString("description", "")),
                aspectRatio = parseAspectRatio(json.optString("aspectRatio", "RATIO_16_9")),
                frameRate = json.optInt("frameRate", 30).coerceIn(1, 240),
                frameRateNumerator = json.optInt(
                    "frameRateNumerator",
                    json.optInt("frameRate", 30),
                ).coerceIn(1, 240_000),
                frameRateDenominator = json.optInt("frameRateDenominator", 1).coerceIn(1, 10_000),
                resolution = parseResolution(json.optString("resolution", "FHD_1080P")),
                trackTypes = normalizedTrackTypes,
                textOverlayCount = state.textOverlays.size,
                effectSummary = effectSummary,
                compatibility = compatibility,
                createdAt = json.optLong("createdAt", defaultCreatedAt).takeIf { it > 0L } ?: defaultCreatedAt,
                stateJson = stateJson,
            ),
            restoreReport = loaded.report,
        )
    }

    private fun templateToJson(template: UserTemplate): JSONObject {
        return JSONObject().apply {
            put("clearcut_template_version", templateSchemaVersion)
            put("id", template.id)
            put("name", template.name)
            put("description", template.description)
            put("aspectRatio", template.aspectRatio.name)
            put("frameRate", template.frameRate)
            put("frameRateNumerator", template.frameRateNumerator)
            put("frameRateDenominator", template.frameRateDenominator)
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
        // "../../etc/passwd" from a hostile .clearcut-template would land in the file system as
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
        return value.asSequence()
            .filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
            .take(MAX_TEMPLATE_ID_CHARS)
            .joinToString("")
    }

    private fun templateFileForId(id: String): File? {
        val sanitizedId = sanitizeFilenameSafe(id)
        if (sanitizedId.isEmpty() || sanitizedId != id) {
            AppLog.w("TemplateManager", "Rejected unsafe template id")
            return null
        }
        return File(templateDir, "$sanitizedId.json")
    }

    private fun ensureUniqueImportedName(name: String, existingNames: Set<String>): String {
        if (name.lowercase() !in existingNames) return name

        val baseName = normalizeTemplateName(name)
        var candidate = importedNameCandidate(baseName, " (Imported)")
        var counter = 2
        while (candidate.lowercase() in existingNames) {
            candidate = importedNameCandidate(baseName, " (Imported $counter)")
            counter++
        }
        return candidate
    }

    private fun importedNameCandidate(baseName: String, suffix: String): String {
        val maxBaseChars = (MAX_TEMPLATE_NAME_CHARS - suffix.length).coerceAtLeast(1)
        val boundedBase = baseName.take(maxBaseChars).trim().ifBlank { "Untitled Template".take(maxBaseChars) }
        return "$boundedBase$suffix"
    }

    private fun parseTrackTypes(jsonArray: JSONArray?, fallback: List<TrackType>): List<TrackType> {
        return jsonArray?.let { arr ->
            (0 until arr.length().coerceAtMost(MAX_TEMPLATE_TRACK_TYPES)).mapNotNull { index ->
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
        return normalizeDisplayText(raw, fallback = "Untitled Template", maxChars = MAX_TEMPLATE_NAME_CHARS)
    }

    private fun normalizeTemplateDescription(raw: String): String {
        return normalizeDisplayText(raw, fallback = "", maxChars = MAX_TEMPLATE_DESCRIPTION_CHARS)
    }

    private fun normalizeDisplayText(raw: String, fallback: String, maxChars: Int): String {
        val normalized = raw
            .map { char -> if (char.isISOControl()) ' ' else char }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return normalized.ifBlank { fallback }.take(maxChars).trim().ifBlank { fallback.take(maxChars) }
    }

    private sealed class TemplateParseResult {
        data class Success(
            val template: UserTemplate,
            val restoreReport: ProjectRestoreReport = ProjectRestoreReport.EMPTY,
        ) : TemplateParseResult()
        data class Failure(
            val failure: TemplateImportFailure,
            val compatibilityReport: TemplateCompatibilityReport? = null
        ) : TemplateParseResult()
    }

}
