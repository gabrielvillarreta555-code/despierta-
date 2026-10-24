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

    @Test
    fun shiftAlarmDuringWorkDays() {
        val startDate = instant(2026, Calendar.OCTOBER, 1, 0, 0)
        // 12x4 cycle
        val pattern = ShiftPattern(startDate, 12, 4)

        // Day 5 of the cycle (work day)
        val now = instant(2026, Calendar.OCTOBER, 5, 6, 0)
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)
        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        assertEquals(instant(2026, Calendar.OCTOBER, 5, 7, 0), result)
    }

    @Test
    fun shiftAlarmDuringRestDaysMovesToNextWorkCycle() {
        val startDate = instant(2026, Calendar.OCTOBER, 1, 0, 0)
        // 12x4 cycle: Oct 1 - Oct 12 (work), Oct 13 - Oct 16 (rest)
        val pattern = ShiftPattern(startDate, 12, 4)

        // Day 14 of the cycle (rest day)
        val now = instant(2026, Calendar.OCTOBER, 14, 6, 0)
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)
        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        // Should trigger on next work day: Oct 17
        assertEquals(instant(2026, Calendar.OCTOBER, 17, 7, 0), result)
    }

    @Test
    fun shiftAlarmRespectsExceptions() {
        val startDate = instant(2026, Calendar.OCTOBER, 1, 0, 0)
        // Exception on Oct 5
        val exceptionDate = instant(2026, Calendar.OCTOBER, 5, 0, 0)
        val pattern = ShiftPattern(startDate, 12, 4, setOf(exceptionDate))

        val now = instant(2026, Calendar.OCTOBER, 4, 8, 0) // Passed time for today
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)

        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        // Should skip Oct 5 (exception) and trigger on Oct 6
        assertEquals(instant(2026, Calendar.OCTOBER, 6, 7, 0), result)
    }

    private fun alarm(hour: Int, minute: Int, days: Set<Int> = emptySet(), pattern: ShiftPattern? = null) = AlarmModel(
        id = 123,
        hour = hour,
        minute = minute,
        label = "Prueba",
        days = days,
        shiftPattern = pattern,
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
