package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val INCIDENT_DIR = "diagnostics/export-incidents"
private const val MAX_INCIDENT_FILES = 10
private const val MAX_INCIDENT_BYTES = 128L * 1024L
private const val MAX_PROGRESS_SAMPLES = 40

data class ExportIncidentBundle(
    val id: String = UUID.randomUUID().toString(),
    val appVersion: String,
    val deviceModel: String,
    val androidSdk: Int,
    val projectId: String,
    val projectName: String,
    val failedPhase: String,
    val errorClass: String,
    val errorMessage: String,
    val encoderPath: String,
    val codecLabel: String,
    val resolutionLabel: String,
    val frameRate: Int,
    val exportAudioOnly: Boolean,
    val hdrRequested: Boolean,
    val streamCopyAttempted: Boolean,
    val timelineDurationMs: Long,
    val elapsedMs: Long,
    val progressSamples: List<Float>,
    val mediaWarningCount: Int,
    val mediaBlockingCount: Int,
    val mediaHealthSummary: String?,
    val timestampEpochMs: Long
)

@Singleton
class ExportIncidentStore internal constructor(private val incidentDir: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, INCIDENT_DIR))

    fun save(bundle: ExportIncidentBundle): File {
        incidentDir.mkdirs()
        val file = File(incidentDir, "incident-${bundle.id.take(8)}.json")
        writeUtf8TextAtomically(file, toJson(bundle).toString(2))
        pruneOldIncidents()
        return file
    }

    fun readAll(): List<ExportIncidentBundle> {
        if (!incidentDir.isDirectory) return emptyList()
        return incidentDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_INCIDENT_FILES)
            ?.mapNotNull { file ->
                if (file.length() > MAX_INCIDENT_BYTES) return@mapNotNull null
                runCatching { fromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull()
            }
            ?: emptyList()
    }

    fun buildDiagnosticJson(): String? {
        val incidents = readAll()
        if (incidents.isEmpty()) return null
        val projectPseudonyms = linkedMapOf<String, String>()
        val array = JSONArray()
        for (incident in incidents) {
            val projectKey = incident.projectId.takeIf { it.isNotBlank() }
                ?: "incident:${incident.id}"
            val pseudonym = projectPseudonyms.getOrPut(projectKey) {
                "project-${projectPseudonyms.size + 1}"
            }
            array.put(toDiagnosticJson(incident, pseudonym))
        }
        return array.toString(2)
    }

    fun clear() {
        if (!incidentDir.isDirectory) return
        incidentDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun pruneOldIncidents() {
        val files = incidentDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_INCIDENT_FILES).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val BUNDLE_ENTRY = "export-incidents.json"

        fun forContext(context: Context): ExportIncidentStore {
            return ExportIncidentStore(File(context.filesDir, INCIDENT_DIR))
        }

        fun toJson(bundle: ExportIncidentBundle): JSONObject = JSONObject().apply {
            put("id", bundle.id)
            put("appVersion", bundle.appVersion)
            put("deviceModel", bundle.deviceModel)
            put("androidSdk", bundle.androidSdk)
            put("projectId", bundle.projectId)
            put("projectName", bundle.projectName)
            put("failedPhase", bundle.failedPhase)
            put("errorClass", bundle.errorClass)
            put("errorMessage", bundle.errorMessage)
            put("encoderPath", bundle.encoderPath)
            put("codecLabel", bundle.codecLabel)
            put("resolutionLabel", bundle.resolutionLabel)
            put("frameRate", bundle.frameRate)
            put("exportAudioOnly", bundle.exportAudioOnly)
            put("hdrRequested", bundle.hdrRequested)
            put("streamCopyAttempted", bundle.streamCopyAttempted)
            put("timelineDurationMs", bundle.timelineDurationMs)
            put("elapsedMs", bundle.elapsedMs)
            put("progressSamples", JSONArray().apply {
                bundle.progressSamples.forEach { put(it.toDouble()) }
            })
            put("mediaWarningCount", bundle.mediaWarningCount)
            put("mediaBlockingCount", bundle.mediaBlockingCount)
            put("mediaHealthSummary", bundle.mediaHealthSummary ?: JSONObject.NULL)
            put("timestampEpochMs", bundle.timestampEpochMs)
        }

        /**
         * Whitelist-only representation for a user-shared diagnostic ZIP.
         *
         * Private incident files retain project context and free-form failure
         * details for local retry/history. The shared form deliberately omits
         * project identity, error text, and media-health prose because generic
         * redaction cannot recognize arbitrary filenames, captions, or names.
         */
        internal fun toDiagnosticJson(
            bundle: ExportIncidentBundle,
            projectPseudonym: String,
        ): JSONObject = JSONObject().apply {
            put("schema", "com.clearcut.export-incident.shared.v1")
            put("id", bundle.id)
            put("appVersion", bundle.appVersion)
            put("deviceModel", bundle.deviceModel)
            put("androidSdk", bundle.androidSdk)
            put("projectPseudonym", projectPseudonym)
            put("failedPhase", bundle.failedPhase)
            put("errorClass", bundle.errorClass)
            put("encoderPath", bundle.encoderPath)
            put("codecLabel", bundle.codecLabel)
            put("resolutionLabel", bundle.resolutionLabel)
            put("frameRate", bundle.frameRate)
            put("exportAudioOnly", bundle.exportAudioOnly)
            put("hdrRequested", bundle.hdrRequested)
            put("streamCopyAttempted", bundle.streamCopyAttempted)
            put("timelineDurationMs", bundle.timelineDurationMs)
            put("elapsedMs", bundle.elapsedMs)
            put("progressSamples", JSONArray().apply {
                bundle.progressSamples.forEach { put(it.toDouble()) }
            })
            put("mediaWarningCount", bundle.mediaWarningCount)
            put("mediaBlockingCount", bundle.mediaBlockingCount)
            put("timestampEpochMs", bundle.timestampEpochMs)
        }

        fun fromJson(json: JSONObject): ExportIncidentBundle? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val progressArray = json.optJSONArray("progressSamples")
            val samples = if (progressArray != null) {
                (0 until progressArray.length()).map { progressArray.optDouble(it, 0.0).toFloat() }
            } else emptyList()
            return ExportIncidentBundle(
                id = id,
                appVersion = json.optString("appVersion", "unknown"),
                deviceModel = json.optString("deviceModel", "unknown"),
                androidSdk = json.optInt("androidSdk", 0),
                projectId = json.optString("projectId", ""),
                projectName = json.optString("projectName", "Untitled"),
                failedPhase = json.optString("failedPhase", "unknown"),
                errorClass = json.optString("errorClass", "unknown"),
                errorMessage = json.optString("errorMessage", ""),
                encoderPath = json.optString("encoderPath", "unknown"),
                codecLabel = json.optString("codecLabel", "unknown"),
                resolutionLabel = json.optString("resolutionLabel", "unknown"),
                frameRate = json.optInt("frameRate", 0),
                exportAudioOnly = json.optBoolean("exportAudioOnly", false),
                hdrRequested = json.optBoolean("hdrRequested", false),
                streamCopyAttempted = json.optBoolean("streamCopyAttempted", false),
                timelineDurationMs = json.optLong("timelineDurationMs", 0L),
                elapsedMs = json.optLong("elapsedMs", 0L),
                progressSamples = samples,
                mediaWarningCount = json.optInt("mediaWarningCount", 0),
                mediaBlockingCount = json.optInt("mediaBlockingCount", 0),
                mediaHealthSummary = if (json.isNull("mediaHealthSummary")) null
                    else json.optString("mediaHealthSummary").takeIf { it.isNotBlank() },
                timestampEpochMs = json.optLong("timestampEpochMs", 0L)
            )
        }
    }
}

object ExportIncidentBuilder {

    fun build(
        appVersion: String,
        projectId: String,
        projectName: String,
        failedPhase: String,
        error: Throwable?,
        errorMessage: String?,
        codecLabel: String,
        resolutionLabel: String,
        frameRate: Int,
        exportAudioOnly: Boolean,
        hdrRequested: Boolean,
        streamCopyAttempted: Boolean,
        timelineDurationMs: Long,
        startedAtMs: Long,
        finishedAtMs: Long = System.currentTimeMillis(),
        progressSamples: List<Float> = emptyList(),
        mediaWarningCount: Int = 0,
        mediaBlockingCount: Int = 0,
        mediaHealthSummary: String? = null
    ): ExportIncidentBundle {
        val encoderPath = detectEncoderPath(codecLabel)
        val errorClassName = error?.javaClass?.simpleName ?: "Unknown"
        val safeMessage = DiagnosticExportEngine.redactSensitive(
            errorMessage ?: error?.message ?: "No error details"
        )
        val boundedSamples = if (progressSamples.size > MAX_PROGRESS_SAMPLES) {
            val step = progressSamples.size.toFloat() / MAX_PROGRESS_SAMPLES
            (0 until MAX_PROGRESS_SAMPLES).map { i ->
                progressSamples[(i * step).toInt().coerceAtMost(progressSamples.lastIndex)]
            }
        } else {
            progressSamples.toList()
        }

        return ExportIncidentBundle(
            appVersion = appVersion,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidSdk = Build.VERSION.SDK_INT,
            projectId = projectId,
            projectName = projectName,
            failedPhase = failedPhase,
            errorClass = errorClassName,
            errorMessage = safeMessage,
            encoderPath = encoderPath,
            codecLabel = codecLabel,
            resolutionLabel = resolutionLabel,
            frameRate = frameRate,
            exportAudioOnly = exportAudioOnly,
            hdrRequested = hdrRequested,
            streamCopyAttempted = streamCopyAttempted,
            timelineDurationMs = timelineDurationMs.coerceAtLeast(0L),
            elapsedMs = (finishedAtMs - startedAtMs).coerceAtLeast(0L),
            progressSamples = boundedSamples,
            mediaWarningCount = mediaWarningCount.coerceAtLeast(0),
            mediaBlockingCount = mediaBlockingCount.coerceAtLeast(0),
            mediaHealthSummary = mediaHealthSummary?.takeIf { it.isNotBlank() },
            timestampEpochMs = finishedAtMs
        )
    }

    private fun detectEncoderPath(codecLabel: String): String {
        val mimeType = when {
            codecLabel.contains("H.265", ignoreCase = true) ||
                codecLabel.contains("HEVC", ignoreCase = true) -> "video/hevc"
            codecLabel.contains("AV1", ignoreCase = true) -> "video/av01"
            codecLabel.contains("VP9", ignoreCase = true) -> "video/x-vnd.on2.vp9"
            else -> "video/avc"
        }
        return try {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder }
                .filter { info ->
                    info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
                }
            if (codecs.isEmpty()) return "none"
            val hwEncoder = codecs.firstOrNull { !it.name.startsWith("c2.android.") }
            val swEncoder = codecs.firstOrNull { it.name.startsWith("c2.android.") }
            when {
                hwEncoder != null -> "hardware: ${hwEncoder.name}"
                swEncoder != null -> "software: ${swEncoder.name}"
                else -> codecs.first().name
            }
        } catch (_: Exception) {
            "unknown"
        }
    }
}
