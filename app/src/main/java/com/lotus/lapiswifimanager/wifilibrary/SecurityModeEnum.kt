package com.lotus.lapiswifimanager.wifilibrary

/**
 * Enum fuer Sicherheits-/Verschluesselungstypen von WiFi-Netzwerken
 *
 * Unterstuetzte Typen:
 * - OPEN  -> Offen (ohne Passwort)
 * - WEP   -> Alte und unsichere Verschluesselung
 * - WPA   -> WiFi Protected Access
 * - WPA2  -> Sicherer (derzeit am weitesten verbreitet)
 *
 * Urspruenglicher Autor: kongqingwei (2017)
 */
enum class SecurityModeEnum {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3
}