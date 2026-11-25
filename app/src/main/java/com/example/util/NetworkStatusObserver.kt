package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes device network availability via Android ConnectivityManager without polling.
 * Uses only install-time ACCESS_NETWORK_STATE permission.
 */
object NetworkStatusObserver {

    fun isCurrentlyOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            // Fall back optimistically to true if connectivity inspection is unavailable in a test sandbox
            true
        }
    }

    fun observeIsOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        trySend(isCurrentlyOnline(context))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline(context))
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(validated)
            }
        }

        val registered = try {
            cm.registerDefaultNetworkCallback(callback)
            true
        } catch (_: Exception) {
            false
        }

        awaitClose {
            if (registered) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }.distinctUntilChanged()
}
