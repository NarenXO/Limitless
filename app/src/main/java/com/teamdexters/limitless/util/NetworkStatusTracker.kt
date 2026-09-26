package com.teamdexters.limitless.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current network connectivity status of the device.
 */
sealed class NetworkStatus {
    /** Device has active internet-capable network connectivity. */
    data object Online : NetworkStatus()

    /** Device has no active internet-capable network connectivity. */
    data object Offline : NetworkStatus()
}

/**
 * Testable interface for network status observation.
 *
 * Allows [HazelQueryHandler] and other components to depend on an abstraction
 * rather than the concrete [NetworkStatusTracker], enabling lightweight fake
 * implementations in unit tests without Android framework dependencies.
 */
interface NetworkStatusProvider {
    /** Reactive [StateFlow] representing real-time network connectivity status. */
    val statusFlow: StateFlow<NetworkStatus>

    /**
     * Immediate synchronous check of current network connectivity.
     * @return `true` if the device currently has an active internet-capable network.
     */
    fun isCurrentlyOnline(): Boolean
}

/**
 * App-wide reactive network status observer using Android [ConnectivityManager].
 *
 * Implements [NetworkStatusProvider] for dependency injection and testability.
 *
 * Provides:
 * - [statusFlow]: A [StateFlow] of [NetworkStatus] for reactive Compose/UI observation.
 * - [isCurrentlyOnline]: An immediate synchronous check for imperative code paths.
 *
 * Usage:
 * ```kotlin
 * val tracker = NetworkStatusTracker(applicationContext)
 * tracker.register()
 * // Observe in Compose: val status by tracker.statusFlow.collectAsState()
 * // Check synchronously: if (tracker.isCurrentlyOnline()) { ... }
 * tracker.unregister() // Call in onDestroy or DisposableEffect
 * ```
 *
 * Handles lifecycle registration/unregistration cleanly without memory leaks.
 * Safe to call [unregister] multiple times without crashing.
 */
class NetworkStatusTracker(
    private val context: Context
) : NetworkStatusProvider {
    companion object {
        private const val TAG = "NetworkStatusTracker"
    }

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _statusFlow = MutableStateFlow(getCurrentNetworkStatus())

    /** Reactive [StateFlow] representing real-time network connectivity status. */
    override val statusFlow: StateFlow<NetworkStatus> = _statusFlow.asStateFlow()

    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            _statusFlow.value = NetworkStatus.Online
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            // Recheck: another network may still be active
            _statusFlow.value = if (checkActiveNetwork()) NetworkStatus.Online else NetworkStatus.Offline
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            Log.d(TAG, "Network capabilities changed. Has internet: $hasInternet")
            _statusFlow.value = if (hasInternet) NetworkStatus.Online else NetworkStatus.Offline
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            _statusFlow.value = NetworkStatus.Offline
        }
    }

    /**
     * Registers the [NetworkCallback] with [ConnectivityManager].
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun register() {
        if (isRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            Log.i(TAG, "NetworkStatusTracker registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    /**
     * Unregisters the [NetworkCallback] from [ConnectivityManager].
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun unregister() {
        if (!isRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            Log.i(TAG, "NetworkStatusTracker unregistered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback: ${e.message}", e)
        }
    }

    /**
     * Immediate synchronous check of current network connectivity.
     * @return `true` if the device currently has an active internet-capable network.
     */
    override fun isCurrentlyOnline(): Boolean {
        return checkActiveNetwork()
    }

    /**
     * Returns the current [NetworkStatus] based on an immediate connectivity check.
     */
    private fun getCurrentNetworkStatus(): NetworkStatus {
        return if (checkActiveNetwork()) NetworkStatus.Online else NetworkStatus.Offline
    }

    /**
     * Checks whether the active network has internet capability.
     */
    private fun checkActiveNetwork(): Boolean {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active network: ${e.message}", e)
            false
        }
    }
}

