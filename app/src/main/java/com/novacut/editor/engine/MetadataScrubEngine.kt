package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MetadataScrubEngine @Inject constructor() {

    private enum class ReencodeKind {
        WEBP,
        TIFF,
    }

    private data class SensitiveSnapshot(
        val tagsRemoved: Int,
        val hadGpsData: Boolean,
    )

    data class ScrubResult(
        val outputFile: File,
        val tagsRemoved: Int,
        val hadGpsData: Boolean
    )

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_DIFFERENTIAL
    )

    private val SENSITIVE_TAGS = GPS_TAGS + arrayOf(
        // Identity / authorship.
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_MAKER_NOTE,
        // Device fingerprint.
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        // Capture timestamps.
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED
    )

    suspend fun scrubImage(inputFile: File, outputFile: File): ScrubResult? = withContext(Dispatchers.IO) {
        if (!inputFile.isFile) return@withContext null
        if (inputFile.length() > NativeProcessingPolicy.MAX_IMAGE_INPUT_BYTES) return@withContext null
        try {
            outputFile.parentFile?.mkdirs()
            when (reencodeKindFor(inputFile)) {
                ReencodeKind.WEBP -> {
                    val snapshot = readSensitiveSnapshot(inputFile)
                    if (!reencodeWebp(inputFile, outputFile)) return@withContext null
                    return@withContext ScrubResult(
                        outputFile = outputFile,
                        tagsRemoved = snapshot.tagsRemoved,
                        hadGpsData = snapshot.hadGpsData,
                    )
                }

                ReencodeKind.TIFF -> {
                    val snapshot = readSensitiveSnapshot(inputFile)
                    if (!reencodeTiff(inputFile, outputFile)) return@withContext null
                    return@withContext ScrubResult(
                        outputFile = outputFile,
                        tagsRemoved = snapshot.tagsRemoved,
                        hadGpsData = snapshot.hadGpsData,
                    )
                }

                null -> Unit
            }

            val exif = ExifInterface(inputFile)
            val hadGps = GPS_TAGS.any { exif.getAttribute(it) != null }
            var removed = 0

            for (tag in SENSITIVE_TAGS) {
                if (exif.getAttribute(tag) != null) {
                    exif.setAttribute(tag, null)
                    removed++
                }
            }

            if (removed > 0) {
                inputFile.copyTo(outputFile, overwrite = true)
                val outExif = ExifInterface(outputFile)
                for (tag in SENSITIVE_TAGS) {
                    outExif.setAttribute(tag, null)
                }
                outExif.saveAttributes()
            } else {
                inputFile.copyTo(outputFile, overwrite = true)
            }

            ScrubResult(
                outputFile = outputFile,
                tagsRemoved = removed,
                hadGpsData = hadGps
            )
        } catch (e: Exception) {
            Log.w(TAG, "EXIF scrub failed for ${inputFile.redacted()}", e)
            null
        }
    }

    /**
     * Returns whether this engine has a metadata-safe path for the image MIME.
     * JPEG/PNG use ExifInterface in-place editing. WebP is decoded and
     * re-compressed without metadata; TIFF is re-encoded through the bundled
     * FFmpeg decoder because Android's BitmapFactory does not decode TIFF.
     */
    fun canScrub(mimeType: String?): Boolean = when (
        mimeType?.substringBefore(';')?.trim()?.lowercase()
    ) {
        "image/jpeg", "image/jpg", "image/png", "image/webp",
        "image/tiff", "image/tif", "image/x-tiff" -> true
        else -> false
    }

    private fun readSensitiveSnapshot(inputFile: File): SensitiveSnapshot {
        return runCatching {
            val exif = ExifInterface(inputFile)
            SensitiveSnapshot(
                tagsRemoved = SENSITIVE_TAGS.count { exif.getAttribute(it) != null },
                hadGpsData = GPS_TAGS.any { exif.getAttribute(it) != null },
            )
        }.getOrDefault(SensitiveSnapshot(tagsRemoved = 0, hadGpsData = false))
    }

    private fun reencodeWebp(inputFile: File, outputFile: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return false
        val parent = outputFile.parentFile ?: File(".")
        parent.mkdirs()
        val temporary = runCatching {
            File.createTempFile("metadata-scrub-", ".webp", parent)
        }.getOrNull() ?: run {
            bitmap.recycle()
            return false
        }

        return try {
            val encoded = FileOutputStream(temporary).use { stream ->
                bitmap.compress(webpCompressFormat(), 100, stream)
            }
            encoded && temporary.length() > 0L && installTemporary(temporary, outputFile)
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

    private suspend fun reencodeTiff(inputFile: File, outputFile: File): Boolean {
        val parent = outputFile.parentFile ?: File(".")
        parent.mkdirs()
        val temporary = runCatching {
            File.createTempFile("metadata-scrub-", ".tiff", parent)
        }.getOrNull() ?: return false

        return try {
            val encoded = withTimeoutOrNull(TIFF_REENCODE_TIMEOUT_MS) {
                executeFfmpeg(
                    listOf(
                        "-hide_banner",
                        "-loglevel", "error",
                        "-y",
                        "-i", inputFile.absolutePath,
                        "-map_metadata", "-1",
                        "-map", "0:v:0",
                        "-frames:v", "1",
                        "-c:v", "tiff",
                        temporary.absolutePath,
                    )
                )
            } == true
            encoded && temporary.length() > 0L && installTemporary(temporary, outputFile)
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    private fun installTemporary(temporary: File, outputFile: File): Boolean {
        return runCatching {
            temporary.copyTo(outputFile, overwrite = true)
            outputFile.isFile && outputFile.length() > 0L
        }.getOrDefault(false)
    }

    private suspend fun executeFfmpeg(arguments: List<String>): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val session = FFmpegKit.executeWithArgumentsAsync(
                    arguments.toTypedArray(),
                    { completed ->
                        if (continuation.isActive) {
                            continuation.resume(ReturnCode.isSuccess(completed.getReturnCode()))
                        }
                    },
                    { },
                    { },
                )
                continuation.invokeOnCancellation { session.cancel() }
            } catch (_: Throwable) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun reencodeKindFor(inputFile: File): ReencodeKind? {
        when (inputFile.extension.lowercase()) {
            "webp" -> return ReencodeKind.WEBP
            "tif", "tiff" -> return ReencodeKind.TIFF
        }

        val header = ByteArray(12)
        val bytesRead = runCatching {
            inputFile.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        if (bytesRead >= 12 &&
            header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(StandardCharsets.US_ASCII)) &&
            header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(StandardCharsets.US_ASCII))
        ) {
            return ReencodeKind.WEBP
        }
        if (bytesRead >= 4 && (
            header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x49, 0x49, 0x2A, 0x00)) ||
                header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)) ||
                header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x49, 0x49, 0x2B, 0x00)) ||
                header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x4D, 0x4D, 0x00, 0x2B))
            )
        ) {
            return ReencodeKind.TIFF
        }
        return null
    }

    fun redactUriForManifest(originalUri: String, assetId: String): String {
        return "asset://$assetId"
    }

    companion object {
        private const val TAG = "MetadataScrub"
        private const val TIFF_REENCODE_TIMEOUT_MS = 120_000L
    }
}
