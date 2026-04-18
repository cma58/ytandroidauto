package com.ytauto.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

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

    fun runCommand(command: String): String {
        if (!hasPermission.value) return "No permission"
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Probeert de video-restrictie tijdens het rijden te omzeilen.
     * Dit werkt door de 'speed_checking' of vergelijkbare instellingen te beïnvloeden via ADB.
     */
    fun disableDrivingRestrictions() {
        // Enkele bekende ADB commando's die kunnen helpen (afhankelijk van device/AA versie)
        val commands = listOf(
            "settings put secure car_speed_limit_mask 0",
            "settings put secure car_parking_brake_required 0",
            "settings put secure car_gear_check_required 0"
        )
        commands.forEach { cmd ->
            val result = runCommand(cmd)
            Log.d(TAG, "Executed '$cmd': $result")
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
