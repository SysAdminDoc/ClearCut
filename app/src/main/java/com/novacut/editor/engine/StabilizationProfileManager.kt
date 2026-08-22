package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.BuildConfig
import com.novacut.editor.model.StabilizationLensProfile
import com.novacut.editor.model.StabilizationMotionProfile
import com.novacut.editor.model.StabilizationProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class StabilizationProfileFailure {
    NONE,
    UNREADABLE,
    INVALID_JSON,
    INVALID_SCHEMA,
    WRONG_KIND,
    INCOMPATIBLE_VERSION,
    MISSING_REQUIRED_METADATA,
    UNSAFE_CONTENT,
    MISSING_CONTENT_HASH,
    HASH_MISMATCH,
    UNKNOWN_REQUIRED_CAPABILITY,
    INCOMPATIBLE_APP_VERSION,
}

data class StabilizationProfileValidation(
    val profile: StabilizationProfile? = null,
    val failure: StabilizationProfileFailure = StabilizationProfileFailure.NONE,
    val schemaVersion: Int = DeclarativePackContract.LEGACY_SCHEMA_VERSION,
    val contentHash: String? = null,
    val provenanceSource: String? = null,
    val warnings: List<String> = emptyList(),
    val reasonCode: String = failure.name,
) {
    val isValid: Boolean get() = profile != null && failure == StabilizationProfileFailure.NONE
}

/** Local-first storage and validation for reusable, non-executable profiles. */
@Singleton
class StabilizationProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val FILE_EXTENSION = "ncstabilization"
        const val MAX_PROFILE_BYTES = 1_000_000L
        private const val PROFILE_VERSION = 1
        private const val ACTIVE_FILE_NAME = "active.json"
        private const val TAG = "StabilizationProfileManager"

        val DEFAULT_PROFILE = StabilizationProfile()
    }

    private val profileDir = File(context.filesDir, "stabilization_profiles").also { it.mkdirs() }
    private val activeFile = File(profileDir, ACTIVE_FILE_NAME)

    @Volatile
    private var cachedProfile: StabilizationProfile? = readActiveProfile()

    fun activeProfile(): StabilizationProfile? = cachedProfile

    fun configFor(profile: StabilizationProfile): StabilizationEngine.StabilizationConfig =
        StabilizationEngine.StabilizationConfig(
            smoothingStrength = profile.motion.smoothingStrength,
            cropPercentage = profile.motion.cropPercentage,
            algorithm = runCatching {
                StabilizationEngine.StabilizationConfig.Algorithm.valueOf(profile.motion.algorithm)
            }.getOrDefault(StabilizationEngine.StabilizationConfig.Algorithm.LK_OPTICAL_FLOW),
            maxFeatures = profile.motion.maxFeatures,
            useAffine = profile.motion.useAffine,
            analysisIntervalMs = profile.motion.analysisIntervalMs,
            smoothingWindow = profile.motion.smoothingWindow,
        )

    fun lensFor(profile: StabilizationProfile): StabilizationEngine.LensProfile =
        StabilizationEngine.LensProfile(
            name = profile.lens.name,
            focalLengthMm = profile.lens.focalLengthMm,
            distortionK1 = profile.lens.distortionK1,
            distortionK2 = profile.lens.distortionK2,
        )

    fun exportProfileFile(profile: StabilizationProfile = activeProfile() ?: DEFAULT_PROFILE): File? {
        return runCatching {
            val safeName = sanitizeFileName(
                profile.name,
                fallback = "stabilization-profile",
                maxLength = 64,
            )
            val file = File(profileDir, "$safeName.$FILE_EXTENSION")
            writeUtf8TextAtomically(file, encode(profile).toString(2))
            file
        }.onFailure { AppLog.w(TAG, "Could not export stabilization profile", it) }.getOrNull()
    }

    suspend fun validateUri(uri: Uri): StabilizationProfileValidation = withContext(Dispatchers.IO) {
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readUtf8WithByteLimit(it, MAX_PROFILE_BYTES) }
        }.onFailure { AppLog.w(TAG, "Could not read stabilization profile", it) }.getOrNull()
            ?: return@withContext StabilizationProfileValidation(failure = StabilizationProfileFailure.UNREADABLE)
        validateJson(json)
    }

    suspend fun importFromUri(uri: Uri): StabilizationProfileValidation = withContext(Dispatchers.IO) {
        install(validateUri(uri))
    }

    fun validateJson(json: String, supportedAppVersion: String = BuildConfig.VERSION_NAME): StabilizationProfileValidation {
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            AppLog.w(TAG, "Stabilization profile is not valid JSON", error)
            return StabilizationProfileValidation(failure = StabilizationProfileFailure.INVALID_JSON)
        }
        val envelope = DeclarativePackContract.inspect(
            root = root,
            expectedKind = DeclarativePackKind.STABILIZATION_PROFILE,
            supportedAppVersion = supportedAppVersion,
        )
        val contractFailure = when (envelope.issue) {
            DeclarativePackIssue.NONE -> null
            DeclarativePackIssue.INVALID_SCHEMA -> StabilizationProfileFailure.INVALID_SCHEMA
            DeclarativePackIssue.FUTURE_SCHEMA -> StabilizationProfileFailure.INCOMPATIBLE_VERSION
            DeclarativePackIssue.WRONG_KIND -> StabilizationProfileFailure.WRONG_KIND
            DeclarativePackIssue.MISSING_MANIFEST_FIELDS -> StabilizationProfileFailure.MISSING_REQUIRED_METADATA
            DeclarativePackIssue.UNKNOWN_REQUIRED_CAPABILITY -> StabilizationProfileFailure.UNKNOWN_REQUIRED_CAPABILITY
            DeclarativePackIssue.INCOMPATIBLE_APP_VERSION -> StabilizationProfileFailure.INCOMPATIBLE_APP_VERSION
            DeclarativePackIssue.MISSING_CONTENT_HASH -> StabilizationProfileFailure.MISSING_CONTENT_HASH
            DeclarativePackIssue.HASH_MISMATCH -> StabilizationProfileFailure.HASH_MISMATCH
            DeclarativePackIssue.EXECUTABLE_CONTENT -> StabilizationProfileFailure.UNSAFE_CONTENT
        }
        if (contractFailure != null) {
            return StabilizationProfileValidation(
                failure = contractFailure,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash,
                provenanceSource = envelope.source,
                warnings = envelope.warnings,
                reasonCode = envelope.reasonCode,
            )
        }

        val profile = runCatching { parse(root) }.getOrElse { error ->
            AppLog.w(TAG, "Stabilization profile metadata is incomplete", error)
            return StabilizationProfileValidation(
                failure = StabilizationProfileFailure.MISSING_REQUIRED_METADATA,
                schemaVersion = envelope.schemaVersion,
                contentHash = envelope.contentHash,
                provenanceSource = envelope.source,
                warnings = listOf("Profile metadata is incomplete or outside the supported bounds."),
                reasonCode = "PROFILE_MISSING_REQUIRED_METADATA",
            )
        }
        val warnings = buildList {
            if (profile.lens.focalLengthMm == null) {
                add("Focal length is not specified; the identity focal-length fallback will be used.")
            }
            val lensRoot = root.optJSONObject("lens")
            if (lensRoot?.has("distortionK1") != true || lensRoot.has("distortionK2") != true) {
                add("Lens distortion coefficients are not specified; the identity lens fallback will be used.")
            }
            if (profile.motion.analysisIntervalMs == null) {
                add("Analysis interval is not specified; the device's bounded offline policy will choose it.")
            }
        }
        return StabilizationProfileValidation(
            profile = profile,
            schemaVersion = envelope.schemaVersion,
            contentHash = envelope.contentHash,
            provenanceSource = envelope.source,
            warnings = warnings,
            reasonCode = envelope.reasonCode,
        )
    }

    fun install(validation: StabilizationProfileValidation): StabilizationProfileValidation {
        val profile = validation.profile ?: return validation
        return try {
            writeUtf8TextAtomically(activeFile, encode(profile).toString(2))
            cachedProfile = profile
            validation
        } catch (error: Exception) {
            AppLog.w(TAG, "Could not install stabilization profile", error)
            validation.copy(
                profile = null,
                failure = StabilizationProfileFailure.UNREADABLE,
                warnings = validation.warnings + "Profile could not be saved; the previous active profile remains in use.",
                reasonCode = "PROFILE_INSTALL_FAILED",
            )
        }
    }

    fun encode(profile: StabilizationProfile): JSONObject = JSONObject().apply {
        put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
        put("packType", DeclarativePackKind.STABILIZATION_PROFILE.wireName)
        put("minAppVersion", BuildConfig.VERSION_NAME)
        put("requiredCapabilities", JSONArray().put(DeclarativePackContract.STABILIZATION_PROFILE_CAPABILITY))
        put("provenance", JSONObject().put("source", "ClearCut offline stabilization profile"))
        put("profileVersion", PROFILE_VERSION)
        put("profileId", profile.id)
        put("name", profile.name)
        put("lens", JSONObject().apply {
            put("name", profile.lens.name)
            profile.lens.focalLengthMm?.let { put("focalLengthMm", it.toDouble()) }
            put("distortionK1", profile.lens.distortionK1.toDouble())
            put("distortionK2", profile.lens.distortionK2.toDouble())
        })
        put("motion", JSONObject().apply {
            put("smoothingStrength", profile.motion.smoothingStrength.toDouble())
            put("cropPercentage", profile.motion.cropPercentage.toDouble())
            put("algorithm", profile.motion.algorithm)
            put("maxFeatures", profile.motion.maxFeatures)
            put("useAffine", profile.motion.useAffine)
            profile.motion.analysisIntervalMs?.let { put("analysisIntervalMs", it) }
            put("smoothingWindow", profile.motion.smoothingWindow)
        })
        put("cropScale", profile.cropScale.toDouble())
        put("syncOffsetMs", profile.syncOffsetMs)
        put("contentHash", DeclarativePackContract.contentHash(this))
    }

    private fun parse(root: JSONObject): StabilizationProfile {
        require(root.optInt("profileVersion", -1) == PROFILE_VERSION)
        val id = root.optString("profileId", "").trim().takeIf { it.isNotEmpty() } ?: error("profileId")
        val name = root.optString("name", "").trim().takeIf { it.isNotEmpty() } ?: error("name")
        require(root.has("cropScale") && root.has("syncOffsetMs"))
        val lensRoot = root.optJSONObject("lens") ?: error("lens")
        val motionRoot = root.optJSONObject("motion") ?: error("motion")
        require(lensRoot.has("name"))
        require(
            motionRoot.has("smoothingStrength") &&
                motionRoot.has("cropPercentage") &&
                motionRoot.has("algorithm") &&
                motionRoot.has("maxFeatures") &&
                motionRoot.has("useAffine") &&
                motionRoot.has("smoothingWindow")
        )
        val lensName = lensRoot.optString("name", "").trim().takeIf { it.isNotEmpty() } ?: error("lens.name")
        val focalLength = if (lensRoot.has("focalLengthMm")) {
            lensRoot.optDouble("focalLengthMm", Double.NaN).toFloat().takeIf { it.isFinite() && it > 0f }
                ?: error("lens.focalLengthMm")
        } else {
            null
        }
        val algorithm = motionRoot.optString("algorithm", "").trim()
        require(algorithm in StabilizationEngine.StabilizationConfig.Algorithm.entries.map { it.name })
        val analysisIntervalMs = if (motionRoot.has("analysisIntervalMs")) {
            motionRoot.optLong("analysisIntervalMs", Long.MIN_VALUE).also {
                require(it in 1L..10_000L)
            }
        } else {
            null
        }
        val motion = StabilizationMotionProfile(
            smoothingStrength = motionRoot.optDouble("smoothingStrength", Double.NaN).toFloat(),
            cropPercentage = motionRoot.optDouble("cropPercentage", Double.NaN).toFloat(),
            algorithm = algorithm,
            maxFeatures = motionRoot.optInt("maxFeatures", -1),
            useAffine = motionRoot.optBoolean("useAffine", false),
            analysisIntervalMs = analysisIntervalMs,
            smoothingWindow = motionRoot.optInt("smoothingWindow", -1),
        )
        val distortionK1 = lensRoot.optDouble("distortionK1", 0.0).toFloat()
        val distortionK2 = lensRoot.optDouble("distortionK2", 0.0).toFloat()
        require(distortionK1.isFinite() && distortionK1 in -2f..2f)
        require(distortionK2.isFinite() && distortionK2 in -2f..2f)
        return StabilizationProfile(
            id = id,
            name = name,
            lens = StabilizationLensProfile(
                name = lensName,
                focalLengthMm = focalLength,
                distortionK1 = distortionK1,
                distortionK2 = distortionK2,
            ),
            motion = motion,
            cropScale = root.optDouble("cropScale", Double.NaN).toFloat(),
            syncOffsetMs = root.optLong("syncOffsetMs", Long.MIN_VALUE),
        )
    }

    private fun readActiveProfile(): StabilizationProfile? {
        if (!activeFile.isFile || activeFile.length() > MAX_PROFILE_BYTES) return null
        return runCatching { validateJson(activeFile.readText(Charsets.UTF_8)).profile }
            .onFailure { AppLog.w(TAG, "Active stabilization profile was ignored", it) }
            .getOrNull()
    }
}
