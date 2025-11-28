package com.lotus.lapiswifimanager.wifilibrary

/**
 * WiFi ağlarının güvenlik/şifreleme türlerini temsil eden enum
 *
 * Desteklenen türler:
 * - OPEN  → Açık (şifresiz)
 * - WEP   → Eski ve güvensiz şifreleme
 * - WPA   → WiFi Protected Access
 * - WPA2  → Daha güvenli (şu anda en yaygın)
 *
 * Orijinal yazar: kongqingwei (2017)
 */
enum class SecurityModeEnum {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3
}