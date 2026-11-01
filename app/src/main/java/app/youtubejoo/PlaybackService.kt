package app.youtubejoo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/** Shared playback state, written by MainActivity's MediaSession delegate. */
object Playback {
    @Volatile var media: MediaSession? = null
    @Volatile var playing = false
    @Volatile var title: String = ""
}

/** Foreground service that keeps the process alive (and shows controls) while media plays in the background. */
class PlaybackService : Service() {
    companion object {
        const val ACTION_REFRESH = "app.youtubejoo.REFRESH"
        const val ACTION_TOGGLE = "app.youtubejoo.TOGGLE"
        const val ACTION_STOP = "app.youtubejoo.STOP"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseLock()
        super.onDestroy()
    }

    private fun updateLock() {
        if (Playback.playing) {
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouTubeJoo:playback")
            }
            wakeLock?.let { if (!it.isHeld) it.acquire(6 * 60 * 60 * 1000L) }
        } else releaseLock()
    }

    private fun releaseLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Playback.media?.let { if (Playback.playing) it.pause() else it.play() }
            ACTION_STOP -> {
                Playback.media?.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateLock()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(a: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, PlaybackService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(Playback.title.ifBlank { getString(R.string.app_name) })
            .setContentText(getString(if (Playback.playing) R.string.notif_playing else R.string.notif_paused))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(if (Playback.playing) R.string.pause else R.string.play), action(ACTION_TOGGLE, 1))
            .addAction(0, getString(R.string.stop), action(ACTION_STOP, 2))
            .build()
    }
}
