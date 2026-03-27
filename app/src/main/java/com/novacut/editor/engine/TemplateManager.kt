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

    fun listTemplates(): List<UserTemplate> {
        if (!templateDir.exists()) return emptyList()
        return templateDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { loadTemplate(it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
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
            name = name,
            description = description,
            aspectRatio = project.aspectRatio,
            frameRate = project.frameRate,
            resolution = project.resolution,
            trackTypes = tracks.map { it.type },
            textOverlayCount = textOverlays.size,
            effectSummary = effectSummary,
            stateJson = stateJson
        )

        val json = JSONObject().apply {
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

        File(templateDir, "${template.id}.json").writeText(json.toString(2))
        template
    }

    fun deleteTemplate(id: String) {
        File(templateDir, "$id.json").delete()
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

    suspend fun exportTemplateToFile(templateName: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val template = listTemplates().find { it.name == templateName } ?: return@withContext false
            val json = JSONObject().apply {
                put("novacut_template_version", 1)
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
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to export template '$templateName'", e)
            false
        }
    }

    suspend fun importTemplateFromUri(uri: Uri): UserTemplate? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: return@withContext null
            val json = JSONObject(text)
            val template = UserTemplate(
                id = UUID.randomUUID().toString(),
                name = json.optString("name", "Imported Template"),
                description = json.optString("description", ""),
                aspectRatio = try {
                    AspectRatio.valueOf(json.optString("aspectRatio", "RATIO_16_9"))
                } catch (_: Exception) { AspectRatio.RATIO_16_9 },
                frameRate = json.optInt("frameRate", 30),
                resolution = try {
                    Resolution.valueOf(json.optString("resolution", "FHD_1080P"))
                } catch (_: Exception) { Resolution.FHD_1080P },
                trackTypes = json.optJSONArray("trackTypes")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        try { TrackType.valueOf(arr.getString(i)) } catch (_: Exception) { null }
                    }
                } ?: listOf(TrackType.VIDEO, TrackType.AUDIO),
                textOverlayCount = json.optInt("textOverlayCount", 0),
                effectSummary = json.optString("effectSummary", ""),
                createdAt = System.currentTimeMillis(),
                stateJson = json.optString("stateJson", "{}")
            )
            // Save locally
            templateDir.mkdirs()
            val saveJson = JSONObject().apply {
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
            File(templateDir, "${template.id}.json").writeText(saveJson.toString(2))
            template
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to import template from URI", e)
            null
        }
    }

    private fun loadTemplate(file: File): UserTemplate? {
        return try {
            val json = JSONObject(file.readText())
            UserTemplate(
                id = json.optString("id", file.nameWithoutExtension),
                name = json.optString("name", "Untitled Template"),
                description = json.optString("description", ""),
                aspectRatio = try {
                    AspectRatio.valueOf(json.optString("aspectRatio", "RATIO_16_9"))
                } catch (_: Exception) { AspectRatio.RATIO_16_9 },
                frameRate = json.optInt("frameRate", 30),
                resolution = try {
                    Resolution.valueOf(json.optString("resolution", "FHD_1080P"))
                } catch (_: Exception) { Resolution.FHD_1080P },
                trackTypes = json.optJSONArray("trackTypes")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        try { TrackType.valueOf(arr.getString(i)) } catch (_: Exception) { null }
                    }
                } ?: listOf(TrackType.VIDEO, TrackType.AUDIO),
                textOverlayCount = json.optInt("textOverlayCount", 0),
                effectSummary = json.optString("effectSummary", ""),
                createdAt = json.optLong("createdAt", 0L),
                stateJson = json.optString("stateJson", "{}")
            )
        } catch (e: Exception) {
            Log.e("TemplateManager", "Failed to load template ${file.name}", e)
            null
        }
    }
}
