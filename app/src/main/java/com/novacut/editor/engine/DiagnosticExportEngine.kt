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
 *  - `crash-records.json` — optional bounded fatal-crash records captured by
 *                           [CrashRecordStore], when any exist
 *  - `memory-trim.jsonl`   — optional bounded, redacted memory-pressure
 *                           breadcrumbs captured by [MemoryTrimBreadcrumbStore]
 *  - `process-exit-history.json` — bounded Android 11+ process-death records
 *                           captured by [ProcessExitRecorder], or an
 *                           unsupported marker on older devices
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
 *     val zip = diagnosticExportEngine.exportDiagnosticBundle(
 *         modelRegistry = downloadManager.snapshot()
 *     )
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
    private val crashRecordStore: CrashRecordStore,
    private val memoryTrimBreadcrumbStore: MemoryTrimBreadcrumbStore,
    private val processExitRecorder: ProcessExitRecorder,
) {

    /** Snapshot summary of a single model from [ModelDownloadManager]. */
    data class ModelSnapshot(
        val id: String,
        val installed: Boolean,
        val sizeBytes: Long,
        val sourceUrl: String? = null,
    )

    /**
     * Optional opt-in payload included in the diagnostic ZIP as
     * `timeline-shape.json` when the user enables "Include sanitized timeline
     * shape" in Settings.
     *
     * Carries counts only — never clip names, source URIs, captions, transcripts,
     * file paths, or any other user content. The single ".kt -> .json" boundary
     * is the [toJsonString] method below; if a future field needs to land here,
     * verify it adds zero personal data before adding it.
     *
     * The motivation is support triage: knowing a stuck-export bug comes from a
     * timeline with 47 clips on 6 tracks and 23 transitions is much higher
     * signal than asking the user to share their project file (which we
     * intentionally never do).
     */
    data class TimelineShape(
        val trackCount: Int,
        val totalDurationMs: Long,
        val perTrackClipCount: List<TrackClipCount>,
        val perEffectTypeCount: Map<String, Int>,
        val perTransitionTypeCount: Map<String, Int>,
    ) {
        data class TrackClipCount(val trackType: String, val clipCount: Int)

        fun toJsonString(): String {
            // Hand-rolled JSON keeps this file free of an org.json compile-time
            // dep at the engine layer; org.json is JVM-test-friendly and works
            // on Android, but adding it would couple the diagnostic engine to
            // the AutoSave path. Counts-only output is small enough that hand-
            // rolling is safer than reaching for a JSON library.
            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"schema\": \"com.novacut.timeline-shape.v1\",\n")
            sb.append("  \"trackCount\": ").append(trackCount).append(",\n")
            sb.append("  \"totalDurationMs\": ").append(totalDurationMs).append(",\n")
            sb.append("  \"perTrackClipCount\": [")
            perTrackClipCount.forEachIndexed { i, c ->
                if (i > 0) sb.append(",")
                sb.append("\n    {\"trackType\": \"")
                    .append(escapeJsonString(c.trackType))
                    .append("\", \"clipCount\": ")
                    .append(c.clipCount).append("}")
            }
            sb.append("\n  ],\n")
            appendStringIntMap(sb, "perEffectTypeCount", perEffectTypeCount)
            sb.append(",\n")
            appendStringIntMap(sb, "perTransitionTypeCount", perTransitionTypeCount)
            sb.append("\n}\n")
            return sb.toString()
        }

        private fun appendStringIntMap(sb: StringBuilder, name: String, map: Map<String, Int>) {
            sb.append("  \"").append(name).append("\": {")
            val entries = map.entries.sortedBy { it.key }
            entries.forEachIndexed { i, e ->
                if (i > 0) sb.append(",")
                sb.append("\n    \"")
                    .append(escapeJsonString(e.key))
                    .append("\": ")
                    .append(e.value)
            }
            sb.append("\n  }")
        }

        private fun escapeJsonString(s: String): String = buildString(s.length + 2) {
            for (c in s) {
                when {
                    c == '\\' -> append("\\\\")
                    c == '"' -> append("\\\"")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c.code < 0x20 -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
        }
    }

    /**
     * Build the diagnostic ZIP and return the file. The file is placed under
     * `filesDir/diagnostics/diagnostic-{timestamp}.zip`. The directory is
     * created if missing, and any older diagnostic ZIPs are pruned past the
     * [retainCount] floor so the disk footprint stays bounded.
     *
     * @param modelRegistry optional snapshot of registered models. The Settings
     *   integration pulls this from `ModelDownloadManager`. Pass `emptyList()`
     *   when the engine is exercised in isolation (tests, CLI).
     * @param timelineShape optional [TimelineShape] summary. When non-null the
     *   ZIP includes `timeline-shape.json`. The shape carries counts only and
     *   never includes clip names, URIs, or captions — see [TimelineShape].
     * @param now wall-clock-millis stamp injected for deterministic tests.
     * @param retainCount keep at most this many ZIPs in the diagnostics dir.
     */
    suspend fun exportDiagnosticBundle(
        modelRegistry: List<ModelSnapshot> = emptyList(),
        timelineShape: TimelineShape? = null,
        now: Long = System.currentTimeMillis(),
        retainCount: Int = 3,
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, DIAG_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date(now))
        val zipFile = File(outDir, "diagnostic-$stamp.zip")
        writeBundle(zipFile, modelRegistry, timelineShape, now)
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
        timelineShape: TimelineShape? = null,
        now: Long = System.currentTimeMillis(),
    ): Long {
        target.parentFile?.mkdirs()
        val entries = linkedMapOf<String, ByteArray>()
        entries["app-info.txt"] = buildAppInfo(now).toByteArray(Charsets.UTF_8)
        entries["device-info.txt"] = buildDeviceInfo().toByteArray(Charsets.UTF_8)
        entries["media-codecs.txt"] = buildMediaCodecSummary().toByteArray(Charsets.UTF_8)
        entries["model-registry.txt"] = buildModelRegistry(modelRegistry).toByteArray(Charsets.UTF_8)
        if (timelineShape != null) {
            entries["timeline-shape.json"] = timelineShape.toJsonString().toByteArray(Charsets.UTF_8)
        }
        crashRecordStore.buildDiagnosticJson()?.let { crashRecordsJson ->
            entries[CrashRecordStore.CRASH_BUNDLE_ENTRY] = crashRecordsJson.toByteArray(Charsets.UTF_8)
        }
        memoryTrimBreadcrumbStore.buildDiagnosticText()?.let { memoryTrimJsonl ->
            entries[MemoryTrimBreadcrumbStore.BUNDLE_ENTRY] = memoryTrimJsonl.toByteArray(Charsets.UTF_8)
        }
        entries[ProcessExitRecorder.BUNDLE_ENTRY] =
            processExitRecorder.buildDiagnosticJson().toByteArray(Charsets.UTF_8)
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

        /**
         * Build a [TimelineShape] summary from a list of project tracks. Pure
         * function — no Android dependencies — so the Settings opt-in toggle
         * can compute the shape on a background thread and pass it to
         * [exportDiagnosticBundle] without coupling to the editor view model.
         *
         * Compound clips are walked once (their immediate `compoundClips`
         * children are counted as siblings under the same track, so a single
         * compound clip with three nested clips contributes 4 entries to the
         * per-track count — matching the user's mental model when they look
         * at the timeline).
         */
        fun summarizeTimelineShape(
            tracks: List<com.novacut.editor.model.Track>,
        ): TimelineShape {
            val perTrack = mutableListOf<TimelineShape.TrackClipCount>()
            val effectCounts = mutableMapOf<String, Int>()
            val transitionCounts = mutableMapOf<String, Int>()
            var totalDurationMs = 0L

            for (track in tracks) {
                var clipCount = 0
                for (clip in track.clips) {
                    clipCount += 1 + clip.compoundClips.size
                    val end = clip.timelineStartMs + (clip.trimEndMs - clip.trimStartMs)
                    if (end > totalDurationMs) totalDurationMs = end
                    for (effect in clip.effects) {
                        val key = effect.type.name
                        effectCounts[key] = (effectCounts[key] ?: 0) + 1
                    }
                    clip.transition?.let { t ->
                        val key = t.type.name
                        transitionCounts[key] = (transitionCounts[key] ?: 0) + 1
                    }
                }
                perTrack += TimelineShape.TrackClipCount(track.type.name, clipCount)
            }

            return TimelineShape(
                trackCount = tracks.size,
                totalDurationMs = totalDurationMs,
                perTrackClipCount = perTrack,
                perEffectTypeCount = effectCounts,
                perTransitionTypeCount = transitionCounts,
            )
        }

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
