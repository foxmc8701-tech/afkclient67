package app.afkconsole

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*

/** Foreground service so connections survive the screen turning off. Holds all runners. */
class AfkService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("afk", "AFK sessions", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "afk").setContentTitle("AFK Console running")
            .setContentText("Keeping your accounts connected").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true).build()
        startForeground(1, n)
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "afk:svc").apply { acquire() }
    }

    override fun onDestroy() {
        Hub.stopAll()
        wake?.release()
        super.onDestroy()
    }

}
