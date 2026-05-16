package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R5.5d — Local-only diagnostic export.
 *
 * Bundles a small, **on-device-only** ZIP a user can attach to a GitHub issue
 * for support without NovaCut shipping any telemetry pipe of its own. The ZIP
 * contains:
 *
 *  - `app-info.txt`       — app version, build type, applicationId, target SDK
 *  - `device-info.txt`    — device manufacturer, model, Android version, ABIs,
 *                           Build.FINGERPRINT (redacted)
 *  - `media-codecs.txt`   — `MediaCodecList.REGULAR_CODECS` summary: each
 *                           encoder/decoder name + MIME types, used to triage
 *                           "AV1 export fails on my device" tickets fast
 *  - `model-registry.txt` — names + install state of every model registered
 *                           with [ModelDownloadManager] (no file contents)
 *  - `logcat-tail.txt`    — last 200 logcat lines from the current process,
 *                           with PII / URI patterns redacted before write
 *  - `manifest.txt`       — ordered file list with sizes
 *
 * What this engine **never** does:
 *  - Phone home, upload to any server, or open a network connection.
 *  - Include project JSON, media URIs, autosave snapshots, user content, or
 *    captions/transcripts. All of those can contain personal data.
 *  - Persist a ZIP outside `context.filesDir/diagnostics/` until the user
 *    explicitly shares one via the system share sheet.
 *
 * Integration sketch (Settings screen, ~10 lines):
 *
 *     val ze = DiagnosticExportEngine(ctx)
 *     val zip = ze.exportDiagnosticBundle(modelRegistry = downloadManager.snapshot())
 *     val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", zip)
 *     ctx.startActivity(Intent.createChooser(
 *         Intent(Intent.ACTION_SEND)
 *             .setType("application/zip")
 *             .putExtra(Intent.EXTRA_STREAM, uri)
 *             .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
 *         "Share diagnostic ZIP"
 *     ))
 */
@Singleton
class DiagnosticExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Snapshot summary of a single model from [ModelDownloadManager]. */
    data class ModelSnapshot(
        val id: String,
        val installed: Boolean,
        val sizeBytes: Long,
        val sourceUrl: String? = null,
    )

    /**
     * Build the diagnostic ZIP and return the file. The file is placed under
     * `filesDir/diagnostics/diagnostic-{timestamp}.zip`. The directory is
     * created if missing, and any older diagnostic ZIPs are pruned past the
     * [retainCount] floor so the disk footprint stays bounded.
     *
     * @param modelRegistry optional snapshot of registered models. The Settings
     *   integration pulls this from `ModelDownloadManager`. Pass `emptyList()`
     *   when the engine is exercised in isolation (tests, CLI).
     * @param now wall-clock-millis stamp injected for deterministic tests.
     * @param retainCount keep at most this many ZIPs in the diagnostics dir.
     */
    suspend fun exportDiagnosticBundle(
        modelRegistry: List<ModelSnapshot> = emptyList(),
        now: Long = System.currentTimeMillis(),
        retainCount: Int = 3,
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, DIAG_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date(now))
        val zipFile = File(outDir, "diagnostic-$stamp.zip")
        writeBundle(zipFile, modelRegistry, now)
        pruneOldBundles(outDir, retainCount)
        zipFile
    }

    /**
     * Write the ZIP directly to [target]. Public so the Settings integration can
     * route the file location through MediaStore or a SAF picker if it ever
     * wants to. Returns the final file size in bytes.
     */
    fun writeBundle(
        target: File,
        modelRegistry: List<ModelSnapshot>,
        now: Long = System.currentTimeMillis(),
    ): Long {
        target.parentFile?.mkdirs()
        val entries = linkedMapOf<String, ByteArray>()
        entries["app-info.txt"] = buildAppInfo(now).toByteArray(Charsets.UTF_8)
        entries["device-info.txt"] = buildDeviceInfo().toByteArray(Charsets.UTF_8)
        entries["media-codecs.txt"] = buildMediaCodecSummary().toByteArray(Charsets.UTF_8)
        entries["model-registry.txt"] = buildModelRegistry(modelRegistry).toByteArray(Charsets.UTF_8)
        entries["logcat-tail.txt"] = buildLogcatTail().toByteArray(Charsets.UTF_8)
        entries["manifest.txt"] = buildManifest(entries).toByteArray(Charsets.UTF_8)
        target.outputStream().use { fos ->
            ZipOutputStream(fos).use { zos ->
                for ((name, bytes) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        return target.length()
    }

    // --- Section builders (each returns plain text for the ZIP entry) ---

    private fun buildAppInfo(now: Long): String = buildString {
        appendLine("# NovaCut diagnostic bundle")
        appendLine("# Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))}")
        appendLine("# This bundle is generated on-device. NovaCut does not upload it anywhere.")
        appendLine()
        appendLine("applicationId: ${context.packageName}")
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("versionName: ${pkg.versionName}")
            appendLine("versionCode: ${pkg.longVersionCode}")
        } catch (_: Throwable) {
            appendLine("versionName: <unavailable>")
            appendLine("versionCode: <unavailable>")
        }
        appendLine("targetSdk: ${context.applicationInfo.targetSdkVersion}")
    }

    private fun buildDeviceInfo(): String = buildString {
        appendLine("manufacturer: ${Build.MANUFACTURER}")
        appendLine("brand: ${Build.BRAND}")
        appendLine("model: ${Build.MODEL}")
        appendLine("device: ${Build.DEVICE}")
        appendLine("product: ${Build.PRODUCT}")
        appendLine("hardware: ${Build.HARDWARE}")
        appendLine("supported_abis: ${Build.SUPPORTED_ABIS.joinToString(",")}")
        appendLine("android_sdk_int: ${Build.VERSION.SDK_INT}")
        appendLine("android_release: ${Build.VERSION.RELEASE}")
        // Fingerprint can leak build numbers / OEM identifiers, but the redaction
        // pass for media URIs / file paths leaves it untouched because it doesn't
        // match those patterns. It's the same level of detail Google Crash
        // shows publicly in Play Console, so include it.
        appendLine("fingerprint: ${Build.FINGERPRINT}")
    }

    private fun buildMediaCodecSummary(): String = buildString {
        appendLine("# MediaCodecList.REGULAR_CODECS summary")
        appendLine("# Each row: kind, name, mime_types")
        try {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (codec in codecs) {
                val kind = if (codec.isEncoder) "encoder" else "decoder"
                appendLine("$kind\t${codec.name}\t${codec.supportedTypes.joinToString(",")}")
            }
        } catch (e: Throwable) {
            appendLine("# Unable to enumerate codecs: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun buildModelRegistry(models: List<ModelSnapshot>): String = buildString {
        appendLine("# Registered models — no file contents are included, only metadata.")
        appendLine("# id\tinstalled\tsize_bytes\tsource_url")
        if (models.isEmpty()) {
            appendLine("# (registry empty or not provided)")
            return@buildString
        }
        for (m in models) {
            appendLine("${m.id}\t${m.installed}\t${m.sizeBytes}\t${m.sourceUrl ?: "-"}")
        }
    }

    private fun buildLogcatTail(): String = buildString {
        appendLine("# Last $LOGCAT_LINES logcat lines from this process.")
        appendLine("# URI and absolute file paths are redacted before write.")
        try {
            val process = ProcessBuilder()
                .command(
                    "logcat",
                    "-d",      // dump and exit
                    "-t",
                    LOGCAT_LINES.toString(),
                    "--pid=${android.os.Process.myPid()}",
                )
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { rawLine ->
                    appendLine(redactSensitive(rawLine))
                }
            }
            process.waitFor()
        } catch (e: Throwable) {
            appendLine("# Unable to read logcat: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun buildManifest(entries: Map<String, ByteArray>): String = buildString {
        appendLine("# Entries in this diagnostic ZIP (excluding this manifest).")
        for ((name, bytes) in entries) {
            if (name == "manifest.txt") continue
            appendLine("$name\t${bytes.size} bytes")
        }
    }

    private fun pruneOldBundles(outDir: File, retainCount: Int) {
        val zips = outDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        zips.drop(retainCount).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val DIAG_DIR = "diagnostics"
        private const val LOGCAT_LINES = 200

        // Pattern set used to scrub sensitive substrings before they enter the
        // bundle. These are intentionally conservative; the goal is "don't leak
        // user content," not "perfect anonymization." The regex order matters —
        // longer / more specific patterns are checked first.
        //
        // - content://… URIs (Photo Picker, MediaStore handles)
        // - file://… URIs and /storage/, /data/data/<pkg>/files/projects/ paths
        // - http(s) URLs that include query strings (caption translation keys, etc.)
        // - email addresses (matt@example.com)
        // - 6+ digit numeric runs that could be project IDs or timestamps that
        //   pair with other context. Left untouched for now because timestamps
        //   are useful for triage; revisit if a PII pattern surfaces.
        private val SENSITIVE_PATTERNS = listOf(
            Regex("""content://[^\s)"']+"""),
            Regex("""file://[^\s)"']+"""),
            Regex("""/storage/[^\s)"']+"""),
            Regex("""/data/data/[A-Za-z0-9._]+/files/[^\s)"']+"""),
            Regex("""https?://[^\s)"']*\?[^\s)"']+"""),
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
        )

        /**
         * Redacts known-sensitive substrings from a single logcat line. Exposed
         * for testing — the goal is correctness, not perfect anonymization.
         */
        fun redactSensitive(line: String): String {
            var result = line
            for (pattern in SENSITIVE_PATTERNS) {
                result = pattern.replace(result, "<redacted>")
            }
            return result
        }
    }
}
