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
import com.lotus.lapiswifimanager.R
import com.lotus.lapiswifimanager.wifilibrary.SecurityModeEnum
import com.lotus.lapiswifimanager.wifilibrary.WiFiManager

/**
 * WiFi tarama sonuçlarını listeleyen modern adapter
 * - Bağlı ağı vurgular
 * - Sinyal gücü ikonları
 * - Güvenlik türü ikonları
 * - Temiz ve okunabilir tasarım
 */
class WifiListAdapter(private val context: Context) : BaseAdapter() {

    private val scanResults = mutableListOf<ScanResult>()
    private val wifiManager = WiFiManager.getInstance(context)
    private val systemWifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as AndroidWifiManager

    /**
     * Yeni tarama sonuçlarını alır ve listeyi yeniler
     */
    fun refreshData(newResults: List<ScanResult>?) {
        scanResults.clear()

        if (!newResults.isNullOrEmpty()) {
            // Zaten unique sonuçlar geliyorsa direkt ekle
            scanResults.addAll(newResults.filter { it.SSID.isNotBlank() })

            // Sinyal gücüne göre sırala (en güçlüden zayıfa)
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

        // Bağlı ağı kontrol et
        val currentConnection = try {
            systemWifiManager.connectionInfo
        } catch (e: SecurityException) {
            null
        }

        val isConnected = currentConnection?.ssid?.removeSurrounding("\"") == scanResult.SSID

        // Bağlı ağı vurgula
        if (isConnected) {
            holder.ssid.setTypeface(null, Typeface.BOLD)
            holder.ssid.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            holder.ivConnected?.visibility = View.VISIBLE
            holder.tvStatus?.apply {
                visibility = View.VISIBLE
                text = "Bağlı"
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
        } else {
            holder.ssid.setTypeface(null, Typeface.NORMAL)
            holder.ssid.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            holder.ivConnected?.visibility = View.GONE
            holder.tvStatus?.visibility = View.GONE
        }

        // Güvenlik türü
        val securityMode = wifiManager.getSecurityMode(scanResult)
        val securityText = when (securityMode) {
            SecurityModeEnum.OPEN -> "Açık"
            SecurityModeEnum.WEP -> "WEP"
            SecurityModeEnum.WPA -> "WPA"
            SecurityModeEnum.WPA2 -> "WPA2"
            SecurityModeEnum.WPA3 -> "WPA3"
        }

        holder.tvSecurity?.text = securityText

        // Güvenlik ikonu
        holder.ivSecurity?.setImageResource(
            when (securityMode) {
                SecurityModeEnum.OPEN -> R.drawable.ic_lock_open
                else -> R.drawable.ic_lock_closed
            }
        )

        // Sinyal gücü
        val signalLevel = AndroidWifiManager.calculateSignalLevel(scanResult.level, 5)
        val signalPercentage = ((signalLevel / 4.0) * 100).toInt()

        holder.tvSignalLevel?.text = "$signalPercentage%"

        // Sinyal gücü ikonu
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

        // Frekans bandı (2.4GHz / 5GHz)
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