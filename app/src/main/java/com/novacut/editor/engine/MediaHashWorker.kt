package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@HiltWorker
class MediaHashWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val mediaDir = managedMediaDir(applicationContext)
            if (!mediaDir.isDirectory) return@withContext Result.success()

            val sidecarDir = File(mediaDir, ".sidecars")
            if (!sidecarDir.isDirectory) return@withContext Result.success()

            val sidecars = sidecarDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            var hashed = 0
            var skipped = 0
            var deduped = 0

            val hashIndex = mutableMapOf<String, String>()

            for (sidecar in sidecars) {
                ensureActive()
                try {
                    val json = JSONObject(sidecar.readText())
                    val status = json.optString("hashStatus", "pending")
                    if (status == "complete") {
                        val sha = json.optString("sha256", "")
                        if (sha.isNotEmpty()) hashIndex[sha] = json.optString("assetId", "")
                        skipped++
                        continue
                    }

                    val managedUri = json.optString("managedUri", "")
                    if (managedUri.isEmpty()) { skipped++; continue }

                    val managedFile = resolveLocalFile(managedUri)
                    if (managedFile == null || !managedFile.isFile) {
                        json.put("hashStatus", "missing")
                        sidecar.writeText(json.toString(2))
                        skipped++
                        continue
                    }

                    val sha256 = computeSha256(managedFile)
                    if (sha256 == null) { skipped++; continue }

                    json.put("sha256", sha256)
                    json.put("hashStatus", "complete")
                    sidecar.writeText(json.toString(2))

                    val assetId = json.optString("assetId", "")
                    val existing = hashIndex[sha256]
                    if (existing != null && existing != assetId) {
                        deduped++
                        Log.d(TAG, "Duplicate detected: $assetId matches $existing (sha256=$sha256)")
                    }
                    hashIndex[sha256] = assetId
                    hashed++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to hash sidecar ${sidecar.name}", e)
                }
            }

            Log.d(TAG, "Media hash pass: hashed=$hashed skipped=$skipped duplicates=$deduped")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Media hash worker failed", e)
            Result.failure()
        }
    }

    private fun resolveLocalFile(uriString: String): File? {
        if (uriString.startsWith("file://")) {
            return File(android.net.Uri.parse(uriString).path ?: return null)
        }
        if (uriString.startsWith("/")) return File(uriString)
        return null
    }

    private suspend fun computeSha256(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            file.inputStream().buffered().use { stream ->
                var bytesRead = stream.read(buffer)
                while (bytesRead != -1) {
                    ensureActive()
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = stream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "SHA-256 computation failed for ${file.name}", e)
            null
        }
    }

    companion object {
        private const val TAG = "MediaHashWorker"
        const val WORK_NAME = "novacut-media-hash"
    }
}
