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


    // Listener'lar
    private var onWifiEnabledListener: OnWifiEnabledListener? = null
    private var onWifiScanResultsListener: OnWifiScanResultsListener? = null
    private var onWifiConnectListener: OnWifiConnectListener? = null

    private val wifiStateReceiver = WiFiBroadcastReceiver()
    private var isReceiverRegistered = false

    init {
        registerReceiver()
    }

    /** WiFi'yi açar */
    @Suppress("DEPRECATION")
    fun openWiFi() {
        if (!hasChangeWifiStatePermission()) {
            Log.w(TAG, "WiFi açmak için CHANGE_WIFI_STATE izni gerekli")
            return
        }

        val enabled = if (hasWifiStatePermission()) {
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(true)
        } else {
            true
        }

        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.w(TAG, "Android 10+ üzerinde WiFi'yi programatik olarak açamazsınız")
                // Listener'a bildir ki MainActivity kullanıcıyı yönlendirsin
                onWifiEnabledListener?.onWifiEnabled(false)
            } else {
                runCatching {
                    wifiManager.isWifiEnabled = true
                }.onFailure { e ->
                    Log.e(TAG, "WiFi açılamadı", e)
                }
            }
        }
    }

    /** WiFi'yi kapatır */
    @Suppress("DEPRECATION")
    fun closeWiFi() {
        if (!hasChangeWifiStatePermission()) {
            Log.w(TAG, "WiFi kapatmak için CHANGE_WIFI_STATE izni gerekli")
            return
        }

        val enabled = if (hasWifiStatePermission()) {
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        } else {
            false
        }

        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.w(TAG, "Android 10+ üzerinde WiFi'yi programatik olarak kapatamazsınız")
                // Listener'a bildir ki MainActivity kullanıcıyı yönlendirsin
                onWifiEnabledListener?.onWifiEnabled(true)
            } else {
                runCatching {
                    wifiManager.isWifiEnabled = false
                }.onFailure { e ->
                    Log.e(TAG, "WiFi kapatılamadı", e)
                }
            }
        }
    }


    fun connectToOpenNetwork(ssid: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "WiFi bağlantısı için gerekli izinler yok")
            return false
        }

        val networkId = setOpenNetwork(ssid)
        return networkId != -1 && enableNetwork(networkId)
    }

    fun connectToWEPNetwork(ssid: String, password: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "WiFi bağlantısı için gerekli izinler yok")
            return false
        }

        val networkId = setWEPNetwork(ssid, password)
        return networkId != -1 && enableNetwork(networkId)
    }

    // ❌ BU METODU SİLİN - BaseWiFiManager'da zaten var
    // fun connectToWPA2Network(...) { ... }

    // Listener ayarları
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
            Log.d(TAG, "WiFi receiver kayıt edildi")
        }.onFailure { e ->
            Log.e(TAG, "Receiver kayıt edilemedi", e)
        }
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return

        runCatching {
            appContext.unregisterReceiver(wifiStateReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "WiFi receiver kayıt silindi")
        }.onFailure { e ->
            Log.e(TAG, "Receiver kayıt silinemedi", e)
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
                    Log.i(TAG, "WiFi açıldı")
                    onWifiEnabledListener?.onWifiEnabled(true)
                }
                AndroidWifiManager.WIFI_STATE_DISABLED -> {
                    Log.i(TAG, "WiFi kapandı")
                    onWifiEnabledListener?.onWifiEnabled(false)
                }
                AndroidWifiManager.WIFI_STATE_ENABLING -> {
                    Log.i(TAG, "WiFi açılıyor...")
                }
                AndroidWifiManager.WIFI_STATE_DISABLING -> {
                    Log.i(TAG, "WiFi kapanıyor...")
                }
            }
        }

        private fun handleScanResultsAvailable() {
            Log.i(TAG, "WiFi tarama tamamlandı")

            if (!hasWifiStatePermission()) {
                Log.e(TAG, "⚠️ WiFi State izni yok!")
                onWifiScanResultsListener?.onScanComplete(emptyList())
                return
            }

            if (!hasLocationPermission()) {
                Log.e(TAG, "⚠️ Location izni yok!")
                onWifiScanResultsListener?.onScanComplete(emptyList())
                return
            }

            runCatching {
                val results = wifiManager.scanResults ?: emptyList()
                Log.d(TAG, "Tarama sonucu: ${results.size} ağ bulundu")

                val uniqueResults = results.asSequence()
                    .filter { it.SSID.isNotBlank() }
                    .groupBy { it.SSID }
                    .mapValues { (_, list) -> list.maxByOrNull { it.level }!! }
                    .values
                    .toList()

                Log.d(TAG, "Benzersiz ağlar: ${uniqueResults.size}")
                onWifiScanResultsListener?.onScanComplete(uniqueResults)
            }.onFailure { e ->
                when (e) {
                    is SecurityException -> {
                        Log.e(TAG, "⚠️ SecurityException - İzinler kaybolmuş!", e)
                    }
                    else -> {
                        Log.e(TAG, "Scan results alınamadı", e)
                    }
                }
                onWifiScanResultsListener?.onScanComplete(emptyList())
            }
        }

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
                    AndroidWifiManager.ERROR_AUTHENTICATING -> "Kimlik doğrulama hatası (yanlış şifre?)"
                    else -> "Bilinmeyen hata: $error"
                }
                Log.e(TAG, "Bağlantı hatası: $errorMsg")
                onWifiConnectListener?.onWiFiConnectLog("HATA: $errorMsg")
            }

            val wifiInfo = if (hasWifiStatePermission()) {
                runCatching { wifiManager.connectionInfo }.getOrNull()
            } else {
                null
            }

            val currentSsid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "Bilinmiyor"

            Log.d(TAG, "Supplicant State: ${newState?.name}, SSID: $currentSsid, Error: $error")

            // Kullanıcı dostu mesajlar
            val userMessage = when (newState) {
                SupplicantState.ASSOCIATING -> "Bağlanıyor..."
                SupplicantState.ASSOCIATED -> "Bağlandı, IP alınıyor..."
                SupplicantState.FOUR_WAY_HANDSHAKE -> "Şifre doğrulanıyor..."
                SupplicantState.GROUP_HANDSHAKE -> "Şifreleme ayarlanıyor..."
                SupplicantState.COMPLETED -> "Bağlantı başarılı!"
                else -> null
            }

            userMessage?.let { msg ->
                onWifiConnectListener?.onWiFiConnectLog(msg)
            }

            when (newState) {
                SupplicantState.COMPLETED -> {
                    Log.i(TAG, "✓ Bağlantı başarılı: $currentSsid")
                    onWifiConnectListener?.onWiFiConnectSuccess(currentSsid)
                }

                SupplicantState.DISCONNECTED,
                SupplicantState.INACTIVE,
                SupplicantState.INTERFACE_DISABLED -> {
                    if (error == AndroidWifiManager.ERROR_AUTHENTICATING) {
                        Log.e(TAG, "✗ Şifre yanlış: $currentSsid")
                        onWifiConnectListener?.onWiFiConnectFailure("$currentSsid (Yanlış şifre)")
                    } else {
                        // INTERFACE_DISABLED durumu başlangıçta normal
                        if (currentSsid != "<unknown ssid>" && currentSsid != "Bilinmiyor") {
                            Log.w(TAG, "✗ Bağlantı koptu: $currentSsid")
                            onWifiConnectListener?.onWiFiConnectFailure(currentSsid)
                        }
                    }
                }

                SupplicantState.SCANNING -> Log.d(TAG, "Taranıyor...")
                SupplicantState.AUTHENTICATING -> Log.d(TAG, "Kimlik doğrulanıyor...")
                SupplicantState.ASSOCIATING -> Log.d(TAG, "Bağlanıyor...")
                SupplicantState.ASSOCIATED -> Log.d(TAG, "Bağlandı, IP alınıyor...")
                SupplicantState.FOUR_WAY_HANDSHAKE -> Log.d(TAG, "4-way handshake...")
                SupplicantState.GROUP_HANDSHAKE -> Log.d(TAG, "Group key handshake...")

                else -> Log.d(TAG, "Bilinmeyen durum: ${newState?.name}")
            }
        }

    }
}