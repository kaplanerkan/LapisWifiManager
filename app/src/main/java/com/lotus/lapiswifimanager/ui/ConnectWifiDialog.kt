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

    // ViewBinding – null-safe + kein Memory Leak
    private var _binding: DialogConnectWifiBinding? = null
    private val binding get() = _binding!! // nur verwenden wenn _binding nicht null ist

    // Temporaer speichern falls setSsid vor show() aufgerufen wird
    private var pendingSsid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = DialogConnectWifiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Falls setSsid zuvor aufgerufen wurde, jetzt anwenden
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
                binding.etPwd.error = "Bitte Passwort eingeben"
            }
        }
    }

    /** SSID von aussen setzen – funktioniert vor/nach show(), kein Crash! */
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

    // SEHR WICHTIG um Memory Leaks zu vermeiden!
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _binding = null
    }
}