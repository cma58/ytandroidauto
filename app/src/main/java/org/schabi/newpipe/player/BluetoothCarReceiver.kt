package org.schabi.newpipe.player

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.schabi.newpipe.player.notification.NotificationConstants

/**
 * Resumes playback automatically when a car Bluetooth audio system connects.
 *
 * Only triggers for [BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO] to avoid false positives
 * with headphones or speakers. If [PlayerService] is already running with content loaded,
 * [NotificationConstants.ACTION_PLAY_PAUSE] will resume it. If the service isn't running
 * the intent is a no-op (Android Auto will show the browse tree instead).
 */
class BluetoothCarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device?.bluetoothClass?.deviceClass != BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) return

        context.startService(
            Intent(context, PlayerService::class.java).apply {
                action = NotificationConstants.ACTION_PLAY_PAUSE
            }
        )
    }
}
