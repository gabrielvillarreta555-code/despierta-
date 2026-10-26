package com.example.despierta

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryInstrumentedTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AlarmStore(context)
    private var alarmId = -1

    @Before
    fun setUp() {
        AlarmService.ensureNotificationChannel(context)
    }

    @After
    fun cleanUp() {
        turnScreenOn()
        if (alarmId > 0) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_STOP)
                        .putExtra(AlarmScheduler.EXTRA_ID, alarmId)
                )
            }
            AlarmScheduler(context).cancel(alarmId)
            store.delete(alarmId)
        }
    }

    @Test
    fun exactAlarmStartsForegroundNotificationWhileScreenIsOff() {
        val scheduler = AlarmScheduler(context)
        assertTrue("El emulador debe permitir alarmas exactas", scheduler.canScheduleExactAlarms())
        val alarm = store.newAlarm().copy(
            label = "Prueba pantalla apagada",
            enabled = false,
            soundUri = "content://invalid/despierta-test",
            snoozedUntilMillis = System.currentTimeMillis() + 8_000L
        )
        alarmId = alarm.id
        store.save(alarm)

        assertTrue(scheduler.schedule(alarm) == ScheduleResult.EXACT)
        turnScreenOff()

        val notificationVisible = waitForAlarmNotification()

        assertTrue("La alarma no inició su servicio en 30 segundos", notificationVisible)
        val fired = requireNotNull(store.find(alarm.id))
        assertFalse(fired.enabled)
        assertTrue(fired.snoozedUntilMillis == null)
    }

    @Test
    fun repeatedAlarmIsRegisteredForItsNextOccurrenceAfterDelivery() {
        val now = System.currentTimeMillis()
        val alarm = store.newAlarm().copy(
            label = "Prueba repetitiva",
            days = (Calendar.SUNDAY..Calendar.SATURDAY).toSet(),
            enabled = true,
            vibrate = false,
            soundUri = "content://invalid/despierta-test",
            snoozedUntilMillis = now
        )
        alarmId = alarm.id
        store.save(alarm)
        AlarmScheduleRegistry(context).record(alarm.id, now)

        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_FIRE_ALARM)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
                .putExtra(AlarmScheduler.EXTRA_EXPECTED_TRIGGER_AT, now)
        )

        assertTrue("No apareció la alarma repetitiva", waitForAlarmNotification())
        val restored = requireNotNull(store.find(alarm.id))
        assertTrue(restored.enabled)
        assertTrue(restored.snoozedUntilMillis == null)
        assertTrue(AlarmScheduler(context).hasPendingIntent(alarm.id))
        val nextTrigger = requireNotNull(AlarmScheduleRegistry(context).expectedTrigger(alarm.id))
        assertTrue(nextTrigger > now)
    }

    private fun waitForAlarmNotification(): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 30_000L
        var notificationVisible = false
        while (System.currentTimeMillis() < deadline && !notificationVisible) {
            notificationVisible = notificationManager.activeNotifications
                .any { it.id == AlarmService.NOTIFICATION_ID }
            if (!notificationVisible) Thread.sleep(250L)
        }
        return notificationVisible
    }

    private fun turnScreenOff() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 223")
            .close()
    }

    private fun turnScreenOn() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224")
            .close()
    }
}
