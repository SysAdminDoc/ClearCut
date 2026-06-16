package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataScrubEngine @Inject constructor() {

    data class ScrubResult(
        val outputFile: File,
        val tagsRemoved: Int,
        val hadGpsData: Boolean
    )

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION
    )

    private val SENSITIVE_TAGS = GPS_TAGS + arrayOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_USER_COMMENT
    )

    suspend fun scrubImage(inputFile: File, outputFile: File): ScrubResult? = withContext(Dispatchers.IO) {
        if (!inputFile.isFile) return@withContext null
        try {
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
            Log.w(TAG, "EXIF scrub failed for ${inputFile.name}", e)
            null
        }
    }

    fun canScrub(mimeType: String?): Boolean = when (mimeType?.lowercase()) {
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/tiff" -> true
        else -> false
    }

    fun redactUriForManifest(originalUri: String, assetId: String): String {
        return "asset://$assetId"
    }

    companion object {
        private const val TAG = "MetadataScrub"
    }
}
