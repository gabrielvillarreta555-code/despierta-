package com.example.despierta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class AlarmPresentationTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun nextAlarmSelectsEarliestEnabledOccurrence() {
        val now = instant(2026, Calendar.OCTOBER, 25, 6, 0)
        val later = alarm(id = 1, hour = 9, minute = 0, label = "Más tarde")
        val earlier = alarm(id = 2, hour = 7, minute = 30, label = "Primera")

        val next = findNextAlarm(listOf(later, earlier), now, utc)

        assertEquals(earlier.id, next?.alarm?.id)
        assertEquals(instant(2026, Calendar.OCTOBER, 25, 7, 30), next?.triggerAtMillis)
    }

    @Test
    fun disabledAlarmWithoutSnoozeHasNoOccurrence() {
        val alarm = alarm(id = 3, hour = 7, minute = 0, enabled = false)

        assertNull(nextOccurrenceMillis(alarm, instant(2026, Calendar.OCTOBER, 25, 6, 0), utc))
    }

    @Test
    fun calendarRepresentsWorkRestAndExceptionDays() {
        val pattern = ShiftPattern.from(
            startDate = LocalDate.of(2026, 10, 1),
            workDays = 2,
            restDays = 2,
            exceptions = setOf(LocalDate.of(2026, 10, 2))
        )

        assertEquals(ShiftDayKind.BEFORE_CYCLE, shiftDayKind(pattern, LocalDate.of(2026, 9, 30)))
        assertEquals(ShiftDayKind.WORK, shiftDayKind(pattern, LocalDate.of(2026, 10, 1)))
        assertEquals(ShiftDayKind.EXCEPTION, shiftDayKind(pattern, LocalDate.of(2026, 10, 2)))
        assertEquals(ShiftDayKind.REST, shiftDayKind(pattern, LocalDate.of(2026, 10, 3)))
        assertEquals(ShiftDayKind.WORK, shiftDayKind(pattern, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun upcomingShiftListCrossesACompleteTwelveByFourCycle() {
        val pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 12, 4)
        val alarm = alarm(id = 4, hour = 7, minute = 0, shiftPattern = pattern)
        val now = instant(2026, Calendar.OCTOBER, 12, 8, 0)

        val upcoming = upcomingShiftOccurrences(alarm, 2, now, utc)

        assertEquals(
            listOf(
                instant(2026, Calendar.OCTOBER, 17, 7, 0),
                instant(2026, Calendar.OCTOBER, 18, 7, 0)
            ),
            upcoming
        )
    }

    @Test
    fun countdownIncludesDaysAndSecondsWithoutRoundingDown() {
        assertEquals("1 d · 01:02:04", formatCountdown(90_123_001L))
        assertEquals("00:00:01", formatCountdown(1L))
        assertEquals("Ahora", formatCountdown(0L))
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(utc).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun alarm(
        id: Int,
        hour: Int,
        minute: Int,
        label: String = "Prueba",
        enabled: Boolean = true,
        shiftPattern: ShiftPattern? = null
    ) = AlarmModel(
        id = id,
        hour = hour,
        minute = minute,
        label = label,
        shiftPattern = shiftPattern,
        soundUri = "content://test/alarm",
        enabled = enabled
    )
}
