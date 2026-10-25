package com.example.despierta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
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
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 12, 4)

        // Day 5 of the cycle (work day)
        val now = instant(2026, Calendar.OCTOBER, 5, 6, 0)
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)
        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        assertEquals(instant(2026, Calendar.OCTOBER, 5, 7, 0), result)
    }

    @Test
    fun shiftAlarmDuringRestDaysMovesToNextWorkCycle() {
        // 12x4 cycle: Oct 1 - Oct 12 (work), Oct 13 - Oct 16 (rest)
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 12, 4)

        // Day 14 of the cycle (rest day)
        val now = instant(2026, Calendar.OCTOBER, 14, 6, 0)
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)
        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        // Should trigger on next work day: Oct 17
        assertEquals(instant(2026, Calendar.OCTOBER, 17, 7, 0), result)
    }

    @Test
    fun sevenBySevenCycleMovesFromRestToNextWorkBlock() {
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 7, 7)
        val now = instant(2026, Calendar.OCTOBER, 8, 6, 0)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, pattern = pattern),
            now,
            utc
        )

        assertEquals(instant(2026, Calendar.OCTOBER, 15, 7, 0), result)
    }

    @Test
    fun customTwoByThreeCycleRepeatsAcrossCompleteCycle() {
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 2, 3)
        val now = instant(2026, Calendar.OCTOBER, 4, 6, 0)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, pattern = pattern),
            now,
            utc
        )

        assertEquals(instant(2026, Calendar.OCTOBER, 6, 7, 0), result)
    }

    @Test
    fun shiftAlarmRespectsExceptions() {
        val pattern = ShiftPattern.from(
            LocalDate.of(2026, 10, 1),
            12,
            4,
            setOf(LocalDate.of(2026, 10, 5))
        )

        val now = instant(2026, Calendar.OCTOBER, 4, 8, 0) // Passed time for today
        val alarmModel = alarm(hour = 7, minute = 0, pattern = pattern)

        val result = AlarmScheduler.nextTriggerMillis(alarmModel, now, utc)

        // Should skip Oct 5 (exception) and trigger on Oct 6
        assertEquals(instant(2026, Calendar.OCTOBER, 6, 7, 0), result)
    }

    @Test
    fun shiftCycleUsesCivilDaysAcrossDstChange() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val pattern = ShiftPattern.from(LocalDate.of(2026, 3, 1), 12, 4)
        // March 13 is the first rest day even though the DST transition made the
        // elapsed duration since March 1 one hour shorter than twelve 24-hour days.
        val now = instant(2026, Calendar.MARCH, 13, 6, 0, newYork)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, pattern = pattern),
            now,
            newYork
        )

        assertEquals(instant(2026, Calendar.MARCH, 17, 7, 0, newYork), result)
    }

    @Test
    fun twelveByFourCycleCrossesLeapDayAndStartsNextBlockCorrectly() {
        // Feb 20 through Mar 2 are twelve work days in leap year 2028.
        val pattern = ShiftPattern.from(LocalDate.of(2028, 2, 20), 12, 4)
        val now = instant(2028, Calendar.MARCH, 4, 6, 0)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, pattern = pattern),
            now,
            utc
        )

        assertEquals(instant(2028, Calendar.MARCH, 7, 7, 0), result)
    }

    @Test
    fun twelveByFourCycleCrossesDecemberAndJanuary() {
        val pattern = ShiftPattern.from(LocalDate.of(2027, 12, 25), 12, 4)
        val now = instant(2028, Calendar.JANUARY, 7, 6, 0)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 5, minute = 45, pattern = pattern),
            now,
            utc
        )

        assertEquals(instant(2028, Calendar.JANUARY, 10, 5, 45), result)
    }

    @Test
    fun nonexistentDstTimeMovesToFirstValidLocalInstant() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val pattern = ShiftPattern.from(LocalDate.of(2026, 3, 8), 12, 4)
        val now = instant(2026, Calendar.MARCH, 8, 1, 0, newYork)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 2, minute = 30, pattern = pattern),
            now,
            newYork
        )

        // 02:30 does not exist on this date. java.time resolves it to 03:30.
        assertEquals(instant(2026, Calendar.MARCH, 8, 3, 30, newYork), result)
    }

    @Test
    fun shiftAlarmBeforeCycleStartWaitsForStartDate() {
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 12, 4)
        val now = instant(2026, Calendar.SEPTEMBER, 29, 8, 0)

        val result = AlarmScheduler.nextTriggerMillis(
            alarm(hour = 7, minute = 0, pattern = pattern),
            now,
            utc
        )

        assertEquals(instant(2026, Calendar.OCTOBER, 1, 7, 0), result)
    }

    @Test
    fun invalidZeroLengthCycleIsRejectedWithoutArithmeticException() {
        val invalid = ShiftPattern(LocalDate.of(2026, 10, 1).toEpochDay(), 0, 0)

        assertThrows(IllegalArgumentException::class.java) {
            AlarmScheduler.nextTriggerMillis(
                alarm(hour = 7, minute = 0, pattern = invalid),
                instant(2026, Calendar.OCTOBER, 1, 6, 0),
                utc
            )
        }
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

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone = utc
    ): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
