package com.example.data.sync

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
 * Monitors real-time Wi-Fi network connectivity using Android's [ConnectivityManager].
 * Notifies listeners immediately when the device joins or leaves a Wi-Fi network so queued
 * nutrition entries can be uploaded to Google Drive automatically.
 */
class WifiAutoSyncMonitor(
    context: Context,
    private val onWifiJoined: () -> Unit
) {
    companion object {
        private const val TAG = "WifiAutoSyncMonitor"
    }

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isHardwareWifiConnected = MutableStateFlow(checkCurrentWifiState())
    val isHardwareWifiConnected: StateFlow<Boolean> = _isHardwareWifiConnected.asStateFlow()

    // Optional manual override when user pauses Wi-Fi uploads to queue entries until resumed
    private val _isWifiHoldEnabled = MutableStateFlow(false)
    val isWifiHoldEnabled: StateFlow<Boolean> = _isWifiHoldEnabled.asStateFlow()

    private val _isEffectiveWifiAvailable = MutableStateFlow(checkCurrentWifiState())
    val isEffectiveWifiAvailable: StateFlow<Boolean> = _isEffectiveWifiAvailable.asStateFlow()

    private var isCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateWifiStateFromSystem(triggerCallbackIfJoined = true)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val wasAvailable = _isEffectiveWifiAvailable.value
            _isHardwareWifiConnected.value = hasWifi || checkCurrentWifiState()
            val nowAvailable = _isHardwareWifiConnected.value && !_isWifiHoldEnabled.value
            _isEffectiveWifiAvailable.value = nowAvailable
            if (!wasAvailable && nowAvailable) {
                Log.d(TAG, "Wi-Fi connection established via onCapabilitiesChanged. Triggering queued sync.")
                onWifiJoined()
            }
        }

        override fun onLost(network: Network) {
            updateWifiStateFromSystem(triggerCallbackIfJoined = false)
        }
    }

    init {
        registerNetworkCallback()
    }

    fun registerNetworkCallback() {
        if (isCallbackRegistered || connectivityManager == null) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register ConnectivityManager NetworkCallback", e)
        }
    }

    fun unregisterNetworkCallback() {
        if (!isCallbackRegistered || connectivityManager == null) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isCallbackRegistered = false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to unregister ConnectivityManager NetworkCallback", e)
        }
    }

    /**
     * Checks live [ConnectivityManager] capabilities for an active Wi-Fi transport.
     */
    fun checkCurrentWifiState(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking active Wi-Fi state", e)
            false
        }
    }

    /**
     * Refreshes the hardware Wi-Fi state from [ConnectivityManager] and fires [onWifiJoined]
     * if the device just transitioned onto Wi-Fi.
     */
    fun refreshWifiState(): Boolean {
        updateWifiStateFromSystem(triggerCallbackIfJoined = true)
        return _isEffectiveWifiAvailable.value
    }

    /**
     * Allows holding/deferring Wi-Fi uploads (or simulating being away from Wi-Fi) and
     * immediately flushes the queue when Wi-Fi is restored.
     */
    fun setWifiHoldMode(holdOffline: Boolean) {
        val wasAvailable = _isEffectiveWifiAvailable.value
        _isWifiHoldEnabled.value = holdOffline
        val hardwareWifi = checkCurrentWifiState()
        _isHardwareWifiConnected.value = hardwareWifi
        val nowAvailable = hardwareWifi && !holdOffline
        _isEffectiveWifiAvailable.value = nowAvailable
        if (!wasAvailable && nowAvailable) {
            Log.d(TAG, "Wi-Fi hold released while on Wi-Fi. Triggering queued sync.")
            onWifiJoined()
        }
    }

    private fun updateWifiStateFromSystem(triggerCallbackIfJoined: Boolean) {
        val wasAvailable = _isEffectiveWifiAvailable.value
        val hardwareWifi = checkCurrentWifiState()
        _isHardwareWifiConnected.value = hardwareWifi
        val nowAvailable = hardwareWifi && !_isWifiHoldEnabled.value
        _isEffectiveWifiAvailable.value = nowAvailable
        if (triggerCallbackIfJoined && !wasAvailable && nowAvailable) {
            Log.d(TAG, "Device joined Wi-Fi. Triggering queued Google Drive upload.")
            onWifiJoined()
        }
    }
}
