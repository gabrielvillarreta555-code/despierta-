package com.example.despierta

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AlarmPersistenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AlarmStore(context)
    private val prefs = context.getSharedPreferences("alarms", android.content.Context.MODE_PRIVATE)
    private val createdIds = mutableListOf<Int>()

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        createdIds.clear()
    }

    @After
    fun cleanUp() {
        createdIds.forEach { id ->
            AlarmScheduler(context).cancel(id)
        }
        prefs.edit().clear().commit()
    }

    @Test
    fun storeRoundTripPreservesReliabilityFields() {
        val alarm = store.newAlarm().copy(
            label = "Turno temprano",
            shiftPattern = ShiftPattern.from(
                LocalDate.of(2026, 10, 1),
                12,
                4,
                setOf(LocalDate.of(2026, 10, 5))
            ),
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
        val alarm = store.newAlarm().copy(
            enabled = true,
            shiftPattern = ShiftPattern.from(LocalDate.now(), 7, 7)
        )
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
        val alarm = store.newAlarm().copy(
            enabled = true,
            shiftPattern = ShiftPattern.from(LocalDate.now(), 12, 4)
        )
        createdIds += alarm.id
        store.save(alarm)

        AlarmScheduler(context).snooze(alarm)

        val restored = store.find(alarm.id)
        assertTrue(restored?.enabled == true)
        assertTrue(restored?.snoozedUntilMillis != null)
        assertTrue(AlarmScheduler(context).hasPendingIntent(alarm.id))
    }

    @Test
    fun corruptAlarmEntryDoesNotHideOtherAlarms() {
        val valid = store.newAlarm().copy(label = "Conservar")
        createdIds += valid.id
        store.save(valid)
        val data = JSONArray(prefs.getString("items", "[]"))
            .put(JSONObject().put("id", 999).put("hour", 99))
        prefs.edit().putString("items", data.toString()).commit()

        val restored = store.getAll()

        assertEquals(listOf(valid), restored)
    }

    @Test
    fun corruptPrimaryStorageRecoversLatestBackup() {
        val first = store.newAlarm().copy(label = "Primera")
        store.save(first)
        val second = store.newAlarm().copy(label = "Segunda")
        store.save(second)
        createdIds += listOf(first.id, second.id)
        prefs.edit().putString("items", "{dato corrupto").commit()

        val restored = store.getAll()

        assertEquals(setOf(first.id, second.id), restored.map { it.id }.toSet())
    }

    @Test
    fun legacyMillisShiftIsMigratedToCivilDates() {
        val startDate = LocalDate.of(2026, 3, 1)
        val exceptionDate = LocalDate.of(2026, 3, 5)
        val zone = ZoneId.systemDefault()
        val legacy = JSONObject()
            .put("id", 7001)
            .put("hour", 7)
            .put("minute", 0)
            .put("label", "Legada")
            .put("days", JSONArray())
            .put(
                "shiftPattern",
                JSONObject()
                    .put("startDateMillis", startDate.atStartOfDay(zone).toInstant().toEpochMilli())
                    .put("workDays", 12)
                    .put("restDays", 4)
                    .put(
                        "exceptions",
                        JSONArray().put(exceptionDate.atStartOfDay(zone).toInstant().toEpochMilli())
                    )
            )
            .put("enabled", true)
        prefs.edit().putString("items", JSONArray().put(legacy).toString()).commit()
        createdIds += 7001

        val restored = store.find(7001)

        assertNotNull(restored)
        assertEquals(startDate.toEpochDay(), restored?.shiftPattern?.startEpochDay)
        assertEquals(setOf(exceptionDate.toEpochDay()), restored?.shiftPattern?.exceptionEpochDays)
        assertTrue(restored?.enabled == true)
    }

    @Test
    fun invalidShiftIsDisabledWithoutLosingValidAlarm() {
        val valid = store.newAlarm().copy(label = "Válida")
        store.save(valid)
        val invalid = store.newAlarm().copy(
            label = "Inválida",
            shiftPattern = ShiftPattern(LocalDate.now().toEpochDay(), 0, 4),
            enabled = true
        )
        store.save(invalid)
        createdIds += listOf(valid.id, invalid.id)

        assertTrue(store.find(valid.id)?.enabled == true)
        assertFalse(store.find(invalid.id)?.enabled ?: true)
    }

    @Test
    fun reconciliationContinuesAfterInvalidShift() {
        val invalid = store.newAlarm().copy(
            shiftPattern = ShiftPattern(LocalDate.now().toEpochDay(), 0, 0),
            enabled = true
        )
        val valid = store.newAlarm().copy(
            shiftPattern = ShiftPattern.from(LocalDate.now(), 12, 4),
            enabled = true
        )
        createdIds += listOf(invalid.id, valid.id)
        val scheduler = AlarmScheduler(context)

        scheduler.reconcile(listOf(invalid, valid))

        assertFalse(scheduler.hasPendingIntent(invalid.id))
        assertTrue(scheduler.hasPendingIntent(valid.id))
    }
}
