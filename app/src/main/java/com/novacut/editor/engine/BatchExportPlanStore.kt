package com.novacut.editor.engine

import android.content.Context
import androidx.core.net.toUri
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ExportQuality
import com.novacut.editor.model.FrameCaptureFormat
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SubtitleFormat
import com.novacut.editor.model.TimelineExportRange
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.Watermark
import com.novacut.editor.model.WatermarkPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

private const val BATCH_PLAN_DIR = "exports"
private const val BATCH_PLAN_FILE = "batch-export-plan.json"
private const val BATCH_PLAN_SCHEMA_VERSION = 1
private const val DEFAULT_MAX_BATCH_ITEMS = 32
private const val MAX_BATCH_PLAN_BYTES = 256L * 1024L
private const val MAX_TEXT_LENGTH = 512
private const val MAX_FINGERPRINT_LENGTH = 128
private const val MAX_CHAPTERS = 500

data class BatchExportPlanContext(
    val projectId: String,
    val projectFingerprint: String
)

/**
 * Persistent queue for work that has not yet become export history.
 *
 * The file is deliberately separate from [ExportHistoryStore]: completed work
 * is durable history, while this store contains only actionable or explainable
 * unfinished work. Every replacement is bounded and goes through the shared
 * atomic writer so a process death cannot expose a half-written plan.
 */
class BatchExportPlanStore(
    private val planFile: File,
    private val maxItems: Int = DEFAULT_MAX_BATCH_ITEMS
) {
    init {
        require(maxItems > 0) { "maxItems must be positive" }
    }

    fun readFor(context: BatchExportPlanContext): List<BatchExportItem> {
        return readAll()
            .asSequence()
            .filter { it.projectId == context.projectId }
            .map { item -> item.restoreFor(context) }
            .toList()
    }

    /**
     * Replace the current project's active plan while retaining plans for other
     * projects. Completed items are intentionally omitted; their individual
     * exports are already represented by [ExportHistoryStore].
     */
    fun saveFor(context: BatchExportPlanContext, items: List<BatchExportItem>) {
        val retainedOtherProjects = readAll().filterNot { it.projectId == context.projectId }
        val activeItems = items.asSequence()
            .filterNot { it.status == BatchExportStatus.COMPLETED }
            .take(maxItems)
            .map { item ->
                item.copy(
                    projectId = context.projectId,
                    projectFingerprint = item.projectFingerprint.ifBlank { context.projectFingerprint },
                    configFingerprint = item.configFingerprint.ifBlank {
                        exportConfigFingerprint(item.config)
                    },
                )
            }
            .toList()
        // Keep the project currently being edited at the front of the bounded
        // file. A large set of plans from other projects must never evict the
        // queue the user just changed.
        writeAll((activeItems + retainedOtherProjects).take(maxItems))
    }

    private fun readAll(): List<BatchExportItem> {
        if (!planFile.isFile || planFile.length() <= 0L || planFile.length() > MAX_BATCH_PLAN_BYTES) {
            return emptyList()
        }
        return runCatching {
            val root = JSONObject(planFile.readText(Charsets.UTF_8))
            if (root.optInt("schemaVersion", -1) != BATCH_PLAN_SCHEMA_VERSION) {
                return@runCatching emptyList()
            }
            val items = root.optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until items.length().coerceAtMost(maxItems)) {
                    parseItem(items.optJSONObject(index))?.let { item ->
                        if (item.status != BatchExportStatus.COMPLETED) add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(items: List<BatchExportItem>) {
        val root = JSONObject().apply {
            put("schemaVersion", BATCH_PLAN_SCHEMA_VERSION)
            put("updatedAtEpochMs", System.currentTimeMillis())
            put("items", JSONArray().apply {
                items.take(maxItems).forEach { put(itemToJson(it)) }
            })
        }
        val contents = root.toString(2)
        require(contents.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_PLAN_BYTES) {
            "Batch export plan exceeds $MAX_BATCH_PLAN_BYTES bytes"
        }
        planFile.parentFile?.mkdirs()
        writeUtf8TextAtomically(planFile, contents)
    }

    private fun BatchExportItem.restoreFor(context: BatchExportPlanContext): BatchExportItem {
        val projectChanged = projectFingerprint.isNotBlank() &&
            projectFingerprint != context.projectFingerprint
        val configChanged = configFingerprint.isNotBlank() &&
            configFingerprint != exportConfigFingerprint(config)
        val reason = when {
            projectChanged && configChanged -> "The project and export settings changed after this job was queued."
            projectChanged -> "The project changed after this job was queued."
            configChanged -> "The export settings changed after this job was queued."
            else -> null
        }
        return when {
            status == BatchExportStatus.IN_PROGRESS -> copy(
                status = BatchExportStatus.INTERRUPTED,
                progress = 0f,
                errorMessage = reason ?: "This export was interrupted when ClearCut closed."
            )
            reason != null -> copy(
                status = BatchExportStatus.REVIEW_REQUIRED,
                progress = 0f,
                errorMessage = reason
            )
            else -> this
        }
    }

    companion object {
        const val MAX_ITEMS = DEFAULT_MAX_BATCH_ITEMS

        fun forContext(context: Context): BatchExportPlanStore = BatchExportPlanStore(
            File(File(context.filesDir, BATCH_PLAN_DIR), BATCH_PLAN_FILE)
        )

        internal fun forFile(file: File, maxItems: Int = DEFAULT_MAX_BATCH_ITEMS): BatchExportPlanStore =
            BatchExportPlanStore(file, maxItems)
    }
}

internal fun exportConfigFingerprint(config: ExportConfig): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(exportConfigToJson(config).toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun itemToJson(item: BatchExportItem): JSONObject = JSONObject().apply {
    put("id", item.id.take(MAX_TEXT_LENGTH))
    put("projectId", item.projectId.take(MAX_TEXT_LENGTH))
    put("projectFingerprint", item.projectFingerprint.take(MAX_FINGERPRINT_LENGTH))
    put("configFingerprint", item.configFingerprint.take(MAX_FINGERPRINT_LENGTH))
    put("outputName", item.outputName.take(MAX_TEXT_LENGTH))
    put("status", item.status.name)
    put("progress", item.progress.coerceIn(0f, 1f).toDouble())
    item.errorMessage?.take(MAX_TEXT_LENGTH)?.let { put("errorMessage", it) }
    put("createdAtEpochMs", item.createdAtEpochMs.coerceAtLeast(0L))
    put("config", exportConfigToJson(item.config))
}

private fun parseItem(json: JSONObject?): BatchExportItem? {
    if (json == null) return null
    val id = json.optString("id").take(MAX_TEXT_LENGTH).takeIf { it.isNotBlank() } ?: return null
    val projectId = json.optString("projectId").take(MAX_TEXT_LENGTH).takeIf { it.isNotBlank() } ?: return null
    val outputName = json.optString("outputName").take(MAX_TEXT_LENGTH).takeIf { it.isNotBlank() } ?: return null
    val status = runCatching { BatchExportStatus.valueOf(json.optString("status")) }.getOrNull() ?: return null
    val config = exportConfigFromJson(json.optJSONObject("config")) ?: return null
    return BatchExportItem(
        id = id,
        config = config,
        outputName = outputName,
        projectId = projectId,
        projectFingerprint = json.optString("projectFingerprint").take(MAX_FINGERPRINT_LENGTH),
        configFingerprint = json.optString("configFingerprint").take(MAX_FINGERPRINT_LENGTH),
        status = status,
        progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
        errorMessage = json.optString("errorMessage").take(MAX_TEXT_LENGTH).takeIf { it.isNotBlank() },
        createdAtEpochMs = json.optLong("createdAtEpochMs").coerceAtLeast(0L),
    )
}

private fun exportConfigToJson(config: ExportConfig): JSONObject = JSONObject().apply {
    put("resolution", config.resolution.name)
    put("frameRate", config.frameRate)
    if (config.forceConstantFrameRate) put("forceConstantFrameRate", true)
    put("codec", config.codec.name)
    put("quality", config.quality.name)
    put("audioCodec", config.audioCodec.name)
    put("audioBitrate", config.audioBitrate)
    put("aspectRatio", config.aspectRatio.name)
    config.platformPreset?.let { put("platformPreset", it.name) }
    put("exportAudioOnly", config.exportAudioOnly)
    put("exportStemsOnly", config.exportStemsOnly)
    put("includeChapterMarkers", config.includeChapterMarkers)
    put("chapters", JSONArray().apply {
        config.chapters.take(MAX_CHAPTERS).forEach { chapter ->
            put(JSONObject().apply {
                put("timeMs", chapter.timeMs.coerceAtLeast(0L))
                put("title", chapter.title.take(MAX_TEXT_LENGTH))
            })
        }
    })
    config.subtitleFormat?.let { put("subtitleFormat", it.name) }
    put("burnSubtitles", config.burnSubtitles)
    put("transparentBackground", config.transparentBackground)
    put("exportAsGif", config.exportAsGif)
    put("gifFrameRate", config.gifFrameRate)
    put("gifMaxWidth", config.gifMaxWidth)
    put("captureFrameOnly", config.captureFrameOnly)
    put("captureFormat", config.captureFormat.name)
    config.targetSizeBytes?.let { put("targetSizeBytes", it.coerceAtLeast(0L)) }
    config.bitrateOverride?.let { put("bitrateOverride", it) }
    put("filenameTemplate", config.filenameTemplate.take(MAX_TEXT_LENGTH))
    put("exportAsContactSheet", config.exportAsContactSheet)
    put("contactSheetColumns", config.contactSheetColumns)
    config.timelineRange?.let { range ->
        put("timelineRange", JSONObject().apply {
            range.startFrame?.let { put("startFrame", it) }
            range.endFrameExclusive?.let { put("endFrameExclusive", it) }
        })
    }
    config.watermark?.let { watermark ->
        put("watermark", JSONObject().apply {
            put("sourceUri", watermark.sourceUri.toString().take(MAX_TEXT_LENGTH))
            put("position", watermark.position.name)
            put("opacity", watermark.opacity.toDouble())
            put("scalePercent", watermark.scalePercent)
        })
    }
    put("discloseAiUse", config.discloseAiUse)
    put("writeAiUseSidecar", config.writeAiUseSidecar)
    put("hdr10PlusMetadata", config.hdr10PlusMetadata)
    put("allowStreamCopy", config.allowStreamCopy)
    put("scrubMetadata", config.scrubMetadata)
    put("preserveSourceLocationMetadata", config.preserveSourceLocationMetadata)
    put("preserveSourceStreamMetadata", config.preserveSourceStreamMetadata)
}

private fun exportConfigFromJson(json: JSONObject?): ExportConfig? {
    if (json == null) return null
    return runCatching {
        val chaptersJson = json.optJSONArray("chapters")
        val chapters = buildList {
            if (chaptersJson != null) {
                for (index in 0 until chaptersJson.length().coerceAtMost(MAX_CHAPTERS)) {
                    val chapter = chaptersJson.optJSONObject(index) ?: continue
                    add(
                        ChapterMarker(
                            timeMs = chapter.optLong("timeMs").coerceAtLeast(0L),
                            title = chapter.optString("title").take(MAX_TEXT_LENGTH),
                        )
                    )
                }
            }
        }
        val watermark = json.optJSONObject("watermark")?.let { watermarkJson ->
            val uri = watermarkJson.optString("sourceUri").take(MAX_TEXT_LENGTH).takeIf { it.isNotBlank() }
                ?: return@let null
            Watermark(
                sourceUri = uri.toUri(),
                position = enumOrDefault(watermarkJson.optString("position"), WatermarkPosition.BOTTOM_RIGHT),
                opacity = watermarkJson.optDouble("opacity", 0.9).toFloat().coerceIn(0f, 1f),
                scalePercent = watermarkJson.optInt("scalePercent", 15).coerceIn(5, 50),
            )
        }
        val timelineRange = json.optJSONObject("timelineRange")?.let { rangeJson ->
            val startFrame = rangeJson.optLong("startFrame")
                .takeIf { rangeJson.has("startFrame") && !rangeJson.isNull("startFrame") }
            val endFrameExclusive = rangeJson.optLong("endFrameExclusive")
                .takeIf { rangeJson.has("endFrameExclusive") && !rangeJson.isNull("endFrameExclusive") }
            if (startFrame == null && endFrameExclusive == null) null
            else TimelineExportRange(startFrame = startFrame, endFrameExclusive = endFrameExclusive)
        }
        ExportConfig(
            resolution = enumOrDefault(json.optString("resolution"), Resolution.FHD_1080P),
            frameRate = json.optInt("frameRate", 30).coerceIn(1, 240),
            forceConstantFrameRate = json.optBoolean("forceConstantFrameRate", false),
            codec = enumOrDefault(json.optString("codec"), VideoCodec.H264),
            quality = enumOrDefault(json.optString("quality"), ExportQuality.HIGH),
            audioCodec = enumOrDefault(json.optString("audioCodec"), AudioCodec.AAC),
            audioBitrate = json.optInt("audioBitrate", 256_000).coerceAtLeast(1),
            aspectRatio = enumOrDefault(json.optString("aspectRatio"), AspectRatio.RATIO_16_9),
            platformPreset = enumOrNull<PlatformPreset>(json.optString("platformPreset")),
            exportAudioOnly = json.optBoolean("exportAudioOnly", false),
            exportStemsOnly = json.optBoolean("exportStemsOnly", false),
            includeChapterMarkers = json.optBoolean("includeChapterMarkers", false),
            chapters = chapters,
            subtitleFormat = enumOrNull<SubtitleFormat>(json.optString("subtitleFormat")),
            burnSubtitles = json.optBoolean("burnSubtitles", false),
            transparentBackground = json.optBoolean("transparentBackground", false),
            exportAsGif = json.optBoolean("exportAsGif", false),
            gifFrameRate = json.optInt("gifFrameRate", 15).coerceIn(1, 60),
            gifMaxWidth = json.optInt("gifMaxWidth", 480).coerceIn(1, 65_535),
            captureFrameOnly = json.optBoolean("captureFrameOnly", false),
            captureFormat = enumOrDefault(json.optString("captureFormat"), FrameCaptureFormat.PNG),
            targetSizeBytes = json.optLong("targetSizeBytes").takeIf { json.has("targetSizeBytes") && it > 0L },
            bitrateOverride = json.optInt("bitrateOverride").takeIf { json.has("bitrateOverride") && it > 0 },
            filenameTemplate = json.optString("filenameTemplate", "{name}").take(MAX_TEXT_LENGTH),
            exportAsContactSheet = json.optBoolean("exportAsContactSheet", false),
            contactSheetColumns = json.optInt("contactSheetColumns", 4).coerceIn(1, 12),
            timelineRange = timelineRange,
            watermark = watermark,
            discloseAiUse = json.optBoolean("discloseAiUse", false),
            writeAiUseSidecar = json.optBoolean("writeAiUseSidecar", true),
            hdr10PlusMetadata = json.optBoolean("hdr10PlusMetadata", false),
            allowStreamCopy = json.optBoolean("allowStreamCopy", true),
            scrubMetadata = json.optBoolean("scrubMetadata", false),
            preserveSourceLocationMetadata = json.optBoolean("preserveSourceLocationMetadata", false),
            preserveSourceStreamMetadata = json.optBoolean("preserveSourceStreamMetadata", false),
        )
    }.getOrNull()
}

private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? =
    enumValues<T>().firstOrNull { it.name == raw }

private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T =
    enumOrNull<T>(raw) ?: fallback
