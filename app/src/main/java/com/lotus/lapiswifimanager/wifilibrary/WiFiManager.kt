package com.lotus.lapiswifimanager.wifilibrary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager as AndroidWifiManager
import android.os.Build
import android.util.Log
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiConnectListener
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiEnabledListener
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiScanResultsListener

class WiFiManager private constructor(context: Context) : BaseWiFiManager(context) {

    companion object {
        private const val TAG = "WiFiManager"

        @Volatile
        private var INSTANCE: WiFiManager? = null

        fun getInstance(context: Context): WiFiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WiFiManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }


    // Listener
    private var onWifiEnabledListener: OnWifiEnabledListener? = null
    private var onWifiScanResultsListener: OnWifiScanResultsListener? = null
    private var onWifiConnectListener: OnWifiConnectListener? = null

    private val wifiStateReceiver = WiFiBroadcastReceiver()
    private var isReceiverRegistered = false

    init {
        registerReceiver()
    }

    /** WiFi aktivieren */
    @Suppress("DEPRECATION")
    fun openWiFi() {
        if (!hasChangeWifiStatePermission()) {
            Log.w(TAG, "CHANGE_WIFI_STATE Berechtigung zum Aktivieren von WiFi erforderlich")
            return
        }

        val enabled = if (hasWifiStatePermission()) {
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(true)
        } else {
            true
        }

        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.w(TAG, "WiFi kann ab Android 10+ nicht programmatisch aktiviert werden")
                // Listener benachrichtigen, damit MainActivity den Benutzer weiterleitet
                onWifiEnabledListener?.onWifiEnabled(false)
            } else {
                runCatching {
                    wifiManager.isWifiEnabled = true
                }.onFailure { e ->
                    Log.e(TAG, "WiFi konnte nicht aktiviert werden", e)
                }
            }
        }
    }

    /** WiFi deaktivieren */
    @Suppress("DEPRECATION")
    fun closeWiFi() {
        if (!hasChangeWifiStatePermission()) {
            Log.w(TAG, "CHANGE_WIFI_STATE Berechtigung zum Deaktivieren von WiFi erforderlich")
            return
        }

        val enabled = if (hasWifiStatePermission()) {
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        } else {
            false
        }

        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.w(TAG, "WiFi kann ab Android 10+ nicht programmatisch deaktiviert werden")
                // Listener benachrichtigen, damit MainActivity den Benutzer weiterleitet
                onWifiEnabledListener?.onWifiEnabled(true)
            } else {
                runCatching {
                    wifiManager.isWifiEnabled = false
                }.onFailure { e ->
                    Log.e(TAG, "WiFi konnte nicht deaktiviert werden", e)
                }
            }
        }
    }


    fun connectToOpenNetwork(ssid: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Erforderliche Berechtigungen fuer WiFi-Verbindung fehlen")
            return false
        }

        val networkId = setOpenNetwork(ssid)
        return networkId != -1 && enableNetwork(networkId)
    }

    fun connectToWEPNetwork(ssid: String, password: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Erforderliche Berechtigungen fuer WiFi-Verbindung fehlen")
            return false
        }

        val networkId = setWEPNetwork(ssid, password)
        return networkId != -1 && enableNetwork(networkId)
    }

    // Listener-Einstellungen
    fun setOnWifiEnabledListener(listener: OnWifiEnabledListener?) {
        onWifiEnabledListener = listener
    }

    fun setOnWifiScanResultsListener(listener: OnWifiScanResultsListener?) {
        onWifiScanResultsListener = listener
    }

    fun setOnWifiConnectListener(listener: OnWifiConnectListener?) {
        onWifiConnectListener = listener
    }

    fun removeAllListeners() {
        onWifiEnabledListener = null
        onWifiScanResultsListener = null
        onWifiConnectListener = null
    }

    fun release() {
        unregisterReceiver()
        removeAllListeners()
        INSTANCE = null
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return

        runCatching {
            val filter = IntentFilter().apply {
                addAction(AndroidWifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(AndroidWifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(AndroidWifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(AndroidWifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
                addAction(AndroidWifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(wifiStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(wifiStateReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "WiFi Receiver registriert")
        }.onFailure { e ->
            Log.e(TAG, "Receiver konnte nicht registriert werden", e)
        }
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return

        runCatching {
            appContext.unregisterReceiver(wifiStateReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "WiFi Receiver abgemeldet")
        }.onFailure { e ->
            Log.e(TAG, "Receiver konnte nicht abgemeldet werden", e)
        }
    }

    inner class WiFiBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AndroidWifiManager.WIFI_STATE_CHANGED_ACTION -> handleWifiStateChanged(intent)
                AndroidWifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> handleScanResultsAvailable()
                AndroidWifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> handleSupplicantStateChanged(intent)
            }
        }

        private fun handleWifiStateChanged(intent: Intent) {
            val state = intent.getIntExtra(
                AndroidWifiManager.EXTRA_WIFI_STATE,
                AndroidWifiManager.WIFI_STATE_UNKNOWN
            )

            when (state) {
                AndroidWifiManager.WIFI_STATE_ENABLED -> {
                    Log.i(TAG, "WiFi aktiviert")
                    onWifiEnabledListener?.onWifiEnabled(true)
                }
                AndroidWifiManager.WIFI_STATE_DISABLED -> {
                    Log.i(TAG, "WiFi deaktiviert")
                    onWifiEnabledListener?.onWifiEnabled(false)
                }
                AndroidWifiManager.WIFI_STATE_ENABLING -> {
                    Log.i(TAG, "WiFi wird aktiviert...")
                }
                AndroidWifiManager.WIFI_STATE_DISABLING -> {
                    Log.i(TAG, "WiFi wird deaktiviert...")
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun handleScanResultsAvailable() {
            Log.i(TAG, "WiFi-Scan abgeschlossen")

            if (!hasWifiStatePermission()) {
                Log.e(TAG, "WiFi State Berechtigung fehlt!")
                onWifiScanResultsListener?.onScanComplete(emptyList())
                return
            }

            if (!hasLocationPermission()) {
                Log.e(TAG, "Location Berechtigung fehlt!")
                onWifiScanResultsListener?.onScanComplete(emptyList())
                return
            }

            runCatching {
                val results = wifiManager.scanResults ?: emptyList()
                Log.d(TAG, "Scan-Ergebnis: ${results.size} Netzwerke gefunden")

                val uniqueResults = results.asSequence()
                    .filter { it.SSID.isNotBlank() }
                    .groupBy { it.SSID }
                    .mapValues { (_, list) -> list.maxByOrNull { it.level }!! }
                    .values
                    .toList()

                Log.d(TAG, "Eindeutige Netzwerke: ${uniqueResults.size}")
                onWifiScanResultsListener?.onScanComplete(uniqueResults)
            }.onFailure { e ->
                when (e) {
                    is SecurityException -> {
                        Log.e(TAG, "SecurityException - Berechtigungen verloren!", e)
                    }
                    else -> {
                        Log.e(TAG, "Scan-Ergebnisse konnten nicht abgerufen werden", e)
                    }
                }
                onWifiScanResultsListener?.onScanComplete(emptyList())
            }
        }

        @Suppress("DEPRECATION")
        private fun handleSupplicantStateChanged(intent: Intent) {
            val newState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    AndroidWifiManager.EXTRA_NEW_STATE,
                    SupplicantState::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(AndroidWifiManager.EXTRA_NEW_STATE)
            }

            val error = intent.getIntExtra(AndroidWifiManager.EXTRA_SUPPLICANT_ERROR, -1)
            if (error != -1) {
                val errorMsg = when (error) {
                    AndroidWifiManager.ERROR_AUTHENTICATING -> "Authentifizierungsfehler (falsches Passwort?)"
                    else -> "Unbekannter Fehler: $error"
                }
                Log.e(TAG, "Verbindungsfehler: $errorMsg")
                onWifiConnectListener?.onWiFiConnectLog("FEHLER: $errorMsg")
            }

            val wifiInfo = if (hasWifiStatePermission()) {
                runCatching { wifiManager.connectionInfo }.getOrNull()
            } else {
                null
            }

            val currentSsid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "Unbekannt"

            Log.d(TAG, "Supplicant State: ${newState?.name}, SSID: $currentSsid, Error: $error")

            // Benutzerfreundliche Meldungen
            val userMessage = when (newState) {
                SupplicantState.ASSOCIATING -> "Verbindung wird hergestellt..."
                SupplicantState.ASSOCIATED -> "Verbunden, IP wird abgerufen..."
                SupplicantState.FOUR_WAY_HANDSHAKE -> "Passwort wird überprüft..."
                SupplicantState.GROUP_HANDSHAKE -> "Verschlüsselung wird konfiguriert..."
                SupplicantState.COMPLETED -> "Verbindung erfolgreich!"
                else -> null
            }

            userMessage?.let { msg ->
                onWifiConnectListener?.onWiFiConnectLog(msg)
            }

            when (newState) {
                SupplicantState.COMPLETED -> {
                    Log.i(TAG, "Verbindung erfolgreich: $currentSsid")
                    onWifiConnectListener?.onWiFiConnectSuccess(currentSsid)
                }

                SupplicantState.DISCONNECTED,
                SupplicantState.INACTIVE,
                SupplicantState.INTERFACE_DISABLED -> {
                    if (error == AndroidWifiManager.ERROR_AUTHENTICATING) {
                        Log.e(TAG, "Falsches Passwort: $currentSsid")
                        onWifiConnectListener?.onWiFiConnectFailure("$currentSsid (Falsches Passwort)")
                    } else {
                        // INTERFACE_DISABLED Zustand ist beim Start normal
                        if (currentSsid != "<unknown ssid>" && currentSsid != "Unbekannt") {
                            Log.w(TAG, "Verbindung unterbrochen: $currentSsid")
                            onWifiConnectListener?.onWiFiConnectFailure(currentSsid)
                        }
                    }
                }

                SupplicantState.SCANNING -> Log.d(TAG, "Wird gescannt...")
                SupplicantState.AUTHENTICATING -> Log.d(TAG, "Authentifizierung...")
                SupplicantState.ASSOCIATING -> Log.d(TAG, "Verbindung wird hergestellt...")
                SupplicantState.ASSOCIATED -> Log.d(TAG, "Verbunden, IP wird abgerufen...")
                SupplicantState.FOUR_WAY_HANDSHAKE -> Log.d(TAG, "4-way handshake...")
                SupplicantState.GROUP_HANDSHAKE -> Log.d(TAG, "Group key handshake...")

                else -> Log.d(TAG, "Unbekannter Zustand: ${newState?.name}")
            }
        }

    }
}