package com.ytauto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ytauto.shizuku.ShizukuManager
import com.ytauto.worker.DownloadWorker

/**
 * BootReceiver - Zorgt dat de app acties onderneemt na een systeemherstart.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            Log.d("BootReceiver", "System boot or package update detected. Initializing YTAuto...")

            // 1. Initialiseer Shizuku verbinding
            ShizukuManager.init()

            // 2. Start een directe Smart Sync check
            val syncRequest = OneTimeWorkRequestBuilder<DownloadWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }
}
