package com.novacut.editor.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

object NativeProcessingPolicy {

    private const val TAG = "NativeProcessingPolicy"

    const val MAX_VIDEO_INPUT_BYTES = 4L * 1024 * 1024 * 1024 // 4 GB
    const val MAX_AUDIO_INPUT_BYTES = 512L * 1024 * 1024      // 512 MB
    const val MAX_IMAGE_INPUT_BYTES = 50L * 1024 * 1024        // 50 MB
    const val MAX_SUBTITLE_INPUT_BYTES = 10L * 1024 * 1024     // 10 MB

    const val TIMEOUT_EXTRACT_AUDIO_MS = 120_000L
    const val TIMEOUT_BURN_SUBTITLES_MS = 600_000L
    const val TIMEOUT_NORMALIZE_LOUDNESS_MS = 300_000L
    const val TIMEOUT_STREAM_COPY_MS = 60_000L
    const val TIMEOUT_CONCAT_MS = 600_000L
    const val TIMEOUT_CHANGE_SPEED_MS = 600_000L
    const val TIMEOUT_REVERSE_MS = 600_000L
    const val TIMEOUT_ENCODE_AUDIO_MS = 120_000L
    const val TIMEOUT_INPAINT_FRAME_MS = 30_000L

    private val SUPPORTED_VIDEO_MIMES = setOf(
        "video/mp4", "video/3gpp", "video/webm", "video/x-matroska",
        "video/quicktime", "video/avi", "video/msvideo", "video/x-msvideo",
        "application/x-troff-msvideo"
    )
    private val SUPPORTED_AUDIO_MIMES = setOf(
        "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav",
        "audio/ogg", "audio/flac", "audio/aac", "audio/opus"
    )
    private val SUPPORTED_IMAGE_MIMES = setOf(
        "image/jpeg", "image/png", "image/webp"
    )
    private val SUPPORTED_SUBTITLE_MIMES = setOf(
        "text/plain", "application/x-subrip", "text/x-ssa",
        "application/x-ass", "text/vtt"
    )

    private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "3gp", "webm", "mkv", "mov", "avi"
    )
    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "pcm"
    )
    private val SUPPORTED_SUBTITLE_EXTENSIONS = setOf(
        "srt", "ssa", "ass", "vtt"
    )

    private val DISABLED_FORMAT_LABELS = mapOf(
        "vobsub" to "VobSub subtitles",
        "tdsc" to "TDSC/AVI video",
        "mace6" to "MACE6/CAF audio",
        "adpcm_adx" to "ADX audio",
        "adx" to "ADX audio",
        "aax" to "AAX audio",
        "png" to "PNG image encoding",
        "apng" to "APNG image encoding",
        "rasc" to "RASC video",
        "iamf" to "IAMF audio",
        "zlib" to "LCL/ZLIB video",
        "lcl" to "LCL/ZLIB video",
    )

    sealed class PolicyViolation(val operation: String) {
        class Oversized(
            val actualBytes: Long,
            val maxBytes: Long,
            operation: String
        ) : PolicyViolation(operation)

        class UnsupportedFormat(
            val detectedMime: String?,
            val detectedExtension: String?,
            operation: String,
            labelOverride: String? = null,
        ) : PolicyViolation(operation) {
            val formatLabel: String? = labelOverride
                ?: detectedExtension?.lowercase(Locale.ROOT)?.let(DISABLED_FORMAT_LABELS::get)
        }

        fun userMessage(): String = when (this) {
            is Oversized -> "File is too large for $operation"
            is UnsupportedFormat -> formatLabel?.let { "Unsupported format ($it) for $operation" }
                ?: "Unsupported file format for $operation"
        }

        fun diagnosticMessage(): String = when (this) {
            is Oversized -> "$operation: input $actualBytes bytes exceeds limit $maxBytes"
            is UnsupportedFormat -> buildString {
                append("$operation: unsupported")
                formatLabel?.let { append(" format=$it") }
                append(" mime=$detectedMime ext=$detectedExtension")
            }
        }
    }

    /**
     * Converts FFmpeg's refusal of a deliberately disabled component into a
     * named policy violation. This keeps security-surface reductions explicit
     * to callers instead of exposing a generic non-zero process result.
     */
    fun unsupportedNativeFailure(
        output: String,
        operation: String,
    ): PolicyViolation.UnsupportedFormat? {
        val normalized = output.lowercase(Locale.ROOT)
        val token = DISABLED_FORMAT_LABELS.keys.firstOrNull { candidate ->
            listOf(
                "unknown decoder '$candidate'",
                "decoder (codec $candidate) not found",
                "no decoder for $candidate",
                "unknown encoder '$candidate'",
                "unknown format '$candidate'",
                "unknown input format: '$candidate'",
            ).any(normalized::contains)
        } ?: return null
        return PolicyViolation.UnsupportedFormat(
            detectedMime = null,
            detectedExtension = null,
            operation = operation,
            labelOverride = DISABLED_FORMAT_LABELS.getValue(token),
        )
    }

    fun validateVideoFile(
        file: File,
        operation: String,
        maxBytes: Long = MAX_VIDEO_INPUT_BYTES
    ): PolicyViolation? {
        if (!file.isFile) return null
        val size = file.length()
        if (size > maxBytes) {
            return PolicyViolation.Oversized(size, maxBytes, operation)
        }
        val ext = normalizeExtension(file.extension)
        if (ext.isNotEmpty() && ext !in SUPPORTED_VIDEO_EXTENSIONS && ext !in SUPPORTED_AUDIO_EXTENSIONS) {
            return PolicyViolation.UnsupportedFormat(null, ext, operation)
        }
        return null
    }

    fun validateVideoPath(
        pathOrName: String,
        operation: String
    ): PolicyViolation? {
        val ext = extensionFromName(pathOrName)
        if (ext != null && ext !in SUPPORTED_VIDEO_EXTENSIONS && ext !in SUPPORTED_AUDIO_EXTENSIONS) {
            return PolicyViolation.UnsupportedFormat(null, ext, operation)
        }
        return null
    }

    fun validateAudioFile(
        file: File,
        operation: String,
        maxBytes: Long = MAX_AUDIO_INPUT_BYTES
    ): PolicyViolation? {
        if (!file.isFile) return null
        val size = file.length()
        if (size > maxBytes) {
            return PolicyViolation.Oversized(size, maxBytes, operation)
        }
        val ext = normalizeExtension(file.extension)
        if (ext.isNotEmpty() && ext !in SUPPORTED_AUDIO_EXTENSIONS) {
            return PolicyViolation.UnsupportedFormat(null, ext, operation)
        }
        return null
    }

    fun validateSubtitleFile(
        file: File,
        operation: String,
        maxBytes: Long = MAX_SUBTITLE_INPUT_BYTES
    ): PolicyViolation? {
        if (!file.isFile) return null
        val size = file.length()
        if (size > maxBytes) {
            return PolicyViolation.Oversized(size, maxBytes, operation)
        }
        val ext = normalizeExtension(file.extension)
        if (ext.isNotEmpty() && ext !in SUPPORTED_SUBTITLE_EXTENSIONS) {
            return PolicyViolation.UnsupportedFormat(null, ext, operation)
        }
        return null
    }

    fun validateUri(
        context: Context,
        uri: Uri,
        operation: String,
        maxBytes: Long,
        supportedMimes: Set<String>
    ): PolicyViolation? {
        val resolver = context.contentResolver
        val size = queryUriSize(resolver, uri)
        if (size != null && size > maxBytes) {
            return PolicyViolation.Oversized(size, maxBytes, operation)
        }
        val mime = normalizeMime(resolver.getType(uri))
        val ext = extensionFromUri(resolver, uri)
        if (mime != null && mime !in supportedMimes) {
            return PolicyViolation.UnsupportedFormat(mime, ext, operation)
        }
        if (mime == null && ext != null) {
            val mimeFromExt = normalizeMime(MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext))
            if (mimeFromExt != null && mimeFromExt !in supportedMimes) {
                return PolicyViolation.UnsupportedFormat(mimeFromExt, ext, operation)
            }
        }
        return null
    }

    fun validateVideoUri(
        context: Context,
        uri: Uri,
        operation: String,
        maxBytes: Long = MAX_VIDEO_INPUT_BYTES
    ): PolicyViolation? {
        return validateUri(context, uri, operation, maxBytes, SUPPORTED_VIDEO_MIMES + SUPPORTED_AUDIO_MIMES)
    }

    fun validateAudioUri(
        context: Context,
        uri: Uri,
        operation: String,
        maxBytes: Long = MAX_AUDIO_INPUT_BYTES
    ): PolicyViolation? {
        return validateUri(context, uri, operation, maxBytes, SUPPORTED_AUDIO_MIMES)
    }

    fun logAndReject(violation: PolicyViolation): Boolean {
        Log.w(TAG, violation.diagnosticMessage())
        return false
    }

    private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val f = File(path)
            return if (f.isFile) f.length() else null
        }
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extensionFromUri(resolver: ContentResolver, uri: Uri): String? {
        return extensionFromName(uri.path)
            ?: extensionFromName(uri.lastPathSegment)
            ?: extensionFromName(queryDisplayName(resolver, uri))
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let { File(it).name }
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extensionFromName(name: String?): String? {
        val cleaned = name
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val separator = maxOf(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'))
        val fileName = cleaned.substring(separator + 1)
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.lastIndex) return null
        return normalizeExtension(fileName.substring(dot + 1))
    }

    private fun normalizeMime(mime: String?): String? {
        return mime
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeExtension(extension: String?): String {
        return extension
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            .orEmpty()
    }
}
