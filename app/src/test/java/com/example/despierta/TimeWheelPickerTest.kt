package com.example.despierta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeWheelPickerTest {
    @Test
    fun twelveHourSelectionConvertsMidnightAndNoonWithoutAmbiguity() {
        assertEquals(0, hour24From12(12, isPm = false))
        assertEquals(12, hour24From12(12, isPm = true))
        assertEquals(23, hour24From12(11, isPm = true))
        assertEquals(12, hour12From24(0))
        assertEquals(12, hour12From24(12))
    }

    @Test
    fun hourWheelPreservesCurrentPeriodInTwelveHourMode() {
        assertEquals(9, selectWheelHour(currentHour24 = 7, wheelHour = 9, is24Hour = false))
        assertEquals(21, selectWheelHour(currentHour24 = 19, wheelHour = 9, is24Hour = false))
        assertEquals(0, selectWheelHour(currentHour24 = 19, wheelHour = 0, is24Hour = true))
    }

    @Test
    fun periodWheelChangesAmAndPmWithoutChangingDisplayedHour() {
        assertEquals(19, selectWheelPeriod(currentHour24 = 7, isPm = true))
        assertEquals(7, selectWheelPeriod(currentHour24 = 19, isPm = false))
        assertEquals(0, selectWheelPeriod(currentHour24 = 12, isPm = false))
    }

    @Test
    fun manualEntryParsesHoursMinutesAndPeriods() {
        assertEquals(SelectedTime(7, 5), parseManualTime("07:05", is24Hour = true))
        assertEquals(SelectedTime(19, 5), parseManualTime("7:05 pm", is24Hour = false))
        assertEquals(SelectedTime(0, 0), parseManualTime("12:00 AM", is24Hour = false))
        assertEquals(SelectedTime(12, 0), parseManualTime("12:00 PM", is24Hour = false))
    }

    @Test
    fun manualEntryRejectsInvalidTimes() {
        assertNull(parseManualTime("24:00", is24Hour = true))
        assertNull(parseManualTime("12:60 PM", is24Hour = false))
        assertNull(parseManualTime("00:30 AM", is24Hour = false))
    }
}
