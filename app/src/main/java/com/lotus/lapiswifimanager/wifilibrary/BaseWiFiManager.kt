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

    // Android 10+ için ConnectivityManager
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
     * WiFi taraması başlat
     */
    fun startScan(): Boolean {
        if (!hasWifiStatePermission() || !hasLocationPermission()) {
            Log.w(TAG, "Tarama için gerekli izinler yok")
            return false
        }

        return try {
            val success = wifiManager.startScan()
            Log.d(TAG, "WiFi taraması başlatıldı: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Tarama başlatılamadı", e)
            false
        }
    }

    /**
     * Tekrarsız WiFi tarama sonuçlarını al
     */
    fun getUniqueScanResults(): List<ScanResult> {
        if (!hasWifiStatePermission() || !hasLocationPermission()) {
            Log.e(TAG, "⚠️ Scan results için gerekli izinler yok!")
            Log.e(TAG, "  WiFi State: ${hasWifiStatePermission()}")
            Log.e(TAG, "  Location: ${hasLocationPermission()}")
            return emptyList()
        }

        return try {
            val results = wifiManager.scanResults ?: emptyList()
            Log.d(TAG, "Ham tarama sonucu: ${results.size} ağ")

            val uniqueResults = results.asSequence()
                .filter { it.SSID.isNotBlank() }
                .groupBy { it.SSID }
                .mapValues { (_, list) -> list.maxByOrNull { it.level }!! }
                .values
                .toList()

            Log.d(TAG, "Benzersiz sonuçlar: ${uniqueResults.size} ağ")
            uniqueResults
        } catch (e: SecurityException) {
            Log.e(TAG, "⚠️ SecurityException: İzinler kaybolmuş olabilir!", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Scan results alınamadı", e)
            emptyList()
        }
    }

    /**
     * Güvenlik modunu belirle
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
     * WPA2 ağa bağlanma - Android sürümüne göre otomatik seçim
     */
    fun connectToWPA2Network(ssid: String, password: String): Boolean {
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Gerekli izinler yok!")
            return false
        }

        if (!isWifiEnabled()) {
            Log.e(TAG, "WiFi kapalı!")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ için WifiNetworkSuggestion kullan (sistem geneli bağlantı)
            connectWithSuggestion(ssid, password)
        } else {
            // Android 9 ve altı için eski yöntem
            val networkId = setWPA2Network(ssid, password)
            if (networkId != -1) {
                enableNetwork(networkId)
            } else {
                false
            }
        }
    }
    /**
     * Android 10+ için WifiNetworkSuggestion - SİSTEM GENELİ BAĞLANTI
     * Bu yöntem kullanıcıya bildirim gösterir ve onay ister
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithSuggestion(ssid: String, password: String): Boolean {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Android 10+ WifiNetworkSuggestion oluşturuluyor:")
        Log.d(TAG, "  SSID: $cleanSsid")
        Log.d(TAG, "  Password length: ${password.length}")

        try {
            // Önce mevcut önerileri temizle
            val existingSuggestions = wifiManager.networkSuggestions
            if (existingSuggestions.isNotEmpty()) {
                Log.d(TAG, "Mevcut ${existingSuggestions.size} öneri temizleniyor")
                wifiManager.removeNetworkSuggestions(existingSuggestions)
            }

            // Yeni öneri oluştur
            val suggestion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ için daha gelişmiş ayarlar
                WifiNetworkSuggestion.Builder()
                    .setSsid(cleanSsid)
                    .setWpa2Passphrase(password)
                    .setIsAppInteractionRequired(true) // Kullanıcı onayı gerekli
                    .setIsUserInteractionRequired(false) // Otomatik bağlan
                    .setPriority(Integer.MAX_VALUE) // En yüksek öncelik
                    .build()
            } else {
                // Android 10 için temel ayarlar
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
                    Log.i(TAG, "✓ Ağ önerisi başarıyla eklendi")
                    Log.i(TAG, "  Kullanıcı bildirimi gelecek")
                    Log.i(TAG, "  Sistem otomatik olarak bağlanacak")
                    true
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                    Log.w(TAG, "⚠ Bu ağ zaten önerilmiş")
                    Log.w(TAG, "  Sistem muhtemelen zaten bağlı veya bağlanacak")
                    true
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> {
                    Log.e(TAG, "✗ Uygulama ağ önerme iznine sahip değil")
                    false
                }
                else -> {
                    Log.e(TAG, "✗ Ağ önerisi eklenemedi: $status")
                    false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSuggestion hatası", e)
            return false
        }
    }




    /**
     * Android 9 ve altı için klasik yöntem
     */
    @Suppress("DEPRECATION")
    protected fun setWPA2Network(ssid: String, password: String): Int {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "WPA2 Config oluşturuluyor:")
        Log.d(TAG, "  SSID: $cleanSsid")
        Log.d(TAG, "  Password length: ${password.length}")

        // Önce aynı SSID'li eski config'i sil
        getConfiguredNetworkBySsid(cleanSsid)?.let { existing ->
            Log.d(TAG, "Eski config siliniyor: ${existing.networkId}")
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
     * Android 10+ için Network Request yöntemi
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithNetworkRequest(ssid: String, password: String): Boolean {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Android 10+ NetworkRequest oluşturuluyor:")
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
                    Log.i(TAG, "✓ Ağ bağlantısı başarılı: $cleanSsid")
                    connectivityManager?.bindProcessToNetwork(network)

                    // Bu callback WiFiManager'daki listener'ı tetiklemez
                    // çünkü BroadcastReceiver zaten COMPLETED durumunu yakalıyor
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e(TAG, "✗ Ağ bağlantısı başarısız: $cleanSsid (timeout)")
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w(TAG, "Ağ bağlantısı kesildi: $cleanSsid")
                }
            }

            connectivityManager?.requestNetwork(request, networkCallback!!)
            Log.d(TAG, "Network request gönderildi")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "NetworkRequest hatası", e)
            return false
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Network Configuration
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Önceden kaydedilmiş ağ konfigürasyonunu SSID'ye göre bul
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
            Log.e(TAG, "Geçersiz network ID: -1")
            return false
        }

        return try {
            wifiManager.disconnect()
            Thread.sleep(500)

            val success = wifiManager.enableNetwork(networkId, true)
            Log.d(TAG, "enableNetwork($networkId) result: $success")

            if (success) {
                wifiManager.reconnect()
                Log.d(TAG, "reconnect() çağrıldı")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "enableNetwork hatası", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    protected fun setOpenNetwork(ssid: String): Int {
        val cleanSsid = ssid.trim().removeSurrounding("\"")

        Log.d(TAG, "Open network config oluşturuluyor: $cleanSsid")

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

        Log.d(TAG, "WEP network config oluşturuluyor: $cleanSsid")

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
            Log.w(TAG, "Bağlantıyı kesmek için izin yok")
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
            Log.d(TAG, "WiFi bağlantısı kesildi")
        } catch (e: Exception) {
            Log.e(TAG, "Bağlantı kesilemedi", e)
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
            Log.e(TAG, "Ağ silinemedi", e)
            false
        }
    }
}