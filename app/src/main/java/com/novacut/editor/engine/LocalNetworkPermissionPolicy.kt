package com.novacut.editor.engine

/**
 * Android local-network permission policy for future live streaming targets.
 *
 * Android 16 target-SDK 36 testing uses the temporary
 * `NEARBY_WIFI_DEVICES` restore path when `RESTRICT_LOCAL_NETWORK` is enabled.
 * Android 17 target-SDK 37+ enforcement uses `ACCESS_LOCAL_NETWORK`.
 */
object LocalNetworkPermissionPolicy {
    const val ANDROID_16_API = 36
    const val ANDROID_17_API = 37
    const val ANDROID_16_PERMISSION = "android.permission.NEARBY_WIFI_DEVICES"
    const val ANDROID_17_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    enum class GateState {
        NOT_REQUIRED,
        ALLOWED,
        NEEDS_PERMISSION,
        DENIED,
    }

    data class Decision(
        val scope: OutputStreamingEngine.LocalNetworkScope,
        val gateState: GateState,
        val permissionName: String? = null,
        val rationaleTitle: String? = null,
        val rationaleBody: String? = null,
        val deniedMessage: String? = null,
    ) {
        val requiresPermission: Boolean get() = permissionName != null
        val canAttemptConnection: Boolean
            get() = gateState == GateState.NOT_REQUIRED || gateState == GateState.ALLOWED
    }

    fun permissionFor(
        scope: OutputStreamingEngine.LocalNetworkScope,
        runtimeSdkInt: Int,
        targetSdkInt: Int,
    ): String? {
        if (!scope.requiresLocalNetworkPermission()) return null
        return when {
            runtimeSdkInt >= ANDROID_17_API && targetSdkInt >= ANDROID_17_API -> ANDROID_17_PERMISSION
            runtimeSdkInt >= ANDROID_16_API && targetSdkInt >= ANDROID_16_API -> ANDROID_16_PERMISSION
            else -> null
        }
    }

    fun evaluate(
        scope: OutputStreamingEngine.LocalNetworkScope,
        runtimeSdkInt: Int,
        targetSdkInt: Int,
        permissionGranted: Boolean,
        permissionDenied: Boolean = false,
    ): Decision {
        val permission = permissionFor(scope, runtimeSdkInt, targetSdkInt)
            ?: return Decision(scope = scope, gateState = GateState.NOT_REQUIRED)
        val gateState = when {
            permissionGranted -> GateState.ALLOWED
            permissionDenied -> GateState.DENIED
            else -> GateState.NEEDS_PERMISSION
        }
        return Decision(
            scope = scope,
            gateState = gateState,
            permissionName = permission,
            rationaleTitle = "Local network access needed",
            rationaleBody = "This destination is on your local network. " +
                "Allow Nearby devices so NovaCut can connect to LAN streaming targets " +
                "like OBS, RTMP/SRT boxes, RTSP cameras, or multicast receivers.",
            deniedMessage = "Local network permission is required for this LAN streaming destination. " +
                "Public internet destinations still work without it.",
        )
    }

    fun permissionFailureMessage(decision: Decision, throwable: Throwable? = null): String? {
        if (!decision.requiresPermission) return null
        if (decision.gateState == GateState.DENIED || decision.gateState == GateState.NEEDS_PERMISSION) {
            return decision.deniedMessage
        }
        val message = throwable?.message.orEmpty().lowercase()
        val localNetworkLikeFailure =
            "eperm" in message ||
                "permission" in message ||
                "timed out" in message ||
                "timeout" in message ||
                "network is unreachable" in message
        return if (localNetworkLikeFailure) {
            "This LAN streaming connection failed in a way that can indicate missing " +
                "local network access. Recheck the Nearby devices permission before retrying."
        } else {
            null
        }
    }

    fun diagnosticLine(decision: Decision): String {
        return buildString {
            append("scope=").append(decision.scope.name)
            append("; gate=").append(decision.gateState.name)
            append("; permission=").append(decision.permissionName ?: "none")
            append("; canAttemptConnection=").append(decision.canAttemptConnection)
        }
    }

    private fun OutputStreamingEngine.LocalNetworkScope.requiresLocalNetworkPermission(): Boolean =
        this == OutputStreamingEngine.LocalNetworkScope.LOCAL_LAN ||
            this == OutputStreamingEngine.LocalNetworkScope.MULTICAST
}
