package com.novacut.editor.engine

import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.Track
import org.json.JSONObject

/**
 * The complete persisted project boundary: database metadata plus the
 * schema-versioned edit document. Stores may choose different containers
 * (Room rows, autosave files, archives, or templates), but they all exchange
 * this same shape at the boundary.
 */
data class ProjectDocument(
    val project: Project,
    val state: AutoSaveState,
    val documentVersion: Int = ProjectDocumentApplicator.FORMAT_VERSION,
) {
    init {
        require(project.id == state.projectId) {
            "Project document id ${project.id} does not match state id ${state.projectId}"
        }
        require(documentVersion in 1..ProjectDocumentApplicator.FORMAT_VERSION) {
            "Unsupported project document version $documentVersion"
        }
    }
}

sealed class ProjectDocumentReadResult {
    data class Loaded(
        val document: ProjectDocument,
        val report: ProjectRestoreReport = ProjectRestoreReport.EMPTY,
        val warnings: List<String> = emptyList(),
        val migratedFromLegacyState: Boolean = false,
    ) : ProjectDocumentReadResult()

    data class FutureSchema(
        val documentVersion: Int,
        val stateVersion: Int?,
        val supportedDocumentVersion: Int,
        val supportedStateVersion: Int,
    ) : ProjectDocumentReadResult()

    data class Corrupt(val cause: Throwable) : ProjectDocumentReadResult()
}

/**
 * Canonical constructor, applicator, and codec for persisted projects.
 *
 * The codec is deliberately backward-compatible: the old root-level
 * [AutoSaveState] JSON is accepted as a legacy document, while new writes use
 * an envelope that also preserves project metadata. Unknown envelope fields
 * are reported rather than silently treated as part of the contract.
 */
object ProjectDocumentApplicator {
    const val FORMAT_VERSION = 1

    private const val DOCUMENT_VERSION_KEY = "documentVersion"
    private const val PROJECT_KEY = "project"
    private const val STATE_KEY = "state"

    private val knownEnvelopeKeys = setOf(DOCUMENT_VERSION_KEY, PROJECT_KEY, STATE_KEY)

    /** Normalize every store's state so the project and edit document share one id. */
    fun capture(project: Project, state: AutoSaveState): ProjectDocument = ProjectDocument(
        project = project,
        state = state.copy(projectId = project.id),
    )

    /** Wrap a legacy state when a store has no separate project metadata. */
    fun fromState(
        state: AutoSaveState,
        projectName: String = "Imported Project",
    ): ProjectDocument = capture(
        project = Project(id = state.projectId, name = projectName),
        state = state,
    )

    /** Re-key an imported or copied document without leaving stale references behind. */
    fun rekey(document: ProjectDocument, project: Project): ProjectDocument = capture(
        project = project,
        state = document.state,
    )

    /** Apply a timeline interchange result to the same persisted document boundary. */
    fun fromTimelineExchange(
        project: Project,
        tracks: List<Track>,
        textOverlays: List<TextOverlay> = emptyList(),
        timelineMarkers: List<TimelineMarker> = emptyList(),
        playheadMs: Long = 0L,
    ): ProjectDocument = capture(
        project = project,
        state = AutoSaveState(
            projectId = project.id,
            tracks = tracks,
            textOverlays = textOverlays,
            timelineMarkers = timelineMarkers,
            playheadMs = playheadMs,
        ),
    )

    fun encode(document: ProjectDocument): String {
        val project = document.project
        return JSONObject().apply {
            put(DOCUMENT_VERSION_KEY, FORMAT_VERSION)
            put(PROJECT_KEY, JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("aspectRatio", project.aspectRatio.name)
                put("frameRate", project.frameRate)
                put("frameRateNumerator", project.frameRateNumerator)
                put("frameRateDenominator", project.frameRateDenominator)
                put("resolution", project.resolution.name)
                put("createdAt", project.createdAt)
                put("updatedAt", project.updatedAt)
                put("durationMs", project.durationMs)
                project.thumbnailUri?.let { put("thumbnailUri", it) }
                project.templateId?.let { put("templateId", it) }
                put("proxyEnabled", project.proxyEnabled)
                put("version", project.version)
                put("notes", project.notes)
                project.deletedAtEpochMs?.let { put("deletedAtEpochMs", it) }
            })
            put(STATE_KEY, JSONObject(document.state.serialize()))
        }.toString(2)
    }

    /** Read an envelope or migrate the pre-envelope autosave/archive payload. */
    fun read(raw: String): ProjectDocumentReadResult {
        return try {
            val root = JSONObject(raw)
            if (!root.has(DOCUMENT_VERSION_KEY)) {
                val stateVersion = AutoSaveState.peekSchemaVersion(raw)
                if (stateVersion != null && stateVersion > AutoSaveState.FORMAT_VERSION) {
                    return ProjectDocumentReadResult.FutureSchema(
                        documentVersion = FORMAT_VERSION,
                        stateVersion = stateVersion,
                        supportedDocumentVersion = FORMAT_VERSION,
                        supportedStateVersion = AutoSaveState.FORMAT_VERSION,
                    )
                }
                val restored = AutoSaveState.deserializeWithReport(raw)
                val warnings = mutableListOf("Legacy project state was wrapped in the current document format.")
                appendRestoreWarning(warnings, restored.report)
                return ProjectDocumentReadResult.Loaded(
                    document = fromState(restored.state),
                    report = restored.report,
                    warnings = warnings,
                    migratedFromLegacyState = true,
                )
            }

            val documentVersion = root.optInt(DOCUMENT_VERSION_KEY, 0)
            val stateObject = root.optJSONObject(STATE_KEY)
                ?: throw IllegalArgumentException("Project document is missing state")
            val stateJson = stateObject.toString()
            val stateVersion = AutoSaveState.peekSchemaVersion(stateJson)
            if (documentVersion > FORMAT_VERSION ||
                (stateVersion != null && stateVersion > AutoSaveState.FORMAT_VERSION)
            ) {
                return ProjectDocumentReadResult.FutureSchema(
                    documentVersion = documentVersion,
                    stateVersion = stateVersion,
                    supportedDocumentVersion = FORMAT_VERSION,
                    supportedStateVersion = AutoSaveState.FORMAT_VERSION,
                )
            }
            if (documentVersion < 1) {
                throw IllegalArgumentException("Invalid project document version $documentVersion")
            }

            val restored = AutoSaveState.deserializeWithReport(stateJson)
            val warnings = mutableListOf<String>()
            val unknownKeys = root.keys().asSequence().filterNot(knownEnvelopeKeys::contains).toList()
            if (unknownKeys.isNotEmpty()) {
                warnings += "Ignored unknown project document fields: ${unknownKeys.joinToString()}"
            }
            appendRestoreWarning(warnings, restored.report)
            val projectJson = root.optJSONObject(PROJECT_KEY)
            val project = if (projectJson == null) {
                warnings += "Project metadata was missing; defaults were synthesized."
                Project(id = restored.state.projectId)
            } else {
                projectFromJson(projectJson, restored.state.projectId, warnings)
            }
            val normalizedProject = if (project.id == restored.state.projectId) {
                project
            } else {
                warnings += "Project metadata id did not match the edit document; the document id won."
                project.copy(id = restored.state.projectId)
            }
            ProjectDocumentReadResult.Loaded(
                document = capture(normalizedProject, restored.state),
                report = restored.report,
                warnings = warnings,
            )
        } catch (e: Exception) {
            ProjectDocumentReadResult.Corrupt(e)
        }
    }

    private fun appendRestoreWarning(
        warnings: MutableList<String>,
        report: ProjectRestoreReport,
    ) {
        if (report.isPartial) {
            warnings += "Project document was partially restored: ${report.summary()}."
        }
    }

    private fun projectFromJson(
        json: JSONObject,
        fallbackId: String,
        warnings: MutableList<String>,
    ): Project {
        val aspectRatio = parseEnum(json.optString("aspectRatio"), AspectRatio.RATIO_16_9, "aspectRatio", warnings)
        val resolution = parseEnum(json.optString("resolution"), Resolution.FHD_1080P, "resolution", warnings)
        val frameRate = json.optInt("frameRate", 30).coerceIn(1, 240_000)
        val numerator = json.optInt("frameRateNumerator", frameRate).coerceIn(1, 240_000)
        val denominator = json.optInt("frameRateDenominator", 1).coerceIn(1, 10_000)
        return Project(
            id = json.optString("id", fallbackId).ifBlank { fallbackId },
            name = json.optString("name", "Untitled"),
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            frameRateNumerator = numerator,
            frameRateDenominator = denominator,
            resolution = resolution,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            durationMs = json.optLong("durationMs", 0L).coerceAtLeast(0L),
            thumbnailUri = json.optString("thumbnailUri", "").takeIf { it.isNotBlank() },
            templateId = json.optString("templateId", "").takeIf { it.isNotBlank() },
            proxyEnabled = json.optBoolean("proxyEnabled", false),
            version = json.optInt("version", 1).coerceAtLeast(1),
            notes = json.optString("notes", ""),
            deletedAtEpochMs = if (json.has("deletedAtEpochMs") && !json.isNull("deletedAtEpochMs")) {
                json.optLong("deletedAtEpochMs")
            } else {
                null
            },
        )
    }

    private fun <T : Enum<T>> parseEnum(
        raw: String,
        fallback: T,
        field: String,
        warnings: MutableList<String>,
    ): T {
        if (raw.isBlank()) {
            warnings += "Project metadata field $field was missing; defaulted to ${fallback.name}."
            return fallback
        }
        @Suppress("UNCHECKED_CAST")
        val match = fallback.javaClass.enumConstants
            ?.firstOrNull { (it as Enum<*>).name == raw } as? T
        return match ?: run {
            warnings += "Unknown project metadata value $field=$raw; defaulted to ${fallback.name}."
            fallback
        }
    }
}
