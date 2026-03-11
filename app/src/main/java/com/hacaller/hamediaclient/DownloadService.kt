package com.hacaller.hamediaclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {
    private val CHANNEL_ID = "download_channel"
    private val HOST = "192.168.18.30"
    private val USER = "android"
    private val PASS = "android"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val remotePath = intent?.getStringExtra("REMOTE_PATH") ?: return START_NOT_STICKY
        val fileName = intent.getStringExtra("FILE_NAME") ?: "file"

        // 1. Create the Notification
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading from Debian")
            .setContentText("Fetching $fileName...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        // 2. Start Foreground (Must match manifest type)
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        // 3. Run the SSH Download in a Coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val destFile = File(getExternalFilesDir(null), fileName)
                // Use the download function we wrote earlier
                downloadWithProgress(HOST, USER, PASS, remotePath, destFile) { progress ->
                    updateNotification(fileName, progress)
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun updateNotification(name: String, progress: Float) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $name")
            .setProgress(100, (progress * 100).toInt(), false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}