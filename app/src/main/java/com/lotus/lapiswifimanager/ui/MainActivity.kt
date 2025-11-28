package com.lotus.lapiswifimanager.ui

import android.Manifest
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

class MainActivity : AppCompatActivity(),
    SwipeRefreshLayout.OnRefreshListener,
    AdapterView.OnItemClickListener,
    AdapterView.OnItemLongClickListener,
    OnWifiScanResultsListener,
    OnWifiConnectListener,
    OnWifiEnabledListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WiFiManager
    private lateinit var wifiListAdapter: WifiListAdapter

    // ═════════════════════════════════════════════════════════════════════════════
    //  Lifecycle Methods
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
    }

    override fun onResume() {
        super.onResume()

        // İzinleri tekrar kontrol et
        checkAndRequestPermissionsIfNeeded()

        wifiManager.apply {
            setOnWifiEnabledListener(this@MainActivity)
            setOnWifiScanResultsListener(this@MainActivity)
            setOnWifiConnectListener(this@MainActivity)
        }
        // WiFi durumunu güncelle
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

    // ═════════════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═════════════════════════════════════════════════════════════════════════════

    private fun initViews() {
        setupWifiManager()
        setupSwipeRefresh()
        setupListView()
        requestWifiPermissions()

        // WiFi anahtarını güncel duruma göre ayarla
        binding.switchWifi.isChecked = wifiManager.isWifiEnabled()
    }

    private fun setupWifiManager() {
        wifiManager = WiFiManager.getInstance(applicationContext)

        binding.switchWifi.setOnCheckedChangeListener { buttonView, isChecked ->
            // Kullanıcı switch'i değiştirdiğinde
            val currentWifiState = wifiManager.isWifiEnabled()

            if (isChecked && !currentWifiState) {
                // WiFi açılmak isteniyor ama kapalı
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ için ayarlara yönlendir
                    showWifiSettingsDialog(true)
                    // Switch'i geri eski haline getir
                    buttonView.isChecked = false
                } else {
                    // Android 9 ve altı için direkt aç
                    wifiManager.openWiFi()
                }
            } else if (!isChecked && currentWifiState) {
                // WiFi kapatılmak isteniyor ama açık
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ için ayarlara yönlendir
                    showWifiSettingsDialog(false)
                    // Switch'i geri eski haline getir
                    buttonView.isChecked = true
                } else {
                    // Android 9 ve altı için direkt kapat
                    wifiManager.closeWiFi()
                }
            }
        }
    }

    /**
     * WiFi ayarlarına yönlendirme dialogu
     */
    private fun showWifiSettingsDialog(turnOn: Boolean) {
        val action = if (turnOn) "açmak" else "kapatmak"
        val instruction = if (turnOn) "açın" else "kapatın"

        AlertDialog.Builder(this)
            .setTitle("WiFi Ayarları")
            .setMessage("Android 10 ve üzeri sürümlerde WiFi'yi $action için sistem ayarlarına gitmeniz gerekiyor.\n\nAyarlardan WiFi'yi $instruction.")
            .setPositiveButton("Ayarlara Git") { _, _ ->
                try {
                    // WiFi ayarlarını aç
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "WiFi ayarları açılamadı", e)
                    Toast.makeText(
                        this,
                        "Ayarlar açılamadı",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("İptal", null)
            .setCancelable(true)
            .show()
    }

    /**
     * Switch'in durumunu WiFi'nin gerçek durumuna göre güncelle
     */
    private fun updateWifiSwitchState() {
        val isWifiEnabled = wifiManager.isWifiEnabled()

        // Listener'ı geçici olarak devre dışı bırak ki döngüye girmesin
        binding.switchWifi.setOnCheckedChangeListener(null)
        binding.switchWifi.isChecked = isWifiEnabled

        // Listener'ı tekrar aktif et
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
            Log.d(TAG, "✓ Tüm izinler verildi")
            Toast.makeText(this, "İzinler verildi", Toast.LENGTH_SHORT).show()

            // İzinler verildikten sonra tarama yap
            wifiManager.startScan()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.w(TAG, "✗ Reddedilen izinler: $deniedPermissions")

            // Kalıcı olarak reddedilmiş mi kontrol et
            val permanentlyDenied = deniedPermissions.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }

            if (permanentlyDenied) {
                showPermissionSettingsDialog()
            } else {
                Toast.makeText(
                    this,
                    "WiFi taraması için tüm izinler gereklidir",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("İzin Gerekli")
            .setMessage("WiFi özellikleri için konum izni gereklidir. Lütfen uygulama ayarlarından izinleri açın.")
            .setPositiveButton("Ayarlara Git") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun requestWifiPermissions() {
        val permissions = when {
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

        requestPermissionLauncher.launch(permissions)
    }


    private fun checkAndRequestPermissionsIfNeeded() {
        val permissions = when {
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

        // İzinleri kontrol et
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Eksik izinler tespit edildi: $missingPermissions")

            // Kullanıcıya bilgi ver
            AlertDialog.Builder(this)
                .setTitle("İzin Gerekli")
                .setMessage("WiFi ağlarını taramak için konum izni gereklidir. İzinleri yeniden vermek ister misiniz?")
                .setPositiveButton("İzin Ver") { _, _ ->
                    requestPermissionLauncher.launch(permissions)
                }
                .setNegativeButton("İptal", null)
                .show()
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
    //  ListView Item Click Listeners
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val scanResult = wifiListAdapter.getItem(position)

        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "Seçilen ağ:")
        Log.d(TAG, "  SSID: ${scanResult.SSID}")
        Log.d(TAG, "  BSSID: ${scanResult.BSSID}")
        Log.d(TAG, "  Capabilities: ${scanResult.capabilities}")
        Log.d(TAG, "  Level: ${scanResult.level}")
        Log.d(TAG, "  Security: ${wifiManager.getSecurityMode(scanResult)}")
        Log.d(TAG, "  Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "═══════════════════════════════════════")

        when (wifiManager.getSecurityMode(scanResult)) {
            SecurityModeEnum.OPEN -> {
                Log.d(TAG, "Açık ağa bağlanılıyor...")
                val result = wifiManager.connectToOpenNetwork(scanResult.SSID)
                if (!result) {
                    Toast.makeText(this, "Bağlantı başlatılamadı", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                showPasswordDialog(scanResult)
            }
        }
    }

    private fun showPasswordDialog(scanResult: ScanResult) {
        ConnectWifiDialog(this@MainActivity) { password ->
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "WPA2 Bağlantı Denemesi:")
            Log.d(TAG, "  SSID: ${scanResult.SSID}")
            Log.d(TAG, "  Şifre uzunluğu: ${password.length}")
            Log.d(TAG, "  Android SDK: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "  WiFi Durumu: ${if (wifiManager.isWifiEnabled()) "AÇIK" else "KAPALI"}")
            Log.d(TAG, "═══════════════════════════════════════")

            // WiFi açık mı kontrol et
            if (!wifiManager.isWifiEnabled()) {
                Toast.makeText(this, "Önce WiFi'yi açın!", Toast.LENGTH_SHORT).show()
                return@ConnectWifiDialog
            }

            // Bağlantıyı başlat
            val result = wifiManager.connectToWPA2Network(scanResult.SSID, password)
            Log.d(TAG, "connectToWPA2Network sonucu: $result")

            // Kullanıcıya bilgi ver
            if (result) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Toast.makeText(
                        this,
                        "Bildirim gelecek - Ağı onaylayın",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Bağlantı başlatılıyor...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Bağlantı başlatılamadı! Logları kontrol edin.",
                    Toast.LENGTH_LONG
                ).show()
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
        val scanResult = wifiListAdapter.getItem(position) as ScanResult
        val ssid = scanResult.SSID

        AlertDialog.Builder(this)
            .setTitle(ssid)
            .setItems(arrayOf("Bağlantıyı Kes", "Ağ Konfigürasyonunu Sil")) { _, which ->
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
            Toast.makeText(this, "Bağlantı kesildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bu ağa bağlı değilsiniz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeNetwork(ssid: String) {
        val config = wifiManager.getConfiguredNetworkBySsid(ssid)
        if (config != null) {
            val deleted = wifiManager.removeNetwork(config.networkId)
            Toast.makeText(
                this,
                if (deleted) "Ağ silindi" else "Silinemedi (ROOT gerekebilir)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Bu ağ kayıtlı değil", Toast.LENGTH_SHORT).show()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  WiFi Listener Callbacks
    // ═════════════════════════════════════════════════════════════════════════════

    override fun onScanComplete(scanResults: List<ScanResult>) {
        Log.d(TAG, "Tarama tamamlandı: ${scanResults.size} ağ bulundu")
        refreshData(scanResults)
    }

    override fun onWiFiConnectLog(log: String) {
        Log.i(TAG, "WiFi Log: $log")
        runOnUiThread {
            // Sadece önemli mesajları göster
            if (log.contains("Bağlanıyor") ||
                log.contains("doğrulan") ||
                log.contains("başarılı") ||
                log.contains("HATA")
            ) {
                Snackbar.make(binding.wifiListView, log, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onWiFiConnectSuccess(ssid: String) {
        Log.i(TAG, "✓ Bağlantı başarılı: $ssid")
        runOnUiThread {
            Toast.makeText(
                this,
                "✓ $ssid ağına bağlandı!",
                Toast.LENGTH_LONG
            ).show()

            // Liste güncellenmeden önce kısa bekle
            binding.wifiListView.postDelayed({
                loadWifiList()
            }, 500)
        }
    }

    override fun onWiFiConnectFailure(ssid: String) {
        Log.w(TAG, "✗ Bağlantı başarısız: $ssid")
        runOnUiThread {
            val message = if (ssid.contains("Yanlış şifre")) {
                "✗ Yanlış şifre!"
            } else {
                "✗ $ssid ağına bağlanılamadı"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onWifiEnabled(enabled: Boolean) {
        Log.d(TAG, "WiFi durumu değişti: ${if (enabled) "AÇIK" else "KAPALI"}")
        runOnUiThread {
            binding.switchWifi.isChecked = enabled
            binding.frameLayoutWifi.visibility = if (enabled) VISIBLE else GONE
            if (enabled) loadWifiList()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  Helper Methods
    // ═════════════════════════════════════════════════════════════════════════════

    private fun refreshData(scanResults: List<ScanResult>?) {
        binding.swipeRefreshLayout.isRefreshing = false
        wifiListAdapter.refreshData(scanResults ?: emptyList())

        val message = "${scanResults?.size ?: 0} ağ bulundu"
        Snackbar.make(binding.wifiListView, message, Snackbar.LENGTH_SHORT).show()
    }
}