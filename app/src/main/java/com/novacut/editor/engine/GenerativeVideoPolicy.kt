package com.novacut.editor.engine

/**
 * Product policy for future generative-video effects.
 *
 * Wan, HunyuanVideo, and VideoCrafter-class models are not mobile model
 * downloads. They must be exposed as optional cloud effects with explicit
 * consent, destination disclosure, payload-size disclosure, and retention copy.
 */
object GenerativeVideoPolicy {

    enum class ExecutionMode {
        CLOUD_OPTIONAL,
        ON_DEVICE_BUNDLED
    }

    enum class Provider(
        val id: String,
        val displayName: String,
        val sourceUrl: String,
        val executionMode: ExecutionMode,
        val requiresExplicitConsent: Boolean,
        val requiresDestinationDisclosure: Boolean,
        val requiresPayloadDisclosure: Boolean,
        val requiresRetentionDisclosure: Boolean
    ) {
        WAN_2_2(
            id = "wan_2_2",
            displayName = "Wan 2.2",
            sourceUrl = "https://github.com/Wan-Video/Wan2.2",
            executionMode = ExecutionMode.CLOUD_OPTIONAL,
            requiresExplicitConsent = true,
            requiresDestinationDisclosure = true,
            requiresPayloadDisclosure = true,
            requiresRetentionDisclosure = true
        ),
        HUNYUAN_VIDEO(
            id = "hunyuan_video",
            displayName = "HunyuanVideo",
            sourceUrl = "https://github.com/Tencent-Hunyuan/HunyuanVideo",
            executionMode = ExecutionMode.CLOUD_OPTIONAL,
            requiresExplicitConsent = true,
            requiresDestinationDisclosure = true,
            requiresPayloadDisclosure = true,
            requiresRetentionDisclosure = true
        ),
        VIDEOCRAFTER2(
            id = "videocrafter2",
            displayName = "VideoCrafter2",
            sourceUrl = "https://github.com/AILab-CVC/VideoCrafter",
            executionMode = ExecutionMode.CLOUD_OPTIONAL,
            requiresExplicitConsent = true,
            requiresDestinationDisclosure = true,
            requiresPayloadDisclosure = true,
            requiresRetentionDisclosure = true
        )
    }

    data class CloudDisclosure(
        val provider: Provider,
        val destinationLabel: String,
        val estimatedUploadBytes: Long,
        val retentionSummary: String,
        val userConsented: Boolean
    ) {
        init {
            require(destinationLabel.isNotBlank()) { "Cloud destination must be disclosed" }
            require(estimatedUploadBytes >= 0L) { "Estimated upload bytes must be non-negative" }
            require(retentionSummary.isNotBlank()) { "Cloud retention summary must be disclosed" }
        }
    }

    fun bundledOnDeviceAllowed(provider: Provider): Boolean =
        provider.executionMode == ExecutionMode.ON_DEVICE_BUNDLED

    fun cloudCalloutRequired(provider: Provider): Boolean =
        provider.executionMode == ExecutionMode.CLOUD_OPTIONAL &&
            provider.requiresExplicitConsent &&
            provider.requiresDestinationDisclosure &&
            provider.requiresPayloadDisclosure &&
            provider.requiresRetentionDisclosure

    fun canStartCloudEffect(disclosure: CloudDisclosure): Boolean =
        cloudCalloutRequired(disclosure.provider) && disclosure.userConsented
}
