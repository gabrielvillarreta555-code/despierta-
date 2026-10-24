package com.example.despierta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentAlarmId: Int = -1

    override fun onCreate() {
        super.onCreate()
        createChannel()
        AlarmLog.info("service_created", "sdk=${Build.VERSION.SDK_INT}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        return when (intent?.action) {
            ACTION_STOP -> {
                if (currentAlarmId == -1 || currentAlarmId == id) stopAlarm("user_stop")
                else START_REDELIVER_INTENT
            }

            ACTION_SNOOZE -> {
                if (currentAlarmId == -1 || currentAlarmId == id) {
                    AlarmStore(this).find(id)?.let { AlarmScheduler(this).snooze(it) }
                    stopAlarm("user_snooze")
                } else {
                    START_REDELIVER_INTENT
                }
            }

            ACTION_RING -> startRinging(id)
            else -> {
                AlarmLog.warning("service_unknown_intent", "action=${intent?.action} id=$id")
                stopAlarm("unknown_intent")
            }
        }
    }

    private fun startRinging(id: Int): Int {
        val alarm = AlarmStore(this).find(id)
        if (id <= 0 || alarm == null) {
            AlarmLog.warning("ring_ignored", "id=$id reason=missing_alarm")
            return stopAlarm("missing_alarm")
        }

        if (currentAlarmId != -1) releaseResources()
        currentAlarmId = id
        val notification = notification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        requestAudioFocus()
        startVibration()
        startSound(alarm)
        AlarmLog.info("ring_started", "id=$id customSound=${alarm.soundUri.isNotBlank()}")
        return START_REDELIVER_INTENT
    }

    private fun notification(alarm: AlarmModel): Notification {
        val fullScreen = alarmActivityPendingIntent(alarm.id, "open")
        val snooze = servicePendingIntent(alarm.id, ACTION_SNOOZE, "snooze")
        val stop = if (alarm.mathChallengeEnabled) {
            alarmActivityPendingIntent(alarm.id, "challenge")
        } else {
            servicePendingIntent(alarm.id, ACTION_STOP, "stop")
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm.label)
            .setContentText(
                if (alarm.mathChallengeEnabled) {
                    "Resuelve el desafío para detener la alarma"
                } else {
                    "Es hora de despertar"
                }
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(
                Notification.Action.Builder(0, "Posponer 5 min", snooze).build()
            )
            .addAction(
                Notification.Action.Builder(
                    0,
                    if (alarm.mathChallengeEnabled) "Resolver" else "Detener",
                    stop
                ).build()
            )
            .build()
    }

    private fun alarmActivityPendingIntent(id: Int, operation: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            id,
            Intent(this, AlarmActivity::class.java)
                .setAction("$ACTION_OPEN.$operation")
                .setData(Uri.parse("despierta://ring/$id/$operation"))
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun servicePendingIntent(id: Int, action: String, operation: String): PendingIntent =
        PendingIntent.getService(
            this,
            id,
            Intent(this, AlarmService::class.java)
                .setAction(action)
                .setData(Uri.parse("despierta://ring/$id/$operation"))
                .putExtra(AlarmScheduler.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun startSound(alarm: AlarmModel) {
        player?.runCatching { stop() }
        player?.release()
        player = null

        val candidates = listOfNotNull(
            alarm.soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinctBy(Uri::toString)

        for (sound in candidates) {
            val candidate = MediaPlayer()
            val started = runCatching {
                candidate.setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
                candidate.setDataSource(this, sound)
                candidate.isLooping = true
                candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                candidate.prepare()
                candidate.start()
            }.onFailure {
                AlarmLog.warning("sound_failed", "id=${alarm.id} uri=$sound", it)
                candidate.release()
            }.isSuccess

            if (started) {
                player = candidate
                AlarmLog.info(
                    "sound_started",
                    "id=${alarm.id} fallback=${sound.toString() != alarm.soundUri} uri=$sound"
                )
                return
            }
        }
        AlarmLog.error("sound_unavailable", "id=${alarm.id}; vibration remains active")
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener { change ->
                AlarmLog.info("audio_focus_changed", "change=$change id=$currentAlarmId")
            }
            .build()
            .also { request ->
                val result = audioManager.requestAudioFocus(request)
                AlarmLog.info("audio_focus_requested", "result=$result id=$currentAlarmId")
            }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        vibrator?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0L, 800L, 400L, 800L), 0),
            ALARM_AUDIO_ATTRIBUTES
        )
        AlarmLog.info("vibration_started", "available=${vibrator?.hasVibrator() == true}")
    }

    private fun acquireWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Despierta:Alarm"
        ).apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun stopAlarm(reason: String): Int {
        AlarmLog.info("ring_stopped", "id=$currentAlarmId reason=$reason")
        releaseResources()
        currentAlarmId = -1
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun releaseResources() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        audioFocusRequest?.let { request ->
            getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request)
        }
        audioFocusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarmas activas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarmas de Despierta que están sonando"
            setSound(null, null)
            enableVibration(false) // The foreground service owns the repeating vibration.
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        releaseResources()
        AlarmLog.info("service_destroyed", "id=$currentAlarmId")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ringing_alarms_v2"
        const val ACTION_RING = "com.example.despierta.action.RING"
        const val ACTION_STOP = "com.example.despierta.action.STOP"
        const val ACTION_SNOOZE = "com.example.despierta.action.SNOOZE"
        private const val ACTION_OPEN = "com.example.despierta.action.OPEN_ALARM"
        private const val NOTIFICATION_ID = 41_001
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15 * 60_000L

        private val ALARM_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
