package com.lotus.lapiswifimanager.wifilibrary.listener

/**
 * WiFi'nin açılıp kapanma durumunu bildiren callback arayüzü
 *
 */
interface OnWifiEnabledListener {

    /**
     * WiFi açılıp kapandığında çağrılır
     *
     * @param enabled true  → WiFi açık
     *                false → WiFi kapalı
     */
    fun onWifiEnabled(enabled: Boolean)
}