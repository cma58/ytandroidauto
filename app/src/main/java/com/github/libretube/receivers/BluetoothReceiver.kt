package com.github.libretube.receivers

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.libretube.services.AutoMediaLibraryService

/**
 * Starts playback automatically when a car Bluetooth audio system connects.
 * Triggers only for AUDIO_VIDEO_CAR_AUDIO device class to avoid false positives
 * with headphones, speakers, etc.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device?.bluetoothClass?.deviceClass != BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) return

        context.startForegroundService(
            Intent(context, AutoMediaLibraryService::class.java).apply {
                action = ACTION_AUTO_PLAY
            }
        )
    }

    companion object {
        const val ACTION_AUTO_PLAY = "com.github.libretube.ACTION_AUTO_PLAY"
    }
}
