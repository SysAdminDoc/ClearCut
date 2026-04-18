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

@Singleton
class ProjectAutoSave @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val autoSaveDir = File(context.filesDir, "autosave").apply { mkdirs() }
    private val saveMutex = Mutex()
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
                AutoSaveState.deserialize(file.readText(Charsets.UTF_8)).also {
                    backupFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recovery data for $projectId", e)
                if (!backupFile.exists()) {
                    return@withLock null
                }
                try {
                    Log.w(TAG, "Primary auto-save is corrupt; attempting backup restore for $projectId")
                    AutoSaveState.deserialize(backupFile.readText(Charsets.UTF_8)).also {
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
        }
    }

    suspend fun copyAutoSave(fromProjectId: String, toProjectId: String): Boolean {
        val fromFile = getAutoSaveFile(fromProjectId)
        if (!fromFile.exists()) {
            Log.w(TAG, "No auto-save found to copy for $fromProjectId")
            return false
        }
        return try {
            // Hold the mutex for the entire read→mutate→write sequence. Releasing between
            // read and write let a concurrent saveState() of the source project overwrite
            // the file with newer data, after which we'd write stale (but newly-tagged)
            // JSON to the destination — silently losing the source project's latest edits
            // from the duplicate.
            saveMutex.withLock {
                val json = JSONObject(fromFile.readText(Charsets.UTF_8))
                json.put("projectId", toProjectId)
                json.put("timestamp", System.currentTimeMillis())
                writeAutoSaveFileLocked(toProjectId, json.toString(2))
            }
            true
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
        writeAutoSaveFileLocked(projectId, state.serialize())
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
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList(),
    val beatMarkers: List<Long> = emptyList()
) {
    fun serialize(): String {
        val json = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("projectId", projectId)
            put("timestamp", timestamp)
            put("playheadMs", playheadMs)
            put("tracks", serializeTracks(tracks))
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
                            put("positionX", io.positionX.toDouble())
                            put("positionY", io.positionY.toDouble())
                            put("scale", io.scale.toDouble())
                            put("rotation", io.rotation.toDouble())
                            put("opacity", io.opacity.toDouble())
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
            if (drawingPaths.isNotEmpty()) {
                put("drawingPaths", JSONArray().apply {
                    drawingPaths.forEach { dp ->
                        put(JSONObject().apply {
                            put("color", dp.color)
                            put("strokeWidth", dp.strokeWidth.toDouble())
                            put("points", JSONArray().apply {
                                dp.points.forEach { (x, y) ->
                                    put(JSONObject().apply {
                                        put("x", x.toDouble())
                                        put("y", y.toDouble())
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
        }
        return json.toString(2)
    }

    companion object {
        const val FORMAT_VERSION = 1

        // Safe enum valueOf with fallback — prevents crashes from stale/unknown enum values
        private inline fun <reified T : Enum<T>> safeValueOf(name: String, default: T): T {
            return try { enumValueOf<T>(name) } catch (_: IllegalArgumentException) { default }
        }

        fun deserialize(raw: String): AutoSaveState {
            val json = JSONObject(raw)
            val fileVersion = json.optInt("version", 0)
            if (fileVersion > FORMAT_VERSION) {
                Log.w(TAG, "Auto-save written by newer format ($fileVersion > $FORMAT_VERSION); attempting best-effort load")
            }
            val tracks = deserializeTracks(json.optJSONArray("tracks") ?: JSONArray())
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
            val chapters = (0 until chaptersArr.length()).mapNotNull { i ->
                try {
                    val ch = chaptersArr.getJSONObject(i)
                    ChapterMarker(
                        timeMs = ch.optLong("timeMs", 0L),
                        title = ch.optString("title", "")
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize chapter marker $i", e); null }
            }
            val imageOverlaysArr = json.optJSONArray("imageOverlays") ?: JSONArray()
            val imageOverlays = (0 until imageOverlaysArr.length()).mapNotNull { i ->
                try {
                    val io = imageOverlaysArr.getJSONObject(i)
                    val srcUri = io.optString("sourceUri", "")
                    if (srcUri.isEmpty()) return@mapNotNull null
                    val parsedUri = try { android.net.Uri.parse(srcUri) } catch (e: Exception) {
                        Log.w(TAG, "Skipping image overlay with malformed URI: $srcUri", e)
                        return@mapNotNull null
                    }
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
            val timelineMarkers = (0 until timelineMarkersArr.length()).mapNotNull { i ->
                try {
                    val m = timelineMarkersArr.getJSONObject(i)
                    TimelineMarker(
                        id = m.optString("id", java.util.UUID.randomUUID().toString()),
                        timeMs = m.optLong("timeMs", 0L),
                        label = m.optString("label", ""),
                        color = safeValueOf(m.optString("color", "BLUE"), MarkerColor.BLUE),
                        notes = m.optString("notes", "")
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize timeline marker $i", e); null }
            }
            val drawingPathsArr = json.optJSONArray("drawingPaths") ?: JSONArray()
            val drawingPaths = (0 until drawingPathsArr.length()).mapNotNull { i ->
                try {
                    val dp = drawingPathsArr.getJSONObject(i)
                    val pointsArr = dp.optJSONArray("points") ?: return@mapNotNull null
                    // Drop NaN/Infinity coordinates — Compose Canvas drawPath silently breaks
                    // rendering for the whole layer when a single segment contains a
                    // non-finite coord, dropping every subsequent drawing on the overlay.
                    val points = (0 until pointsArr.length()).mapNotNull { j ->
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
            val beatMarkers = (0 until beatMarkersArr.length()).mapNotNull { i ->
                try { beatMarkersArr.getLong(i) }
                catch (e: Exception) { Log.w(TAG, "Failed to deserialize beat marker $i", e); null }
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
                drawingPaths = drawingPaths,
                beatMarkers = beatMarkers
            )
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
                put("volume", track.volume.toDouble())
                put("pan", track.pan.toDouble())
                put("opacity", track.opacity.toDouble())
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
                    put("sourceUri", clip.sourceUri.toString())
                    put("sourceDurationMs", clip.sourceDurationMs)
                    put("trimStartMs", clip.trimStartMs)
                    put("trimEndMs", clip.trimEndMs)
                }
            }
            return JSONObject().apply {
                put("id", clip.id)
                put("sourceUri", clip.sourceUri.toString())
                put("sourceDurationMs", clip.sourceDurationMs)
                put("timelineStartMs", clip.timelineStartMs)
                put("trimStartMs", clip.trimStartMs)
                put("trimEndMs", clip.trimEndMs)
                put("volume", clip.volume.toDouble())
                put("speed", clip.speed.toDouble())
                put("isReversed", clip.isReversed)
                put("opacity", clip.opacity.toDouble())
                put("rotation", clip.rotation.toDouble())
                put("scaleX", clip.scaleX.toDouble())
                put("scaleY", clip.scaleY.toDouble())
                put("positionX", clip.positionX.toDouble())
                put("positionY", clip.positionY.toDouble())
                put("anchorX", clip.anchorX.toDouble())
                put("anchorY", clip.anchorY.toDouble())
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
                put("effects", JSONArray().apply {
                    clip.effects.forEach { put(serializeEffect(it)) }
                })
                put("keyframes", JSONArray().apply {
                    clip.keyframes.forEach { put(serializeKeyframe(it)) }
                })
                clip.transition?.let { put("transition", serializeTransition(it)) }
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
                                    put("x", tp.x.toDouble())
                                    put("y", tp.y.toDouble())
                                    put("scaleX", tp.scaleX.toDouble())
                                    put("scaleY", tp.scaleY.toDouble())
                                    put("rotation", tp.rotation.toDouble())
                                    put("confidence", tp.confidence.toDouble())
                                })
                            }
                        })
                    })
                }
            }
        }

        private fun serializeEffect(effect: Effect): JSONObject {
            return JSONObject().apply {
                put("id", effect.id)
                put("type", effect.type.name)
                put("enabled", effect.enabled)
                put("params", JSONObject().apply {
                    effect.params.forEach { (k, v) -> put(k, v.toDouble()) }
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
                put("value", kf.value.toDouble())
                put("easing", kf.easing.name)
                put("handleInX", kf.handleInX.toDouble())
                put("handleInY", kf.handleInY.toDouble())
                put("handleOutX", kf.handleOutX.toDouble())
                put("handleOutY", kf.handleOutY.toDouble())
            }
        }

        private fun serializeKeyframe(kf: Keyframe): JSONObject {
            return JSONObject().apply {
                put("timeOffsetMs", kf.timeOffsetMs)
                put("property", kf.property.name)
                put("value", kf.value.toDouble())
                put("easing", kf.easing.name)
                put("handleInX", kf.handleInX.toDouble())
                put("handleInY", kf.handleInY.toDouble())
                put("handleOutX", kf.handleOutX.toDouble())
                put("handleOutY", kf.handleOutY.toDouble())
                put("interpolation", kf.interpolation.name)
            }
        }

        private fun serializeColorGrade(g: ColorGrade): JSONObject {
            return JSONObject().apply {
                put("enabled", g.enabled)
                put("liftR", g.liftR.toDouble()); put("liftG", g.liftG.toDouble()); put("liftB", g.liftB.toDouble())
                put("gammaR", g.gammaR.toDouble()); put("gammaG", g.gammaG.toDouble()); put("gammaB", g.gammaB.toDouble())
                put("gainR", g.gainR.toDouble()); put("gainG", g.gainG.toDouble()); put("gainB", g.gainB.toDouble())
                put("offsetR", g.offsetR.toDouble()); put("offsetG", g.offsetG.toDouble()); put("offsetB", g.offsetB.toDouble())
                g.lutPath?.let { put("lutPath", it) }
                put("lutIntensity", g.lutIntensity.toDouble())
                g.colorMatchRef?.let { put("colorMatchRef", it) }
                put("curves", serializeColorCurves(g.curves))
                g.hslQualifier?.let { hsl ->
                    put("hsl", JSONObject().apply {
                        put("hueCenter", hsl.hueCenter.toDouble()); put("hueWidth", hsl.hueWidth.toDouble())
                        put("satMin", hsl.satMin.toDouble()); put("satMax", hsl.satMax.toDouble())
                        put("lumMin", hsl.lumMin.toDouble()); put("lumMax", hsl.lumMax.toDouble())
                        put("softness", hsl.softness.toDouble())
                        put("adjustHue", hsl.adjustHue.toDouble()); put("adjustSat", hsl.adjustSat.toDouble()); put("adjustLum", hsl.adjustLum.toDouble())
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
                        put("x", p.x.toDouble()); put("y", p.y.toDouble())
                        put("hix", p.handleInX.toDouble()); put("hiy", p.handleInY.toDouble())
                        put("hox", p.handleOutX.toDouble()); put("hoy", p.handleOutY.toDouble())
                    })
                }
            }
        }

        private fun serializeSpeedCurve(sc: SpeedCurve): JSONObject {
            return JSONObject().apply {
                put("points", JSONArray().apply {
                    sc.points.forEach { pt ->
                        put(JSONObject().apply {
                            put("position", pt.position.toDouble())
                            put("speed", pt.speed.toDouble())
                            put("handleInY", pt.handleInY.toDouble())
                            put("handleOutY", pt.handleOutY.toDouble())
                        })
                    }
                })
            }
        }

        private fun serializeMask(mask: Mask): JSONObject {
            return JSONObject().apply {
                put("id", mask.id)
                put("type", mask.type.name)
                put("feather", mask.feather.toDouble())
                put("opacity", mask.opacity.toDouble())
                put("inverted", mask.inverted)
                put("expansion", mask.expansion.toDouble())
                put("trackToMotion", mask.trackToMotion)
                put("points", JSONArray().apply {
                    mask.points.forEach { pt ->
                        put(JSONObject().apply {
                            put("x", pt.x.toDouble()); put("y", pt.y.toDouble())
                            put("handleInX", pt.handleInX.toDouble())
                            put("handleInY", pt.handleInY.toDouble())
                            put("handleOutX", pt.handleOutX.toDouble())
                            put("handleOutY", pt.handleOutY.toDouble())
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
                                            put("x", pt.x.toDouble()); put("y", pt.y.toDouble())
                                            put("handleInX", pt.handleInX.toDouble())
                                            put("handleInY", pt.handleInY.toDouble())
                                            put("handleOutX", pt.handleOutX.toDouble())
                                            put("handleOutY", pt.handleOutY.toDouble())
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
                    ae.params.forEach { (k, v) -> put(k, v.toDouble()) }
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
                put("fontSize", cap.style.fontSize.toDouble())
                put("positionY", cap.style.positionY.toDouble())
                put("fontFamily", cap.style.fontFamily)
                put("color", cap.style.color)
                put("backgroundColor", cap.style.backgroundColor)
                put("highlightColor", cap.style.highlightColor)
                put("outline", cap.style.outline)
                put("shadow", cap.style.shadow)
                if (cap.words.isNotEmpty()) {
                    put("words", JSONArray().apply {
                        cap.words.forEach { w ->
                            put(JSONObject().apply {
                                put("text", w.text)
                                put("startTimeMs", w.startTimeMs)
                                put("endTimeMs", w.endTimeMs)
                                put("confidence", w.confidence.toDouble())
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
                put("fontSize", t.fontSize.toDouble())
                put("color", t.color)
                put("backgroundColor", t.backgroundColor)
                put("strokeColor", t.strokeColor)
                put("strokeWidth", t.strokeWidth.toDouble())
                put("bold", t.bold)
                put("italic", t.italic)
                put("alignment", t.alignment.name)
                put("positionX", t.positionX.toDouble())
                put("positionY", t.positionY.toDouble())
                put("startTimeMs", t.startTimeMs)
                put("endTimeMs", t.endTimeMs)
                put("animationIn", t.animationIn.name)
                put("animationOut", t.animationOut.name)
                put("rotation", t.rotation.toDouble())
                put("scaleX", t.scaleX.toDouble())
                put("scaleY", t.scaleY.toDouble())
                put("shadowColor", t.shadowColor)
                put("shadowOffsetX", t.shadowOffsetX.toDouble())
                put("shadowOffsetY", t.shadowOffsetY.toDouble())
                put("shadowBlur", t.shadowBlur.toDouble())
                put("glowColor", t.glowColor)
                put("glowRadius", t.glowRadius.toDouble())
                put("letterSpacing", t.letterSpacing.toDouble())
                put("lineHeight", t.lineHeight.toDouble())
                t.textPath?.let { tp ->
                    put("textPath", JSONObject().apply {
                        put("type", tp.type.name)
                        put("progress", tp.progress.toDouble())
                        put("points", JSONArray().apply {
                            tp.points.forEach { pt ->
                                put(JSONObject().apply {
                                    put("x", pt.x.toDouble()); put("y", pt.y.toDouble())
                                    put("handleInX", pt.handleInX.toDouble())
                                    put("handleInY", pt.handleInY.toDouble())
                                    put("handleOutX", pt.handleOutX.toDouble())
                                    put("handleOutY", pt.handleOutY.toDouble())
                                })
                            }
                        })
                    })
                }
                t.templateId?.let { put("templateId", it) }
                if (t.keyframes.isNotEmpty()) {
                    put("keyframes", JSONArray().apply {
                        t.keyframes.forEach { put(serializeKeyframe(it)) }
                    })
                }
            }
        }

        // --- Deserialization ---

        private fun deserializeTracks(arr: JSONArray): List<Track> {
            return (0 until arr.length()).map { deserializeTrack(arr.getJSONObject(it)) }
        }

        private fun deserializeTrack(json: JSONObject): Track {
            val clipsArr = json.optJSONArray("clips") ?: JSONArray()
            val audioFxArr = json.optJSONArray("audioEffects") ?: JSONArray()
            return Track(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "VIDEO"), TrackType.VIDEO),
                index = json.optInt("index", 0),
                isLocked = json.optBoolean("isLocked", false),
                isVisible = json.optBoolean("isVisible", true),
                isMuted = json.optBoolean("isMuted", false),
                isSolo = json.optBoolean("isSolo", false),
                volume = json.optDouble("volume", 1.0).toFloat(),
                pan = json.optDouble("pan", 0.0).toFloat(),
                opacity = json.optDouble("opacity", 1.0).toFloat(),
                blendMode = safeValueOf(json.optString("blendMode", "NORMAL"), BlendMode.NORMAL),
                isLinkedAV = json.optBoolean("isLinkedAV", true),
                showWaveform = json.optBoolean("showWaveform", true),
                trackHeight = json.optInt("trackHeight", 64),
                isCollapsed = json.optBoolean("isCollapsed", false),
                clips = (0 until clipsArr.length()).mapNotNull { i ->
                    try { deserializeClip(clipsArr.getJSONObject(i)) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize clip $i", e); null
                    }
                },
                audioEffects = (0 until audioFxArr.length()).mapNotNull { i ->
                    try { deserializeAudioEffect(audioFxArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize track audio effect $i", e); null }
                }
            )
        }

        private fun deserializeClip(json: JSONObject): Clip? {
            val effectsArr = json.optJSONArray("effects") ?: JSONArray()
            val keyframesArr = json.optJSONArray("keyframes") ?: JSONArray()
            val masksArr = json.optJSONArray("masks") ?: JSONArray()
            val audioFxArr = json.optJSONArray("audioEffects") ?: JSONArray()
            val captionsArr = json.optJSONArray("captions") ?: JSONArray()
            val sourceUriStr = json.optString("sourceUri", "")
            if (sourceUriStr.isEmpty()) {
                Log.w(TAG, "Skipping clip ${json.optString("id", "?")} with empty sourceUri")
                return null
            }
            val parsedSourceUri = try { Uri.parse(sourceUriStr) } catch (e: Exception) {
                Log.w(TAG, "Skipping clip with malformed sourceUri: $sourceUriStr", e)
                return null
            }
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
                try { Uri.parse(uriStr) } catch (e: Exception) {
                    Log.w(TAG, "Discarding malformed proxyUri: $uriStr", e); null
                }
            }
            return Clip(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                sourceUri = parsedSourceUri,
                sourceDurationMs = sourceDurationMs.coerceAtLeast(trimEndMs),
                timelineStartMs = json.optLong("timelineStartMs", 0L).coerceAtLeast(0L),
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                volume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 2f),
                speed = json.optDouble("speed", 1.0).toFloat().coerceAtLeast(0.01f),
                isReversed = json.optBoolean("isReversed", false),
                opacity = json.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                rotation = json.optDouble("rotation", 0.0).toFloat(),
                scaleX = json.optDouble("scaleX", 1.0).toFloat(),
                scaleY = json.optDouble("scaleY", 1.0).toFloat(),
                positionX = json.optDouble("positionX", 0.0).toFloat(),
                positionY = json.optDouble("positionY", 0.0).toFloat(),
                anchorX = json.optDouble("anchorX", 0.5).toFloat(),
                anchorY = json.optDouble("anchorY", 0.5).toFloat(),
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
                blendMode = safeValueOf(json.optString("blendMode", "NORMAL"), BlendMode.NORMAL),
                isCompound = json.optBoolean("isCompound", false),
                compoundClips = json.optJSONArray("compoundClips")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        try { deserializeClip(arr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize compound clip $i", e); null }
                    }
                } ?: emptyList(),
                linkedClipId = json.optString("linkedClipId", "").takeIf { it.isNotEmpty() },
                groupId = json.optString("groupId", "").takeIf { it.isNotEmpty() },
                clipLabel = safeValueOf(json.optString("clipLabel", "NONE"), ClipLabel.NONE),
                effects = (0 until effectsArr.length()).mapNotNull { i ->
                    try { deserializeEffect(effectsArr.getJSONObject(i)) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize effect $i", e); null
                    }
                },
                keyframes = (0 until keyframesArr.length()).mapNotNull { i ->
                    try { deserializeKeyframe(keyframesArr.getJSONObject(i)) } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize keyframe $i", e); null
                    }
                }.distinctBy { Pair(it.timeOffsetMs, it.property) },
                transition = json.optJSONObject("transition")?.let { deserializeTransition(it) },
                colorGrade = json.optJSONObject("colorGrade")?.let { deserializeColorGrade(it) },
                speedCurve = json.optJSONObject("speedCurve")?.let { deserializeSpeedCurve(it) },
                masks = (0 until masksArr.length()).mapNotNull { i ->
                    try { deserializeMask(masksArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize mask $i", e); null }
                },
                audioEffects = (0 until audioFxArr.length()).mapNotNull { i ->
                    try { deserializeAudioEffect(audioFxArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize clip audio effect $i", e); null }
                },
                captions = (0 until captionsArr.length()).mapNotNull { i ->
                    try { deserializeCaption(captionsArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize caption $i", e); null }
                },
                proxyUri = proxyUri,
                motionTrackingData = json.optJSONObject("motionTrackingData")?.let { mtd ->
                    val tpArr = mtd.optJSONArray("trackPoints") ?: JSONArray()
                    MotionTrackingData(
                        id = mtd.optString("id", java.util.UUID.randomUUID().toString()),
                        trackPoints = (0 until tpArr.length()).mapNotNull { i ->
                            try {
                                val tp = tpArr.getJSONObject(i)
                                MotionTrackPoint(
                                    timeOffsetMs = tp.optLong("timeOffsetMs", 0L),
                                    x = tp.optDouble("x", 0.0).toFloat(),
                                    y = tp.optDouble("y", 0.0).toFloat(),
                                    scaleX = tp.optDouble("scaleX", 1.0).toFloat(),
                                    scaleY = tp.optDouble("scaleY", 1.0).toFloat(),
                                    rotation = tp.optDouble("rotation", 0.0).toFloat(),
                                    confidence = tp.optDouble("confidence", 1.0).toFloat()
                                )
                            } catch (e: Exception) { Log.w(TAG, "Failed to deserialize motion track point $i", e); null }
                        },
                        targetType = safeValueOf(mtd.optString("targetType", "POINT"), TrackTargetType.POINT),
                        isActive = mtd.optBoolean("isActive", true)
                    )
                }
            )
        }

        private fun deserializeEffect(json: JSONObject): Effect {
            val paramsJson = json.optJSONObject("params")
            val params = buildMap {
                paramsJson?.keys()?.forEach { key ->
                    put(key, paramsJson.optDouble(key, 0.0).toFloat())
                }
            }
            val effectKfArr = json.optJSONArray("keyframes") ?: JSONArray()
            return Effect(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "BRIGHTNESS"), EffectType.BRIGHTNESS),
                enabled = json.optBoolean("enabled", true),
                params = params,
                keyframes = (0 until effectKfArr.length()).mapNotNull { i ->
                    try { deserializeEffectKeyframe(effectKfArr.getJSONObject(i)) } catch (e: Exception) { Log.w(TAG, "Failed to deserialize effect keyframe $i", e); null }
                }.distinctBy { Pair(it.timeOffsetMs, it.paramName) }
            )
        }

        private fun deserializeEffectKeyframe(json: JSONObject): EffectKeyframe {
            return EffectKeyframe(
                timeOffsetMs = json.optLong("timeOffsetMs", 0L),
                paramName = json.optString("paramName", ""),
                value = json.optDouble("value", 0.0).toFloat(),
                easing = safeValueOf(json.optString("easing", "LINEAR"), Easing.LINEAR),
                handleInX = json.optDouble("handleInX", 0.0).toFloat(),
                handleInY = json.optDouble("handleInY", 0.0).toFloat(),
                handleOutX = json.optDouble("handleOutX", 0.0).toFloat(),
                handleOutY = json.optDouble("handleOutY", 0.0).toFloat()
            )
        }

        private fun deserializeKeyframe(json: JSONObject): Keyframe {
            return Keyframe(
                timeOffsetMs = json.optLong("timeOffsetMs", 0L),
                property = safeValueOf(json.optString("property", "OPACITY"), KeyframeProperty.OPACITY),
                value = json.optDouble("value", 1.0).toFloat(),
                easing = safeValueOf(json.optString("easing", "LINEAR"), Easing.LINEAR),
                handleInX = json.optDouble("handleInX", 0.0).toFloat(),
                handleInY = json.optDouble("handleInY", 0.0).toFloat(),
                handleOutX = json.optDouble("handleOutX", 0.0).toFloat(),
                handleOutY = json.optDouble("handleOutY", 0.0).toFloat(),
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
            return (0 until arr.length()).mapNotNull { i ->
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
            val points = (0 until pointsArr.length()).map { i ->
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
            }
            return SpeedCurve(points.ifEmpty { listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f)) })
        }

        private fun deserializeMask(json: JSONObject): Mask {
            val pointsArr = json.optJSONArray("points") ?: JSONArray()
            return Mask(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "RECTANGLE"), MaskType.RECTANGLE),
                feather = json.optDouble("feather", 0.0).toFloat(),
                opacity = json.optDouble("opacity", 1.0).toFloat(),
                inverted = json.optBoolean("inverted", false),
                expansion = json.optDouble("expansion", 0.0).toFloat(),
                trackToMotion = json.optBoolean("trackToMotion", false),
                points = (0 until pointsArr.length()).map { i ->
                    val pt = pointsArr.getJSONObject(i)
                    MaskPoint(
                        x = pt.optDouble("x", 0.0).toFloat(),
                        y = pt.optDouble("y", 0.0).toFloat(),
                        handleInX = pt.optDouble("handleInX", 0.0).toFloat(),
                        handleInY = pt.optDouble("handleInY", 0.0).toFloat(),
                        handleOutX = pt.optDouble("handleOutX", 0.0).toFloat(),
                        handleOutY = pt.optDouble("handleOutY", 0.0).toFloat()
                    )
                },
                keyframes = json.optJSONArray("keyframes")?.let { kfArr ->
                    (0 until kfArr.length()).mapNotNull { i ->
                        try {
                            val mkf = kfArr.getJSONObject(i)
                            val mkfPointsArr = mkf.optJSONArray("points") ?: JSONArray()
                            MaskKeyframe(
                                timeOffsetMs = mkf.optLong("timeOffsetMs", 0L),
                                points = (0 until mkfPointsArr.length()).map { j ->
                                    val pt = mkfPointsArr.getJSONObject(j)
                                    MaskPoint(
                                        x = pt.optDouble("x", 0.0).toFloat(),
                                        y = pt.optDouble("y", 0.0).toFloat(),
                                        handleInX = pt.optDouble("handleInX", 0.0).toFloat(),
                                        handleInY = pt.optDouble("handleInY", 0.0).toFloat(),
                                        handleOutX = pt.optDouble("handleOutX", 0.0).toFloat(),
                                        handleOutY = pt.optDouble("handleOutY", 0.0).toFloat()
                                    )
                                },
                                easing = safeValueOf(mkf.optString("easing", "LINEAR"), Easing.LINEAR)
                            )
                        } catch (e: Exception) { Log.w(TAG, "Failed to deserialize mask keyframe $i", e); null }
                    }
                } ?: emptyList()
            )
        }

        private fun deserializeAudioEffect(json: JSONObject): AudioEffect {
            val paramsJson = json.optJSONObject("params")
            val params = buildMap {
                paramsJson?.keys()?.forEach { key ->
                    put(key, paramsJson.optDouble(key, 0.0).toFloat())
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
                text = json.optString("text", ""),
                startTimeMs = rawStart,
                endTimeMs = endTimeMs,
                style = CaptionStyle(
                    type = safeValueOf(json.optString("styleType", "SUBTITLE_BAR"), CaptionStyleType.SUBTITLE_BAR),
                    fontSize = safeFontSize,
                    positionY = safePositionY,
                    fontFamily = json.optString("fontFamily", "sans-serif-medium"),
                    color = json.optLong("color", 0xFFFFFFFFL),
                    backgroundColor = json.optLong("backgroundColor", 0xCC000000L),
                    highlightColor = json.optLong("highlightColor", 0xFFFFD700L),
                    outline = json.optBoolean("outline", true),
                    shadow = json.optBoolean("shadow", false)
                ),
                words = (0 until wordsArr.length()).mapNotNull { i ->
                    val w = wordsArr.getJSONObject(i)
                    val wStart = w.optLong("startTimeMs", 0L).coerceAtLeast(0L)
                    val wEnd = w.optLong("endTimeMs", wStart).coerceAtLeast(wStart)
                    if (wStart > endTimeMs) return@mapNotNull null
                    CaptionWord(
                        text = w.optString("text", ""),
                        startTimeMs = wStart,
                        endTimeMs = wEnd.coerceAtMost(endTimeMs),
                        confidence = safeFloat(w.optDouble("confidence", 1.0), 1f).coerceIn(0f, 1f)
                    )
                }
            )
        }

        private fun deserializeTransition(json: JSONObject): Transition {
            return Transition(
                type = safeValueOf(json.optString("type", "DISSOLVE"), TransitionType.DISSOLVE),
                durationMs = json.optLong("durationMs", 500L).coerceIn(100L, 2000L)
            )
        }

        private fun deserializeTextOverlays(arr: JSONArray): List<TextOverlay> {
            return (0 until arr.length()).mapNotNull { i ->
                try { deserializeTextOverlay(arr.getJSONObject(i)) } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize text overlay $i", e); null
                }
            }
        }

        private fun deserializeTextOverlay(json: JSONObject): TextOverlay? {
            val text = json.optString("text", "")
            if (text.isEmpty()) return null // TextOverlay requires non-empty text
            return TextOverlay(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                text = text,
                fontFamily = json.optString("fontFamily", "sans-serif"),
                fontSize = json.optDouble("fontSize", 48.0).toFloat(),
                color = json.optLong("color", 0xFFFFFFFF),
                backgroundColor = json.optLong("backgroundColor", 0x00000000),
                strokeColor = json.optLong("strokeColor", 0xFF000000),
                strokeWidth = json.optDouble("strokeWidth", 0.0).toFloat(),
                bold = json.optBoolean("bold", false),
                italic = json.optBoolean("italic", false),
                alignment = safeValueOf(json.optString("alignment", "CENTER"), TextAlignment.CENTER),
                positionX = json.optDouble("positionX", 0.5).toFloat(),
                positionY = json.optDouble("positionY", 0.5).toFloat(),
                startTimeMs = json.optLong("startTimeMs", 0L),
                endTimeMs = json.optLong("endTimeMs", 3000L),
                animationIn = safeValueOf(json.optString("animationIn", "NONE"), TextAnimation.NONE),
                animationOut = safeValueOf(json.optString("animationOut", "NONE"), TextAnimation.NONE),
                rotation = json.optDouble("rotation", 0.0).toFloat(),
                scaleX = json.optDouble("scaleX", 1.0).toFloat(),
                scaleY = json.optDouble("scaleY", 1.0).toFloat(),
                shadowColor = json.optLong("shadowColor", 0x80000000),
                shadowOffsetX = json.optDouble("shadowOffsetX", 0.0).toFloat(),
                shadowOffsetY = json.optDouble("shadowOffsetY", 0.0).toFloat(),
                shadowBlur = json.optDouble("shadowBlur", 0.0).toFloat(),
                glowColor = json.optLong("glowColor", 0x00000000),
                glowRadius = json.optDouble("glowRadius", 0.0).toFloat(),
                letterSpacing = json.optDouble("letterSpacing", 0.0).toFloat(),
                lineHeight = json.optDouble("lineHeight", 1.2).toFloat(),
                textPath = json.optJSONObject("textPath")?.let { tp ->
                    val tpPointsArr = tp.optJSONArray("points") ?: JSONArray()
                    TextPath(
                        type = safeValueOf(tp.optString("type", "STRAIGHT"), TextPathType.STRAIGHT),
                        points = (0 until tpPointsArr.length()).map { i ->
                            val pt = tpPointsArr.getJSONObject(i)
                            MaskPoint(
                                x = pt.optDouble("x", 0.0).toFloat(),
                                y = pt.optDouble("y", 0.0).toFloat(),
                                handleInX = pt.optDouble("handleInX", 0.0).toFloat(),
                                handleInY = pt.optDouble("handleInY", 0.0).toFloat(),
                                handleOutX = pt.optDouble("handleOutX", 0.0).toFloat(),
                                handleOutY = pt.optDouble("handleOutY", 0.0).toFloat()
                            )
                        },
                        progress = tp.optDouble("progress", 1.0).toFloat()
                    )
                },
                templateId = json.optString("templateId", "").takeIf { it.isNotEmpty() },
                keyframes = json.optJSONArray("keyframes")?.let { kfArr ->
                    (0 until kfArr.length()).mapNotNull { i ->
                        try { deserializeKeyframe(kfArr.getJSONObject(i)) } catch (e: Exception) {
                            Log.w(TAG, "Failed to deserialize text overlay keyframe $i", e); null
                        }
                    }.distinctBy { Pair(it.timeOffsetMs, it.property) }
                } ?: emptyList()
            )
        }
    }
}
