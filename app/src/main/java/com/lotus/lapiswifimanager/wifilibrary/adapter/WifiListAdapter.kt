package com.lotus.lapiswifimanager.wifilibrary.adapter

import android.content.Context
import android.graphics.Typeface
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager as AndroidWifiManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.lotus.lapiswifimanager.R
import com.lotus.lapiswifimanager.wifilibrary.SecurityModeEnum
import com.lotus.lapiswifimanager.wifilibrary.WiFiManager

/**
 * Moderner Adapter zur Auflistung der WiFi-Scan-Ergebnisse
 * - Verbundenes Netzwerk hervorheben
 * - Signalstaerke-Icons
 * - Sicherheitstyp-Icons
 * - Sauberes und lesbares Design
 */
class WifiListAdapter(private val context: Context) : BaseAdapter() {

    private val scanResults = mutableListOf<ScanResult>()
    private val wifiManager = WiFiManager.getInstance(context)
    private val systemWifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as AndroidWifiManager

    /**
     * Neue Scan-Ergebnisse uebernehmen und Liste aktualisieren
     */
    fun refreshData(newResults: List<ScanResult>?) {
        scanResults.clear()

        if (!newResults.isNullOrEmpty()) {
            // Bereits eindeutige Ergebnisse direkt hinzufuegen
            scanResults.addAll(newResults.filter { it.SSID.isNotBlank() })

            // Nach Signalstaerke sortieren (staerkstes zuerst)
            scanResults.sortByDescending { it.level }
        }

        notifyDataSetChanged()
    }

    override fun getCount(): Int = scanResults.size

    override fun getItem(position: Int): ScanResult = scanResults[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_wifi, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val scanResult = scanResults[position]
        bindData(holder, scanResult)

        return view
    }

    private fun bindData(holder: ViewHolder, scanResult: ScanResult) {
        // SSID
        holder.ssid.text = scanResult.SSID

        // Verbundenes Netzwerk pruefen
        val currentConnection = try {
            systemWifiManager.connectionInfo
        } catch (e: SecurityException) {
            null
        }

        val isConnected = currentConnection?.ssid?.removeSurrounding("\"") == scanResult.SSID

        // Verbundenes Netzwerk hervorheben
        if (isConnected) {
            holder.ssid.setTypeface(null, Typeface.BOLD)
            holder.ssid.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            holder.ivConnected?.visibility = View.VISIBLE
            holder.tvStatus?.apply {
                visibility = View.VISIBLE
                text = "Verbunden"
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
            // Orange border for connected WiFi
            holder.cardView?.apply {
                strokeColor = ContextCompat.getColor(context, R.color.wifi_toolbar_bg)
                strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            }
        } else {
            holder.ssid.setTypeface(null, Typeface.NORMAL)
            holder.ssid.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            holder.ivConnected?.visibility = View.GONE
            holder.tvStatus?.visibility = View.GONE
            // Thin gray border for non-connected WiFi
            holder.cardView?.apply {
                strokeColor = ContextCompat.getColor(context, android.R.color.darker_gray)
                strokeWidth = (0.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            }
        }

        // Sicherheitstyp
        val securityMode = wifiManager.getSecurityMode(scanResult)
        val securityText = when (securityMode) {
            SecurityModeEnum.OPEN -> "Offen"
            SecurityModeEnum.WEP -> "WEP"
            SecurityModeEnum.WPA -> "WPA"
            SecurityModeEnum.WPA2 -> "WPA2"
            SecurityModeEnum.WPA3 -> "WPA3"
        }

        holder.tvSecurity?.text = securityText

        // Sicherheits-Icon
        holder.ivSecurity?.setImageResource(
            when (securityMode) {
                SecurityModeEnum.OPEN -> R.drawable.ic_lock_open
                else -> R.drawable.ic_lock_closed
            }
        )

        // Signalstaerke
        val signalLevel = AndroidWifiManager.calculateSignalLevel(scanResult.level, 5)
        val signalPercentage = ((signalLevel / 4.0) * 100).toInt()

        holder.tvSignalLevel?.text = "$signalPercentage%"

        // Signalstaerke-Icon
        holder.ivSignalLevel?.setImageResource(
            when (signalLevel) {
                0 -> R.drawable.ic_signal_0
                1 -> R.drawable.ic_signal_1
                2 -> R.drawable.ic_signal_2
                3 -> R.drawable.ic_signal_3
                4 -> R.drawable.ic_signal_4
                else -> R.drawable.ic_signal_4
            }
        )

        // Frequenzband (2.4GHz / 5GHz)
        val frequency = scanResult.frequency
        val band = when {
            frequency in 2400..2500 -> "2.4 GHz"
            frequency in 4900..5900 -> "5 GHz"
            else -> ""
        }

        holder.tvFrequency?.apply {
            if (band.isNotEmpty()) {
                visibility = View.VISIBLE
                text = band
            } else {
                visibility = View.GONE
            }
        }
    }

    private class ViewHolder(view: View) {
        val cardView: MaterialCardView? = view.findViewById(R.id.card_wifi_item)
        val ssid: TextView = view.findViewById(R.id.ssid)
        val tvSecurity: TextView? = view.findViewById(R.id.tv_security)
        val tvSignalLevel: TextView? = view.findViewById(R.id.tv_signal_level)
        val tvFrequency: TextView? = view.findViewById(R.id.tv_frequency)
        val tvStatus: TextView? = view.findViewById(R.id.tv_status)
        val ivSecurity: ImageView? = view.findViewById(R.id.iv_security)
        val ivSignalLevel: ImageView? = view.findViewById(R.id.iv_signal_level)
        val ivConnected: ImageView? = view.findViewById(R.id.iv_connected)
    }
}