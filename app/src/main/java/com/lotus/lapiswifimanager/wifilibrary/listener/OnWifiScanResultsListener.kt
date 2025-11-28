package com.lotus.lapiswifimanager.wifilibrary.listener

import android.net.wifi.ScanResult

/**
 * WiFi tarama sonuçlarını dinleyen gelişmiş arayüz
 */
interface OnWifiScanResultsListener {

    /**
     * WiFi taraması başladığında çağrılır (opsiyonel)
     */
    fun onScanStarted() {
        // Varsayılan implementasyon - override etmek opsiyonel
    }

    /**
     * WiFi taraması tamamlandığında çağrılır
     *
     * @param results Bulunan tüm WiFi ağları
     */
    fun onScanComplete(scanResults: List<ScanResult>)

    /**
     * WiFi taraması başarısız olduğunda çağrılır (opsiyonel)
     *
     * @param reason Hata nedeni
     */
    fun onScanFailed(reason: String) {
        // Varsayılan implementasyon - override etmek opsiyonel
    }
}