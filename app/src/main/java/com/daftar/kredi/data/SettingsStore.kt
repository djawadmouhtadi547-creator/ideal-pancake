package com.daftar.kredi.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(sp.getString("currency", "") ?: "")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _pinEnabled = MutableStateFlow(sp.getString("pin_hash", null) != null)
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private val _biometric = MutableStateFlow(sp.getBoolean("biometric", false))
    val biometricEnabled: StateFlow<Boolean> = _biometric.asStateFlow()

    fun setCurrency(value: String) {
        sp.edit().putString("currency", value).apply()
        _currency.value = value
    }

    fun setPin(pin: String) {
        sp.edit().putString("pin_hash", hash(pin)).apply()
        _pinEnabled.value = true
    }

    fun clearPin() {
        sp.edit().remove("pin_hash").putBoolean("biometric", false).apply()
        _pinEnabled.value = false
        _biometric.value = false
    }

    fun checkPin(pin: String): Boolean = sp.getString("pin_hash", null) == hash(pin)

    fun setBiometric(enabled: Boolean) {
        sp.edit().putBoolean("biometric", enabled).apply()
        _biometric.value = enabled
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("kredi-salt-$s").toByteArray())
            .joinToString("") { "%02x".format(it) }
}
