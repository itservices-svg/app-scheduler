package com.example.appscheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Schedule ke launch par alarm bajata hai (30 second tak ya Stop dabane tak). */
class AlarmSoundService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.appscheduler.STOP_ALARM"
        const val EXTRA_SOUND = "sound"
        private const val CHANNEL = "alarm_sound"
        private const val NOTIF_ID = 42
        private const val MAX_MS = 30_000L

        fun start(ctx: Context, sound: String) {
            val i = Intent(ctx, AlarmSoundService::class.java).putExtra(EXTRA_SOUND, sound)
            try {
                ctx.startForegroundService(i)
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AlarmSoundService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }

        play(intent?.getStringExtra(EXTRA_SOUND) ?: "default")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, MAX_MS)
        return START_NOT_STICKY
    }

    private fun defaultUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun play(sound: String) {
        release()
        val wanted: Uri? = if (sound == "default" || sound.isEmpty()) defaultUri() else Uri.parse(sound)
        if (!tryPlay(wanted)) tryPlay(defaultUri())
    }

    private fun tryPlay(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun release() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Schedule alarm", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("App Scheduler alarm")
            .setContentText("Alarm band karne ke liye tap karo")
            .setContentIntent(stopPi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        release()
        super.onDestroy()
    }
}
