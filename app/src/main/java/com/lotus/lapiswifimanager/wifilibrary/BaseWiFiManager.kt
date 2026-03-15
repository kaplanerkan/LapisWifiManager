package com.lotus.lapiswifimanager.wifilibrary

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

open class BaseWiFiManager(context: Context) {

    companion object {
        private const val TAG = "BaseWiFiManager"
    }

    private var onNetworkAvailableListener: ((String) -> Unit)? = null
    protected val appContext: Context = context.applicationContext
    protected val wifiManager: WifiManager by lazy {
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // ConnectivityManager fuer Android 10+
    private val connectivityManager: ConnectivityManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } else {
            null
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ═════════════════════════════════════════════════════════════════════════════
    //  Permission Checks
    // ═════════════════════════════════════════════════════════════════════════════

    protected fun hasWifiStatePermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    protected fun hasChangeWifiStatePermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    protected fun hasLocationPermission(): Boolean {
        val fineLocation = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocation = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    protected fun hasRequiredPermissions(): Boolean {
        return hasWifiStatePermission() && hasChangeWifiStatePermission() && hasLocationPermission()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi State
    // ═════════════════════════════════════════════════════════════════════════════

    fun isWifiEnabled(): Boolean {
        return if (hasWifiStatePermission()) {
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        } else {
            false
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi Scanning
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * WiFi-Scan starten
     */
    fun startScan(): Boolean {
        if (!hasWifiStatePermission() || !hasLocationPermission()) {
            Log.w(TAG, "Erforderliche Berechtigungen fuer Scan fehlen")
            return false
        }

        return try {
            val success = wifiManager.startScan()
            Log.d(TAG, "WiFi-Scan gestartet: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Scan konnte nicht gestartet werden", e)
            false
        }
    }

    /**
     * Eindeutige WiFi-Scan-Ergebnisse abrufen
     */
    fun getUniqueScanResults(): List<ScanResult> {
        if (!hasWifiStatePermission() || !hasLocationPermission()) {
            Log.e(TAG, "Erforderliche Berechtigungen fuer Scan-Ergebnisse fehlen!")
            Log.e(TAG, "  WiFi State: ${hasWifiStatePermission()}")
            Log.e(TAG, "  Location: ${hasLocationPermission()}")
            return emptyList()
        }

        return try {
            val results = wifiManager.scanResults ?: emptyList()
            Log.d(TAG, "Rohe Scan-Ergebnisse: ${results.size} Netzwerke")

            val uniqueResults = results.asSequence()
                .filter { it.SSID.isNotBlank() }
                .groupBy { it.SSID }
                .mapValues { (_, list) -> list.maxByOrNull { it.level }!! }
                .values
                .toList()

            Log.d(TAG, "Eindeutige Ergebnisse: ${uniqueResults.size} Netzwerke")
            uniqueResults
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Berechtigungen moeglicherweise verloren!", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Scan-Ergebnisse konnten nicht abgerufen werden", e)
            emptyList()
        }
    }

    /**
     * Sicherheitsmodus bestimmen
     */
    fun getSecurityMode(scanResult: ScanResult): SecurityModeEnum {
        val capabilities = scanResult.capabilities.uppercase()

        return when {
            capabilities.contains("WEP") -> SecurityModeEnum.WEP
            capabilities.contains("WPA3") -> SecurityModeEnum.WPA3
            capabilities.contains("WPA2") || capabilities.contains("RSN") -> SecurityModeEnum.WPA2
            capabilities.contains("WPA") -> SecurityModeEnum.WPA
            else -> SecurityModeEnum.OPEN
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi Connection
    // ═════════════════════════════════════════════════════════════════════════════
    /**
     * WPA2-Netzwerkverbindung - automatische Auswahl nach Android-Version
     */
    fun connectToWPA2Network(ssid: String, password: String): Boolean {
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Erforderliche Berechtigungen fehlen!")
            return false
        }

        if (!isWifiEnabled()) {
            Log.e(TAG, "WiFi ist deaktiviert!")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // WifiNetworkSuggestion fuer Android 10+ verwenden (systemweite Verbindung)
            try {
                connectWithSuggestion(ssid, password)
            } catch (e: NoSuchMethodError) {
                // Huawei/HarmonyOS meldet falschen SDK-Level - Fallback auf alte Methode
                Log.w(TAG, "WifiNetworkSuggestion nicht verfuegbar trotz SDK ${Build.VERSION.SDK_INT}, Fallback auf Legacy-Methode", e)
                connectLegacy(ssid, password)
            }
        } else {
            // Alte Methode fuer Android 9 und niedriger
            connectLegacy(ssid, password)
        }
    }

    private fun connectLegacy(ssid: String, password: String): Boolean {
        val networkId = setWPA2Network(ssid, password)
        return if (networkId != -1) {
            enableNetwork(networkId)
        } else {
            false
        }
    }
    /**
     * WifiNetworkSuggestion fuer Android 10+ - SYSTEMWEITE VERBINDUNG
     * Diese Methode zeigt dem Benutzer eine Benachrichtigung und fordert Bestaetigung an
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithSuggestion(ssid: String, password: String): Boolean {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Android 10+ WifiNetworkSuggestion wird erstellt:")
        Log.d(TAG, "  SSID: $cleanSsid")
        Log.d(TAG, "  Password length: ${password.length}")

        try {
            // Bestehende Vorschlaege zuerst entfernen
            val existingSuggestions = wifiManager.networkSuggestions
            if (existingSuggestions.isNotEmpty()) {
                Log.d(TAG, "Bestehende ${existingSuggestions.size} Vorschlaege werden entfernt")
                wifiManager.removeNetworkSuggestions(existingSuggestions)
            }

            // Neuen Vorschlag erstellen
            val suggestion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Erweiterte Einstellungen fuer Android 11+
                WifiNetworkSuggestion.Builder()
                    .setSsid(cleanSsid)
                    .setWpa2Passphrase(password)
                    .setIsAppInteractionRequired(true) // Benutzerbestaetigung erforderlich
                    .setIsUserInteractionRequired(false) // Automatisch verbinden
                    .setPriority(Integer.MAX_VALUE) // Hoechste Prioritaet
                    .build()
            } else {
                // Grundeinstellungen fuer Android 10
                WifiNetworkSuggestion.Builder()
                    .setSsid(cleanSsid)
                    .setWpa2Passphrase(password)
                    .setIsAppInteractionRequired(true)
                    .build()
            }

            val suggestionsList = listOf(suggestion)
            val status = wifiManager.addNetworkSuggestions(suggestionsList)

            return when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                    Log.i(TAG, "Netzwerkvorschlag erfolgreich hinzugefuegt")
                    Log.i(TAG, "  Benutzerbenachrichtigung wird angezeigt")
                    Log.i(TAG, "  System verbindet automatisch")
                    true
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                    Log.w(TAG, "Dieses Netzwerk wurde bereits vorgeschlagen")
                    Log.w(TAG, "  System ist wahrscheinlich bereits verbunden oder wird sich verbinden")
                    true
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> {
                    Log.e(TAG, "App hat keine Berechtigung fuer Netzwerkvorschlaege")
                    false
                }
                else -> {
                    Log.e(TAG, "Netzwerkvorschlag konnte nicht hinzugefuegt werden: $status")
                    false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSuggestion Fehler", e)
            return false
        }
    }




    /**
     * Klassische Methode fuer Android 9 und niedriger
     */
    @Suppress("DEPRECATION")
    protected fun setWPA2Network(ssid: String, password: String): Int {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "WPA2 Config wird erstellt:")
        Log.d(TAG, "  SSID: $cleanSsid")
        Log.d(TAG, "  Password length: ${password.length}")

        // Alte Konfiguration mit gleicher SSID zuerst entfernen
        getConfiguredNetworkBySsid(cleanSsid)?.let { existing ->
            Log.d(TAG, "Alte Konfiguration wird entfernt: ${existing.networkId}")
            wifiManager.removeNetwork(existing.networkId)
            wifiManager.saveConfiguration()
        }

        val config = WifiConfiguration().apply {
            SSID = "\"$cleanSsid\""
            preSharedKey = "\"$password\""

            allowedKeyManagement.clear()
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)

            allowedProtocols.clear()
            allowedProtocols.set(WifiConfiguration.Protocol.RSN)
            allowedProtocols.set(WifiConfiguration.Protocol.WPA)

            allowedAuthAlgorithms.clear()
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)

            allowedPairwiseCiphers.clear()
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)

            allowedGroupCiphers.clear()
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)

            status = WifiConfiguration.Status.ENABLED
        }

        val networkId = wifiManager.addNetwork(config)
        Log.d(TAG, "Network ID: $networkId")

        if (networkId != -1) {
            wifiManager.saveConfiguration()
        }

        return networkId
    }

    /**
     * Network Request Methode fuer Android 10+
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithNetworkRequest(ssid: String, password: String): Boolean {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Android 10+ NetworkRequest wird erstellt:")
        Log.d(TAG, "  SSID: $cleanSsid")
        Log.d(TAG, "  Password length: ${password.length}")

        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(cleanSsid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i(TAG, "Netzwerkverbindung erfolgreich: $cleanSsid")
                    connectivityManager?.bindProcessToNetwork(network)

                    // Dieser Callback loest nicht den Listener im WiFiManager aus,
                    // da der BroadcastReceiver bereits den COMPLETED-Zustand erfasst
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e(TAG, "Netzwerkverbindung fehlgeschlagen: $cleanSsid (timeout)")
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w(TAG, "Netzwerkverbindung unterbrochen: $cleanSsid")
                }
            }

            connectivityManager?.requestNetwork(request, networkCallback!!)
            Log.d(TAG, "Network Request gesendet")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "NetworkRequest Fehler", e)
            return false
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Network Configuration
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Gespeicherte Netzwerkkonfiguration anhand der SSID finden
     */
    @Suppress("DEPRECATION")
    fun getConfiguredNetworkBySsid(ssid: String): WifiConfiguration? {
        if (!hasWifiStatePermission()) return null

        val cleanSsid = ssid.trim().removeSurrounding("\"")

        return runCatching {
            wifiManager.configuredNetworks?.find { config ->
                config.SSID?.removeSurrounding("\"") == cleanSsid
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    protected fun enableNetwork(networkId: Int): Boolean {
        if (networkId == -1) {
            Log.e(TAG, "Ungueltige Network ID: -1")
            return false
        }

        return try {
            wifiManager.disconnect()
            Thread.sleep(500)

            val success = wifiManager.enableNetwork(networkId, true)
            Log.d(TAG, "enableNetwork($networkId) result: $success")

            if (success) {
                wifiManager.reconnect()
                Log.d(TAG, "reconnect() aufgerufen")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "enableNetwork Fehler", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    protected fun setOpenNetwork(ssid: String): Int {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Open Network Config wird erstellt: $cleanSsid")

        val config = WifiConfiguration().apply {
            SSID = "\"$cleanSsid\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            allowedProtocols.set(WifiConfiguration.Protocol.RSN)
            allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            allowedAuthAlgorithms.clear()
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId != -1) {
            wifiManager.saveConfiguration()
        }

        return networkId
    }

    @Suppress("DEPRECATION")
    protected fun setWEPNetwork(ssid: String, password: String): Int {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "WEP Network Config wird erstellt: $cleanSsid")

        val config = WifiConfiguration().apply {
            SSID = "\"$cleanSsid\""
            wepKeys[0] = "\"$password\""
            wepTxKeyIndex = 0
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            allowedProtocols.set(WifiConfiguration.Protocol.RSN)
            allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId != -1) {
            wifiManager.saveConfiguration()
        }

        return networkId
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Network Management
    // ═════════════════════════════════════════════════════════════════════════════

    fun disconnectCurrentWifi() {
        if (!hasChangeWifiStatePermission()) {
            Log.w(TAG, "Keine Berechtigung zum Trennen der Verbindung")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                networkCallback?.let {
                    connectivityManager?.unregisterNetworkCallback(it)
                    networkCallback = null
                }
                connectivityManager?.bindProcessToNetwork(null)
            } else {
                wifiManager.disconnect()
            }
            Log.d(TAG, "WiFi-Verbindung getrennt")
        } catch (e: Exception) {
            Log.e(TAG, "Verbindung konnte nicht getrennt werden", e)
        }
    }

    fun getCurrentConnectionInfo(): android.net.wifi.WifiInfo? {
        return if (hasWifiStatePermission()) {
            runCatching { wifiManager.connectionInfo }.getOrNull()
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun removeNetwork(networkId: Int): Boolean {
        return try {
            wifiManager.removeNetwork(networkId)
        } catch (e: Exception) {
            Log.e(TAG, "Netzwerk konnte nicht entfernt werden", e)
            false
        }
    }
}