package com.lotus.lapiswifimanager.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.lotus.lapiswifimanager.R
import com.lotus.lapiswifimanager.databinding.ActivityMainBinding
import com.lotus.lapiswifimanager.wifilibrary.SecurityModeEnum
import com.lotus.lapiswifimanager.wifilibrary.WiFiManager
import com.lotus.lapiswifimanager.wifilibrary.adapter.WifiListAdapter
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiConnectListener
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiEnabledListener
import com.lotus.lapiswifimanager.wifilibrary.listener.OnWifiScanResultsListener
import timber.log.Timber

class MainActivity : AppCompatActivity(),
    SwipeRefreshLayout.OnRefreshListener,
    AdapterView.OnItemClickListener,
    AdapterView.OnItemLongClickListener,
    OnWifiScanResultsListener,
    OnWifiConnectListener,
    OnWifiEnabledListener {

    companion object {
        private const val TAG = "LapisWifiManager"
        const val EXTRA_CONNECTED_SSID = "connected_ssid"
        const val EXTRA_CONNECTED_PASSWORD = "connected_password"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WiFiManager
    private lateinit var wifiListAdapter: WifiListAdapter

    // Letztes verwendetes Passwort waehrend der Verbindung speichern
    private var lastUsedPassword: String = ""
    private var lastConnectingSsid: String = ""

    // ═════════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.wifi_manager_title)
        }
        binding.toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        initViews()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissionsIfNeeded()
        wifiManager.apply {
            setOnWifiEnabledListener(this@MainActivity)
            setOnWifiScanResultsListener(this@MainActivity)
            setOnWifiConnectListener(this@MainActivity)
        }
        updateWifiSwitchState()
    }

    override fun onPause() {
        super.onPause()
        wifiManager.apply {
            setOnWifiEnabledListener(null)
            setOnWifiScanResultsListener(null)
            setOnWifiConnectListener(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wifiManager.isInitialized) {
            wifiManager.release()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═════════════════════════════════════════════════════════════════════════════

    private fun initViews() {
        setupWifiManager()
        setupSwipeRefresh()
        setupListView()
        requestWifiPermissions()

        binding.switchWifi.isChecked = wifiManager.isWifiEnabled()
    }

    private fun setupWifiManager() {
        wifiManager = WiFiManager.getInstance(applicationContext)

        binding.switchWifi.setOnCheckedChangeListener { buttonView, isChecked ->
            val currentWifiState = wifiManager.isWifiEnabled()

            if (isChecked && !currentWifiState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showWifiSettingsDialog(true)
                    buttonView.isChecked = false
                } else {
                    wifiManager.openWiFi()
                }
            } else if (!isChecked && currentWifiState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showWifiSettingsDialog(false)
                    buttonView.isChecked = true
                } else {
                    wifiManager.closeWiFi()
                }
            }
        }
    }

    private fun showWifiSettingsDialog(turnOn: Boolean) {
        val action = if (turnOn) "einschalten" else "ausschalten"

        AlertDialog.Builder(this)
            .setTitle("WiFi Einstellungen")
            .setMessage("Ab Android 10 müssen Sie WiFi über die Systemeinstellungen $action.")
            .setPositiveButton("Einstellungen") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) {
                    Log.e(TAG, "WiFi-Einstellungen konnten nicht geöffnet werden", e)
                    Toast.makeText(this, "Einstellungen konnten nicht geöffnet werden", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .setCancelable(true)
            .show()
    }

    private fun updateWifiSwitchState() {
        val isWifiEnabled = wifiManager.isWifiEnabled()

        binding.switchWifi.setOnCheckedChangeListener(null)
        binding.switchWifi.isChecked = isWifiEnabled

        binding.switchWifi.setOnCheckedChangeListener { buttonView, isChecked ->
            val currentWifiState = wifiManager.isWifiEnabled()

            if (isChecked && !currentWifiState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showWifiSettingsDialog(true)
                    buttonView.isChecked = false
                } else {
                    wifiManager.openWiFi()
                }
            } else if (!isChecked && currentWifiState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showWifiSettingsDialog(false)
                    buttonView.isChecked = true
                } else {
                    wifiManager.closeWiFi()
                }
            }
        }
    }

    private fun setupListView() {
        wifiListAdapter = WifiListAdapter(this)
        binding.wifiListView.adapter = wifiListAdapter
        binding.wifiListView.onItemClickListener = this
        binding.wifiListView.onItemLongClickListener = this
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Permissions
    // ═════════════════════════════════════════════════════════════════════════════

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            Log.d(TAG, "Alle Berechtigungen erteilt")
            Toast.makeText(this, "Berechtigungen erteilt", Toast.LENGTH_SHORT).show()
            wifiManager.startScan()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Timber.tag(TAG).w("Abgelehnte Berechtigungen: $deniedPermissions")

            val permanentlyDenied = deniedPermissions.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }

            if (permanentlyDenied) {
                showPermissionSettingsDialog()
            } else {
                Toast.makeText(this, "Alle Berechtigungen werden für den WiFi-Scan benötigt", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Berechtigung erforderlich")
            .setMessage("Für die WiFi-Funktionen wird die Standortberechtigung benötigt. Bitte aktivieren Sie die Berechtigungen in den App-Einstellungen.")
            .setPositiveButton("Einstellungen") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun requestWifiPermissions() {
        val permissions = getRequiredPermissions()
        requestPermissionLauncher.launch(permissions)
    }

    private fun checkAndRequestPermissionsIfNeeded() {
        val permissions = getRequiredPermissions()

        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Fehlende Berechtigungen: $missingPermissions")
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi Operations
    // ═════════════════════════════════════════════════════════════════════════════

    private fun loadWifiList() {
        val results = wifiManager.getUniqueScanResults()
        refreshData(results)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  SwipeRefreshLayout.OnRefreshListener
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onRefresh() {
        wifiManager.startScan()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  ListView Item Click
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val scanResult = wifiListAdapter.getItem(position)

        Log.d(TAG, "Gewähltes Netzwerk: SSID=${scanResult.SSID}, Security=${wifiManager.getSecurityMode(scanResult)}")

        when (wifiManager.getSecurityMode(scanResult)) {
            SecurityModeEnum.OPEN -> {
                Timber.tag(TAG).d("Verbindung mit offenem Netzwerk...")
                lastConnectingSsid = scanResult.SSID
                lastUsedPassword = ""
                val result = wifiManager.connectToOpenNetwork(scanResult.SSID)
                if (!result) {
                    Toast.makeText(this, "Verbindung konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                showPasswordDialog(scanResult)
            }
        }
    }

    private fun showPasswordDialog(scanResult: ScanResult) {
        ConnectWifiDialog(this@MainActivity) { password ->
            Log.d(TAG, "WPA2 Verbindungsversuch: SSID=${scanResult.SSID}")
            Timber.tag(TAG).d("showPasswordDialog: $scanResult")

            if (!wifiManager.isWifiEnabled()) {
                Toast.makeText(this, "Bitte schalten Sie WiFi zuerst ein!", Toast.LENGTH_SHORT).show()
                return@ConnectWifiDialog
            }

            // Passwort und SSID speichern
            lastUsedPassword = password
            lastConnectingSsid = scanResult.SSID

            val result = wifiManager.connectToWPA2Network(scanResult.SSID, password)
            Timber.tag(TAG).d("connectToWPA2Network result: $result")

            if (result) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Toast.makeText(this, "Benachrichtigung wird kommen - Netzwerk bestätigen", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Verbindung wird hergestellt...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Verbindung konnte nicht gestartet werden!", Toast.LENGTH_LONG).show()
            }
        }.apply {
            setSsid(scanResult.SSID)
            show()
        }
    }

    override fun onItemLongClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ): Boolean {
        val scanResult = wifiListAdapter.getItem(position)
        val ssid = scanResult.SSID

        AlertDialog.Builder(this)
            .setTitle(ssid)
            .setItems(arrayOf("Verbindung trennen", "Netzwerk entfernen")) { _, which ->
                when (which) {
                    0 -> disconnectNetwork(ssid)
                    1 -> removeNetwork(ssid)
                }
            }
            .show()

        return true
    }

    private fun disconnectNetwork(ssid: String) {
        val current = wifiManager.getCurrentConnectionInfo()
        if (current?.ssid?.removeSurrounding("\"") == ssid) {
            wifiManager.disconnectCurrentWifi()
            Toast.makeText(this, "Verbindung getrennt", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Sie sind nicht mit diesem Netzwerk verbunden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeNetwork(ssid: String) {
        val config = wifiManager.getConfiguredNetworkBySsid(ssid)
        if (config != null) {
            val deleted = wifiManager.removeNetwork(config.networkId)
            Toast.makeText(
                this,
                if (deleted) "Netzwerk entfernt" else "Konnte nicht entfernt werden (ROOT erforderlich)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Dieses Netzwerk ist nicht gespeichert", Toast.LENGTH_SHORT).show()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi Listener Callbacks
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onScanComplete(scanResults: List<ScanResult>) {
        Timber.tag(TAG).d("Scan abgeschlossen: ${scanResults.size} Netzwerke gefunden")
        refreshData(scanResults)
    }

    override fun onWiFiConnectLog(log: String) {
        Timber.tag(TAG).d("WiFi Log: $log")

        runOnUiThread {
            if (log.contains("Verbindung") || log.contains("überprüft") ||
                log.contains("erfolgreich") || log.contains("FEHLER") ||
                log.contains("Verbund") || log.contains("Passwort") || log.contains("Fehler")) {
                Snackbar.make(binding.wifiListView, log, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onWiFiConnectSuccess(ssid: String) {
        Timber.tag(TAG).d("Verbindung erfolgreich: $ssid")
        runOnUiThread {
            Toast.makeText(this, "Verbunden mit $ssid", Toast.LENGTH_LONG).show()

            // SSID und Passwort zurueckgeben
            val resultIntent = Intent().apply {
                putExtra(EXTRA_CONNECTED_SSID, ssid.removeSurrounding("\""))
                putExtra(EXTRA_CONNECTED_PASSWORD, lastUsedPassword)
            }
            setResult(Activity.RESULT_OK, resultIntent)

            // Mit kurzer Verzoegerung schliessen (damit der Benutzer die Meldung sieht)
            binding.wifiListView.postDelayed({
                finish()
            }, 1500)
        }
    }

    override fun onWiFiConnectFailure(ssid: String) {
        Timber.tag(TAG).w("Verbindung fehlgeschlagen: $ssid")
        runOnUiThread {
            val message = if (ssid.contains("Falsches Passwort")) {
                "Falsches Passwort!"
            } else {
                "Verbindung mit $ssid fehlgeschlagen"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onWifiEnabled(enabled: Boolean) {
        Timber.tag(TAG).d("WiFi Status: ${if (enabled) "EIN" else "AUS"}")
        runOnUiThread {
            binding.switchWifi.isChecked = enabled
            binding.frameLayoutWifi.visibility = if (enabled) VISIBLE else GONE
            if (enabled) loadWifiList()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Helper
    // ═════════════════════════════════════════════════════════════════════════════

    private fun refreshData(scanResults: List<ScanResult>?) {
        binding.swipeRefreshLayout.isRefreshing = false
        wifiListAdapter.refreshData(scanResults ?: emptyList())

        val message = "${scanResults?.size ?: 0} Netzwerke gefunden"
        Snackbar.make(binding.wifiListView, message, Snackbar.LENGTH_SHORT).show()
    }
}
