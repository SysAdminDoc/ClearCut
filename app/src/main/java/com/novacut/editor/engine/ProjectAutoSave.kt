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

    fun saveNow(projectId: String, state: AutoSaveState) {
        runBlocking {
            try {
                saveState(projectId, state)
            } catch (e: Exception) {
                Log.e(TAG, "Manual save failed for $projectId", e)
            }
        }
    }

    fun hasRecoveryData(projectId: String): Boolean {
        return getAutoSaveFile(projectId).exists()
    }

    suspend fun loadRecoveryData(projectId: String): AutoSaveState? = withContext(Dispatchers.IO) {
        // Clean up stale temp file if present
        val tempFile = File(autoSaveDir, "${projectId}.tmp")
        if (tempFile.exists()) {
            Log.w(TAG, "Cleaning up stale temp file for $projectId")
            tempFile.delete()
        }
        val file = getAutoSaveFile(projectId)
        if (!file.exists()) return@withContext null
        try {
            AutoSaveState.deserialize(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recovery data for $projectId", e)
            null
        }
    }

    fun clearRecoveryData(projectId: String) {
        getAutoSaveFile(projectId).delete()
    }

    suspend fun copyAutoSave(fromProjectId: String, toProjectId: String) {
        val fromFile = getAutoSaveFile(fromProjectId)
        if (!fromFile.exists()) return
        try {
            val json = saveMutex.withLock { JSONObject(fromFile.readText()) }
            json.put("projectId", toProjectId)
            json.put("timestamp", System.currentTimeMillis())
            val toFile = getAutoSaveFile(toProjectId)
            val tempFile = File(autoSaveDir, "${toProjectId}.tmp")
            try {
                tempFile.writeText(json.toString(2))
                if (!tempFile.renameTo(toFile)) {
                    tempFile.copyTo(toFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy auto-save from $fromProjectId to $toProjectId", e)
        }
    }

    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun release() {
        autoSaveJob?.cancel()
        runBlocking {
            saveMutex.withLock { /* wait for any in-flight save to finish */ }
        }
        scope.cancel()
    }

    private suspend fun saveState(projectId: String, state: AutoSaveState) = saveMutex.withLock {
        val file = getAutoSaveFile(projectId)
        val tempFile = File(autoSaveDir, "${projectId}.tmp")
        try {
            tempFile.writeText(state.serialize())
            // renameTo can fail on some filesystems — fallback to copy+delete
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            // Clean up temp file on failure to prevent stale data
            tempFile.delete()
            throw e
        }
    }

    private fun getAutoSaveFile(projectId: String): File {
        return File(autoSaveDir, "${projectId}.json")
    }
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
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList()
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
            val tracks = deserializeTracks(json.optJSONArray("tracks") ?: JSONArray())
            // Clean up orphaned linkedClipId references
            val allClipIds = tracks.flatMap { it.clips.map { c -> c.id } }.toSet()
            val cleanedTracks = tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.linkedClipId != null && clip.linkedClipId !in allClipIds) {
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
                    ImageOverlay(
                        id = io.optString("id", java.util.UUID.randomUUID().toString()),
                        sourceUri = android.net.Uri.parse(srcUri),
                        startTimeMs = io.optLong("startTimeMs", 0L),
                        endTimeMs = io.optLong("endTimeMs", 5000L),
                        positionX = io.optDouble("positionX", 0.0).toFloat(),
                        positionY = io.optDouble("positionY", 0.0).toFloat(),
                        scale = io.optDouble("scale", 0.3).toFloat().coerceAtLeast(0.01f),
                        rotation = io.optDouble("rotation", 0.0).toFloat(),
                        opacity = io.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
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
                    val points = (0 until pointsArr.length()).map { j ->
                        val pt = pointsArr.getJSONObject(j)
                        pt.optDouble("x", 0.0).toFloat() to pt.optDouble("y", 0.0).toFloat()
                    }
                    com.novacut.editor.model.DrawingPath(
                        points = points,
                        color = dp.optLong("color", 0xFFCBA6F7L),
                        strokeWidth = dp.optDouble("strokeWidth", 4.0).toFloat()
                    )
                } catch (e: Exception) { Log.w(TAG, "Failed to deserialize drawing path $i", e); null }
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
                drawingPaths = drawingPaths
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

        private fun serializeClip(clip: Clip): JSONObject {
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
                        clip.compoundClips.forEach { put(serializeClip(it)) }
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
            val sourceUri = json.optString("sourceUri", "")
            if (sourceUri.isEmpty()) return null
            val sourceDurationMs = json.optLong("sourceDurationMs", 0L)
            val rawTrimEnd = json.optLong("trimEndMs", sourceDurationMs)
            // Coerce trim values to satisfy model invariants (trimEndMs <= sourceDurationMs)
            val trimStartMs = json.optLong("trimStartMs", 0L).coerceIn(0L, sourceDurationMs)
            val trimEndMs = rawTrimEnd.coerceIn(trimStartMs, sourceDurationMs.coerceAtLeast(trimStartMs))
            return Clip(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                sourceUri = Uri.parse(sourceUri),
                sourceDurationMs = sourceDurationMs.coerceAtLeast(trimEndMs),
                timelineStartMs = json.optLong("timelineStartMs", 0L),
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
                fadeInMs = json.optLong("fadeInMs", 0L),
                fadeOutMs = json.optLong("fadeOutMs", 0L),
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
                proxyUri = json.optString("proxyUri", "").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
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

        private fun deserializeColorGrade(json: JSONObject): ColorGrade {
            return ColorGrade(
                enabled = json.optBoolean("enabled", true),
                liftR = json.optDouble("liftR", 0.0).toFloat(), liftG = json.optDouble("liftG", 0.0).toFloat(), liftB = json.optDouble("liftB", 0.0).toFloat(),
                gammaR = json.optDouble("gammaR", 1.0).toFloat(), gammaG = json.optDouble("gammaG", 1.0).toFloat(), gammaB = json.optDouble("gammaB", 1.0).toFloat(),
                gainR = json.optDouble("gainR", 1.0).toFloat(), gainG = json.optDouble("gainG", 1.0).toFloat(), gainB = json.optDouble("gainB", 1.0).toFloat(),
                offsetR = json.optDouble("offsetR", 0.0).toFloat(), offsetG = json.optDouble("offsetG", 0.0).toFloat(), offsetB = json.optDouble("offsetB", 0.0).toFloat(),
                lutPath = json.optString("lutPath", "").takeIf { it.isNotEmpty() },
                lutIntensity = json.optDouble("lutIntensity", 1.0).toFloat(),
                hslQualifier = json.optJSONObject("hsl")?.let { hsl ->
                    HslQualifier(
                        hueCenter = hsl.optDouble("hueCenter", 0.0).toFloat(),
                        hueWidth = hsl.optDouble("hueWidth", 30.0).toFloat(),
                        satMin = hsl.optDouble("satMin", 0.0).toFloat(),
                        satMax = hsl.optDouble("satMax", 1.0).toFloat(),
                        lumMin = hsl.optDouble("lumMin", 0.0).toFloat(),
                        lumMax = hsl.optDouble("lumMax", 1.0).toFloat(),
                        softness = hsl.optDouble("softness", 0.1).toFloat(),
                        adjustHue = hsl.optDouble("adjustHue", 0.0).toFloat(),
                        adjustSat = hsl.optDouble("adjustSat", 0.0).toFloat(),
                        adjustLum = hsl.optDouble("adjustLum", 0.0).toFloat()
                    )
                }
            )
        }

        private fun deserializeSpeedCurve(json: JSONObject): SpeedCurve {
            val pointsArr = json.optJSONArray("points") ?: JSONArray()
            val points = (0 until pointsArr.length()).map { i ->
                val pt = pointsArr.getJSONObject(i)
                SpeedPoint(
                    position = pt.optDouble("position", 0.0).toFloat(),
                    speed = pt.optDouble("speed", 1.0).toFloat(),
                    handleInY = pt.optDouble("handleInY", pt.optDouble("speed", 1.0)).toFloat(),
                    handleOutY = pt.optDouble("handleOutY", pt.optDouble("speed", 1.0)).toFloat()
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
            return Caption(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                startTimeMs = json.optLong("startTimeMs", 0L),
                endTimeMs = json.optLong("endTimeMs", 0L),
                style = CaptionStyle(
                    type = safeValueOf(json.optString("styleType", "SUBTITLE_BAR"), CaptionStyleType.SUBTITLE_BAR),
                    fontSize = json.optDouble("fontSize", 36.0).toFloat(),
                    positionY = json.optDouble("positionY", 0.85).toFloat(),
                    fontFamily = json.optString("fontFamily", "sans-serif-medium"),
                    color = json.optLong("color", 0xFFFFFFFFL),
                    backgroundColor = json.optLong("backgroundColor", 0xCC000000L),
                    highlightColor = json.optLong("highlightColor", 0xFFFFD700L),
                    outline = json.optBoolean("outline", true),
                    shadow = json.optBoolean("shadow", false)
                ),
                words = (0 until wordsArr.length()).map { i ->
                    val w = wordsArr.getJSONObject(i)
                    CaptionWord(
                        text = w.optString("text", ""),
                        startTimeMs = w.optLong("startTimeMs", 0L),
                        endTimeMs = w.optLong("endTimeMs", 0L),
                        confidence = w.optDouble("confidence", 1.0).toFloat()
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
