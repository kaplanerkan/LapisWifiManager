package com.lotus.lapiswifimanager.wifilibrary.listener

/**
 * WiFi bağlantı durumlarını dinlemek için kullanılan callback arayüzü
 *
 */
interface OnWifiConnectListener {

    /**
     * WiFi bağlantı sürecindeki log/adım bilgilerini bildirir
     *
     * @param log Gösterilecek log mesajı
     */
    fun onWiFiConnectLog(log: String)

    /**
     * WiFi'ye başarıyla bağlanıldığında çağrılır
     *
     * @param ssid Bağlanılan WiFi'nin adı (SSID)
     */
    fun onWiFiConnectSuccess(ssid: String)

    /**
     * WiFi'ye bağlanma başarısız olduğunda çağrılır
     *
     * @param ssid Bağlanılmaya çalışılan WiFi'nin adı (SSID)
     */
    fun onWiFiConnectFailure(ssid: String)
}