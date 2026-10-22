package com.example.despierta

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Despierta:Alarm"
        ).apply { acquire(10 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_STOP -> return stopAlarm()
            ACTION_SNOOZE -> {
                val alarm = AlarmStore(this).find(id)
                if (alarm != null) scheduleSnooze(alarm)
                return stopAlarm()
            }
        }
        val alarm = AlarmStore(this).find(id) ?: return stopAlarm()
        startForeground(id, notification(alarm))
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(this@AlarmService, Uri.parse(alarm.soundUri))
            isLooping = true
            prepare()
            start()
        }
        return START_NOT_STICKY
    }

    private fun notification(alarm: AlarmModel): Notification {
        val fullScreen = PendingIntent.getActivity(this, alarm.id,
            Intent(this, AlarmActivity::class.java).putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, action: String, request: Int) = Notification.Action.Builder(
            0, name, PendingIntent.getService(this, request,
                Intent(this, AlarmService::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.despierta.R.drawable.ic_alarm)
            .setContentTitle(alarm.label).setContentText("Es hora de despertar")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .addAction(action("Posponer 5 min", ACTION_SNOOZE, alarm.id + 1))
            .addAction(action("Detener", ACTION_STOP, alarm.id + 2)).build()
    }

    private fun scheduleSnooze(alarm: AlarmModel) {
        val pending = PendingIntent.getBroadcast(this, alarm.id,
            Intent(this, AlarmReceiver::class.java).putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5 * 60_000L, pending)
    }

    private fun stopAlarm(): Int {
        player?.run { stop(); release() }; player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val soundless = NotificationChannel(CHANNEL_ID, "Alarmas activas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarmas que están sonando"; setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(soundless)
    }

    override fun onDestroy() { stopAlarm(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ringing_alarms"
        const val ACTION_STOP = "com.example.despierta.STOP"
        const val ACTION_SNOOZE = "com.example.despierta.SNOOZE"
    }
}
