package com.novacut.editor.engine

import android.content.Context
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ResolvedTimelineExportRange
import com.novacut.editor.model.Track
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.model.requiresStreamSafeOutput
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val EXPORT_HISTORY_DIR = "diagnostics"
private const val EXPORT_HISTORY_FILE = "export-history.json"
private const val DEFAULT_EXPORT_HISTORY_LIMIT = 25
private const val MAX_EXPORT_HISTORY_BYTES = 256L * 1024L

enum class ExportHistoryStatus {
    COMPLETE,
    FAILED,
    CANCELLED,
    BLOCKED,
    /** Android stopped or refused the export service; the timeline itself was fine. */
    INTERRUPTED
}

/** Statuses whose kept partial file can be offered to Transformer.resume. */
val ExportHistoryStatus.keepsResumablePartial: Boolean
    get() = this == ExportHistoryStatus.CANCELLED || this == ExportHistoryStatus.INTERRUPTED

/** How a finished file compares with what its export asked for. */
enum class ExportContractDisposition {
    /** Read back and matching every requested property. */
    EXACT,
    /** An encoder or pipeline fallback was taken; the file is valid. */
    ACCEPTED_FALLBACK,
    /** Valid, but it differs from the request or the timeline. */
    DEGRADED,
    /** Read back and found unusable: missing tracks, unreadable or the wrong length. */
    REJECTED,
    /** Never read back: an older history row, or the file was gone by then. */
    UNVERIFIED,
}

enum class ExportOutputKind { VIDEO, AUDIO, GIF, IMAGE }

/** A requested property the finished file didn't match. */
enum class ExportContractField { CONTAINER, VIDEO_CODEC, AUDIO_CODEC, SIZE, FRAME_RATE, FAST_START }

/** What a finished file holds, read from the file itself. Null means it couldn't be read. */
data class ExportObservation(
    val valid: Boolean,
    val failure: String? = null,
    val container: String? = null,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val durationMs: Long? = null,
    val fastStart: Boolean? = null,
)

/**
 * What an export asked for next to what its file turned out to hold. Nothing on
 * the observed side is copied from the request or the file name: until the file
 * has been read back, [observed] is null and the disposition is UNVERIFIED.
 */
data class ExportContractReport(
    val kind: ExportOutputKind,
    val requestedContainer: String? = null,
    val requestedVideoMimeType: String? = null,
    val requestedAudioMimeType: String? = null,
    val requestedWidth: Int? = null,
    val requestedHeight: Int? = null,
    /** GIFs are scaled down to fit this width rather than to an exact size. */
    val requestedMaxWidth: Int? = null,
    val requestedFrameRate: Int? = null,
    val requestedFastStart: Boolean = false,
    val expectedDurationMs: Long? = null,
    /** Encoder or pipeline fallbacks taken while the file was made. */
    val fallbackSummary: String? = null,
    /** Differences from the timeline the user accepted before exporting. */
    val degradationSummary: String? = null,
    val observed: ExportObservation? = null,
    val mismatches: List<ExportContractField> = emptyList(),
    val disposition: ExportContractDisposition = ExportContractDisposition.UNVERIFIED,
) {
    fun evaluate(observation: ExportObservation): ExportContractReport {
        val differences = if (observation.valid) mismatchesWith(observation) else emptyList()
        return copy(
            observed = observation,
            mismatches = differences,
            disposition = when {
                !observation.valid -> ExportContractDisposition.REJECTED
                degradationSummary != null -> ExportContractDisposition.DEGRADED
                fallbackSummary != null -> ExportContractDisposition.ACCEPTED_FALLBACK
                differences.isNotEmpty() -> ExportContractDisposition.DEGRADED
                else -> ExportContractDisposition.EXACT
            },
        )
    }

    private fun mismatchesWith(observed: ExportObservation): List<ExportContractField> = buildList {
        if (requestedContainer != null && observed.container != null && observed.container != requestedContainer) {
            add(ExportContractField.CONTAINER)
        }
        if (requestedVideoMimeType != null && !requestedVideoMimeType.equals(observed.videoMimeType, ignoreCase = true)) {
            add(ExportContractField.VIDEO_CODEC)
        }
        // A silent timeline makes a video with no audio track, which isn't a mismatch;
        // an audio export without its audio fails verification instead.
        if (requestedAudioMimeType != null && observed.audioMimeType != null &&
            !requestedAudioMimeType.equals(observed.audioMimeType, ignoreCase = true)
        ) {
            add(ExportContractField.AUDIO_CODEC)
        }
        val sizeOff = when {
            requestedWidth != null && requestedHeight != null ->
                observed.width != requestedWidth || observed.height != requestedHeight
            requestedMaxWidth != null -> (observed.width ?: 0) > requestedMaxWidth
            else -> false
        }
        if (sizeOff) add(ExportContractField.SIZE)
        val frameRate = observed.frameRate
        if (requestedFrameRate != null && frameRate != null && frameRate > 0f &&
            kotlin.math.abs(frameRate - requestedFrameRate) > CONTRACT_FRAME_RATE_TOLERANCE
        ) {
            add(ExportContractField.FRAME_RATE)
        }
        if (requestedFastStart && observed.fastStart == false) add(ExportContractField.FAST_START)
    }
}

private const val CONTRACT_FRAME_RATE_TOLERANCE = 0.5f
private const val CONTRACT_NOTE_MAX_CHARS = 600

data class ExportHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectName: String,
    val status: ExportHistoryStatus,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val elapsedMs: Long,
    val outputPath: String?,
    val outputName: String?,
    val outputBytes: Long?,
    val codecLabel: String,
    val resolutionLabel: String,
    val frameRate: Int,
    val timelineDurationMs: Long,
    val rangeStartFrame: Long? = null,
    val rangeEndFrameExclusive: Long? = null,
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
    /** A non-final Media3 MP4 that can be offered to Transformer.resume. */
    val resumePartialPath: String? = null,
    val resumeProjectFingerprint: String? = null,
    val resumeConfigFingerprint: String? = null,
    val errorMessage: String? = null,
    val diagnosticSummary: String? = null,
    val mediaWarningCount: Int = 0,
    val mediaBlockingCount: Int = 0,
    /** What the color plan said the file would carry; null for outputs without a video track. */
    val requestedColor: DeliveredColor? = null,
    /** What the finished file's video track is tagged with, read back from the file. */
    val observedColor: DeliveredColor? = null,
    /** Requested against observed for a finished file; null for runs that made none. */
    val contract: ExportContractReport? = null,
)

class ExportHistoryStore(
    private val historyFile: File,
    private val retainCount: Int = DEFAULT_EXPORT_HISTORY_LIMIT,
    /** Reads a finished file's video color; production passes ExportOutputVerifier.observedColor. */
    private val observeColor: (File) -> DeliveredColor? = { null },
    /** Reads a finished file back for its contract; null when it can't be read at all. */
    private val inspectOutput: (File, ExportContractReport) -> ExportObservation? = { _, _ -> null },
) {
    fun read(): List<ExportHistoryEntry> {
        if (!historyFile.isFile || historyFile.length() <= 0L || historyFile.length() > MAX_EXPORT_HISTORY_BYTES) {
            return emptyList()
        }
        return runCatching {
            val root = JSONArray(historyFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until root.length()) {
                    root.optJSONObject(index)?.let { json ->
                        exportHistoryEntryFromJson(json)?.let(::add)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun append(entry: ExportHistoryEntry): List<ExportHistoryEntry> {
        val updated = (listOf(entry.withObservedColor().withObservedContract()) + read().filterNot { it.id == entry.id })
            .take(retainCount.coerceAtLeast(1))
        write(updated)
        return updated
    }

    fun remove(id: String): List<ExportHistoryEntry> {
        val updated = read().filterNot { it.id == id }
        write(updated)
        return updated
    }

    // Callers append on an IO thread, so this is where the output gets read back.
    private fun ExportHistoryEntry.withObservedColor(): ExportHistoryEntry {
        if (status != ExportHistoryStatus.COMPLETE || requestedColor == null || observedColor != null) return this
        val output = outputPath?.let(::File)?.takeIf { it.isFile } ?: return this
        return copy(observedColor = observeColor(output))
    }

    private fun ExportHistoryEntry.withObservedContract(): ExportHistoryEntry {
        val requested = contract?.takeIf { it.observed == null } ?: return this
        val output = outputPath?.let(::File)?.takeIf { it.isFile } ?: return this
        val observation = runCatching { inspectOutput(output, requested) }
            .onFailure { AppLog.w("ExportHistoryStore", "Couldn't read ${output.name} back for its contract", it) }
            .getOrNull() ?: return this
        return copy(contract = requested.evaluate(observation))
    }

    private fun write(entries: List<ExportHistoryEntry>) {
        historyFile.parentFile?.mkdirs()
        writeUtf8TextAtomically(historyFile, exportHistoryToJson(entries).toString(2))
    }

    companion object {
        fun forContext(context: Context): ExportHistoryStore {
            return ExportHistoryStore(
                historyFile = File(File(context.filesDir, EXPORT_HISTORY_DIR), EXPORT_HISTORY_FILE),
                observeColor = ExportOutputVerifier::observedColor,
                inspectOutput = ::inspectExportOutput,
            )
        }
    }
}

fun buildExportHistoryEntry(
    projectId: String,
    projectName: String,
    status: ExportHistoryStatus,
    startedAtEpochMs: Long,
    finishedAtEpochMs: Long,
    outputFile: File?,
    config: ExportConfig,
    timelineDurationMs: Long,
    resolvedRange: ResolvedTimelineExportRange? = null,
    resumePartialFile: File? = null,
    resumeProjectFingerprint: String? = null,
    resumeConfigFingerprint: String? = null,
    errorMessage: String? = null,
    diagnosticSummary: String? = null,
    mediaWarningCount: Int = 0,
    mediaBlockingCount: Int = 0,
    /** The timeline's tracks, so the record carries the color the plan asked for. */
    tracks: List<Track> = emptyList(),
    /** Encoder or pipeline fallbacks this run took. */
    fallbackSummary: String? = null,
    /** Timeline differences the user accepted before this run. */
    degradationSummary: String? = null,
): ExportHistoryEntry {
    val existingOutput = outputFile?.takeIf { it.isFile && it.length() > 0L }
    return ExportHistoryEntry(
        projectId = projectId,
        projectName = projectName,
        status = status,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs,
        elapsedMs = (finishedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L),
        outputPath = existingOutput?.absolutePath,
        outputName = existingOutput?.name,
        outputBytes = existingOutput?.length(),
        codecLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
            config.audioCodec.label
        } else {
            config.codec.label
        },
        resolutionLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
            "Audio"
        } else {
            config.resolution.label
        },
        frameRate = config.frameRate,
        timelineDurationMs = timelineDurationMs.coerceAtLeast(0L),
        rangeStartFrame = resolvedRange?.startFrame,
        rangeEndFrameExclusive = resolvedRange?.endFrameExclusive,
        rangeStartMs = resolvedRange?.startMs,
        rangeEndMs = resolvedRange?.endMs,
        resumePartialPath = resumePartialFile?.absolutePath,
        resumeProjectFingerprint = resumeProjectFingerprint?.takeIf { it.isNotBlank() },
        resumeConfigFingerprint = resumeConfigFingerprint?.takeIf { it.isNotBlank() },
        errorMessage = errorMessage?.takeIf { it.isNotBlank() },
        diagnosticSummary = diagnosticSummary?.takeIf { it.isNotBlank() },
        mediaWarningCount = mediaWarningCount.coerceAtLeast(0),
        mediaBlockingCount = mediaBlockingCount.coerceAtLeast(0),
        requestedColor = requestedExportColor(config, tracks),
        contract = existingOutput?.takeIf { status == ExportHistoryStatus.COMPLETE }?.let { output ->
            requestedExportContract(
                config = config,
                outputExtension = output.extension,
                expectedDurationMs = resolvedRange?.let { it.endMs - it.startMs } ?: timelineDurationMs,
                fallbackSummary = fallbackSummary,
                degradationSummary = degradationSummary,
            )
        },
    )
}

/** What a finished file of this kind should hold, before anything has been read back. */
internal fun requestedExportContract(
    config: ExportConfig,
    outputExtension: String,
    expectedDurationMs: Long,
    fallbackSummary: String? = null,
    degradationSummary: String? = null,
): ExportContractReport {
    val extension = outputExtension.lowercase()
    val kind = when {
        config.exportAsGif -> ExportOutputKind.GIF
        config.exportAsContactSheet || config.captureFrameOnly -> ExportOutputKind.IMAGE
        config.exportAudioOnly || config.exportStemsOnly -> ExportOutputKind.AUDIO
        else -> ExportOutputKind.VIDEO
    }
    val base = ExportContractReport(
        kind = kind,
        requestedContainer = containerForExtension(extension),
        fallbackSummary = fallbackSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(CONTRACT_NOTE_MAX_CHARS),
        degradationSummary = degradationSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(CONTRACT_NOTE_MAX_CHARS),
    )
    val duration = expectedDurationMs.takeIf { it > 0L }
    return when (kind) {
        ExportOutputKind.VIDEO -> {
            val (width, height) = config.resolution.forAspect(config.aspectRatio)
            val safe = Media3ExportRobustnessPolicy.encoderSafeDimensions(width, height)
            base.copy(
                requestedVideoMimeType = (if (config.transparentBackground) VideoCodec.VP9 else config.codec).mimeType,
                requestedAudioMimeType = config.audioCodec.mimeType,
                requestedWidth = safe.width,
                requestedHeight = safe.height,
                requestedFrameRate = config.frameRate,
                requestedFastStart = config.requiresStreamSafeOutput(extension),
                expectedDurationMs = duration,
            )
        }
        ExportOutputKind.AUDIO -> base.copy(
            requestedAudioMimeType = config.audioCodec.mimeType,
            expectedDurationMs = duration,
        )
        ExportOutputKind.GIF -> base.copy(requestedMaxWidth = config.gifMaxWidth.takeIf { it > 0 })
        ExportOutputKind.IMAGE -> base
    }
}

private fun containerForExtension(extension: String): String? = when (extension) {
    "mp4", "m4a", "mov" -> "MP4"
    "webm" -> "WEBM"
    "gif" -> "GIF"
    "png" -> "PNG"
    "jpg", "jpeg" -> "JPEG"
    else -> null
}

/** Production reader: MediaExtractor for audio and video, the file header for images. */
internal fun inspectExportOutput(file: File, contract: ExportContractReport): ExportObservation =
    when (contract.kind) {
        ExportOutputKind.VIDEO, ExportOutputKind.AUDIO -> {
            val expected = contract.expectedDurationMs ?: 0L
            val result = ExportOutputVerifier.verify(
                outputFile = file,
                expectVideo = contract.kind == ExportOutputKind.VIDEO,
                expectAudio = contract.kind == ExportOutputKind.AUDIO,
                expectedDurationMs = expected,
                durationToleranceMs = maxOf(2_000L, expected / 20L),
            )
            ExportObservation(
                valid = result.valid,
                failure = result.reason,
                container = result.container.takeIf { it != ExportContainer.UNKNOWN }?.name,
                videoMimeType = result.videoMimeType,
                audioMimeType = result.audioMimeType,
                width = result.width.takeIf { result.hasVideo && it > 0 },
                height = result.height.takeIf { result.hasVideo && it > 0 },
                frameRate = result.frameRate.takeIf { result.hasVideo && it > 0f },
                durationMs = result.durationMs.takeIf { it > 0L },
                fastStart = result.fastStart.takeIf { result.container == ExportContainer.MP4 },
            )
        }
        ExportOutputKind.GIF, ExportOutputKind.IMAGE -> ImageHeader.read(file)?.let { header ->
            ExportObservation(valid = true, container = header.format, width = header.width, height = header.height)
        } ?: ExportObservation(valid = false, failure = "Not a readable PNG, JPEG or GIF image")
    }

/** Format and size from an image file's first bytes, without decoding it. */
internal data class ImageHeader(val format: String, val width: Int, val height: Int) {
    companion object {
        private const val PROBE_BYTES = 64 * 1024

        fun read(file: File): ImageHeader? = runCatching {
            val buffer = ByteArray(PROBE_BYTES)
            var filled = 0
            file.inputStream().use { input ->
                while (filled < buffer.size) {
                    val read = input.read(buffer, filled, buffer.size - filled)
                    if (read < 0) break
                    filled += read
                }
            }
            parse(buffer.copyOf(filled))
        }.getOrNull()

        fun parse(bytes: ByteArray): ImageHeader? {
            fun u8(i: Int) = bytes[i].toInt() and 0xFF
            fun be16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            fun le16(i: Int) = u8(i) or (u8(i + 1) shl 8)
            fun be32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)
            val header = when {
                bytes.size >= 24 && u8(0) == 0x89 && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG" &&
                    String(bytes, 12, 4, Charsets.US_ASCII) == "IHDR" ->
                    ImageHeader("PNG", be32(16), be32(20))
                bytes.size >= 10 && String(bytes, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" } ->
                    ImageHeader("GIF", le16(6), le16(8))
                bytes.size >= 4 && u8(0) == 0xFF && u8(1) == 0xD8 -> {
                    var i = 2
                    var found: ImageHeader? = null
                    while (found == null && i + 9 < bytes.size && u8(i) == 0xFF) {
                        val marker = u8(i + 1)
                        val length = be16(i + 2)
                        if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                            found = ImageHeader("JPEG", be16(i + 7), be16(i + 5))
                        }
                        i += 2 + length
                    }
                    found
                }
                else -> null
            }
            return header?.takeIf { it.width > 0 && it.height > 0 }
        }
    }
}

/** Null for exports that carry no tagged video track: audio, stems, GIFs, stills and contact sheets. */
internal fun requestedExportColor(config: ExportConfig, tracks: List<Track>): DeliveredColor? {
    if (tracks.isEmpty() || config.exportAudioOnly || config.exportStemsOnly || config.exportAsGif ||
        config.captureFrameOnly || config.exportAsContactSheet
    ) {
        return null
    }
    val plan = CompositionPlanBuilder.build(tracks)
    return ColorRenderPlanner.plan(config.colorPolicy, plan.visualTracks, plan.durationMs).expected
}

private fun exportHistoryToJson(entries: List<ExportHistoryEntry>): JSONArray {
    return JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().apply {
                put("id", entry.id)
                put("projectId", entry.projectId)
                put("projectName", entry.projectName)
                put("status", entry.status.name)
                put("startedAtEpochMs", entry.startedAtEpochMs)
                put("finishedAtEpochMs", entry.finishedAtEpochMs)
                put("elapsedMs", entry.elapsedMs)
                putNullable("outputPath", entry.outputPath)
                putNullable("outputName", entry.outputName)
                putNullable("outputBytes", entry.outputBytes)
                put("codecLabel", entry.codecLabel)
                put("resolutionLabel", entry.resolutionLabel)
                put("frameRate", entry.frameRate)
                put("timelineDurationMs", entry.timelineDurationMs)
                putNullable("rangeStartFrame", entry.rangeStartFrame)
                putNullable("rangeEndFrameExclusive", entry.rangeEndFrameExclusive)
                putNullable("rangeStartMs", entry.rangeStartMs)
                putNullable("rangeEndMs", entry.rangeEndMs)
                putNullable("resumePartialPath", entry.resumePartialPath)
                putNullable("resumeProjectFingerprint", entry.resumeProjectFingerprint)
                putNullable("resumeConfigFingerprint", entry.resumeConfigFingerprint)
                putNullable("errorMessage", entry.errorMessage)
                putNullable("diagnosticSummary", entry.diagnosticSummary)
                put("mediaWarningCount", entry.mediaWarningCount)
                put("mediaBlockingCount", entry.mediaBlockingCount)
                putNullable("requestedColor", entry.requestedColor?.name)
                putNullable("observedColor", entry.observedColor?.name)
                putNullable("contract", entry.contract?.toJson())
            })
        }
    }
}

private fun exportHistoryEntryFromJson(json: JSONObject): ExportHistoryEntry? {
    val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
    val projectId = json.optString("projectId").takeIf { it.isNotBlank() } ?: return null
    val projectName = json.optString("projectName").takeIf { it.isNotBlank() } ?: "Untitled"
    val status = runCatching {
        ExportHistoryStatus.valueOf(json.optString("status"))
    }.getOrNull() ?: return null
    return ExportHistoryEntry(
        id = id,
        projectId = projectId,
        projectName = projectName,
        status = status,
        startedAtEpochMs = json.optLong("startedAtEpochMs").coerceAtLeast(0L),
        finishedAtEpochMs = json.optLong("finishedAtEpochMs").coerceAtLeast(0L),
        elapsedMs = json.optLong("elapsedMs").coerceAtLeast(0L),
        outputPath = json.optNullableString("outputPath"),
        outputName = json.optNullableString("outputName"),
        outputBytes = json.optNullableLong("outputBytes"),
        codecLabel = json.optString("codecLabel", "Unknown"),
        resolutionLabel = json.optString("resolutionLabel", "Unknown"),
        frameRate = json.optInt("frameRate").coerceAtLeast(0),
        timelineDurationMs = json.optLong("timelineDurationMs").coerceAtLeast(0L),
        rangeStartFrame = json.optNullableLong("rangeStartFrame"),
        rangeEndFrameExclusive = json.optNullableLong("rangeEndFrameExclusive"),
        rangeStartMs = json.optNullableLong("rangeStartMs"),
        rangeEndMs = json.optNullableLong("rangeEndMs"),
        resumePartialPath = json.optNullableString("resumePartialPath"),
        resumeProjectFingerprint = json.optNullableString("resumeProjectFingerprint"),
        resumeConfigFingerprint = json.optNullableString("resumeConfigFingerprint"),
        errorMessage = json.optNullableString("errorMessage"),
        diagnosticSummary = json.optNullableString("diagnosticSummary"),
        mediaWarningCount = json.optInt("mediaWarningCount").coerceAtLeast(0),
        mediaBlockingCount = json.optInt("mediaBlockingCount").coerceAtLeast(0),
        requestedColor = json.optDeliveredColor("requestedColor"),
        observedColor = json.optDeliveredColor("observedColor"),
        contract = json.optJSONObject("contract")?.let(::exportContractFromJson),
    )
}

private fun ExportContractReport.toJson(): JSONObject = JSONObject().apply {
    put("kind", kind.name)
    putNullable("requestedContainer", requestedContainer)
    putNullable("requestedVideoMimeType", requestedVideoMimeType)
    putNullable("requestedAudioMimeType", requestedAudioMimeType)
    putNullable("requestedWidth", requestedWidth)
    putNullable("requestedHeight", requestedHeight)
    putNullable("requestedMaxWidth", requestedMaxWidth)
    putNullable("requestedFrameRate", requestedFrameRate)
    put("requestedFastStart", requestedFastStart)
    putNullable("expectedDurationMs", expectedDurationMs)
    putNullable("fallbackSummary", fallbackSummary)
    putNullable("degradationSummary", degradationSummary)
    putNullable("observed", observed?.let { observed ->
        JSONObject().apply {
            put("valid", observed.valid)
            putNullable("failure", observed.failure)
            putNullable("container", observed.container)
            putNullable("videoMimeType", observed.videoMimeType)
            putNullable("audioMimeType", observed.audioMimeType)
            putNullable("width", observed.width)
            putNullable("height", observed.height)
            putNullable("frameRate", observed.frameRate?.toDouble())
            putNullable("durationMs", observed.durationMs)
            putNullable("fastStart", observed.fastStart)
        }
    })
    put("mismatches", JSONArray(mismatches.map { it.name }))
    put("disposition", disposition.name)
}

// Anything unreadable comes back as an unverified request, never as an observation.
private fun exportContractFromJson(json: JSONObject): ExportContractReport? {
    val kind = ExportOutputKind.entries.firstOrNull { it.name == json.optString("kind") } ?: return null
    val observed = json.optJSONObject("observed")?.let { o ->
        ExportObservation(
            valid = o.optBoolean("valid"),
            failure = o.optNullableString("failure"),
            container = o.optNullableString("container"),
            videoMimeType = o.optNullableString("videoMimeType"),
            audioMimeType = o.optNullableString("audioMimeType"),
            width = o.optNullableInt("width"),
            height = o.optNullableInt("height"),
            frameRate = o.optNullableDouble("frameRate")?.toFloat(),
            durationMs = o.optNullableLong("durationMs"),
            fastStart = if (o.has("fastStart") && !o.isNull("fastStart")) o.optBoolean("fastStart") else null,
        )
    }
    val disposition = ExportContractDisposition.entries.firstOrNull { it.name == json.optString("disposition") }
        ?.takeIf { observed != null || it == ExportContractDisposition.UNVERIFIED }
        ?: ExportContractDisposition.UNVERIFIED
    val mismatches = json.optJSONArray("mismatches")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            ExportContractField.entries.firstOrNull { it.name == array.optString(index) }
        }
    }.orEmpty()
    return ExportContractReport(
        kind = kind,
        requestedContainer = json.optNullableString("requestedContainer"),
        requestedVideoMimeType = json.optNullableString("requestedVideoMimeType"),
        requestedAudioMimeType = json.optNullableString("requestedAudioMimeType"),
        requestedWidth = json.optNullableInt("requestedWidth"),
        requestedHeight = json.optNullableInt("requestedHeight"),
        requestedMaxWidth = json.optNullableInt("requestedMaxWidth"),
        requestedFrameRate = json.optNullableInt("requestedFrameRate"),
        requestedFastStart = json.optBoolean("requestedFastStart"),
        expectedDurationMs = json.optNullableLong("expectedDurationMs"),
        fallbackSummary = json.optNullableString("fallbackSummary"),
        degradationSummary = json.optNullableString("degradationSummary"),
        observed = observed,
        mismatches = if (observed != null) mismatches else emptyList(),
        disposition = disposition,
    )
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optDeliveredColor(name: String): DeliveredColor? =
    optNullableString(name)?.let { value -> DeliveredColor.entries.firstOrNull { it.name == value } }

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name).takeIf { it >= 0L }
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}
