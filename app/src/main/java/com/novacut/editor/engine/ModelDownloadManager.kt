package com.novacut.editor.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ModelDownloadManager @Inject constructor() {

    data class ModelFile(
        val url: String,
        val targetFile: File,
        val minimumBytes: Long,
        val estimatedBytes: Long = minimumBytes,
        val displayName: String = targetFile.name
    )

    data class DownloadResult(
        val downloadedBytes: Long,
        val reusedBytes: Long,
        val filesReady: Int
    )

    suspend fun downloadFiles(
        files: List<ModelFile>,
        totalEstimateBytes: Long = estimateTotalBytes(files),
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
        onProgress: (Float) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "At least one model file is required" }
        validateRequests(files)
        ensureStorageAvailable(files)

        var completedEstimateBytes = 0L
        var downloadedBytes = 0L
        var reusedBytes = 0L
        var filesReady = 0
        val safeTotal = totalEstimateBytes.coerceAtLeast(1L)

        files.forEach { request ->
            coroutineContext.ensureActive()
            val estimatedBytes = request.estimatedBytes.coerceAtLeast(request.minimumBytes)
            if (isValidModelFile(request.targetFile, request.minimumBytes)) {
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

        internal fun isValidModelFile(file: File, minimumBytes: Long): Boolean {
            return file.isFile && file.length() >= minimumBytes
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
                val canonicalTarget = request.targetFile.absoluteFile.canonicalPath
                require(targets.add(canonicalTarget)) {
                    "Duplicate model target: ${request.targetFile.absolutePath}"
                }
            }
        }

        private fun ensureStorageAvailable(files: List<ModelFile>) {
            val neededBytes = files
                .filterNot { isValidModelFile(it.targetFile, it.minimumBytes) }
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
    }
}
