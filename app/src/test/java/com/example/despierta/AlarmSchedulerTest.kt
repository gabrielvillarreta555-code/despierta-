package com.example.despierta

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class AlarmSchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun oneTimeAlarmLaterTodayStaysToday() {
        val now = instant(2026, Calendar.OCTOBER, 24, 6, 30)
        val result = AlarmScheduler.nextTriggerMillis(alarm(hour = 7, minute = 15), now, utc)

        assertEquals(instant(2026, Calendar.OCTOBER, 24, 7, 15), result)
    }

    @Test
    fun oneTimeAlarmAlreadyPassedMovesToTomorrow() {
        val now = instant(2026, Calendar.OCTOBER, 24, 8, 0)
        val result = AlarmScheduler.nextTriggerMillis(alarm(hour = 7, minute = 15), now, utc)

        assertEquals(instant(2026, Calendar.OCTOBER, 25, 7, 15), result)
    }

    @Test
    fun repeatingAlarmSelectsNextEnabledDay() {
        // Saturday -> Monday.
        val now = instant(2026, Calendar.OCTOBER, 24, 8, 0)
        val weekdays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, days = weekdays),
            now,
            utc
        )

        assertEquals(instant(2026, Calendar.OCTOBER, 26, 7, 0), result)
    }

    @Test
    fun repeatingAlarmTodayUsesFutureTime() {
        val now = instant(2026, Calendar.OCTOBER, 24, 6, 0)
        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, days = setOf(Calendar.SATURDAY)),
            now,
            utc
        )

        assertEquals(instant(2026, Calendar.OCTOBER, 24, 7, 0), result)
    }

    private fun alarm(hour: Int, minute: Int, days: Set<Int> = emptySet()) = AlarmModel(
        id = 123,
        hour = hour,
        minute = minute,
        label = "Prueba",
        days = days,
        soundUri = "content://test/alarm",
        enabled = true,
        mathChallengeEnabled = false,
        snoozedUntilMillis = null
    )

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(utc).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
