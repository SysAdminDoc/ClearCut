package com.novacut.editor.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Process-wide internet reachability state for actions that require a remote service.
 *
 * A transport can be present while a captive portal or disconnected upstream makes
 * internet requests fail, so this observer requires both INTERNET and VALIDATED network
 * capabilities. The callback updates automatically when the active network changes;
 * [refresh] is available for lifecycle seams and tests.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(readCurrentState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = hasValidatedInternet(networkCapabilities)
        }

        override fun onLost(network: Network) {
            refresh()
        }
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        }.onFailure { error ->
            AppLog.w(TAG, "Unable to register connectivity observer", error)
        }
    }

    /** Re-read the active network after a lifecycle transition or a failed request. */
    fun refresh() {
        _isOnline.value = readCurrentState()
    }

    private fun readCurrentState(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return hasValidatedInternet(manager.getNetworkCapabilities(network))
    }

    companion object {
        private const val TAG = "ConnectivityObserver"

        internal fun hasValidatedInternet(capabilities: NetworkCapabilities?): Boolean =
            hasValidatedInternet(
                hasInternetCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                hasValidatedCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            )

        internal fun hasValidatedInternet(
            hasInternetCapability: Boolean,
            hasValidatedCapability: Boolean,
        ): Boolean = hasInternetCapability && hasValidatedCapability
    }
}
