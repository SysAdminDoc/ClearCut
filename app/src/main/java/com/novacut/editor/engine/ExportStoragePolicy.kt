package com.novacut.editor.engine

import android.content.Context
import android.os.StatFs
import android.system.Os
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import java.io.File

object ExportStoragePolicy {
    const val FIXED_RESERVE_BYTES = 128L * 1024L * 1024L
    private const val UNKNOWN_OUTPUT_BYTES = 64L * 1024L * 1024L
    private const val REVERSE_WORST_CASE_BITS_PER_SECOND = 200_000_000L

    enum class Mode { VIDEO, AUDIO_ONLY, STEMS, GIF, CONTACT_SHEET, FRAME }
    enum class Location { OUTPUT, CACHE, SHARED }
    enum class Suggestion { VIDEO, GIF, BATCH, FRAME }

    data class SpaceSnapshot(val volumeId: String, val availableBytes: Long)
    fun interface SpaceProbe { fun probe(path: File): SpaceSnapshot }

    data class Request(
        val durationMs: Long,
        val videoBitrate: Int,
        val audioBitrate: Int,
        val mode: Mode,
        val targetSizeBytes: Long? = null,
        val reversedDurationMs: Long = 0L,
        val reversedSourceBytes: Long = 0L,
        val reversedSourceBytesKnown: Boolean = false,
        val allSourceBytes: Long = 0L,
        val allSourceBytesKnown: Boolean = false,
        val mixedRender: Boolean = false,
        val burnSubtitles: Boolean = false,
        val width: Int = 1920,
        val height: Int = 1080,
        val gifFrameRate: Int = 15,
        val contactSheetClipCount: Int = 0,
        val contactSheetColumns: Int = 4,
    )

    data class Estimate(
        val finalOutputBytes: Long,
        val outputTemporaryBytes: Long,
        val cacheTemporaryBytes: Long,
        val unbounded: Boolean,
        val suggestion: Suggestion,
    )

    data class VolumeRequirement(
        val volumeId: String,
        val location: Location,
        val requiredBytes: Long,
        val availableBytes: Long,
    )

    sealed interface Failure {
        val suggestion: Suggestion

        data class UnknownSize(override val suggestion: Suggestion) : Failure
        data class InsufficientSpace(
            val location: Location,
            val requiredBytes: Long,
            val availableBytes: Long,
            override val suggestion: Suggestion,
        ) : Failure
    }

    data class Check(
        val estimate: Estimate,
        val requirements: List<VolumeRequirement>,
        val failure: Failure?,
    ) {
        val canProceed: Boolean get() = failure == null
    }

    fun request(
        durationMs: Long,
        config: ExportConfig,
        tracks: List<Track>,
        mixedRender: Boolean = false,
        sourceSizeBytes: (Clip) -> Long? = { null },
    ): Request {
        val mode = when {
            config.exportAsGif -> Mode.GIF
            config.exportAsContactSheet -> Mode.CONTACT_SHEET
            config.captureFrameOnly -> Mode.FRAME
            config.exportStemsOnly -> Mode.STEMS
            config.exportAudioOnly -> Mode.AUDIO_ONLY
            else -> Mode.VIDEO
        }
        val clips = tracks.flatMap { it.clips }
        val reversed = clips.filter { it.isReversed }
        val sourceSizesById = clips.associate { clip ->
            clip.id to sourceSizeBytes(clip)?.takeIf { size -> size > 0L }
        }
        val sourceSizes = clips.map { sourceSizesById[it.id] }
        val reversedSizes = reversed.map { sourceSizesById[it.id] }
        val sourceBytes = sourceSizes.filterNotNull()
            .fold(0L, ::saturatingAdd)
        val reversedBytes = reversedSizes.filterNotNull()
            .fold(0L, ::saturatingAdd)
        val visualClipCount = tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
            .sumOf { it.clips.size }
        val (resolvedWidth, resolvedHeight) = config.resolution.forAspect(config.aspectRatio)
        val width = if (mode == Mode.GIF) config.gifMaxWidth.coerceAtLeast(1) else resolvedWidth
        val height = if (mode == Mode.GIF) {
            (width.toLong() * resolvedHeight / resolvedWidth.coerceAtLeast(1))
                .coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else resolvedHeight
        return Request(
            durationMs = durationMs,
            videoBitrate = config.videoBitrate,
            audioBitrate = config.audioBitrate,
            mode = mode,
            targetSizeBytes = config.targetSizeBytes,
            reversedDurationMs = reversed.sumOf { it.durationMs.coerceAtLeast(0L) },
            reversedSourceBytes = reversedBytes,
            reversedSourceBytesKnown = reversed.isNotEmpty() && reversedSizes.all { it != null },
            allSourceBytes = sourceBytes,
            allSourceBytesKnown = clips.isNotEmpty() && sourceSizes.all { it != null },
            mixedRender = mixedRender,
            burnSubtitles = config.burnSubtitles,
            width = width,
            height = height,
            gifFrameRate = config.gifFrameRate,
            contactSheetClipCount = visualClipCount,
            contactSheetColumns = config.contactSheetColumns,
        )
    }

    fun estimate(request: Request): Estimate {
        val duration = request.durationMs.coerceAtLeast(0L)
        val encodedVideo = withContainerOverhead(encodedBytes(
            request.videoBitrate.toLong() + request.audioBitrate.toLong(), duration
        ))
        val needsDuration = request.mode !in setOf(Mode.CONTACT_SHEET, Mode.FRAME)
        val unbounded = needsDuration && duration <= 0L && request.targetSizeBytes == null
        val finalBytes = when (request.mode) {
            Mode.VIDEO -> request.targetSizeBytes?.takeIf { it > 0L }
                ?: encodedVideo.takeIf { it > 0L } ?: UNKNOWN_OUTPUT_BYTES
            // The current Transformer path still writes one MP4 with video for these flags.
            // Budget the real renderer until a true remove-video/stem graph ships.
            Mode.AUDIO_ONLY, Mode.STEMS -> encodedVideo.takeIf { it > 0L } ?: UNKNOWN_OUTPUT_BYTES
            Mode.GIF -> {
                val frames = if (duration > 0L) {
                    (saturatingMultiply(duration, request.gifFrameRate.coerceIn(1, 60).toLong()) / 1000L)
                        .coerceIn(1L, 300L)
                } else 1L
                val pixels = saturatingMultiply(request.width.toLong(), request.height.toLong(), frames)
                saturatingAdd(saturatingMultiply(pixels, 2L), saturatingMultiply(frames, 1024L), 64L * 1024L)
            }
            Mode.CONTACT_SHEET -> {
                val columns = request.contactSheetColumns.coerceIn(1, 8)
                val rows = ((request.contactSheetClipCount.coerceAtLeast(1) + columns - 1) / columns)
                val sheetWidth = 32L + columns * 320L + (columns - 1) * 12L
                val sheetHeight = 32L + rows * 208L + (rows - 1) * 12L
                saturatingMultiply(sheetWidth, sheetHeight, 4L)
            }
            Mode.FRAME -> saturatingMultiply(request.width.toLong(), request.height.toLong(), 4L)
        }
        val reverseByConfig = encodedBytes(
            request.videoBitrate.toLong() + request.audioBitrate.toLong(),
            request.reversedDurationMs,
        )
        val reverseByRate = encodedBytes(REVERSE_WORST_CASE_BITS_PER_SECOND, request.reversedDurationMs)
        val reverseBySource = saturatingMultiply(request.reversedSourceBytes, 2L)
        val reverseTemporary = if (request.reversedDurationMs > 0L) {
            if (request.reversedSourceBytesKnown) {
                maxOf(reverseByConfig, reverseBySource)
            } else {
                maxOf(reverseByConfig, reverseByRate)
            }
        } else 0L
        val mixedByRate = encodedBytes(REVERSE_WORST_CASE_BITS_PER_SECOND, duration)
        val mixedTemporary = if (request.mixedRender) {
            if (request.allSourceBytesKnown) {
                maxOf(finalBytes, request.allSourceBytes)
            } else {
                maxOf(finalBytes, mixedByRate)
            }
        } else 0L
        val outputTemporary = maxOf(if (request.burnSubtitles) finalBytes else 0L, mixedTemporary)
        val suggestion = when (request.mode) {
            Mode.GIF -> Suggestion.GIF
            Mode.FRAME, Mode.CONTACT_SHEET -> Suggestion.FRAME
            else -> Suggestion.VIDEO
        }
        return Estimate(finalBytes, outputTemporary, reverseTemporary, unbounded, suggestion)
    }

    fun check(
        request: Request,
        outputDirectory: File,
        cacheDirectory: File,
        probe: SpaceProbe = SpaceProbe(::probeSpace),
    ): Check = evaluate(estimate(request), outputDirectory, cacheDirectory, probe)

    fun checkBatch(
        requests: List<Request>,
        outputDirectory: File,
        cacheDirectory: File,
        probe: SpaceProbe = SpaceProbe(::probeSpace),
    ): Check {
        val estimates = requests.map(::estimate)
        val combined = Estimate(
            finalOutputBytes = estimates.fold(0L) { total, item -> saturatingAdd(total, item.finalOutputBytes) },
            outputTemporaryBytes = estimates.maxOfOrNull { it.outputTemporaryBytes } ?: 0L,
            cacheTemporaryBytes = estimates.maxOfOrNull { it.cacheTemporaryBytes } ?: 0L,
            unbounded = estimates.any { it.unbounded },
            suggestion = Suggestion.BATCH,
        )
        return evaluate(combined, outputDirectory, cacheDirectory, probe)
    }

    private fun evaluate(
        estimate: Estimate,
        outputDirectory: File,
        cacheDirectory: File,
        probe: SpaceProbe,
    ): Check {
        if (estimate.unbounded) {
            return Check(estimate, emptyList(), Failure.UnknownSize(estimate.suggestion))
        }
        val output = probe.probe(outputDirectory)
        val cache = probe.probe(cacheDirectory)
        val rawDemands = listOf(
            Triple(output, Location.OUTPUT, saturatingAdd(estimate.finalOutputBytes, estimate.outputTemporaryBytes)),
            Triple(cache, Location.CACHE, estimate.cacheTemporaryBytes),
        ).filter { it.third > 0L }
        val requirements = rawDemands.groupBy { it.first.volumeId }.map { (volumeId, demands) ->
            val demandBytes = demands.fold(0L) { total, item -> saturatingAdd(total, item.third) }
            val locations = demands.map { it.second }.toSet()
            VolumeRequirement(
                volumeId = volumeId,
                location = if (locations.size > 1) Location.SHARED else locations.single(),
                requiredBytes = saturatingAdd(demandBytes, FIXED_RESERVE_BYTES),
                availableBytes = demands.minOf { it.first.availableBytes.coerceAtLeast(0L) },
            )
        }
        val short = requirements.firstOrNull { it.availableBytes < it.requiredBytes }
        val failure = short?.let {
            Failure.InsufficientSpace(it.location, it.requiredBytes, it.availableBytes, estimate.suggestion)
        }
        return Check(estimate, requirements, failure)
    }

    private fun probeSpace(path: File): SpaceSnapshot {
        var existing: File? = path
        while (existing != null && !existing.exists()) existing = existing.parentFile
        val target = existing ?: throw IllegalStateException("No existing storage ancestor for ${path.absolutePath}")
        return SpaceSnapshot(
            volumeId = Os.stat(target.absolutePath).st_dev.toString(),
            availableBytes = StatFs(target.absolutePath).availableBytes,
        )
    }

    private fun encodedBytes(bitsPerSecond: Long, durationMs: Long): Long =
        saturatingMultiply(bitsPerSecond.coerceAtLeast(0L), durationMs.coerceAtLeast(0L)) / 8000L

    private fun withContainerOverhead(bytes: Long): Long = saturatingAdd(bytes, bytes / 20L)

    private fun saturatingMultiply(vararg values: Long): Long = values.fold(1L) { total, value ->
        if (value <= 0L) 0L else if (total > Long.MAX_VALUE / value) Long.MAX_VALUE else total * value
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right <= 0L) left else if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun saturatingAdd(vararg values: Long): Long = values.fold(0L, ::saturatingAdd)
}

fun Context.exportStorageFailureMessage(failure: ExportStoragePolicy.Failure): String {
    val suggestion = when (failure.suggestion) {
        ExportStoragePolicy.Suggestion.VIDEO -> getString(R.string.export_storage_suggestion_video)
        ExportStoragePolicy.Suggestion.GIF -> getString(R.string.export_storage_suggestion_gif)
        ExportStoragePolicy.Suggestion.BATCH -> getString(R.string.export_storage_suggestion_batch)
        ExportStoragePolicy.Suggestion.FRAME -> getString(R.string.export_storage_suggestion_frame)
    }
    return when (failure) {
        is ExportStoragePolicy.Failure.UnknownSize ->
            getString(R.string.export_storage_unknown_size, suggestion)
        is ExportStoragePolicy.Failure.InsufficientSpace -> {
            val location = when (failure.location) {
                ExportStoragePolicy.Location.OUTPUT -> getString(R.string.export_storage_location_output)
                ExportStoragePolicy.Location.CACHE -> getString(R.string.export_storage_location_cache)
                ExportStoragePolicy.Location.SHARED -> getString(R.string.export_storage_location_shared)
            }
            getString(
                R.string.export_storage_insufficient,
                location,
                formatStorageBytes(failure.requiredBytes),
                formatStorageBytes(failure.availableBytes),
                suggestion,
            )
        }
    }
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.0f KB".format(bytes / 1024.0)
}

class ExportStorageException(
    val failure: ExportStoragePolicy.Failure,
    message: String,
) : IllegalStateException(message)
