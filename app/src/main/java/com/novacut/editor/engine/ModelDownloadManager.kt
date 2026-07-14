package com.novacut.editor.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
        /** Hard transfer and storage ceiling. Downloads abort before writing byte maxBytes + 1. */
        val maxBytes: Long,
        val displayName: String = targetFile.name,
        // Optional lowercase hex SHA-256 of the expected file. When set, the
        // download is verified before being moved into place — a length-only
        // check is not enough for model assets we re-use across releases.
        val sha256: String? = null,
        // Fail closed when an engine marks its model as activation-critical.
        // Future registries may still stage exploratory downloads without a
        // hash, but active model paths must opt into this guard.
        val checksumRequired: Boolean = false
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

        val needsNetwork = files.any {
            !isValidModelFile(
                file = it.targetFile,
                minimumBytes = it.minimumBytes,
                expectedSha256 = it.sha256,
                requireChecksum = it.checksumRequired,
            )
        }
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
            if (isValidModelFile(
                    file = request.targetFile,
                    minimumBytes = request.minimumBytes,
                    expectedSha256 = request.sha256,
                    requireChecksum = request.checksumRequired,
                )
            ) {
                completedEstimateBytes = saturatingAdd(completedEstimateBytes, estimatedBytes)
                reusedBytes = saturatingAdd(reusedBytes, request.targetFile.length())
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
            downloadedBytes = saturatingAdd(downloadedBytes, result.actualBytes)
            completedEstimateBytes = saturatingAdd(completedEstimateBytes, estimatedBytes)
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
        // Follow redirects manually so every hop is re-validated as HTTPS. With
        // the default auto-follow, a 3xx from the model host could transparently
        // reach any other host and (for an un-checksummed model) its bytes would
        // be accepted with only a length check.
        val connection = openHttpsFollowingRedirects(
            initialUrl = request.url,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            displayName = request.displayName,
        )

        try {
            val serverLength = connection.contentLengthLong
            validateDeclaredLength(serverLength, request.maxBytes, request.displayName)
            val progressLength = serverLength.takeIf { it > 0L } ?: request.maxBytes
            val actualBytes = BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                writeDownloadTemp(tempFile, input, request.maxBytes) { copied ->
                    val ratio = copied.toDouble() / progressLength.coerceAtLeast(1L).toDouble()
                    val downloadedEstimate = (ratio * request.estimatedBytes)
                        .toLong()
                        .coerceIn(0L, request.estimatedBytes)
                    val progress = (completedEstimateBytes.toDouble() + downloadedEstimate.toDouble()) /
                        safeTotalBytes.toDouble()
                    onProgress(progress.toFloat().coerceIn(0f, 0.99f))
                }
            }

            validateDownloadedFile(
                file = tempFile,
                minimumBytes = request.minimumBytes,
                expectedBytes = serverLength.takeIf { it > 0L },
                displayName = request.displayName
            )
            if (request.sha256 != null) {
                val actualHash = sha256Of(tempFile)
                val expected = requireNotNull(request.sha256) {
                    "sha256 was null but digest was created — logic error"
                }.lowercase()
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

    /**
     * Open a connection to [initialUrl], following 3xx redirects manually and
     * re-validating that every hop is HTTPS. Returns a connected connection whose
     * response is 2xx. The caller owns disconnecting it.
     */
    private fun openHttpsFollowingRedirects(
        initialUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        displayName: String,
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            if (!currentUrl.startsWith("https://")) {
                throw IOException("Refusing non-HTTPS URL for $displayName")
            }
            val c = URL(currentUrl).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.connect()
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without Location for $displayName")
                if (++redirects > MAX_REDIRECTS) throw IOException("Too many redirects for $displayName")
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("HTTP $code for $displayName")
            }
            return c
        }
    }

    private data class SingleDownloadResult(val actualBytes: Long)

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val MAX_REDIRECTS = 5
        private const val STORAGE_HEADROOM_BYTES = 16L * 1024L * 1024L
        private val USER_AGENT = "ClearCut/${com.novacut.editor.ClearCutApp.VERSION.removePrefix("v")}"

        internal fun estimateTotalBytes(files: List<ModelFile>): Long {
            return files.fold(0L) { total, request ->
                saturatingAdd(total, request.estimatedBytes.coerceAtLeast(request.minimumBytes).coerceAtLeast(1L))
            }
        }

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

        internal fun validateDeclaredLength(serverLength: Long, maxBytes: Long, displayName: String) {
            if (serverLength > maxBytes) {
                throw IOException(
                    "Model download is larger than the ${maxBytes}-byte limit: $displayName"
                )
            }
        }

        internal suspend fun writeDownloadTemp(
            tempFile: File,
            input: InputStream,
            maxBytes: Long,
            onBytesCopied: (Long) -> Unit = {}
        ): Long {
            require(maxBytes > 0L) { "maxBytes must be positive" }
            var copied = 0L
            try {
                tempFile.outputStream().buffered().use { output ->
                    copied = copyWithByteCeiling(input, output, maxBytes, onBytesCopied)
                }
                return copied
            } catch (failure: Throwable) {
                tempFile.delete()
                throw failure
            }
        }

        internal suspend fun copyWithByteCeiling(
            input: InputStream,
            output: OutputStream,
            maxBytes: Long,
            onBytesCopied: (Long) -> Unit = {}
        ): Long {
            require(maxBytes > 0L) { "maxBytes must be positive" }
            val buffer = ByteArray(BUFFER_SIZE)
            var copied = 0L
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read.toLong() > maxBytes - copied) {
                    throw IOException("Model download exceeded the $maxBytes-byte limit")
                }
                output.write(buffer, 0, read)
                copied += read
                onBytesCopied(copied)
            }
            return copied
        }

        /**
         * A model is considered valid when the file exists, meets the minimum
         * byte threshold, and (if a checksum was declared) matches it. Without
         * the checksum gate, a partial-but-large-enough download from a prior
         * crash would be accepted and surface as a corrupt-model crash later.
         *
         * On checksum mismatch we delete the corrupt file before returning false
         * so a subsequent retry doesn't waste a SHA-256 pass over the same bad
         * bytes on every isValidModelFile() call (this method is hot — called
         * from every downloadFiles() invocation and every Whisper init).
         *
         * R5.9b — When [requireChecksum] is true, a null [expectedSha256] is a
         * **failure**, not a pass-through. Callers that distribute models from
         * potentially-tampered sources (Hugging Face, GitHub release assets)
         * should pass `requireChecksum = true` on the first-use verification
         * call so a missing SHA-256 in the registry blocks the load instead
         * of silently trusting the bytes.
         */
        internal fun isValidModelFile(
            file: File,
            minimumBytes: Long,
            expectedSha256: String? = null,
            requireChecksum: Boolean = false,
        ): Boolean {
            if (!file.isFile || file.length() < minimumBytes) return false
            if (expectedSha256 == null) {
                if (requireChecksum) {
                    Log.w(
                        "ModelDownloadManager",
                        "Checksum verification required but no SHA-256 recorded for ${file.name}; " +
                            "treating as invalid (R5.9b)"
                    )
                    return false
                }
                return true
            }
            val actual = runCatching { sha256Of(file) }.getOrNull() ?: return false
            if (actual == expectedSha256.lowercase()) return true
            Log.w(
                "ModelDownloadManager",
                "Checksum mismatch for ${file.name} — deleting corrupt cached file"
            )
            runCatching { file.delete() }
            return false
        }

        /**
         * Explicit first-run verification entry point (R5.9b). Callers invoke
         * this once per app launch for each model they intend to load, with
         * `requireChecksum = true` to fail-closed when the registry hasn't
         * recorded a SHA-256 yet. Returns true iff the file is present,
         * meets the minimum size, AND matches the recorded SHA-256.
         */
        fun verifyChecksumOrDelete(
            file: File,
            minimumBytes: Long,
            expectedSha256: String?,
        ): Boolean = isValidModelFile(
            file = file,
            minimumBytes = minimumBytes,
            expectedSha256 = expectedSha256,
            requireChecksum = true,
        )

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
                require(request.maxBytes >= request.minimumBytes) {
                    "Model maximum size must cover its minimum: ${request.displayName}"
                }
                require(request.estimatedBytes in request.minimumBytes..request.maxBytes) {
                    "Model estimate must be between minimum and maximum: ${request.displayName}"
                }
                request.sha256?.let { hash ->
                    require(hash.length == 64 && hash.all { it.isHexDigit() }) {
                        "SHA-256 must be 64 hex characters: ${request.displayName}"
                    }
                }
                require(!request.checksumRequired || request.sha256 != null) {
                    "Checksum is required but missing for ${request.displayName}"
                }
                val canonicalTarget = request.targetFile.absoluteFile.canonicalPath
                require(targets.add(canonicalTarget)) {
                    "Duplicate model target: ${request.targetFile.absolutePath}"
                }
            }
        }

        private fun ensureStorageAvailable(files: List<ModelFile>) {
            val neededBytes = files
                .filterNot {
                    isValidModelFile(
                        file = it.targetFile,
                        minimumBytes = it.minimumBytes,
                        expectedSha256 = it.sha256,
                        requireChecksum = it.checksumRequired,
                    )
                }
                .fold(0L) { total, request -> saturatingAdd(total, request.maxBytes) }
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
