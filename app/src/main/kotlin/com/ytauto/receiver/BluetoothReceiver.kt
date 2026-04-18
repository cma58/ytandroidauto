package com.ytauto.receiver

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytauto.service.PlaybackService

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val deviceClass = device?.bluetoothClass?.deviceClass
            
            // 1056 staat voor AUDIO_VIDEO_CAR_AUDIO (Auto Bluetooth systemen)
            if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
                // Stuur een veilig commando naar je service om af te spelen
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = "ACTION_AUTO_PLAY"
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
