package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProjectAutoSave"
private const val MAX_AUTOSAVE_FILE_BYTES = 25_000_000L

internal fun collectMediaReferenceUrisFromAutoSaveJson(raw: String): Set<String> {
    val mediaUriKeys = listOf("sourceUri", "managedUri")
    return mediaUriKeys
        .flatMap { key ->
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(raw)
                .map { it.groupValues[1] }
                .toList()
        }
        .filter { it.isNotBlank() }
        .toSet()
}

@Singleton
class ProjectAutoSave @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val autoSaveDir = File(context.filesDir, "autosave").apply { mkdirs() }
    private val saveMutex = Mutex()
    private val lastSavedFingerprints = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile
    private var autoSaveJob: Job? = null
    private var consecutiveFailures = 0

    fun startAutoSave(
        projectId: String,
        intervalMs: Long = 30_000,
        getState: () -> AutoSaveState
    ) {
        autoSaveJob?.cancel()
        consecutiveFailures = 0
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(intervalMs.coerceIn(10_000, 600_000))
                try {
                    saveState(projectId, getState())
                    consecutiveFailures = 0
                } catch (e: Exception) {
                    consecutiveFailures++
                    Log.e(TAG, "Auto-save failed for $projectId (attempt $consecutiveFailures)", e)
                    if (consecutiveFailures >= 3) {
                        Log.w(TAG, "Auto-save has failed $consecutiveFailures times in a row for $projectId")
                    }
                }
            }
        }
    }

    suspend fun saveNow(projectId: String, state: AutoSaveState): Boolean = withContext(Dispatchers.IO) {
        try {
            saveState(projectId, state)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Manual save failed for $projectId", e)
            false
        }
    }

    fun hasRecoveryData(projectId: String): Boolean {
        return getAutoSaveFile(projectId).exists()
    }

    /**
     * Schema-aware load result for callers that need to distinguish a corrupt
     * autosave from one written by a newer ClearCut build (R5/AS-Schema gate).
     *
     * `loadRecoveryData` keeps its `AutoSaveState?` return for back-compat —
     * future-schema files fall through to `null` there, which existing call
     * sites already handle as "no recovery available." UI surfaces that want
     * to render a "Project needs newer ClearCut" dialog should call
     * [loadRecoveryDataWithOutcome] instead.
     */
    sealed class LoadOutcome {
        data class Loaded(val state: AutoSaveState) : LoadOutcome()
        data class FutureSchema(val fileVersion: Int, val supportedVersion: Int) : LoadOutcome()
        data class Corrupt(val cause: Throwable) : LoadOutcome()
        data object NotFound : LoadOutcome()
    }

    /**
     * Like [loadRecoveryData] but distinguishes future-schema files from
     * genuinely corrupt ones. A future-schema main file blocks the backup
     * attempt — the backup is almost certainly also future-schema, and
     * silently downgrading to a stale, *older* backup that happens to load
     * would discard work the user did on the newer build.
     */
    suspend fun loadRecoveryDataWithOutcome(projectId: String): LoadOutcome = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val tempFile = getTempFile(projectId)
            if (tempFile.exists()) {
                Log.w(TAG, "Cleaning up stale temp file for $projectId")
                tempFile.delete()
            }
            val file = getAutoSaveFile(projectId)
            val backupFile = getBackupFile(projectId)
            if (!file.exists() && backupFile.exists()) {
                Log.w(TAG, "Restoring auto-save from backup for $projectId")
                moveFileReplacing(backupFile, file)
            }
            if (!file.exists()) return@withLock LoadOutcome.NotFound
            val raw = try {
                readAutoSaveText(file)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read auto-save for $projectId", e)
                return@withLock LoadOutcome.Corrupt(e)
            }
            val peek = AutoSaveState.peekSchemaVersion(raw)
            if (peek != null && peek > AutoSaveState.FORMAT_VERSION) {
                Log.w(
                    TAG,
                    "Auto-save for $projectId was written by a newer ClearCut " +
                        "(schema $peek > supported ${AutoSaveState.FORMAT_VERSION}); " +
                        "refusing to load to avoid data loss"
                )
                return@withLock LoadOutcome.FutureSchema(peek, AutoSaveState.FORMAT_VERSION)
            }
            try {
                val state = AutoSaveState.deserialize(raw)
                backupFile.delete()
                LoadOutcome.Loaded(state)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recovery data for $projectId", e)
                if (!backupFile.exists()) return@withLock LoadOutcome.Corrupt(e)
                val backupRaw = try {
                    readAutoSaveText(backupFile)
                } catch (readErr: Exception) {
                    Log.e(TAG, "Backup auto-save read failed for $projectId", readErr)
                    return@withLock LoadOutcome.Corrupt(readErr)
                }
                val backupPeek = AutoSaveState.peekSchemaVersion(backupRaw)
                if (backupPeek != null && backupPeek > AutoSaveState.FORMAT_VERSION) {
                    return@withLock LoadOutcome.FutureSchema(backupPeek, AutoSaveState.FORMAT_VERSION)
                }
                try {
                    Log.w(TAG, "Primary auto-save is corrupt; attempting backup restore for $projectId")
                    val state = AutoSaveState.deserialize(backupRaw)
                    moveFileReplacing(backupFile, file)
                    LoadOutcome.Loaded(state)
                } catch (backupError: Exception) {
                    Log.e(TAG, "Backup auto-save restore failed for $projectId", backupError)
                    LoadOutcome.Corrupt(backupError)
                }
            }
        }
    }

    suspend fun loadRecoveryData(projectId: String): AutoSaveState? = withContext(Dispatchers.IO) {
        // Serialize load against in-flight writes. Without the mutex a load that
        // races a `saveState()` (temp-write → rename) could see the rename
        // midway and read either no file (null) or a half-renamed file whose
        // JSON parse throws — the second branch would fall into the backup
        // recovery path unnecessarily and clear the backup even though the
        // primary was fine.
        saveMutex.withLock {
            val tempFile = getTempFile(projectId)
            if (tempFile.exists()) {
                Log.w(TAG, "Cleaning up stale temp file for $projectId")
                tempFile.delete()
            }
            val file = getAutoSaveFile(projectId)
            val backupFile = getBackupFile(projectId)
            // If main file is missing but backup exists, a save was interrupted — restore
            if (!file.exists() && backupFile.exists()) {
                Log.w(TAG, "Restoring auto-save from backup for $projectId")
                moveFileReplacing(backupFile, file)
            }
            if (!file.exists()) return@withLock null
            try {
                AutoSaveState.deserialize(readAutoSaveText(file)).also {
                    backupFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recovery data for $projectId", e)
                if (!backupFile.exists()) {
                    return@withLock null
                }
                try {
                    Log.w(TAG, "Primary auto-save is corrupt; attempting backup restore for $projectId")
                    AutoSaveState.deserialize(readAutoSaveText(backupFile)).also {
                        moveFileReplacing(backupFile, file)
                    }
                } catch (backupError: Exception) {
                    Log.e(TAG, "Backup auto-save restore failed for $projectId", backupError)
                    null
                }
            }
        }
    }

    /**
     * Delete the on-disk recovery artifacts for a project. Now a suspend
     * function so it can hold `saveMutex` for the full delete sequence —
     * previously a concurrent auto-save could mid-delete create a new
     * `.json` file between our `main.delete()` and `backup.delete()` calls,
     * leaving a stale recovery behind that would re-appear on next open.
     */
    suspend fun clearRecoveryData(projectId: String) = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            getAutoSaveFile(projectId).delete()
            getTempFile(projectId).delete()
            getBackupFile(projectId).delete()
            lastSavedFingerprints.remove(projectId)
        }
    }

    /**
     * Collect every media URI referenced by every project's auto-save JSON
     * on disk. Used by the managed-media GC to know which imports are still
     * live. Uses a cheap regex over the raw JSON rather than full deserializer
     * round-trip so this runs in milliseconds even across hundreds of projects
     * and stays forward-compatible with new Clip fields.
     */
    suspend fun collectReferencedSourceUris(): Set<String> = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val uris = mutableSetOf<String>()
            autoSaveDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.forEach { file ->
                    try {
                        uris += collectMediaReferenceUrisFromAutoSaveJson(readAutoSaveText(file))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to scan ${file.name} for source URIs", e)
                    }
                }
            uris
        }
    }

    suspend fun copyAutoSave(fromProjectId: String, toProjectId: String): Boolean {
        return try {
            // Hold the mutex for the entire exists-check → read → mutate → write sequence.
            // Checking exists() outside the lock created a window where a concurrent
            // clearRecoveryData() or saveState() could delete or overwrite the source file
            // between the check and the read, producing a FileNotFoundException or stale data.
            saveMutex.withLock {
                val fromFile = getAutoSaveFile(fromProjectId)
                if (!fromFile.exists()) {
                    Log.w(TAG, "No auto-save found to copy for $fromProjectId")
                    return@withLock false
                }
                val json = JSONObject(readAutoSaveText(fromFile))
                json.put("projectId", toProjectId)
                json.put("timestamp", System.currentTimeMillis())
                writeAutoSaveFileLocked(toProjectId, json.toString(2))
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy auto-save from $fromProjectId to $toProjectId", e)
            false
        }
    }

    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun release() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        // Cancel the entire scope which also cancels any in-flight save.
        // This is safe because saveState() is idempotent — an incomplete write
        // leaves only a .tmp file which loadRecoveryData() cleans up on next launch.
        scope.cancel()
        // Sweep any leftover .tmp files from interrupted saves so the autosave directory
        // doesn't accumulate orphans across process lifetimes (filesDir has quota pressure).
        runCatching {
            autoSaveDir.listFiles { f -> f.isFile && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        }.onFailure { e -> Log.w(TAG, "Failed to sweep orphan .tmp files on release()", e) }
    }

    private suspend fun saveState(projectId: String, state: AutoSaveState) = saveMutex.withLock {
        val contents = state.serialize()
        // Skip the temp-write + double-rename + backup churn when nothing
        // changed since the last save — the periodic loop fires every 30s
        // regardless of activity, which otherwise grinds flash storage all
        // session long on an idle project.
        val fingerprint = contents.length.toLong() * 31 + contents.hashCode()
        if (lastSavedFingerprints[projectId] == fingerprint && getAutoSaveFile(projectId).exists()) {
            return@withLock
        }
        writeAutoSaveFileLocked(projectId, contents)
        lastSavedFingerprints[projectId] = fingerprint
    }

    private fun writeAutoSaveFileLocked(projectId: String, contents: String) {
        val file = getAutoSaveFile(projectId)
        val tempFile = getTempFile(projectId)
        val backupFile = getBackupFile(projectId)
        try {
            tempFile.writeText(contents, Charsets.UTF_8)
            // Keep a backup of the previous save so a failed rename/copy doesn't lose data
            if (file.exists()) {
                backupFile.delete()
                moveFileReplacing(file, backupFile)
            }
            moveFileReplacing(tempFile, file)
            // Successful write — remove backup
            backupFile.delete()
        } catch (e: Exception) {
            // Restore from backup if the new write failed partway
            tempFile.delete()
            if (backupFile.exists()) {
                moveFileReplacing(backupFile, file)
            }
            throw e
        }
    }

    private fun getAutoSaveFile(projectId: String): File {
        return File(autoSaveDir, "${autoSaveFileStem(projectId)}.json")
    }

    private fun getTempFile(projectId: String): File {
        return File(autoSaveDir, "${autoSaveFileStem(projectId)}.tmp")
    }

    private fun getBackupFile(projectId: String): File {
        return File(autoSaveDir, "${autoSaveFileStem(projectId)}.bak")
    }

    data class StorageInfo(
        val autoSaveSizeBytes: Long,
        val autoSaveLastModifiedMs: Long,
        val backupFileCount: Int
    )

    fun getStorageInfo(projectId: String): StorageInfo {
        val primary = getAutoSaveFile(projectId)
        val backup = getBackupFile(projectId)
        return StorageInfo(
            autoSaveSizeBytes = if (primary.exists()) primary.length() else 0L,
            autoSaveLastModifiedMs = if (primary.exists()) primary.lastModified() else 0L,
            backupFileCount = listOf(primary, backup).count { it.exists() }
        )
    }

    private fun readAutoSaveText(file: File): String {
        if (file.length() > MAX_AUTOSAVE_FILE_BYTES) {
            throw java.io.IOException("Auto-save file exceeds $MAX_AUTOSAVE_FILE_BYTES byte limit")
        }
        return file.inputStream().use { input ->
            readUtf8WithByteLimit(input, MAX_AUTOSAVE_FILE_BYTES)
        }
    }
}

internal fun autoSaveFileStem(projectId: String): String {
    val safeProjectId = sanitizeFileName(projectId, fallback = "project", maxLength = 96)
    val stableSuffix = projectId.hashCode().toUInt().toString(16)
    return "${safeProjectId}_$stableSuffix"
}

data class AutoSaveState(
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val playheadMs: Long = 0L,
    val chapterMarkers: List<ChapterMarker> = emptyList(),
    val imageOverlays: List<ImageOverlay> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
    val globalTransitions: List<com.novacut.editor.model.GlobalTransition> = emptyList(),
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList(),
    val beatMarkers: List<Long> = emptyList(),
    // v3.69: transcript cached from Auto Captions. Persisted so text-based
    // editing survives app restart without forcing the user to re-transcribe.
    val transcript: com.novacut.editor.model.Transcript? = null,
    // v3.71: object-aware editing scaffolding. Tracked objects survive
    // restart so accept/reject decisions about a tracked subject are not
    // re-asked every session even before SAM 2 / MediaPipe trackers ship.
    val trackedObjects: List<com.novacut.editor.model.TrackedObject> = emptyList(),
    // R8.9: per-project AI usage ledger. This drives disclosure defaults
    // during export and survives restarts without involving telemetry.
    val aiUsageLedger: List<AiUsageLedger.Entry> = emptyList(),
    // v3.74.75: project-level media asset manifest. Clips still carry sourceUri
    // as the compatibility fallback while new saves can diagnose/repair assets
    // through stable ids.
    val mediaAssets: List<ProjectMediaAsset> = emptyList(),
    val storyboardCards: List<com.novacut.editor.model.StoryboardCard> = emptyList()
) {
    fun serialize(): String {
        val json = JSONObject().apply {
            // Write both keys: `version` is the historic name, kept so older
            // builds reading newer files don't lose the version signal.
            // `schemaVersion` is the canonical forward-going name and is what
            // peekSchemaVersion prefers when both are present.
            put("version", FORMAT_VERSION)
            put("schemaVersion", FORMAT_VERSION)
            put("projectId", projectId)
            put("timestamp", timestamp)
            put("playheadMs", playheadMs)
            put("tracks", serializeTracks(tracks))
            if (mediaAssets.isNotEmpty()) {
                put("mediaAssets", serializeMediaAssets(mediaAssets))
            }
            put("textOverlays", serializeTextOverlays(textOverlays))
            if (chapterMarkers.isNotEmpty()) {
                put("chapterMarkers", JSONArray().apply {
                    chapterMarkers.forEach { ch ->
                        put(JSONObject().apply {
                            put("timeMs", ch.timeMs)
                            put("title", ch.title)
                        })
                    }
                })
            }
            if (imageOverlays.isNotEmpty()) {
                put("imageOverlays", JSONArray().apply {
                    imageOverlays.forEach { io ->
                        put(JSONObject().apply {
                            put("id", io.id)
                            put("sourceUri", io.sourceUri.toString())
                            put("startTimeMs", io.startTimeMs)
                            put("endTimeMs", io.endTimeMs)
                            putSafeFloat("positionX", io.positionX)
                            putSafeFloat("positionY", io.positionY)
                            putSafeFloat("scale", io.scale, default = 0.3f)
                            putSafeFloat("rotation", io.rotation)
                            putSafeFloat("opacity", io.opacity, default = 1f)
                            put("type", io.type.name)
                        })
                    }
                })
            }
            if (timelineMarkers.isNotEmpty()) {
                put("timelineMarkers", JSONArray().apply {
                    timelineMarkers.forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id)
                            put("timeMs", m.timeMs)
                            put("label", m.label)
                            put("color", m.color.name)
                            put("notes", m.notes)
                        })
                    }
                })
            }
            if (globalTransitions.isNotEmpty()) {
                put("globalTransitions", JSONArray().apply {
                    globalTransitions.forEach { gt ->
                        put(JSONObject().apply {
                            put("id", gt.id)
                            put("type", gt.type.name)
                            put("durationMs", gt.durationMs)
                            put("timelineAnchorMs", gt.timelineAnchorMs)
                            put("easing", gt.easing.name)
                        })
                    }
                })
            }
            if (drawingPaths.isNotEmpty()) {
                put("drawingPaths", JSONArray().apply {
                    drawingPaths.forEach { dp ->
                        put(JSONObject().apply {
                            put("color", dp.color)
                            putSafeFloat("strokeWidth", dp.strokeWidth, default = 4f)
                            put("points", JSONArray().apply {
                                dp.points.forEach { (x, y) ->
                                    put(JSONObject().apply {
                                        putSafeFloat("x", x)
                                        putSafeFloat("y", y)
                                    })
                                }
                            })
                        })
                    }
                })
            }
            if (beatMarkers.isNotEmpty()) {
                put("beatMarkers", JSONArray().apply {
                    beatMarkers.forEach { put(it) }
                })
            }
            if (aiUsageLedger.isNotEmpty()) {
                put("aiUsageLedger", AiUsageLedger.toJsonArray(aiUsageLedger))
            }
            if (trackedObjects.isNotEmpty()) {
                put("trackedObjects", JSONArray().apply {
                    trackedObjects.forEach { obj ->
                        put(JSONObject().apply {
                            put("id", obj.id)
                            put("label", obj.label)
                            put("sourceClipId", obj.sourceClipId)
                            put("source", obj.source.name)
                            put("category", obj.category.name)
                            put("isEnabled", obj.isEnabled)
                            put("keyframes", JSONArray().apply {
                                obj.keyframes.forEach { kf ->
                                    put(JSONObject().apply {
                                        put("clipTimeMs", kf.clipTimeMs)
                                        putSafeFloat("centerX", kf.centerX, default = 0.5f)
                                        putSafeFloat("centerY", kf.centerY, default = 0.5f)
                                        putSafeFloat("width", kf.width, default = 0.1f)
                                        putSafeFloat("height", kf.height, default = 0.1f)
                                        putSafeFloat("confidence", kf.confidence, default = 1f)
                                        if (kf.maskPolygon.isNotEmpty()) {
                                            put("maskPolygon", JSONArray().apply {
                                                kf.maskPolygon.forEach { p ->
                                                    put(JSONObject().apply {
                                                        putSafeFloat("x", p.x)
                                                        putSafeFloat("y", p.y)
                                                    })
                                                }
                                            })
                                        }
                                    })
                                }
                            })
                        })
                    }
                })
            }
            transcript?.let { tr ->
                put("transcript", JSONObject().apply {
                    put("id", tr.id)
                    put("clipId", tr.clipId)
                    put("language", tr.language)
                    // Mirror the deserialize-side cap so a runaway transcript
                    // can't produce an auto-save file that then fails to
                    // re-open (or blows past DataStore's IO timeout).
                    val capped = tr.words.take(MAX_TRANSCRIPT_WORDS)
                    put("words", JSONArray().apply {
                        capped.forEach { w ->
                            put(JSONObject().apply {
                                put("text", w.text)
                                put("startMs", w.startMs)
                                put("endMs", w.endMs)
                                putSafeFloat("confidence", w.confidence, default = 1f)
                            })
                        }
                    })
                })
            }
            if (storyboardCards.isNotEmpty()) {
                put("storyboardCards", JSONArray().apply {
                    storyboardCards.forEach { card ->
                        put(JSONObject().apply {
                            put("id", card.id)
                            put("ordinal", card.ordinal)
                            put("shotText", card.shotText)
                            put("targetDurationMs", card.targetDurationMs)
                            put("status", card.status.name)
                            card.mediaUri?.let { put("mediaUri", it.toString()) }
                        })
                    }
                })
            }
        }
        return json.toString(2)
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val MAX_TRANSCRIPT_WORDS = 20_000
        private const val MAX_STORYBOARD_CARDS = 200
        private const val MAX_TRACKS = 64
        private const val MAX_CLIPS_PER_TRACK = 2_000
        private const val MAX_AUDIO_EFFECTS_PER_SCOPE = 128
        private const val MAX_COMPOUND_CLIP_DEPTH = 8
        private const val MAX_COMPOUND_CLIPS_PER_CLIP = 256
        private const val MAX_CLIP_EFFECTS = 256
        private const val MAX_EFFECT_PARAMS = 256
        private const val MAX_KEYFRAMES_PER_SCOPE = 2_000
        private const val MAX_MASKS_PER_CLIP = 256
        private const val MAX_MASK_POINTS = 512
        private const val MAX_CAPTIONS_PER_CLIP = 5_000
        private const val MAX_CAPTION_WORDS = 512
        private const val MAX_MOTION_TRACK_POINTS = 10_000
        private const val MAX_PROJECT_MARKERS = 5_000
        private const val MAX_IMAGE_OVERLAYS = 2_000
        private const val MAX_DRAWING_PATHS = 2_000
        private const val MAX_DRAWING_POINTS_PER_PATH = 4_096
        private const val MAX_BEAT_MARKERS = 20_000
        private const val MAX_AI_USAGE_ENTRIES = 2_000
        private const val MAX_MEDIA_ASSETS = 5_000
        private const val MAX_TRACKED_OBJECTS = 1_000
        private const val MAX_TRACKED_OBJECT_KEYFRAMES = 10_000
        private const val MAX_TEXT_OVERLAYS = 5_000
        private const val MAX_TEXT_VALUE_CHARS = 4_000
        private const val MAX_SHORT_TEXT_CHARS = 256
        private const val MAX_NOTES_CHARS = 2_000
        private const val MAX_CURVE_POINTS = 256

        /**
         * Cheap top-level inspection of an autosave JSON's schema version
         * without paying the cost (or risk) of a full deserialize.
         *
         * Returns:
         *  - `null` when the raw text is not valid JSON or the version field is
         *    missing — callers should treat as "unknown schema, attempt
         *    best-effort load."
         *  - The integer schema version otherwise.
         *
         * Used by [loadRecoveryDataWithOutcome] to gate future-schema loads
         * and by UI pre-flight checks. Pure — no I/O, safe to call on the
         * main thread for small JSON.
         */
        fun peekSchemaVersion(raw: String): Int? = try {
            val obj = JSONObject(raw)
            when {
                obj.has("schemaVersion") -> obj.optInt("schemaVersion", 0)
                obj.has("version") -> obj.optInt("version", 0)
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        // Safe enum valueOf with fallback — prevents crashes from stale/unknown enum values
        private inline fun <reified T : Enum<T>> safeValueOf(name: String, default: T): T {
            return try { enumValueOf<T>(name) } catch (_: IllegalArgumentException) { default }
        }

        private fun cappedArrayLength(arr: JSONArray, max: Int, label: String): Int {
            if (arr.length() > max) {
                Log.w(TAG, "Auto-save contains ${arr.length()} $label; loading first $max")
            }
            return arr.length().coerceAtMost(max)
        }

        private fun boundedText(raw: String, maxChars: Int): String =
            raw.take(maxChars)

        private fun JSONObject.putSafeFloat(
            name: String,
            value: Float,
            default: Float = 0f
        ): JSONObject {
            val fallback = if (default.isFinite()) default else 0f
            return put(name, (if (value.isFinite()) value else fallback).toDouble())
        }

        fun deserialize(
            raw: String,
            uriParser: (String) -> Uri? = { Uri.parse(it) },
        ): AutoSaveState {
            val json = JSONObject(raw)
            val fileVersion = json.optInt("version", 0)
            if (fileVersion > FORMAT_VERSION) {
                Log.w(TAG, "Auto-save written by newer format ($fileVersion > $FORMAT_VERSION); attempting best-effort load")
            }
            val mediaAssets = deserializeMediaAssets(json.optJSONArray("mediaAssets") ?: JSONArray())
            val mediaAssetUrisById = mediaAssets.associate { it.assetId to it.managedUri }
            val tracks = deserializeTracks(
                json.optJSONArray("tracks") ?: JSONArray(),
                uriParser,
                mediaAssetUrisById
            )
            // Clean up orphaned linkedClipId references, and break any self-reference —
            // a clip linked to itself would produce an infinite loop in any traversal
            // that follows the chain (e.g. slip-link propagation, group moves).
            val allClipIds = tracks.flatMap { it.clips.map { c -> c.id } }.toSet()
            val cleanedTracks = tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    val linked = clip.linkedClipId
                    if (linked != null && (linked !in allClipIds || linked == clip.id)) {
                        clip.copy(linkedClipId = null)
                    } else clip
                })
            }
            val chaptersArr = json.optJSONArray("chapterMarkers") ?: JSONArray()
            val chapters = (0 until cappedArrayLength(chaptersArr, MAX_PROJECT_MARKERS, "chapter markers")).mapNotNull { i ->
                try {
                    val ch = chaptersArr.getJSONObject(i)
                    ChapterMarker(
                        timeMs = ch.optLong("timeMs", 0L),
                        title = boundedText(ch.optString("title", ""), MAX_SHORT_TEXT_CHARS)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize chapter marker $i", e); null }
            }
            val imageOverlaysArr = json.optJSONArray("imageOverlays") ?: JSONArray()
            val imageOverlays = (0 until cappedArrayLength(imageOverlaysArr, MAX_IMAGE_OVERLAYS, "image overlays")).mapNotNull { i ->
                try {
                    val io = imageOverlaysArr.getJSONObject(i)
                    val srcUri = io.optString("sourceUri", "")
                    if (srcUri.isEmpty()) return@mapNotNull null
                    val parsedUri = try { uriParser(srcUri) } catch (e: Exception) {
                        Log.w(TAG, "Skipping image overlay with malformed URI: $srcUri", e)
                        return@mapNotNull null
                    } ?: return@mapNotNull null
                    // Coerce time range BEFORE constructing so a corrupt save with
                    // startTimeMs >= endTimeMs doesn't trip the ImageOverlay require()
                    // block and silently drop the overlay (data loss on recovery).
                    val ioStart = io.optLong("startTimeMs", 0L).coerceAtLeast(0L)
                    val rawEnd = io.optLong("endTimeMs", ioStart + 5000L)
                    val ioEnd = if (rawEnd > ioStart) rawEnd else ioStart + 1L
                    ImageOverlay(
                        id = io.optString("id", java.util.UUID.randomUUID().toString()),
                        sourceUri = parsedUri,
                        startTimeMs = ioStart,
                        endTimeMs = ioEnd,
                        positionX = io.optDouble("positionX", 0.0).toFloat().let { if (it.isFinite()) it.coerceIn(-5f, 5f) else 0f },
                        positionY = io.optDouble("positionY", 0.0).toFloat().let { if (it.isFinite()) it.coerceIn(-5f, 5f) else 0f },
                        scale = io.optDouble("scale", 0.3).toFloat().let { if (it.isFinite()) it.coerceAtLeast(0.01f) else 0.3f },
                        rotation = io.optDouble("rotation", 0.0).toFloat().let { if (it.isFinite()) it else 0f },
                        opacity = io.optDouble("opacity", 1.0).toFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 1f },
                        type = safeValueOf(io.optString("type", "STICKER"), ImageOverlayType.STICKER)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize image overlay $i", e); null }
            }
            val timelineMarkersArr = json.optJSONArray("timelineMarkers") ?: JSONArray()
            val timelineMarkers = (0 until cappedArrayLength(timelineMarkersArr, MAX_PROJECT_MARKERS, "timeline markers")).mapNotNull { i ->
                try {
                    val m = timelineMarkersArr.getJSONObject(i)
                    TimelineMarker(
                        id = m.optString("id", java.util.UUID.randomUUID().toString()),
                        timeMs = m.optLong("timeMs", 0L),
                        label = boundedText(m.optString("label", ""), MAX_SHORT_TEXT_CHARS),
                        color = safeValueOf(m.optString("color", "BLUE"), MarkerColor.BLUE),
                        notes = boundedText(m.optString("notes", ""), MAX_NOTES_CHARS)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize timeline marker $i", e); null }
            }
            val globalTransitionsArr = json.optJSONArray("globalTransitions") ?: JSONArray()
            val globalTransitions = (0 until globalTransitionsArr.length().coerceAtMost(20)).mapNotNull { i ->
                try {
                    val gt = globalTransitionsArr.getJSONObject(i)
                    com.novacut.editor.model.GlobalTransition(
                        id = gt.optString("id", java.util.UUID.randomUUID().toString()),
                        type = safeValueOf(gt.optString("type"), com.novacut.editor.model.GlobalTransitionType.FADE_TO_BLACK),
                        durationMs = gt.optLong("durationMs", 1000L).coerceIn(100L, 10000L),
                        timelineAnchorMs = gt.optLong("timelineAnchorMs", 0L).coerceAtLeast(0L),
                        easing = safeValueOf(gt.optString("easing", "EASE_IN_OUT"), TransitionEasing.EASE_IN_OUT)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize global transition $i", e); null }
            }
            val drawingPathsArr = json.optJSONArray("drawingPaths") ?: JSONArray()
            val drawingPaths = (0 until cappedArrayLength(drawingPathsArr, MAX_DRAWING_PATHS, "drawing paths")).mapNotNull { i ->
                try {
                    val dp = drawingPathsArr.getJSONObject(i)
                    val pointsArr = dp.optJSONArray("points") ?: return@mapNotNull null
                    // Drop NaN/Infinity coordinates — Compose Canvas drawPath silently breaks
                    // rendering for the whole layer when a single segment contains a
                    // non-finite coord, dropping every subsequent drawing on the overlay.
                    val points = (0 until cappedArrayLength(pointsArr, MAX_DRAWING_POINTS_PER_PATH, "drawing points")).mapNotNull { j ->
                        val pt = pointsArr.getJSONObject(j)
                        val x = pt.optDouble("x", Double.NaN).toFloat()
                        val y = pt.optDouble("y", Double.NaN).toFloat()
                        if (x.isFinite() && y.isFinite()) x to y else null
                    }
                    if (points.size < 2) return@mapNotNull null
                    val rawStroke = dp.optDouble("strokeWidth", 4.0).toFloat()
                    com.novacut.editor.model.DrawingPath(
                        points = points,
                        color = dp.optLong("color", 0xFFCBA6F7L),
                        strokeWidth = (if (rawStroke.isFinite()) rawStroke else 4f).coerceIn(0.5f, 64f)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize drawing path $i", e); null }
            }
            val beatMarkersArr = json.optJSONArray("beatMarkers") ?: JSONArray()
            val beatMarkers = (0 until cappedArrayLength(beatMarkersArr, MAX_BEAT_MARKERS, "beat markers")).mapNotNull { i ->
                try { beatMarkersArr.getLong(i) }
                catch (e: Exception) { Log.w(TAG, "Failed to deserialize beat marker $i", e); null }
            }
            val aiUsageLedger = AiUsageLedger.fromJsonArray(
                json.optJSONArray("aiUsageLedger"),
                maxEntries = MAX_AI_USAGE_ENTRIES
            )
            val transcriptObj = json.optJSONObject("transcript")
            val transcript: com.novacut.editor.model.Transcript? = transcriptObj?.let { tr ->
                try {
                    val wordsArr = tr.optJSONArray("words") ?: JSONArray()
                    // Cap at MAX_TRANSCRIPT_WORDS so a corrupt save with a
                    // pathologically-long transcript can't stall startup.
                    // Whisper Tiny produces roughly 2 words/sec of speech, so
                    // 20k words is ~2.7 hours — far beyond realistic mobile use.
                    val cappedLen = wordsArr.length().coerceAtMost(MAX_TRANSCRIPT_WORDS)
                    val words = (0 until cappedLen).mapNotNull { wi ->
                        val w = wordsArr.optJSONObject(wi) ?: return@mapNotNull null
                        val text = w.optString("text", "").trim().take(128)
                        if (text.isEmpty()) return@mapNotNull null
                        val s = w.optLong("startMs", 0L).coerceAtLeast(0L)
                        val e = w.optLong("endMs", s)
                        val conf = w.optDouble("confidence", 1.0).toFloat().let {
                            if (it.isFinite()) it.coerceIn(0f, 1f) else 1f
                        }
                        com.novacut.editor.model.WordTimestamp(
                            text = text,
                            startMs = s,
                            endMs = if (e > s) e else s + 1L,
                            confidence = conf
                        )
                    }
                    if (words.isEmpty()) null
                    else com.novacut.editor.model.Transcript(
                        id = tr.optString("id", java.util.UUID.randomUUID().toString()),
                        clipId = tr.optString("clipId", ""),
                        language = tr.optString("language", "en").take(8),
                        words = words
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize transcript", e); null }
            }
            val trackedObjectsArr = json.optJSONArray("trackedObjects") ?: JSONArray()
            val trackedObjects = (0 until cappedArrayLength(trackedObjectsArr, MAX_TRACKED_OBJECTS, "tracked objects")).mapNotNull { i ->
                try {
                    val obj = trackedObjectsArr.optJSONObject(i) ?: return@mapNotNull null
                    val label = obj.optString("label", "").trim().take(64)
                    if (label.isEmpty()) return@mapNotNull null
                    val sourceClipId = obj.optString("sourceClipId", "")
                    if (sourceClipId.isBlank()) return@mapNotNull null
                    val keyframesArr = obj.optJSONArray("keyframes") ?: JSONArray()
                    val keyframes = (0 until cappedArrayLength(keyframesArr, MAX_TRACKED_OBJECT_KEYFRAMES, "tracked-object keyframes")).mapNotNull { ki ->
                        try {
                            val kf = keyframesArr.optJSONObject(ki) ?: return@mapNotNull null
                            // Coerce normalised coords into [0, 1] / (0, 1] BEFORE constructing
                            // so a corrupt save can't trip the require() block and silently drop
                            // every subsequent keyframe in this object.
                            val w = kf.optDouble("width", 0.1).toFloat().let {
                                if (it.isFinite() && it > 0f) it.coerceIn(0.001f, 1f) else 0.1f
                            }
                            val h = kf.optDouble("height", 0.1).toFloat().let {
                                if (it.isFinite() && it > 0f) it.coerceIn(0.001f, 1f) else 0.1f
                            }
                            val maskArr = kf.optJSONArray("maskPolygon")
                            val polygon = if (maskArr != null) {
                                (0 until cappedArrayLength(maskArr, MAX_MASK_POINTS, "tracked-object mask points")).mapNotNull { pi ->
                                    val pt = maskArr.optJSONObject(pi) ?: return@mapNotNull null
                                    val x = pt.optDouble("x", Double.NaN).toFloat()
                                    val y = pt.optDouble("y", Double.NaN).toFloat()
                                    if (x.isFinite() && y.isFinite()) {
                                        com.novacut.editor.model.MaskPoint(x = x, y = y)
                                    } else null
                                }
                            } else emptyList()
                            com.novacut.editor.model.TrackedObjectKeyframe(
                                clipTimeMs = kf.optLong("clipTimeMs", 0L).coerceAtLeast(0L),
                                centerX = kf.optDouble("centerX", 0.5).toFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0.5f },
                                centerY = kf.optDouble("centerY", 0.5).toFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0.5f },
                                width = w,
                                height = h,
                                confidence = kf.optDouble("confidence", 1.0).toFloat().let {
                                    if (it.isFinite()) it.coerceIn(0f, 1f) else 1f
                                },
                                maskPolygon = polygon
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to deserialize tracked-object keyframe $ki", e); null
                        }
                    }
                    com.novacut.editor.model.TrackedObject(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        label = label,
                        sourceClipId = sourceClipId,
                        source = safeValueOf(
                            obj.optString("source", "MANUAL"),
                            com.novacut.editor.model.TrackedObjectSource.MANUAL
                        ),
                        category = safeValueOf(
                            obj.optString("category", "UNKNOWN"),
                            com.novacut.editor.model.TrackedObjectCategory.UNKNOWN
                        ),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        keyframes = keyframes
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize tracked object $i", e); null
                }
            }
            val storyboardArr = json.optJSONArray("storyboardCards") ?: JSONArray()
            val storyboardCards = (0 until cappedArrayLength(storyboardArr, MAX_STORYBOARD_CARDS, "storyboard cards")).mapNotNull { i ->
                try {
                    val card = storyboardArr.getJSONObject(i)
                    com.novacut.editor.model.StoryboardCard(
                        id = card.optString("id", java.util.UUID.randomUUID().toString()),
                        ordinal = card.optInt("ordinal", i),
                        shotText = boundedText(card.optString("shotText", ""), MAX_SHORT_TEXT_CHARS),
                        targetDurationMs = card.optLong("targetDurationMs", 5000L).coerceIn(500L, 300_000L),
                        status = safeValueOf(card.optString("status", "PLANNED"), com.novacut.editor.model.StoryboardCardStatus.PLANNED),
                        mediaUri = card.optString("mediaUri", "").takeIf { it.isNotEmpty() }?.let { uriParser(it) }
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize storyboard card $i", e); null }
            }
            return AutoSaveState(
                projectId = json.optString("projectId", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                playheadMs = json.optLong("playheadMs", 0L),
                tracks = cleanedTracks,
                textOverlays = deserializeTextOverlays(json.optJSONArray("textOverlays") ?: JSONArray()),
                chapterMarkers = chapters,
                imageOverlays = imageOverlays,
                timelineMarkers = timelineMarkers,
                globalTransitions = globalTransitions,
                drawingPaths = drawingPaths,
                beatMarkers = beatMarkers,
                transcript = transcript,
                trackedObjects = trackedObjects,
                aiUsageLedger = aiUsageLedger,
                mediaAssets = mediaAssets,
                storyboardCards = storyboardCards
            )
        }

        private fun serializeMediaAssets(mediaAssets: List<ProjectMediaAsset>): JSONArray {
            return JSONArray().apply {
                mediaAssets.forEach { asset ->
                    put(JSONObject().apply {
                        put("assetId", asset.assetId)
                        put("managedUri", asset.managedUri)
                        put("originalUri", asset.originalUri)
                        put("mediaType", asset.mediaType)
                        asset.displayName?.let { put("displayName", it) }
                        asset.mimeType?.let { put("mimeType", it) }
                        put("sizeBytes", asset.sizeBytes)
                        asset.durationMs?.let { put("durationMs", it) }
                        asset.width?.let { put("width", it) }
                        asset.height?.let { put("height", it) }
                        asset.quickFingerprint?.let { put("quickFingerprint", it) }
                        put("importStatus", asset.importStatus)
                        put("lastVerifiedAtEpochMs", asset.lastVerifiedAtEpochMs)
                    })
                }
            }
        }

        private fun deserializeMediaAssets(arr: JSONArray): List<ProjectMediaAsset> {
            return (0 until cappedArrayLength(arr, MAX_MEDIA_ASSETS, "media assets")).mapNotNull { i ->
                try {
                    val asset = arr.getJSONObject(i)
                    val assetId = asset.optString("assetId")
                    val managedUri = asset.optString("managedUri")
                    val originalUri = asset.optString("originalUri")
                    if (assetId.isBlank() || managedUri.isBlank()) return@mapNotNull null
                    ProjectMediaAsset(
                        assetId = assetId,
                        managedUri = managedUri,
                        originalUri = originalUri.ifBlank { managedUri },
                        displayName = asset.optString("displayName", "").takeIf { it.isNotEmpty() },
                        mediaType = asset.optString("mediaType", "video"),
                        mimeType = asset.optString("mimeType", "").takeIf { it.isNotEmpty() },
                        sizeBytes = asset.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                        durationMs = asset.optLong("durationMs", -1L).takeIf { it >= 0L },
                        width = asset.optInt("width", 0).takeIf { it > 0 },
                        height = asset.optInt("height", 0).takeIf { it > 0 },
                        quickFingerprint = asset.optString("quickFingerprint", "").takeIf { it.isNotEmpty() },
                        importStatus = asset.optString("importStatus", "unknown"),
                        lastVerifiedAtEpochMs = asset.optLong("lastVerifiedAtEpochMs", 0L).coerceAtLeast(0L)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize media asset $i", e)
                    null
                }
            }
        }

        // --- Track serialization ---

        private fun serializeTracks(tracks: List<Track>): JSONArray {
            return JSONArray().apply {
                tracks.forEach { put(serializeTrack(it)) }
            }
        }

        private fun serializeTrack(track: Track): JSONObject {
            return JSONObject().apply {
                put("id", track.id)
                put("type", track.type.name)
                put("index", track.index)
                put("isLocked", track.isLocked)
                put("isVisible", track.isVisible)
                put("isMuted", track.isMuted)
                put("isSolo", track.isSolo)
                putSafeFloat("volume", track.volume, default = 1f)
                putSafeFloat("pan", track.pan)
                putSafeFloat("opacity", track.opacity, default = 1f)
                put("blendMode", track.blendMode.name)
                put("isLinkedAV", track.isLinkedAV)
                put("showWaveform", track.showWaveform)
                put("trackHeight", track.trackHeight)
                put("isCollapsed", track.isCollapsed)
                put("clips", JSONArray().apply {
                    track.clips.forEach { put(serializeClip(it)) }
                })
                if (track.audioEffects.isNotEmpty()) {
                    put("audioEffects", JSONArray().apply {
                        track.audioEffects.forEach { put(serializeAudioEffect(it)) }
                    })
                }
            }
        }

        private fun serializeClip(clip: Clip, depth: Int = 0): JSONObject {
            // Cycles in compoundClips would otherwise stack-overflow here. Depth 8 covers
            // any realistic nesting a user could construct (picture-in-picture-in-pip etc.);
            // beyond that something is wrong with the graph.
            if (depth > 8) {
                Log.w(TAG, "serializeClip: compound nesting depth exceeded for ${clip.id}; truncating")
                return JSONObject().apply {
                    put("id", clip.id)
                    clip.assetId?.let { put("assetId", it) }
                    put("sourceUri", clip.sourceUri.toString())
                    put("sourceDurationMs", clip.sourceDurationMs)
                    put("trimStartMs", clip.trimStartMs)
                    put("trimEndMs", clip.trimEndMs)
                }
            }
            return JSONObject().apply {
                put("id", clip.id)
                clip.assetId?.let { put("assetId", it) }
                put("sourceUri", clip.sourceUri.toString())
                put("sourceDurationMs", clip.sourceDurationMs)
                put("timelineStartMs", clip.timelineStartMs)
                put("trimStartMs", clip.trimStartMs)
                put("trimEndMs", clip.trimEndMs)
                putSafeFloat("volume", clip.volume, default = 1f)
                putSafeFloat("speed", clip.speed, default = 1f)
                put("isReversed", clip.isReversed)
                putSafeFloat("opacity", clip.opacity, default = 1f)
                putSafeFloat("rotation", clip.rotation)
                putSafeFloat("scaleX", clip.scaleX, default = 1f)
                putSafeFloat("scaleY", clip.scaleY, default = 1f)
                putSafeFloat("positionX", clip.positionX)
                putSafeFloat("positionY", clip.positionY)
                putSafeFloat("anchorX", clip.anchorX, default = 0.5f)
                putSafeFloat("anchorY", clip.anchorY, default = 0.5f)
                put("fadeInMs", clip.fadeInMs)
                put("fadeOutMs", clip.fadeOutMs)
                put("blendMode", clip.blendMode.name)
                put("isCompound", clip.isCompound)
                if (clip.isCompound && clip.compoundClips.isNotEmpty()) {
                    put("compoundClips", JSONArray().apply {
                        clip.compoundClips.forEach { put(serializeClip(it, depth + 1)) }
                    })
                }
                clip.linkedClipId?.let { put("linkedClipId", it) }
                clip.groupId?.let { put("groupId", it) }
                put("clipLabel", clip.clipLabel.name)
                if (clip.sourceColorMetadata.isInspected) {
                    put("sourceColorMetadata", serializeSourceColorMetadata(clip.sourceColorMetadata))
                }
                put("effects", JSONArray().apply {
                    clip.effects.forEach { put(serializeEffect(it)) }
                })
                put("keyframes", JSONArray().apply {
                    clip.keyframes.forEach { put(serializeKeyframe(it)) }
                })
                clip.headTransition?.let { put("headTransition", serializeTransition(it)) }
                clip.tailTransition?.let { put("tailTransition", serializeTransition(it)) }
                clip.colorGrade?.let { put("colorGrade", serializeColorGrade(it)) }
                clip.speedCurve?.let { put("speedCurve", serializeSpeedCurve(it)) }
                if (clip.masks.isNotEmpty()) {
                    put("masks", JSONArray().apply {
                        clip.masks.forEach { put(serializeMask(it)) }
                    })
                }
                if (clip.audioEffects.isNotEmpty()) {
                    put("audioEffects", JSONArray().apply {
                        clip.audioEffects.forEach { put(serializeAudioEffect(it)) }
                    })
                }
                if (clip.captions.isNotEmpty()) {
                    put("captions", JSONArray().apply {
                        clip.captions.forEach { put(serializeCaption(it)) }
                    })
                }
                clip.name?.let { put("name", it) }
                clip.proxyUri?.let { put("proxyUri", it.toString()) }
                clip.motionTrackingData?.let { mtd ->
                    put("motionTrackingData", JSONObject().apply {
                        put("id", mtd.id)
                        put("targetType", mtd.targetType.name)
                        put("isActive", mtd.isActive)
                        put("trackPoints", JSONArray().apply {
                            mtd.trackPoints.forEach { tp ->
                                put(JSONObject().apply {
                                    put("timeOffsetMs", tp.timeOffsetMs)
                                    putSafeFloat("x", tp.x)
                                    putSafeFloat("y", tp.y)
                                    putSafeFloat("scaleX", tp.scaleX, default = 1f)
                                    putSafeFloat("scaleY", tp.scaleY, default = 1f)
                                    putSafeFloat("rotation", tp.rotation)
                                    putSafeFloat("confidence", tp.confidence, default = 1f)
                                })
                            }
                        })
                    })
                }
            }
        }

        private fun serializeSourceColorMetadata(metadata: SourceColorMetadata): JSONObject {
            return JSONObject().apply {
                metadata.mimeType?.let { put("mimeType", it) }
                metadata.colorStandard?.let { put("colorStandard", it) }
                metadata.colorTransfer?.let { put("colorTransfer", it) }
                put("inspectedAtMs", metadata.inspectedAtMs)
                put("hdrFormats", JSONArray().apply {
                    metadata.hdrFormats.forEach { put(it.name) }
                })
            }
        }

        private fun serializeEffect(effect: Effect): JSONObject {
            return JSONObject().apply {
                put("id", effect.id)
                put("type", effect.type.name)
                put("enabled", effect.enabled)
                effect.targetTrackedObjectId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("targetTrackedObjectId", it) }
                put("params", JSONObject().apply {
                    effect.params.forEach { (k, v) -> putSafeFloat(k, v) }
                })
                if (effect.keyframes.isNotEmpty()) {
                    put("keyframes", JSONArray().apply {
                        effect.keyframes.forEach { put(serializeEffectKeyframe(it)) }
                    })
                }
            }
        }

        private fun serializeEffectKeyframe(kf: EffectKeyframe): JSONObject {
            return JSONObject().apply {
                put("timeOffsetMs", kf.timeOffsetMs)
                put("paramName", kf.paramName)
                putSafeFloat("value", kf.value, default = 1f)
                put("easing", kf.easing.name)
                putSafeFloat("handleInX", kf.handleInX)
                putSafeFloat("handleInY", kf.handleInY)
                putSafeFloat("handleOutX", kf.handleOutX)
                putSafeFloat("handleOutY", kf.handleOutY)
            }
        }

        private fun serializeKeyframe(kf: Keyframe): JSONObject {
            return JSONObject().apply {
                put("timeOffsetMs", kf.timeOffsetMs)
                put("property", kf.property.name)
                putSafeFloat("value", kf.value, default = 1f)
                put("easing", kf.easing.name)
                putSafeFloat("handleInX", kf.handleInX)
                putSafeFloat("handleInY", kf.handleInY)
                putSafeFloat("handleOutX", kf.handleOutX)
                putSafeFloat("handleOutY", kf.handleOutY)
                put("interpolation", kf.interpolation.name)
            }
        }

        private fun serializeColorGrade(g: ColorGrade): JSONObject {
            return JSONObject().apply {
                put("enabled", g.enabled)
                putSafeFloat("liftR", g.liftR); putSafeFloat("liftG", g.liftG); putSafeFloat("liftB", g.liftB)
                putSafeFloat("gammaR", g.gammaR, default = 1f); putSafeFloat("gammaG", g.gammaG, default = 1f); putSafeFloat("gammaB", g.gammaB, default = 1f)
                putSafeFloat("gainR", g.gainR, default = 1f); putSafeFloat("gainG", g.gainG, default = 1f); putSafeFloat("gainB", g.gainB, default = 1f)
                putSafeFloat("offsetR", g.offsetR); putSafeFloat("offsetG", g.offsetG); putSafeFloat("offsetB", g.offsetB)
                g.lutPath?.let { put("lutPath", it) }
                putSafeFloat("lutIntensity", g.lutIntensity, default = 1f)
                g.colorMatchRef?.let { put("colorMatchRef", it) }
                put("curves", serializeColorCurves(g.curves))
                g.hslQualifier?.let { hsl ->
                    put("hsl", JSONObject().apply {
                        putSafeFloat("hueCenter", hsl.hueCenter); putSafeFloat("hueWidth", hsl.hueWidth, default = 30f)
                        putSafeFloat("satMin", hsl.satMin); putSafeFloat("satMax", hsl.satMax, default = 1f)
                        putSafeFloat("lumMin", hsl.lumMin); putSafeFloat("lumMax", hsl.lumMax, default = 1f)
                        putSafeFloat("softness", hsl.softness, default = 0.1f)
                        putSafeFloat("adjustHue", hsl.adjustHue); putSafeFloat("adjustSat", hsl.adjustSat); putSafeFloat("adjustLum", hsl.adjustLum)
                    })
                }
            }
        }

        private fun serializeColorCurves(curves: ColorCurves): JSONObject {
            return JSONObject().apply {
                put("master", serializeCurvePoints(curves.master))
                put("red", serializeCurvePoints(curves.red))
                put("green", serializeCurvePoints(curves.green))
                put("blue", serializeCurvePoints(curves.blue))
            }
        }

        private fun serializeCurvePoints(points: List<CurvePoint>): JSONArray {
            return JSONArray().apply {
                points.forEach { p ->
                    put(JSONObject().apply {
                        putSafeFloat("x", p.x); putSafeFloat("y", p.y)
                        putSafeFloat("hix", p.handleInX); putSafeFloat("hiy", p.handleInY)
                        putSafeFloat("hox", p.handleOutX); putSafeFloat("hoy", p.handleOutY)
                    })
                }
            }
        }

        private fun serializeSpeedCurve(sc: SpeedCurve): JSONObject {
            return JSONObject().apply {
                put("points", JSONArray().apply {
                    sc.points.forEach { pt ->
                        put(JSONObject().apply {
                            val pointSpeedDefault = if (pt.speed.isFinite()) pt.speed else 1f
                            putSafeFloat("position", pt.position)
                            putSafeFloat("speed", pt.speed, default = 1f)
                            putSafeFloat("handleInY", pt.handleInY, default = pointSpeedDefault)
                            putSafeFloat("handleOutY", pt.handleOutY, default = pointSpeedDefault)
                        })
                    }
                })
            }
        }

        private fun serializeMask(mask: Mask): JSONObject {
            return JSONObject().apply {
                put("id", mask.id)
                put("type", mask.type.name)
                putSafeFloat("feather", mask.feather)
                putSafeFloat("opacity", mask.opacity, default = 1f)
                put("inverted", mask.inverted)
                putSafeFloat("expansion", mask.expansion)
                put("trackToMotion", mask.trackToMotion)
                put("points", JSONArray().apply {
                    mask.points.forEach { pt ->
                        put(JSONObject().apply {
                            putSafeFloat("x", pt.x); putSafeFloat("y", pt.y)
                            putSafeFloat("handleInX", pt.handleInX, default = pt.x)
                            putSafeFloat("handleInY", pt.handleInY, default = pt.y)
                            putSafeFloat("handleOutX", pt.handleOutX, default = pt.x)
                            putSafeFloat("handleOutY", pt.handleOutY, default = pt.y)
                        })
                    }
                })
                if (mask.keyframes.isNotEmpty()) {
                    put("keyframes", JSONArray().apply {
                        mask.keyframes.forEach { mkf ->
                            put(JSONObject().apply {
                                put("timeOffsetMs", mkf.timeOffsetMs)
                                put("easing", mkf.easing.name)
                                put("points", JSONArray().apply {
                                    mkf.points.forEach { pt ->
                                        put(JSONObject().apply {
                                            putSafeFloat("x", pt.x); putSafeFloat("y", pt.y)
                                            putSafeFloat("handleInX", pt.handleInX, default = pt.x)
                                            putSafeFloat("handleInY", pt.handleInY, default = pt.y)
                                            putSafeFloat("handleOutX", pt.handleOutX, default = pt.x)
                                            putSafeFloat("handleOutY", pt.handleOutY, default = pt.y)
                                        })
                                    }
                                })
                            })
                        }
                    })
                }
            }
        }

        private fun serializeAudioEffect(ae: AudioEffect): JSONObject {
            return JSONObject().apply {
                put("id", ae.id)
                put("type", ae.type.name)
                put("enabled", ae.enabled)
                put("params", JSONObject().apply {
                    ae.params.forEach { (k, v) -> putSafeFloat(k, v) }
                })
            }
        }

        private fun serializeCaption(cap: Caption): JSONObject {
            return JSONObject().apply {
                put("id", cap.id)
                put("text", cap.text)
                put("startTimeMs", cap.startTimeMs)
                put("endTimeMs", cap.endTimeMs)
                put("styleType", cap.style.type.name)
                putSafeFloat("fontSize", cap.style.fontSize, default = 36f)
                putSafeFloat("positionY", cap.style.positionY, default = 0.85f)
                put("fontFamily", cap.style.fontFamily)
                put("color", cap.style.color)
                put("backgroundColor", cap.style.backgroundColor)
                put("highlightColor", cap.style.highlightColor)
                put("outline", cap.style.outline)
                put("outlineColor", cap.style.outlineColor)
                putSafeFloat("outlineWidth", cap.style.outlineWidth, default = 2f)
                put("shadow", cap.style.shadow)
                if (cap.words.isNotEmpty()) {
                    put("words", JSONArray().apply {
                        cap.words.forEach { w ->
                            put(JSONObject().apply {
                                put("text", w.text)
                                put("startTimeMs", w.startTimeMs)
                                put("endTimeMs", w.endTimeMs)
                                putSafeFloat("confidence", w.confidence, default = 1f)
                            })
                        }
                    })
                }
            }
        }

        private fun serializeTransition(t: Transition): JSONObject {
            return JSONObject().apply {
                put("type", t.type.name)
                put("durationMs", t.durationMs)
                put("easing", t.easing.name)
            }
        }

        // --- TextOverlay serialization ---

        private fun serializeTextOverlays(overlays: List<TextOverlay>): JSONArray {
            return JSONArray().apply {
                overlays.forEach { put(serializeTextOverlay(it)) }
            }
        }

        private fun serializeTextOverlay(t: TextOverlay): JSONObject {
            return JSONObject().apply {
                put("id", t.id)
                put("text", t.text)
                put("fontFamily", t.fontFamily)
                putSafeFloat("fontSize", t.fontSize, default = 48f)
                put("color", t.color)
                put("backgroundColor", t.backgroundColor)
                put("strokeColor", t.strokeColor)
                putSafeFloat("strokeWidth", t.strokeWidth)
                put("bold", t.bold)
                put("italic", t.italic)
                put("alignment", t.alignment.name)
                putSafeFloat("positionX", t.positionX, default = 0.5f)
                putSafeFloat("positionY", t.positionY, default = 0.5f)
                put("startTimeMs", t.startTimeMs)
                put("endTimeMs", t.endTimeMs)
                put("animationIn", t.animationIn.name)
                put("animationOut", t.animationOut.name)
                putSafeFloat("rotation", t.rotation)
                putSafeFloat("scaleX", t.scaleX, default = 1f)
                putSafeFloat("scaleY", t.scaleY, default = 1f)
                put("shadowColor", t.shadowColor)
                putSafeFloat("shadowOffsetX", t.shadowOffsetX)
                putSafeFloat("shadowOffsetY", t.shadowOffsetY)
                putSafeFloat("shadowBlur", t.shadowBlur)
                put("glowColor", t.glowColor)
                putSafeFloat("glowRadius", t.glowRadius)
                putSafeFloat("letterSpacing", t.letterSpacing)
                putSafeFloat("lineHeight", t.lineHeight, default = 1.2f)
                t.textPath?.let { tp ->
                    put("textPath", JSONObject().apply {
                        put("type", tp.type.name)
                        putSafeFloat("progress", tp.progress, default = 1f)
                        put("points", JSONArray().apply {
                            tp.points.forEach { pt ->
                                put(JSONObject().apply {
                                    putSafeFloat("x", pt.x); putSafeFloat("y", pt.y)
                                    putSafeFloat("handleInX", pt.handleInX, default = pt.x)
                                    putSafeFloat("handleInY", pt.handleInY, default = pt.y)
                                    putSafeFloat("handleOutX", pt.handleOutX, default = pt.x)
                                    putSafeFloat("handleOutY", pt.handleOutY, default = pt.y)
                                })
                            }
                        })
                    })
                }
                t.templateId?.let { put("templateId", it) }
                t.gradientStartColor?.let { put("gradientStartColor", it) }
                t.gradientEndColor?.let { put("gradientEndColor", it) }
                if (t.gradientAngle != 0f) putSafeFloat("gradientAngle", t.gradientAngle)
                if (t.wordStaggerMs > 0L) put("wordStaggerMs", t.wordStaggerMs)
                if (t.keyframes.isNotEmpty()) {
                    put("keyframes", JSONArray().apply {
                        t.keyframes.forEach { put(serializeKeyframe(it)) }
                    })
                }
            }
        }

        // --- Deserialization ---

        private fun deserializeTracks(
            arr: JSONArray,
            uriParser: (String) -> Uri?,
            mediaAssetUrisById: Map<String, String>,
        ): List<Track> {
            if (arr.length() > MAX_TRACKS) {
                Log.w(TAG, "Auto-save contains ${arr.length()} tracks; loading first $MAX_TRACKS")
            }
            return (0 until arr.length().coerceAtMost(MAX_TRACKS)).mapNotNull { i ->
                try {
                    deserializeTrack(arr.getJSONObject(i), uriParser, mediaAssetUrisById)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize track $i", e)
                    null
                }
            }
        }

        private fun deserializeTrack(
            json: JSONObject,
            uriParser: (String) -> Uri?,
            mediaAssetUrisById: Map<String, String>,
        ): Track {
            val clipsArr = json.optJSONArray("clips") ?: JSONArray()
            val audioFxArr = json.optJSONArray("audioEffects") ?: JSONArray()
            if (clipsArr.length() > MAX_CLIPS_PER_TRACK) {
                Log.w(TAG, "Track ${json.optString("id", "?")} has ${clipsArr.length()} clips; loading first $MAX_CLIPS_PER_TRACK")
            }
            return Track(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "VIDEO"), TrackType.VIDEO),
                index = json.optInt("index", 0).coerceAtLeast(0),
                isLocked = json.optBoolean("isLocked", false),
                isVisible = json.optBoolean("isVisible", true),
                isMuted = json.optBoolean("isMuted", false),
                isSolo = json.optBoolean("isSolo", false),
                volume = safeFloat(json.optDouble("volume", 1.0), 1f).coerceIn(0f, 2f),
                pan = safeFloat(json.optDouble("pan", 0.0), 0f).coerceIn(-1f, 1f),
                opacity = safeFloat(json.optDouble("opacity", 1.0), 1f).coerceIn(0f, 1f),
                blendMode = safeValueOf(json.optString("blendMode", "NORMAL"), BlendMode.NORMAL),
                isLinkedAV = json.optBoolean("isLinkedAV", true),
                showWaveform = json.optBoolean("showWaveform", true),
                trackHeight = json.optInt("trackHeight", 64).coerceIn(32, 240),
                isCollapsed = json.optBoolean("isCollapsed", false),
                clips = (0 until clipsArr.length().coerceAtMost(MAX_CLIPS_PER_TRACK)).mapNotNull { i ->
                    try { deserializeClip(clipsArr.getJSONObject(i), uriParser, mediaAssetUrisById) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize clip $i", e); null
                    }
                },
                audioEffects = (0 until audioFxArr.length().coerceAtMost(MAX_AUDIO_EFFECTS_PER_SCOPE)).mapNotNull { i ->
                    try { deserializeAudioEffect(audioFxArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize track audio effect $i", e); null }
                }
            )
        }

        private fun deserializeClip(
            json: JSONObject,
            uriParser: (String) -> Uri?,
            mediaAssetUrisById: Map<String, String>,
            depth: Int = 0,
        ): Clip? {
            if (depth > MAX_COMPOUND_CLIP_DEPTH) {
                Log.w(TAG, "Skipping compound clip beyond depth $MAX_COMPOUND_CLIP_DEPTH")
                return null
            }
            val effectsArr = json.optJSONArray("effects") ?: JSONArray()
            val keyframesArr = json.optJSONArray("keyframes") ?: JSONArray()
            val masksArr = json.optJSONArray("masks") ?: JSONArray()
            val audioFxArr = json.optJSONArray("audioEffects") ?: JSONArray()
            val captionsArr = json.optJSONArray("captions") ?: JSONArray()
            val assetId = json.optString("assetId", "").takeIf { it.isNotEmpty() }
            val serializedSourceUri = json.optString("sourceUri", "")
            val sourceUriStr = assetId
                ?.let { mediaAssetUrisById[it] }
                ?: serializedSourceUri
            if (sourceUriStr.isEmpty()) {
                Log.w(TAG, "Skipping clip ${json.optString("id", "?")} with empty sourceUri")
                return null
            }
            val parsedSourceUri = try { uriParser(sourceUriStr) } catch (e: Exception) {
                Log.w(TAG, "Skipping clip with malformed sourceUri: $sourceUriStr", e)
                return null
            } ?: return null
            val sourceDurationMs = json.optLong("sourceDurationMs", 0L)
            if (sourceDurationMs <= 0L) {
                Log.w(TAG, "Skipping clip ${json.optString("id", "?")} with non-positive sourceDurationMs=$sourceDurationMs")
                return null
            }
            val rawTrimEnd = json.optLong("trimEndMs", sourceDurationMs)
            // Coerce trim values to satisfy model invariants (trimEndMs <= sourceDurationMs)
            val trimStartMs = json.optLong("trimStartMs", 0L).coerceIn(0L, sourceDurationMs)
            val trimEndMs = rawTrimEnd.coerceIn(trimStartMs, sourceDurationMs.coerceAtLeast(trimStartMs))
            // Coerce fade durations: each must fit within remaining clip duration after the other fade.
            val clipDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
            val rawFadeIn = json.optLong("fadeInMs", 0L).coerceAtLeast(0L)
            val rawFadeOut = json.optLong("fadeOutMs", 0L).coerceAtLeast(0L)
            val fadeInMs = rawFadeIn.coerceAtMost(clipDurationMs)
            val fadeOutMs = rawFadeOut.coerceAtMost((clipDurationMs - fadeInMs).coerceAtLeast(0L))
            val proxyUri = json.optString("proxyUri", "").takeIf { it.isNotEmpty() }?.let { uriStr ->
                try { uriParser(uriStr) } catch (e: Exception) {
                    Log.w(TAG, "Discarding malformed proxyUri: $uriStr", e); null
                }
            }
            return Clip(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                assetId = json.optString("assetId", "").takeIf { it.isNotEmpty() },
                sourceUri = parsedSourceUri,
                sourceDurationMs = sourceDurationMs.coerceAtLeast(trimEndMs),
                timelineStartMs = json.optLong("timelineStartMs", 0L).coerceAtLeast(0L),
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                volume = safeFloat(json.optDouble("volume", 1.0), 1f).coerceIn(0f, 2f),
                speed = safeFloat(json.optDouble("speed", 1.0), 1f).coerceIn(0.01f, 100f),
                isReversed = json.optBoolean("isReversed", false),
                opacity = safeFloat(json.optDouble("opacity", 1.0), 1f).coerceIn(0f, 1f),
                rotation = safeFloat(json.optDouble("rotation", 0.0), 0f),
                scaleX = safeFloat(json.optDouble("scaleX", 1.0), 1f),
                scaleY = safeFloat(json.optDouble("scaleY", 1.0), 1f),
                positionX = safeFloat(json.optDouble("positionX", 0.0), 0f),
                positionY = safeFloat(json.optDouble("positionY", 0.0), 0f),
                anchorX = safeFloat(json.optDouble("anchorX", 0.5), 0.5f),
                anchorY = safeFloat(json.optDouble("anchorY", 0.5), 0.5f),
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
                blendMode = safeValueOf(json.optString("blendMode", "NORMAL"), BlendMode.NORMAL),
                isCompound = json.optBoolean("isCompound", false),
                compoundClips = json.optJSONArray("compoundClips")?.let { arr ->
                    if (depth >= MAX_COMPOUND_CLIP_DEPTH) {
                        if (arr.length() > 0) {
                            Log.w(TAG, "Dropping nested compound clips at max depth $MAX_COMPOUND_CLIP_DEPTH")
                        }
                        return@let emptyList()
                    }
                    (0 until cappedArrayLength(arr, MAX_COMPOUND_CLIPS_PER_CLIP, "compound clips")).mapNotNull { i ->
                        try { deserializeClip(arr.getJSONObject(i), uriParser, mediaAssetUrisById, depth + 1) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize compound clip $i", e); null }
                    }
                } ?: emptyList(),
                linkedClipId = json.optString("linkedClipId", "").takeIf { it.isNotEmpty() },
                groupId = json.optString("groupId", "").takeIf { it.isNotEmpty() },
                clipLabel = safeValueOf(json.optString("clipLabel", "NONE"), ClipLabel.NONE),
                sourceColorMetadata = deserializeSourceColorMetadata(json.optJSONObject("sourceColorMetadata")),
                effects = (0 until cappedArrayLength(effectsArr, MAX_CLIP_EFFECTS, "clip effects")).mapNotNull { i ->
                    try { deserializeEffect(effectsArr.getJSONObject(i)) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize effect $i", e); null
                    }
                },
                keyframes = (0 until cappedArrayLength(keyframesArr, MAX_KEYFRAMES_PER_SCOPE, "clip keyframes")).mapNotNull { i ->
                    try { deserializeKeyframe(keyframesArr.getJSONObject(i)) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize keyframe $i", e); null
                    }
                }.distinctBy { Pair(it.timeOffsetMs, it.property) },
                headTransition = (json.optJSONObject("headTransition") ?: json.optJSONObject("transition"))?.let { deserializeTransition(it) },
                tailTransition = json.optJSONObject("tailTransition")?.let { deserializeTransition(it) },
                colorGrade = json.optJSONObject("colorGrade")?.let { deserializeColorGrade(it) },
                speedCurve = json.optJSONObject("speedCurve")?.let { deserializeSpeedCurve(it) },
                masks = (0 until cappedArrayLength(masksArr, MAX_MASKS_PER_CLIP, "clip masks")).mapNotNull { i ->
                    try { deserializeMask(masksArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize mask $i", e); null }
                },
                audioEffects = (0 until cappedArrayLength(audioFxArr, MAX_AUDIO_EFFECTS_PER_SCOPE, "clip audio effects")).mapNotNull { i ->
                    try { deserializeAudioEffect(audioFxArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize clip audio effect $i", e); null }
                },
                captions = (0 until cappedArrayLength(captionsArr, MAX_CAPTIONS_PER_CLIP, "clip captions")).mapNotNull { i ->
                    try { deserializeCaption(captionsArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize caption $i", e); null }
                },
                name = json.optString("name", "").takeIf { it.isNotEmpty() },
                proxyUri = proxyUri,
                motionTrackingData = json.optJSONObject("motionTrackingData")?.let { mtd ->
                    val tpArr = mtd.optJSONArray("trackPoints") ?: JSONArray()
                    MotionTrackingData(
                        id = mtd.optString("id", java.util.UUID.randomUUID().toString()),
                        trackPoints = (0 until cappedArrayLength(tpArr, MAX_MOTION_TRACK_POINTS, "motion track points")).mapNotNull { i ->
                            try {
                                val tp = tpArr.getJSONObject(i)
                                MotionTrackPoint(
                                    timeOffsetMs = tp.optLong("timeOffsetMs", 0L),
                                    x = safeFloat(tp.optDouble("x", 0.0), 0f),
                                    y = safeFloat(tp.optDouble("y", 0.0), 0f),
                                    scaleX = safeFloat(tp.optDouble("scaleX", 1.0), 1f),
                                    scaleY = safeFloat(tp.optDouble("scaleY", 1.0), 1f),
                                    rotation = safeFloat(tp.optDouble("rotation", 0.0), 0f),
                                    confidence = safeFloat(tp.optDouble("confidence", 1.0), 1f).coerceIn(0f, 1f)
                                )
                            } catch (e: Exception) { Log.w(TAG, "Failed to deserialize motion track point $i", e); null }
                        },
                        targetType = safeValueOf(mtd.optString("targetType", "POINT"), TrackTargetType.POINT),
                        isActive = mtd.optBoolean("isActive", true)
                    )
                }
            )
        }

        private fun deserializeSourceColorMetadata(json: JSONObject?): SourceColorMetadata {
            if (json == null) return SourceColorMetadata()
            val formatsArr = json.optJSONArray("hdrFormats") ?: JSONArray()
            val formats = (0 until formatsArr.length()).mapNotNull { i ->
                formatsArr.optString(i)
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { SourceHdrFormat.valueOf(it) }.getOrNull() }
            }.toSet()
            return SourceColorMetadata(
                mimeType = json.optString("mimeType", "").takeIf { it.isNotBlank() },
                colorStandard = json.optString("colorStandard", "").takeIf { it.isNotBlank() },
                colorTransfer = json.optString("colorTransfer", "").takeIf { it.isNotBlank() },
                hdrFormats = formats,
                inspectedAtMs = json.optLong("inspectedAtMs", 0L).coerceAtLeast(0L)
            )
        }

        private fun deserializeEffect(json: JSONObject): Effect {
            val paramsJson = json.optJSONObject("params")
            val params = buildMap {
                val keys = paramsJson?.keys()
                var count = 0
                while (keys != null && keys.hasNext() && count < MAX_EFFECT_PARAMS) {
                    val key = keys.next()
                    put(boundedText(key, MAX_SHORT_TEXT_CHARS), safeFloat(paramsJson.optDouble(key, 0.0), 0f))
                    count++
                }
            }
            val effectKfArr = json.optJSONArray("keyframes") ?: JSONArray()
            return Effect(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "BRIGHTNESS"), EffectType.BRIGHTNESS),
                enabled = json.optBoolean("enabled", true),
                params = params,
                keyframes = (0 until cappedArrayLength(effectKfArr, MAX_KEYFRAMES_PER_SCOPE, "effect keyframes")).mapNotNull { i ->
                    try { deserializeEffectKeyframe(effectKfArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize effect keyframe $i", e); null }
                }.distinctBy { Pair(it.timeOffsetMs, it.paramName) },
                targetTrackedObjectId = json.optString("targetTrackedObjectId", "")
                    .takeIf { it.isNotBlank() }
            )
        }

        private fun deserializeEffectKeyframe(json: JSONObject): EffectKeyframe {
            return EffectKeyframe(
                timeOffsetMs = json.optLong("timeOffsetMs", 0L),
                paramName = json.optString("paramName", ""),
                value = safeFloat(json.optDouble("value", 0.0), 0f),
                easing = safeValueOf(json.optString("easing", "LINEAR"), Easing.LINEAR),
                handleInX = safeFloat(json.optDouble("handleInX", 0.0), 0f),
                handleInY = safeFloat(json.optDouble("handleInY", 0.0), 0f),
                handleOutX = safeFloat(json.optDouble("handleOutX", 0.0), 0f),
                handleOutY = safeFloat(json.optDouble("handleOutY", 0.0), 0f)
            )
        }

        private fun deserializeKeyframe(json: JSONObject): Keyframe {
            return Keyframe(
                timeOffsetMs = json.optLong("timeOffsetMs", 0L),
                property = safeValueOf(json.optString("property", "OPACITY"), KeyframeProperty.OPACITY),
                value = safeFloat(json.optDouble("value", 1.0), 1f),
                easing = safeValueOf(json.optString("easing", "LINEAR"), Easing.LINEAR),
                handleInX = safeFloat(json.optDouble("handleInX", 0.0), 0f),
                handleInY = safeFloat(json.optDouble("handleInY", 0.0), 0f),
                handleOutX = safeFloat(json.optDouble("handleOutX", 0.0), 0f),
                handleOutY = safeFloat(json.optDouble("handleOutY", 0.0), 0f),
                interpolation = safeValueOf(json.optString("interpolation", "BEZIER"), KeyframeInterpolation.BEZIER)
            )
        }

        // NaN/Infinity guard — JSONObject.optDouble can round-trip non-finite values that
        // a compromised export/import or file-system corruption introduced. Color matrices
        // and blend math propagate NaN across every pixel, turning clips black on playback
        // and export. Fall back to each field's natural identity so recovery is silent.
        private fun safeFloat(value: Double, default: Float): Float {
            val f = value.toFloat()
            return if (f.isFinite()) f else default
        }

        private fun deserializeColorGrade(json: JSONObject): ColorGrade {
            return ColorGrade(
                enabled = json.optBoolean("enabled", true),
                liftR = safeFloat(json.optDouble("liftR", 0.0), 0f), liftG = safeFloat(json.optDouble("liftG", 0.0), 0f), liftB = safeFloat(json.optDouble("liftB", 0.0), 0f),
                gammaR = safeFloat(json.optDouble("gammaR", 1.0), 1f), gammaG = safeFloat(json.optDouble("gammaG", 1.0), 1f), gammaB = safeFloat(json.optDouble("gammaB", 1.0), 1f),
                gainR = safeFloat(json.optDouble("gainR", 1.0), 1f), gainG = safeFloat(json.optDouble("gainG", 1.0), 1f), gainB = safeFloat(json.optDouble("gainB", 1.0), 1f),
                offsetR = safeFloat(json.optDouble("offsetR", 0.0), 0f), offsetG = safeFloat(json.optDouble("offsetG", 0.0), 0f), offsetB = safeFloat(json.optDouble("offsetB", 0.0), 0f),
                lutPath = json.optString("lutPath", "").takeIf { it.isNotEmpty() },
                lutIntensity = safeFloat(json.optDouble("lutIntensity", 1.0), 1f).coerceIn(0f, 1f),
                colorMatchRef = json.optString("colorMatchRef", "").takeIf { it.isNotEmpty() },
                curves = json.optJSONObject("curves")?.let { deserializeColorCurves(it) } ?: ColorCurves(),
                hslQualifier = json.optJSONObject("hsl")?.let { hsl ->
                    HslQualifier(
                        hueCenter = safeFloat(hsl.optDouble("hueCenter", 0.0), 0f),
                        hueWidth = safeFloat(hsl.optDouble("hueWidth", 30.0), 30f),
                        satMin = safeFloat(hsl.optDouble("satMin", 0.0), 0f),
                        satMax = safeFloat(hsl.optDouble("satMax", 1.0), 1f),
                        lumMin = safeFloat(hsl.optDouble("lumMin", 0.0), 0f),
                        lumMax = safeFloat(hsl.optDouble("lumMax", 1.0), 1f),
                        softness = safeFloat(hsl.optDouble("softness", 0.1), 0.1f),
                        adjustHue = safeFloat(hsl.optDouble("adjustHue", 0.0), 0f),
                        adjustSat = safeFloat(hsl.optDouble("adjustSat", 0.0), 0f),
                        adjustLum = safeFloat(hsl.optDouble("adjustLum", 0.0), 0f)
                    )
                }
            )
        }

        private fun deserializeColorCurves(json: JSONObject): ColorCurves {
            return ColorCurves(
                master = deserializeCurvePoints(json.optJSONArray("master")) ?: ColorCurves().master,
                red = deserializeCurvePoints(json.optJSONArray("red")) ?: ColorCurves().red,
                green = deserializeCurvePoints(json.optJSONArray("green")) ?: ColorCurves().green,
                blue = deserializeCurvePoints(json.optJSONArray("blue")) ?: ColorCurves().blue
            )
        }

        private fun deserializeCurvePoints(arr: JSONArray?): List<CurvePoint>? {
            if (arr == null || arr.length() == 0) return null
            return (0 until cappedArrayLength(arr, MAX_CURVE_POINTS, "curve points")).mapNotNull { i ->
                try {
                    val pt = arr.getJSONObject(i)
                    val rawX = pt.optDouble("x", 0.0).toFloat()
                    val rawY = pt.optDouble("y", 0.0).toFloat()
                    val x = (if (rawX.isFinite()) rawX else 0f).coerceIn(0f, 1f)
                    val y = (if (rawY.isFinite()) rawY else 0f).coerceIn(0f, 1f)
                    // NaN handles silently corrupt the bezier evaluator → clips render black.
                    // Fall back to the anchor point coords so curves stay usable after recovery.
                    CurvePoint(
                        x = x,
                        y = y,
                        handleInX = safeFloat(pt.optDouble("hix", x.toDouble()), x).coerceIn(-1f, 2f),
                        handleInY = safeFloat(pt.optDouble("hiy", y.toDouble()), y).coerceIn(-1f, 2f),
                        handleOutX = safeFloat(pt.optDouble("hox", x.toDouble()), x).coerceIn(-1f, 2f),
                        handleOutY = safeFloat(pt.optDouble("hoy", y.toDouble()), y).coerceIn(-1f, 2f)
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize curve point $i", e); null }
            }.takeIf { it.isNotEmpty() }
        }

        private fun deserializeSpeedCurve(json: JSONObject): SpeedCurve {
            val pointsArr = json.optJSONArray("points") ?: JSONArray()
            // Corrupted control points (speed<=0, position outside [0,1], NaN handles) feed
            // directly into the harmonic-mean duration math and the bezier evaluator — clamp
            // at the edge so downstream callers can trust the data.
            val points = (0 until cappedArrayLength(pointsArr, MAX_CURVE_POINTS, "speed-curve points")).mapNotNull { i ->
                try {
                    val pt = pointsArr.getJSONObject(i)
                    val rawSpeed = pt.optDouble("speed", 1.0).toFloat()
                    val speed = if (rawSpeed.isFinite()) rawSpeed.coerceIn(0.01f, 100f) else 1f
                    val rawPosition = pt.optDouble("position", 0.0).toFloat()
                    val position = if (rawPosition.isFinite()) rawPosition.coerceIn(0f, 1f) else 0f
                    val rawInY = pt.optDouble("handleInY", pt.optDouble("speed", 1.0)).toFloat()
                    val handleInY = if (rawInY.isFinite()) rawInY.coerceIn(0.01f, 100f) else speed
                    val rawOutY = pt.optDouble("handleOutY", pt.optDouble("speed", 1.0)).toFloat()
                    val handleOutY = if (rawOutY.isFinite()) rawOutY.coerceIn(0.01f, 100f) else speed
                    SpeedPoint(
                        position = position,
                        speed = speed,
                        handleInY = handleInY,
                        handleOutY = handleOutY
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize speed point $i", e)
                    null
                }
            }
            return SpeedCurve(points.ifEmpty { listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f)) })
        }

        private fun deserializeMask(json: JSONObject): Mask {
            val pointsArr = json.optJSONArray("points") ?: JSONArray()
            return Mask(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "RECTANGLE"), MaskType.RECTANGLE),
                feather = safeFloat(json.optDouble("feather", 0.0), 0f).coerceAtLeast(0f),
                opacity = safeFloat(json.optDouble("opacity", 1.0), 1f).coerceIn(0f, 1f),
                inverted = json.optBoolean("inverted", false),
                expansion = safeFloat(json.optDouble("expansion", 0.0), 0f),
                trackToMotion = json.optBoolean("trackToMotion", false),
                points = (0 until cappedArrayLength(pointsArr, MAX_MASK_POINTS, "mask points")).map { i ->
                    deserializeMaskPoint(pointsArr.getJSONObject(i))
                },
                keyframes = json.optJSONArray("keyframes")?.let { kfArr ->
                    (0 until cappedArrayLength(kfArr, MAX_KEYFRAMES_PER_SCOPE, "mask keyframes")).mapNotNull { i ->
                        try {
                            val mkf = kfArr.getJSONObject(i)
                            val mkfPointsArr = mkf.optJSONArray("points") ?: JSONArray()
                            MaskKeyframe(
                                timeOffsetMs = mkf.optLong("timeOffsetMs", 0L),
                                points = (0 until cappedArrayLength(mkfPointsArr, MAX_MASK_POINTS, "mask keyframe points")).map { j ->
                                    deserializeMaskPoint(mkfPointsArr.getJSONObject(j))
                                },
                                easing = safeValueOf(mkf.optString("easing", "LINEAR"), Easing.LINEAR)
                            )
                        } catch (e: Exception) { Log.w(TAG, "Failed to deserialize mask keyframe $i", e); null }
                    }
                } ?: emptyList()
            )
        }

        private fun deserializeMaskPoint(json: JSONObject): MaskPoint {
            val x = safeFloat(json.optDouble("x", 0.0), 0f)
            val y = safeFloat(json.optDouble("y", 0.0), 0f)
            return MaskPoint(
                x = x,
                y = y,
                handleInX = safeFloat(json.optDouble("handleInX", x.toDouble()), x),
                handleInY = safeFloat(json.optDouble("handleInY", y.toDouble()), y),
                handleOutX = safeFloat(json.optDouble("handleOutX", x.toDouble()), x),
                handleOutY = safeFloat(json.optDouble("handleOutY", y.toDouble()), y)
            )
        }

        private fun deserializeAudioEffect(json: JSONObject): AudioEffect {
            val paramsJson = json.optJSONObject("params")
            val params = buildMap {
                val keys = paramsJson?.keys()
                var count = 0
                while (keys != null && keys.hasNext() && count < MAX_EFFECT_PARAMS) {
                    val key = keys.next()
                    put(boundedText(key, MAX_SHORT_TEXT_CHARS), safeFloat(paramsJson.optDouble(key, 0.0), 0f))
                    count++
                }
            }
            return AudioEffect(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "PARAMETRIC_EQ"), AudioEffectType.PARAMETRIC_EQ),
                enabled = json.optBoolean("enabled", true),
                params = params
            )
        }

        private fun deserializeCaption(json: JSONObject): Caption {
            val wordsArr = json.optJSONArray("words") ?: JSONArray()
            val rawStart = json.optLong("startTimeMs", 0L).coerceAtLeast(0L)
            val rawEnd = json.optLong("endTimeMs", 0L)
            val endTimeMs = if (rawEnd < rawStart) rawStart + 1000L else rawEnd
            // Defensive: drop word timings that violate their own monotonicity or escape
            // the parent caption's window. Whisper output is usually well-formed but a
            // corrupt recovery file or manually-edited JSON can produce garbage that
            // breaks the karaoke-highlight renderer (it assumes sorted, in-bounds words).
            val safeFontSize = safeFloat(json.optDouble("fontSize", 36.0), 36f).coerceAtLeast(1f)
            val safePositionY = safeFloat(json.optDouble("positionY", 0.85), 0.85f).coerceIn(0f, 1f)
            return Caption(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                text = boundedText(json.optString("text", ""), MAX_TEXT_VALUE_CHARS),
                startTimeMs = rawStart,
                endTimeMs = endTimeMs,
                style = CaptionStyle(
                    type = safeValueOf(json.optString("styleType", "SUBTITLE_BAR"), CaptionStyleType.SUBTITLE_BAR),
                    fontSize = safeFontSize,
                    positionY = safePositionY,
                    fontFamily = boundedText(json.optString("fontFamily", "sans-serif-medium"), MAX_SHORT_TEXT_CHARS),
                    color = json.optLong("color", 0xFFFFFFFFL),
                    backgroundColor = json.optLong("backgroundColor", 0xCC000000L),
                    highlightColor = json.optLong("highlightColor", 0xFFFFD700L),
                    outline = json.optBoolean("outline", true),
                    outlineColor = json.optLong("outlineColor", 0xFF000000L),
                    outlineWidth = safeFloat(json.optDouble("outlineWidth", 2.0), 2f).coerceAtLeast(0f),
                    shadow = json.optBoolean("shadow", false)
                ),
                words = (0 until cappedArrayLength(wordsArr, MAX_CAPTION_WORDS, "caption words")).mapNotNull { i ->
                    val w = wordsArr.getJSONObject(i)
                    val wStart = w.optLong("startTimeMs", rawStart).coerceIn(rawStart, endTimeMs)
                    val wEnd = w.optLong("endTimeMs", wStart).coerceAtLeast(wStart)
                    if (wStart >= endTimeMs) return@mapNotNull null
                    CaptionWord(
                        text = boundedText(w.optString("text", ""), MAX_SHORT_TEXT_CHARS),
                        startTimeMs = wStart,
                        endTimeMs = wEnd.coerceAtMost(endTimeMs),
                        confidence = safeFloat(w.optDouble("confidence", 1.0), 1f).coerceIn(0f, 1f)
                    )
                }.sortedBy { it.startTimeMs }
            )
        }

        private fun deserializeTransition(json: JSONObject): Transition {
            return Transition(
                type = safeValueOf(json.optString("type", "DISSOLVE"), TransitionType.DISSOLVE),
                durationMs = json.optLong("durationMs", 500L).coerceIn(100L, 2000L),
                easing = safeValueOf(json.optString("easing", "LINEAR"), TransitionEasing.LINEAR)
            )
        }

        private fun deserializeTextOverlays(arr: JSONArray): List<TextOverlay> {
            return (0 until cappedArrayLength(arr, MAX_TEXT_OVERLAYS, "text overlays")).mapNotNull { i ->
                try { deserializeTextOverlay(arr.getJSONObject(i)) } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize text overlay $i", e); null
                }
            }
        }

        private fun deserializeTextOverlay(json: JSONObject): TextOverlay? {
            val text = boundedText(json.optString("text", ""), MAX_TEXT_VALUE_CHARS)
            if (text.isEmpty()) return null // TextOverlay requires non-empty text
            val startMs = json.optLong("startTimeMs", 0L).coerceAtLeast(0L)
            val rawEndMs = json.optLong("endTimeMs", startMs + 3000L)
            val endMs = if (rawEndMs > startMs) rawEndMs else startMs + 1L
            val positionX = safeFloat(json.optDouble("positionX", 0.5), 0.5f).coerceIn(-5f, 5f)
            val positionY = safeFloat(json.optDouble("positionY", 0.5), 0.5f).coerceIn(-5f, 5f)
            val scaleX = safeFloat(json.optDouble("scaleX", 1.0), 1f).coerceIn(0.01f, 100f)
            val scaleY = safeFloat(json.optDouble("scaleY", 1.0), 1f).coerceIn(0.01f, 100f)
            return TextOverlay(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                text = text,
                fontFamily = boundedText(json.optString("fontFamily", "sans-serif"), MAX_SHORT_TEXT_CHARS),
                fontSize = safeFloat(json.optDouble("fontSize", 48.0), 48f).coerceIn(1f, 512f),
                color = json.optLong("color", 0xFFFFFFFF),
                backgroundColor = json.optLong("backgroundColor", 0x00000000),
                strokeColor = json.optLong("strokeColor", 0xFF000000),
                strokeWidth = safeFloat(json.optDouble("strokeWidth", 0.0), 0f).coerceAtLeast(0f),
                bold = json.optBoolean("bold", false),
                italic = json.optBoolean("italic", false),
                alignment = safeValueOf(json.optString("alignment", "CENTER"), TextAlignment.CENTER),
                positionX = positionX,
                positionY = positionY,
                startTimeMs = startMs,
                endTimeMs = endMs,
                animationIn = safeValueOf(json.optString("animationIn", "NONE"), TextAnimation.NONE),
                animationOut = safeValueOf(json.optString("animationOut", "NONE"), TextAnimation.NONE),
                rotation = safeFloat(json.optDouble("rotation", 0.0), 0f),
                scaleX = scaleX,
                scaleY = scaleY,
                shadowColor = json.optLong("shadowColor", 0x80000000),
                shadowOffsetX = safeFloat(json.optDouble("shadowOffsetX", 0.0), 0f),
                shadowOffsetY = safeFloat(json.optDouble("shadowOffsetY", 0.0), 0f),
                shadowBlur = safeFloat(json.optDouble("shadowBlur", 0.0), 0f).coerceAtLeast(0f),
                glowColor = json.optLong("glowColor", 0x00000000),
                glowRadius = safeFloat(json.optDouble("glowRadius", 0.0), 0f).coerceAtLeast(0f),
                letterSpacing = safeFloat(json.optDouble("letterSpacing", 0.0), 0f),
                lineHeight = safeFloat(json.optDouble("lineHeight", 1.2), 1.2f).coerceAtLeast(0.1f),
                textPath = json.optJSONObject("textPath")?.let { tp ->
                    val tpPointsArr = tp.optJSONArray("points") ?: JSONArray()
                    TextPath(
                        type = safeValueOf(tp.optString("type", "STRAIGHT"), TextPathType.STRAIGHT),
                        points = (0 until cappedArrayLength(tpPointsArr, MAX_MASK_POINTS, "text-path points")).map { i ->
                            deserializeMaskPoint(tpPointsArr.getJSONObject(i))
                        },
                        progress = safeFloat(tp.optDouble("progress", 1.0), 1f).coerceIn(0f, 1f)
                    )
                },
                templateId = boundedText(json.optString("templateId", ""), MAX_SHORT_TEXT_CHARS).takeIf { it.isNotEmpty() },
                keyframes = json.optJSONArray("keyframes")?.let { kfArr ->
                    (0 until cappedArrayLength(kfArr, MAX_KEYFRAMES_PER_SCOPE, "text-overlay keyframes")).mapNotNull { i ->
                        try { deserializeKeyframe(kfArr.getJSONObject(i)) } catch (e: Exception) {
                            Log.w(TAG, "Failed to deserialize text overlay keyframe $i", e); null
                        }
                    }.distinctBy { Pair(it.timeOffsetMs, it.property) }
                } ?: emptyList(),
                gradientStartColor = if (json.has("gradientStartColor")) json.optLong("gradientStartColor") else null,
                gradientEndColor = if (json.has("gradientEndColor")) json.optLong("gradientEndColor") else null,
                gradientAngle = safeFloat(json.optDouble("gradientAngle", 0.0), 0f),
                wordStaggerMs = json.optLong("wordStaggerMs", 0L).coerceAtLeast(0L)
            )
        }
    }
}
