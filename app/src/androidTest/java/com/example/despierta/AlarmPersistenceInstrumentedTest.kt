package com.example.despierta

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPersistenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AlarmStore(context)
    private val createdIds = mutableListOf<Int>()

    @After
    fun cleanUp() {
        createdIds.forEach { id ->
            AlarmScheduler(context).cancel(id)
            store.delete(id)
        }
    }

    @Test
    fun storeRoundTripPreservesReliabilityFields() {
        val alarm = store.newAlarm().copy(
            label = "Turno temprano",
            mathChallengeEnabled = true,
            snoozedUntilMillis = System.currentTimeMillis() + 300_000L
        )
        createdIds += alarm.id

        store.save(alarm)
        val restored = store.find(alarm.id)

        assertEquals(alarm, restored)
    }

    @Test
    fun multipleAlarmsUseDistinctPendingIntentsAndCanBeCancelled() {
        val first = store.newAlarm()
        store.save(first)
        val second = store.newAlarm()
        store.save(second)
        createdIds += listOf(first.id, second.id)
        val scheduler = AlarmScheduler(context)

        scheduler.schedule(first)
        scheduler.schedule(second)

        assertTrue(scheduler.hasPendingIntent(first.id))
        assertTrue(scheduler.hasPendingIntent(second.id))
        scheduler.cancel(first.id)
        assertFalse(scheduler.hasPendingIntent(first.id))
        assertTrue(scheduler.hasPendingIntent(second.id))
    }

    @Test
    fun bootReceiverRestoresEnabledAlarmWithoutDuplicates() {
        val alarm = store.newAlarm().copy(enabled = true)
        createdIds += alarm.id
        store.save(alarm)
        val scheduler = AlarmScheduler(context)
        scheduler.cancel(alarm.id)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(scheduler.hasPendingIntent(alarm.id))
    }

    @Test
    fun snoozeIsPersistedAndScheduled() {
        val alarm = store.newAlarm().copy(enabled = false)
        createdIds += alarm.id
        store.save(alarm)

        AlarmScheduler(context).snooze(alarm)

        assertTrue(store.find(alarm.id)?.snoozedUntilMillis != null)
        assertTrue(AlarmScheduler(context).hasPendingIntent(alarm.id))
    }
}
