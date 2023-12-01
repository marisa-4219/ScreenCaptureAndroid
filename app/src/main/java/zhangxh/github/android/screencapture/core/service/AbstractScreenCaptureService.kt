package zhangxh.github.android.screencapture.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import zhangxh.github.android.screencapture.R
import java.util.UUID

abstract class AbstractScreenCaptureService(
    private val CHANNEL_ID: String = UUID.randomUUID().toString(),
    private val NOTIFICATION_ID: Int = System.currentTimeMillis().toInt()
) : Service() {

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                notificationChannel
            )

            val notification =
                Notification.Builder(this, CHANNEL_ID).setContentTitle("ScreenRecordService")
                    .setContentText("Record a screen...")
                    .setSmallIcon(R.drawable.ic_launcher_foreground).build()
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return Binder()
    }

    override fun onDestroy() {
        stopForeground(NOTIFICATION_ID)
    }

    fun getChannelID(): String = CHANNEL_ID

    fun getNotificationID(): Int = NOTIFICATION_ID
}