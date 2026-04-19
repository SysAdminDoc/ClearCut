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
    val createdAt: Long = System.currentTimeMillis(),
    val stateJson: String
)

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
            stateJson = stateJson
        )

        val templateFile = templateFileForId(template.id)
            ?: throw IllegalStateException("Generated template id was not file-safe")
        writeUtf8TextAtomically(templateFile, templateToJson(template).toString(2))
        template
    }

    fun deleteTemplate(id: String) {
        templateFileForId(id)?.delete()
    }

    fun loadTemplateState(template: UserTemplate): Pair<List<Track>, List<TextOverlay>>? {
        return try {
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
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                readUtf8WithByteLimit(stream, maxTemplateBytes)
            } ?: return@withContext null
            val json = JSONObject(text)
            val importedTemplate = parseTemplateJson(
                json = json,
                fallbackId = UUID.randomUUID().toString(),
                defaultCreatedAt = System.currentTimeMillis()
            ) ?: return@withContext null
            val template = normalizeImportedTemplate(importedTemplate, listTemplates())
            // Save locally
            templateDir.mkdirs()
            val templateFile = templateFileForId(template.id) ?: return@withContext null
            writeUtf8TextAtomically(templateFile, templateToJson(template).toString(2))
            template
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to import template from URI", e)
            null
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
            parseTemplateJson(
                json = json,
                fallbackId = file.nameWithoutExtension,
                defaultCreatedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to load template ${file.name}", e)
            null
        }
    }

    private fun parseTemplateJson(
        json: JSONObject,
        fallbackId: String,
        defaultCreatedAt: Long
    ): UserTemplate? {
        val stateJson = json.optString("stateJson", "").trim()
        if (stateJson.isBlank()) {
            Log.w("TemplateManager", "Template JSON missing stateJson payload")
            return null
        }

        val state = try {
            AutoSaveState.deserialize(stateJson)
        } catch (e: Exception) {
            Log.e("TemplateManager", "Template stateJson is invalid", e)
            return null
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

        return UserTemplate(
            id = json.optString("id", fallbackId).ifBlank { fallbackId },
            name = normalizeTemplateName(json.optString("name", "Untitled Template")),
            description = json.optString("description", "").trim(),
            aspectRatio = parseAspectRatio(json.optString("aspectRatio", "RATIO_16_9")),
            frameRate = json.optInt("frameRate", 30).coerceIn(1, 240),
            resolution = parseResolution(json.optString("resolution", "FHD_1080P")),
            trackTypes = normalizedTrackTypes,
            textOverlayCount = state.textOverlays.size,
            effectSummary = effectSummary,
            createdAt = json.optLong("createdAt", defaultCreatedAt).takeIf { it > 0L } ?: defaultCreatedAt,
            stateJson = stateJson
        )
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
            put("createdAt", template.createdAt)
            put("stateJson", template.stateJson)
        }
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

}
