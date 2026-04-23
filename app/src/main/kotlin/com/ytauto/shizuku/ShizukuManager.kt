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
        try {
            val available = Shizuku.pingBinder()
            _isAvailable.value = available
            if (available) {
                _hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                _hasPermission.value = false
            }
            Log.d(TAG, "Shizuku state check - Available: $available, HasPermission: ${_hasPermission.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
            _isAvailable.value = false
            _hasPermission.value = false
        }
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.manager", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestPermission() {
        if (!isAvailable.value) {
            Log.e(TAG, "Cannot request permission: Shizuku is not running")
            return
        }
        
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku version is too old")
            return
        }
        
        try {
            Log.d(TAG, "Requesting Shizuku permission...")
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    // Shizuku.newProcess is @hide in de publieke API — via reflectie benaderen om visibility errors te voorkomen.
    private val newProcessMethod by lazy {
        try {
            Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reflect newProcess", e)
            null
        }
    }

    private fun executeShizukuProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val method = newProcessMethod ?: throw IllegalStateException("Shizuku newProcess method not found via reflection")
        return method.invoke(null, cmd, env, dir) as Process
    }

    fun runCommand(command: String): String {
        if (!_isAvailable.value) return "Error: Shizuku not running"
        if (!_hasPermission.value) return "Error: No Shizuku permission"

        Log.d(TAG, "Running command: $command")
        return try {
            val process = executeShizukuProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val errors = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errors.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.e(TAG, "Command failed with exit code $exitCode. Errors: $errors")
                "Error (Exit $exitCode): $errors".trim()
            } else {
                output.toString().trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception running command: $command", e)
            "Exception: ${e.message}"
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
        forceAndroidAutoWhitelist()
    }

    /**
     * De Ultimate Hack: Voegt de app geforceerd toe aan de interne whitelist van Android Auto
     * via de Google Play Services phenotype database.
     */
    fun forceAndroidAutoWhitelist() {
        if (!isAvailable.value || !hasPermission.value) return

        val packageName = "com.ytauto"
        
        // Het SQLite commando om de verborgen database van Google Play Services te beïnvloeden
        val command = "sqlite3 /data/data/com.google.android.gms/databases/phenotype.db " +
                "\"UPDATE FlagOverrides SET stringVal = stringVal || ',$packageName' " +
                "WHERE name='app_whitelist' AND packageName='com.google.android.projection.gearhead';\""

        try {
            runCommand(command)
            // Force-stop Android Auto zodat hij de gehackte lijst opnieuw inlaadt
            runCommand("am force-stop com.google.android.projection.gearhead")
            Log.d(TAG, "Android Auto whitelist hack attempted and AA force-stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Whitelist hack failed", e)
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
