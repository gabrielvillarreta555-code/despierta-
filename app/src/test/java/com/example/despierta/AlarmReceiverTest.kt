package com.example.despierta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class AlarmReceiverTest {
    @Test
    fun oneTimeAlarmIsDisabledAfterFiring() {
        val updated = AlarmReceiver.stateAfterFiring(alarm(), isSnooze = false)

        assertFalse(updated.enabled)
        assertNull(updated.snoozedUntilMillis)
    }

    @Test
    fun normalWeeklyAlarmRemainsEnabledAfterFiring() {
        val updated = AlarmReceiver.stateAfterFiring(
            alarm(days = setOf(Calendar.MONDAY)),
            isSnooze = false
        )

        assertTrue(updated.enabled)
        assertTrue(AlarmReceiver.isRecurring(updated))
    }

    @Test
    fun shiftAlarmRemainsEnabledWhenStoppedAfterFiring() {
        val updated = AlarmReceiver.stateAfterFiring(
            alarm(pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 12, 4)),
            isSnooze = false
        )

        assertTrue(updated.enabled)
        assertTrue(AlarmReceiver.isRecurring(updated))
        assertNull(updated.snoozedUntilMillis)
    }

    @Test
    fun shiftAlarmReturnsToCycleAfterSnoozeFires() {
        val updated = AlarmReceiver.stateAfterFiring(
            alarm(
                pattern = ShiftPattern.from(LocalDate.of(2026, 10, 1), 7, 7),
                snoozedUntilMillis = 123_456L
            ),
            isSnooze = true
        )

        assertTrue(updated.enabled)
        assertTrue(AlarmReceiver.isRecurring(updated))
        assertNull(updated.snoozedUntilMillis)
    }

    @Test
    fun invalidShiftIsNotTreatedAsRecurring() {
        val invalid = ShiftPattern(LocalDate.of(2026, 10, 1).toEpochDay(), 0, 4)
        val updated = AlarmReceiver.stateAfterFiring(
            alarm(pattern = invalid),
            isSnooze = false
        )

        assertFalse(AlarmReceiver.isRecurring(updated))
        assertFalse(updated.enabled)
    }

    private fun alarm(
        days: Set<Int> = emptySet(),
        pattern: ShiftPattern? = null,
        snoozedUntilMillis: Long? = null
    ) = AlarmModel(
        id = 321,
        hour = 7,
        minute = 0,
        days = days,
        shiftPattern = pattern,
        soundUri = "content://test/alarm",
        enabled = true,
        snoozedUntilMillis = snoozedUntilMillis
    )
}
