package com.pcosina.app.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

fun isNetworkOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasValidatedInternet()
}

@Composable
fun rememberIsOnline(context: Context): State<Boolean> {
    val isOnline = remember(context) { mutableStateOf(isNetworkOnline(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = isNetworkOnline(context)
            }

            override fun onLost(network: Network) {
                isOnline.value = isNetworkOnline(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isOnline.value = networkCapabilities.hasValidatedInternet()
            }
        }

        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { isOnline.value = isNetworkOnline(context) }

        onDispose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
    return isOnline
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
