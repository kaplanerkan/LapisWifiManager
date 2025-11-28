package com.lotus.lapiswifimanager.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import com.lotus.lapiswifimanager.R
import com.lotus.lapiswifimanager.databinding.DialogConnectWifiBinding

class ConnectWifiDialog(
    context: Context,
    private val onConnectClick: (String) -> Unit
) : Dialog(context, R.style.ShareDialog) {

    // ViewBinding – null-safe + memory leak yok
    private var _binding: DialogConnectWifiBinding? = null
    private val binding get() = _binding!! // sadece _binding null değilse kullanılır

    // show()'dan önce setSsid çağrılırsa diye geçici saklarız
    private var pendingSsid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = DialogConnectWifiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Daha önce setSsid çağrıldıysa şimdi uygula
        pendingSsid?.let { ssid ->
            binding.tvSsid.text = ssid
            pendingSsid = null
        }

        setupClickListeners()
        setupWindow()

        binding.etPwd.requestFocus()
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnConnect.setOnClickListener {
            val password = binding.etPwd.text.toString().trim()
            if (password.isNotEmpty()) {
                onConnectClick(password)
                dismiss()
            } else {
                binding.etPwd.error = "Şifre giriniz"
            }
        }
    }

    /** Dışarıdan SSID ayarla – show()'dan önce/sonra fark etmez, crash vermez! */
    fun setSsid(ssid: String): ConnectWifiDialog {
        if (_binding != null) {
            binding.tvSsid.text = ssid
        } else {
            pendingSsid = ssid
        }
        return this
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val params = attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER
            params.windowAnimations = R.style.AnimBottom
            attributes = params
        }
        setCancelable(true)
        setCanceledOnTouchOutside(false)
    }

    // Memory leak önlemek için ÇOK ÖNEMLİ!
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _binding = null
    }
}