package com.novacut.editor.engine

/**
 * Registry of model + dependency requirements per AI tool, so the
 * `AiModelRequirementSheet` (Highest-Value #10) renders one consistent
 * pre-flight bottom sheet across every model-gated tile in `AiToolsPanel`.
 *
 * Today each tool tile shows a "Model gated" status chip; users have to
 * navigate to Settings → AI Models to learn what they need. The sheet
 * surfaced by this registry tells them in-context: model display name,
 * download size, license, whether Wi-Fi-only applies, and what happens when
 * they accept.
 *
 * Pure data — no Compose, no Hilt, no Android — so the contract is locked by
 * JVM unit tests and the UI layer can adopt it without coupling to engine
 * internals. The composable that consumes [requirementFor] will live under
 * `ui/editor/` in a follow-up commit.
 */
object AiToolRequirements {

    enum class Tool(val toolId: String) {
        /** Whisper / Sherpa ASR → caption pipeline. */
        AUTO_CAPTIONS("auto_captions"),

        /** MediaPipe selfie segmenter / future RVM. */
        REMOVE_BACKGROUND("remove_bg"),

        /** AI Background — RVM full matting. */
        AI_BACKGROUND("ai_background"),

        /** AI Stabilize — OpenCV optical flow. */
        AI_STABILIZE("ai_stabilize"),

        /** Style Transfer — AnimeGAN / Fast NST. */
        AI_STYLE("ai_style"),

        /** Real-ESRGAN upscaler. */
        AI_UPSCALE("ai_upscale"),

        /** RIFE frame interpolation. */
        FRAME_INTERP("frame_interp"),

        /** LaMa inpainting (Object Remove). */
        OBJECT_REMOVE("object_remove"),

        /** SAM 2.1 / MobileSAM tap-to-segment. */
        TAP_SEGMENT("tap_segment"),

        /** DeepFilterNet noise reduction. */
        REDUCE_NOISE("reduce_noise"),

        /** Demucs htdemucs stem separation. */
        STEM_SEPARATION("stem_separation"),

        /** Cloud-only generative video (Wan / HunyuanVideo). */
        GENERATIVE_VIDEO("generative_video"),

        /** Lip-sync (MuseTalk / Wav2Lip cloud). */
        LIP_SYNC("lip_sync"),

        /** Sherpa-ONNX XTTS v2 voice clone. */
        VOICE_CLONE("voice_clone"),

        /** Piper / Sherpa TTS. */
        TTS_OFFLINE("tts_offline"),

        /** Caption translation (MADLAD / Bergamot). */
        CAPTION_TRANSLATE("caption_translate"),
    }

    /**
     * Status of the tool's dependencies on the running device. Drives the
     * sheet's primary CTA — Download / Cancel / Ready to use / Cloud upload.
     */
    enum class Availability {
        /** Dep + model both ready on device. Tool can run immediately. */
        READY,

        /** Dep is in tree (or built-in); model is downloadable. */
        MODEL_DOWNLOAD_REQUIRED,

        /** Dep is missing entirely; show "Not yet available in this build". */
        DEPENDENCY_MISSING,

        /** Tool is cloud-only and the user must explicitly opt in to a network call. */
        CLOUD_OPT_IN,
    }

    /**
     * One pre-flight requirement record. The sheet renders these fields top
     * to bottom; any change to the data class is a UX change.
     *
     * @property modelDisplayName the model the user is about to fetch /
     *   invoke. Suitable for a UI chip.
     * @property estimatedBytes wall-clock disk delta on first download.
     *   Used as the "Download size: 145 MB" body line.
     * @property license short license string (e.g. "Apache-2.0", "MIT",
     *   "ProprietaryAPI"). The sheet renders this as a small caption row;
     *   the user can long-press to open the full license doc.
     * @property sourceUrl optional public source URL for the model. Used in
     *   the diagnostics ZIP, not shown on the sheet directly.
     * @property runtimeLocation [Runtime] hint so the sheet can render
     *   "Runs on this device" vs "Sent to our cloud" with the right copy.
     * @property requiresOptInConsent additional consent gate for cloud or
     *   voice-clone style tools. The sheet always shows the standard
     *   download confirmation; this flag adds a second checkbox.
     */
    data class ToolRequirement(
        val tool: Tool,
        val modelDisplayName: String,
        val estimatedBytes: Long,
        val license: String,
        val sourceUrl: String?,
        val runtimeLocation: Runtime,
        val availability: Availability,
        val requiresOptInConsent: Boolean = false,
    ) {
        val isDownloadable: Boolean
            get() = availability == Availability.MODEL_DOWNLOAD_REQUIRED

        val sizeMb: Int get() = (estimatedBytes / 1_048_576L).toInt()
    }

    enum class Runtime {
        ON_DEVICE,
        CLOUD,
    }

    /**
     * Lookup the requirement for the given tool ID (matches the string IDs
     * used in `AiToolsPanel`'s tile grid and `EditorScreen.onAction` dispatch).
     *
     * Returns `null` for unknown tool IDs — the caller can fall back to the
     * generic "Tap to use" tile without showing the sheet.
     */
    fun requirementFor(toolId: String): ToolRequirement? {
        val tool = Tool.entries.firstOrNull { it.toolId == toolId } ?: return null
        return DEFAULTS[tool]
    }

    /**
     * Default requirement table. Centralised so a single edit updates the
     * pre-flight sheet for every consumer. Sizes are conservative upper
     * bounds — the actual download is whatever `ModelDownloadManager` reports.
     */
    private val DEFAULTS: Map<Tool, ToolRequirement> = mapOf(
        Tool.AUTO_CAPTIONS to ToolRequirement(
            tool = Tool.AUTO_CAPTIONS,
            modelDisplayName = "Whisper tiny.en (ONNX)",
            estimatedBytes = 152_000_000L,
            license = "Apache-2.0 (model: MIT)",
            sourceUrl = "https://huggingface.co/onnx-community/whisper-tiny.en",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.REMOVE_BACKGROUND to ToolRequirement(
            tool = Tool.REMOVE_BACKGROUND,
            modelDisplayName = "MediaPipe Selfie Segmenter",
            estimatedBytes = 256_000L,
            license = "Apache-2.0",
            sourceUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.AI_BACKGROUND to ToolRequirement(
            tool = Tool.AI_BACKGROUND,
            modelDisplayName = "Robust Video Matting (ONNX)",
            estimatedBytes = 15_000_000L,
            license = "GPL-3.0 (RVM upstream)",
            sourceUrl = "https://github.com/PeterL1n/RobustVideoMatting",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
        Tool.AI_STABILIZE to ToolRequirement(
            tool = Tool.AI_STABILIZE,
            modelDisplayName = "OpenCV Android (arm64)",
            estimatedBytes = 40_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://opencv.org",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
        Tool.AI_STYLE to ToolRequirement(
            tool = Tool.AI_STYLE,
            modelDisplayName = "AnimeGANv2 / Fast NST (per style)",
            estimatedBytes = 9_000_000L,
            license = "Apache-2.0 / MIT (per variant)",
            sourceUrl = "https://github.com/TachibanaYoshino/AnimeGANv2",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.AI_UPSCALE to ToolRequirement(
            tool = Tool.AI_UPSCALE,
            modelDisplayName = "Real-ESRGAN x4plus (ONNX)",
            estimatedBytes = 17_000_000L,
            license = "BSD-3-Clause",
            sourceUrl = "https://github.com/xinntao/Real-ESRGAN",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.FRAME_INTERP to ToolRequirement(
            tool = Tool.FRAME_INTERP,
            modelDisplayName = "RIFE v4.6 (NCNN + Vulkan)",
            estimatedBytes = 10_000_000L,
            license = "MIT (model: research)",
            sourceUrl = "https://github.com/nihui/rife-ncnn-vulkan",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
        Tool.OBJECT_REMOVE to ToolRequirement(
            tool = Tool.OBJECT_REMOVE,
            modelDisplayName = "LaMa-Dilated (Qualcomm AI Hub export)",
            estimatedBytes = 183_000_000L,
            license = "Apache-2.0 (model: see NOTICE)",
            sourceUrl = "https://huggingface.co/qualcomm/LaMa-Dilated",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.TAP_SEGMENT to ToolRequirement(
            tool = Tool.TAP_SEGMENT,
            modelDisplayName = "SAM 2.1 Hiera Tiny (ONNX) / MobileSAM fallback",
            estimatedBytes = 160_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/onnx-community/sam2.1-hiera-tiny-ONNX",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.MODEL_DOWNLOAD_REQUIRED,
        ),
        Tool.REDUCE_NOISE to ToolRequirement(
            tool = Tool.REDUCE_NOISE,
            modelDisplayName = "DeepFilterNet 3 (native AAR)",
            estimatedBytes = 27_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://github.com/Rikorose/DeepFilterNet",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.READY,
        ),
        Tool.STEM_SEPARATION to ToolRequirement(
            tool = Tool.STEM_SEPARATION,
            modelDisplayName = "Demucs htdemucs (ONNX quantised)",
            estimatedBytes = 80_000_000L,
            license = "MIT (model: research)",
            sourceUrl = "https://github.com/facebookresearch/demucs",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
        Tool.GENERATIVE_VIDEO to ToolRequirement(
            tool = Tool.GENERATIVE_VIDEO,
            modelDisplayName = "Wan 2.2 / HunyuanVideo (cloud)",
            estimatedBytes = 0L,
            license = "ProviderTerms",
            sourceUrl = "https://github.com/Wan-Video/Wan2.2",
            runtimeLocation = Runtime.CLOUD,
            availability = Availability.CLOUD_OPT_IN,
            requiresOptInConsent = true,
        ),
        Tool.LIP_SYNC to ToolRequirement(
            tool = Tool.LIP_SYNC,
            modelDisplayName = "MuseTalk / Wav2Lip (cloud)",
            estimatedBytes = 0L,
            license = "Mixed (per provider; some research-only)",
            sourceUrl = "https://github.com/TMElyralab/MuseTalk",
            runtimeLocation = Runtime.CLOUD,
            availability = Availability.CLOUD_OPT_IN,
            requiresOptInConsent = true,
        ),
        Tool.VOICE_CLONE to ToolRequirement(
            tool = Tool.VOICE_CLONE,
            modelDisplayName = "XTTS v2 (via Sherpa-ONNX)",
            estimatedBytes = 400_000_000L,
            license = "Coqui MPL-2.0",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
            requiresOptInConsent = true,
        ),
        Tool.TTS_OFFLINE to ToolRequirement(
            tool = Tool.TTS_OFFLINE,
            modelDisplayName = "Piper VITS (per voice)",
            estimatedBytes = 50_000_000L,
            license = "MIT (per voice)",
            sourceUrl = "https://github.com/rhasspy/piper",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
        Tool.CAPTION_TRANSLATE to ToolRequirement(
            tool = Tool.CAPTION_TRANSLATE,
            modelDisplayName = "MADLAD-400 3B (Q4) / Bergamot per-pair",
            estimatedBytes = 1_500_000_000L,
            license = "Apache-2.0 / MPL-2.0",
            sourceUrl = "https://huggingface.co/google/madlad400-3b-mt",
            runtimeLocation = Runtime.ON_DEVICE,
            availability = Availability.DEPENDENCY_MISSING,
        ),
    )
}
