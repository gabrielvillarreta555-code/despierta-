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

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 30_000L
        var notificationVisible = false
        while (System.currentTimeMillis() < deadline && !notificationVisible) {
            notificationVisible = notificationManager.activeNotifications
                .any { it.id == AlarmService.NOTIFICATION_ID }
            if (!notificationVisible) Thread.sleep(250L)
        }

        assertTrue("La alarma no inició su servicio en 30 segundos", notificationVisible)
        val fired = requireNotNull(store.find(alarm.id))
        assertFalse(fired.enabled)
        assertTrue(fired.snoozedUntilMillis == null)
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
