package com.novacut.editor.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    data class ModelFile(
        val url: String,
        val targetFile: File,
        val minimumBytes: Long,
        val estimatedBytes: Long = minimumBytes,
        val displayName: String = targetFile.name,
        // Optional lowercase hex SHA-256 of the expected file. When set, the
        // download is verified before being moved into place — a length-only
        // check is not enough for model assets we re-use across releases.
        val sha256: String? = null
    )

    data class DownloadResult(
        val downloadedBytes: Long,
        val reusedBytes: Long,
        val filesReady: Int
    )

    /**
     * Thrown when the active network is metered and the caller required
     * Wi-Fi-only. Surface to the user with a "switch to Wi-Fi or override"
     * prompt — never silently fall back to mobile data for a 100 MB model.
     */
    class MeteredNetworkException(message: String) : IOException(message)

    suspend fun downloadFiles(
        files: List<ModelFile>,
        totalEstimateBytes: Long = estimateTotalBytes(files),
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
        wifiOnly: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "At least one model file is required" }
        validateRequests(files)
        ensureStorageAvailable(files)

        val needsNetwork = files.any { !isValidModelFile(it.targetFile, it.minimumBytes, it.sha256) }
        if (needsNetwork && wifiOnly && isMeteredNetwork()) {
            throw MeteredNetworkException(
                "Wi-Fi-only is enabled and the active network is metered or unavailable"
            )
        }

        var completedEstimateBytes = 0L
        var downloadedBytes = 0L
        var reusedBytes = 0L
        var filesReady = 0
        val safeTotal = totalEstimateBytes.coerceAtLeast(1L)

        files.forEach { request ->
            coroutineContext.ensureActive()
            val estimatedBytes = request.estimatedBytes.coerceAtLeast(request.minimumBytes)
            if (isValidModelFile(request.targetFile, request.minimumBytes, request.sha256)) {
                completedEstimateBytes += estimatedBytes
                reusedBytes += request.targetFile.length()
                filesReady++
                onProgress((completedEstimateBytes.toFloat() / safeTotal).coerceIn(0f, 0.99f))
                return@forEach
            }

            val result = downloadOne(
                request = request,
                completedEstimateBytes = completedEstimateBytes,
                safeTotalBytes = safeTotal,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                onProgress = onProgress
            )
            downloadedBytes += result.actualBytes
            completedEstimateBytes += estimatedBytes
            filesReady++
            onProgress((completedEstimateBytes.toFloat() / safeTotal).coerceIn(0f, 0.99f))
        }

        onProgress(1f)
        DownloadResult(
            downloadedBytes = downloadedBytes,
            reusedBytes = reusedBytes,
            filesReady = filesReady
        )
    }

    /**
     * Delete a previously-downloaded model file and any sibling `.tmp` artifacts
     * left behind by an interrupted download. Returns true if the target file
     * existed and was removed; false if it was already absent.
     */
    fun removeModel(targetFile: File): Boolean {
        val canonical = targetFile.absoluteFile
        val parent = canonical.parentFile
        parent?.listFiles { f ->
            f.name.startsWith("${canonical.name}.") && f.name.endsWith(".tmp")
        }?.forEach { runCatching { it.delete() } }
        return if (canonical.exists()) canonical.delete() else false
    }

    /**
     * Bulk variant of [removeModel] for callers that group several files behind
     * a single feature ("remove all SAM 2 weights"). Returns the count actually
     * deleted so the UI can confirm "freed N files".
     */
    fun removeModels(targetFiles: List<File>): Int =
        targetFiles.count { removeModel(it) }

    /**
     * Total bytes on disk for a set of model files. Useful for "X uses Y MB"
     * disclosures next to a Remove button.
     */
    fun installedBytes(targetFiles: List<File>): Long =
        targetFiles.sumOf { if (it.isFile) it.length() else 0L }

    /**
     * True when the active network is metered or unavailable. Public so callers
     * can disable a download button preemptively rather than waiting for an
     * exception mid-flow.
     */
    fun isMeteredNetwork(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val active = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(active) ?: return true
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true
        // NET_CAPABILITY_NOT_METERED is set on Wi-Fi/Ethernet that the user hasn't
        // marked as metered. Cellular and metered Wi-Fi both lack it.
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun downloadOne(
        request: ModelFile,
        completedEstimateBytes: Long,
        safeTotalBytes: Long,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        onProgress: (Float) -> Unit
    ): SingleDownloadResult {
        val targetDir = request.targetFile.absoluteFile.parentFile
            ?: throw IOException("No parent directory for ${request.targetFile.absolutePath}")
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.exists()) {
            throw IOException("Failed to create model directory: ${targetDir.absolutePath}")
        }

        val tempFile = File.createTempFile("${request.targetFile.name}.", ".tmp", targetDir)
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)

        val digest = request.sha256?.let { MessageDigest.getInstance("SHA-256") }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} for ${request.displayName}")
            }

            val serverLength = connection.contentLengthLong
            var actualBytes = 0L
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        actualBytes += read
                        val downloadedEstimate = when {
                            serverLength > 0L -> (actualBytes.toFloat() / serverLength * request.estimatedBytes).toLong()
                            else -> actualBytes
                        }
                        val progress = (completedEstimateBytes + downloadedEstimate)
                            .toFloat() / safeTotalBytes
                        onProgress(progress.coerceIn(0f, 0.99f))
                    }
                }
            }

            validateDownloadedFile(
                file = tempFile,
                minimumBytes = request.minimumBytes,
                expectedBytes = serverLength.takeIf { it > 0L },
                displayName = request.displayName
            )
            if (digest != null) {
                val actualHash = digest.digest().toHexString()
                val expected = request.sha256!!.lowercase()
                if (actualHash != expected) {
                    throw IOException(
                        "Checksum mismatch for ${request.displayName}: expected $expected, got $actualHash"
                    )
                }
            }
            moveFileReplacing(tempFile, request.targetFile)
            return SingleDownloadResult(actualBytes = actualBytes)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private data class SingleDownloadResult(val actualBytes: Long)

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val STORAGE_HEADROOM_BYTES = 16L * 1024L * 1024L
        private val USER_AGENT = "NovaCut/${com.novacut.editor.NovaCutApp.VERSION.removePrefix("v")}"

        internal fun estimateTotalBytes(files: List<ModelFile>): Long {
            return files.sumOf { it.estimatedBytes.coerceAtLeast(it.minimumBytes).coerceAtLeast(1L) }
        }

        /**
         * A model is considered valid when the file exists, meets the minimum
         * byte threshold, and (if a checksum was declared) matches it. Without
         * the checksum gate, a partial-but-large-enough download from a prior
         * crash would be accepted and surface as a corrupt-model crash later.
         */
        internal fun isValidModelFile(file: File, minimumBytes: Long, expectedSha256: String? = null): Boolean {
            if (!file.isFile || file.length() < minimumBytes) return false
            if (expectedSha256 == null) return true
            return runCatching { sha256Of(file) == expectedSha256.lowercase() }.getOrDefault(false)
        }

        internal fun validateDownloadedFile(
            file: File,
            minimumBytes: Long,
            expectedBytes: Long?,
            displayName: String
        ) {
            if (!file.isFile || file.length() <= 0L) {
                throw IOException("Downloaded model is empty: $displayName")
            }
            if (expectedBytes != null && file.length() != expectedBytes) {
                throw IOException("Downloaded model is incomplete: $displayName")
            }
            if (file.length() < minimumBytes) {
                throw IOException("Downloaded model is smaller than expected: $displayName")
            }
        }

        private fun validateRequests(files: List<ModelFile>) {
            val targets = hashSetOf<String>()
            files.forEach { request ->
                require(request.url.startsWith("https://")) {
                    "Model downloads must use HTTPS: ${request.displayName}"
                }
                require(request.minimumBytes > 0L) {
                    "Model minimum size must be positive: ${request.displayName}"
                }
                request.sha256?.let { hash ->
                    require(hash.length == 64 && hash.all { it.isHexDigit() }) {
                        "SHA-256 must be 64 hex characters: ${request.displayName}"
                    }
                }
                val canonicalTarget = request.targetFile.absoluteFile.canonicalPath
                require(targets.add(canonicalTarget)) {
                    "Duplicate model target: ${request.targetFile.absolutePath}"
                }
            }
        }

        private fun ensureStorageAvailable(files: List<ModelFile>) {
            val neededBytes = files
                .filterNot { isValidModelFile(it.targetFile, it.minimumBytes, it.sha256) }
                .sumOf { it.estimatedBytes.coerceAtLeast(it.minimumBytes).coerceAtLeast(1L) }
            if (neededBytes <= 0L) return

            val targetDir = files.first().targetFile.absoluteFile.parentFile ?: return
            val usableBytes = targetDir.takeIf { it.exists() }?.usableSpace
                ?: targetDir.parentFile?.usableSpace
                ?: return
            if (usableBytes < neededBytes + STORAGE_HEADROOM_BYTES) {
                throw IOException("Not enough free storage for model download")
            }
        }

        private fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHexString()
        }

        private fun ByteArray.toHexString(): String = buildString(size * 2) {
            for (b in this@toHexString) {
                val v = b.toInt() and 0xff
                append(HEX_DIGITS[v ushr 4])
                append(HEX_DIGITS[v and 0x0f])
            }
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
