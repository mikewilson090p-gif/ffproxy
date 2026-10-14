package com.venice.ffproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProxyService : Service() {

    companion object {
        const val EXTRA_PORT = "port"
        const val EXTRA_MITM = "mitm"
        private const val CH = "ffproxy"
        private const val ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CaStore.init(filesDir)
        RulesStore.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CH) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CH, "FFProxy", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        val mitm = intent?.getBooleanExtra(EXTRA_MITM, true) ?: true

        startForeground(ID, notification(port))
        runCatching { ProxyEngine.start(port, mitm) }
            .onFailure { LogStore.log("ERR", "falha ao subir proxy: " + it.message) }
        return START_STICKY
    }

    override fun onDestroy() {
        ProxyEngine.stop()
        super.onDestroy()
    }

    private fun notification(port: Int): Notification =
        NotificationCompat.Builder(this, CH)
            .setContentTitle("FFProxy ativo")
            .setContentText("Ouvindo na porta " + port)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
}