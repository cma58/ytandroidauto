package com.ytauto.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ShizukuManager - Beheert de verbinding met de Shizuku service.
 * Shizuku geeft ons ADB-rechten zonder root, wat essentieel is voor
 * systeem-hacks op Android Auto.
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAvailability()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        _hasPermission.value = result == PackageManager.PERMISSION_GRANTED
    }

    fun init() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        checkAvailability()
    }

    fun checkAvailability() {
        val available = Shizuku.pingBinder()
        _isAvailable.value = available
        if (available) {
            _hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Shizuku available: $available, permission: ${_hasPermission.value}")
    }

    fun requestPermission() {
        if (Shizuku.isPreV11()) {
            // Voor oude versies van Shizuku
            Log.w(TAG, "Shizuku version is too old")
            return
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
