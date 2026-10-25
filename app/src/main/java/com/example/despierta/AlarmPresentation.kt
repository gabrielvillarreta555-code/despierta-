package com.example.despierta

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.TimeZone

internal data class AlarmOccurrence(
    val alarm: AlarmModel,
    val triggerAtMillis: Long
)

internal enum class ShiftDayKind {
    BEFORE_CYCLE,
    WORK,
    REST,
    EXCEPTION,
    INVALID
}

internal fun nextOccurrenceMillis(
    alarm: AlarmModel,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): Long? {
    val snoozeAt = alarm.snoozedUntilMillis
    if (snoozeAt != null && snoozeAt > nowMillis) return snoozeAt
    if (!alarm.enabled) return null

    return runCatching {
        AlarmScheduler.nextTriggerMillis(alarm, nowMillis, timeZone)
    }.onFailure {
        AlarmLog.error("presentation_next_alarm_failed", "id=${alarm.id}", it)
    }.getOrNull()
}

internal fun findNextAlarm(
    alarms: List<AlarmModel>,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): AlarmOccurrence? = alarms.mapNotNull { alarm ->
    nextOccurrenceMillis(alarm, nowMillis, timeZone)?.let { AlarmOccurrence(alarm, it) }
}.minByOrNull(AlarmOccurrence::triggerAtMillis)

internal fun upcomingShiftOccurrences(
    alarm: AlarmModel,
    count: Int,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): List<Long> {
    if (count <= 0 || alarm.shiftPattern?.isValid != true || !alarm.enabled) return emptyList()
    val occurrences = mutableListOf<Long>()
    var cursor = nowMillis
    repeat(count.coerceAtMost(10)) {
        val next = runCatching {
            AlarmScheduler.nextTriggerMillis(alarm.copy(snoozedUntilMillis = null), cursor, timeZone)
        }.getOrNull() ?: return occurrences
        occurrences += next
        cursor = next
    }
    return occurrences
}

internal fun shiftDayKind(pattern: ShiftPattern, date: LocalDate): ShiftDayKind {
    if (!pattern.isValid) return ShiftDayKind.INVALID
    val start = runCatching { LocalDate.ofEpochDay(pattern.startEpochDay) }.getOrNull()
        ?: return ShiftDayKind.INVALID
    val elapsedDays = ChronoUnit.DAYS.between(start, date)
    if (elapsedDays < 0) return ShiftDayKind.BEFORE_CYCLE
    if (date.toEpochDay() in pattern.exceptionEpochDays) return ShiftDayKind.EXCEPTION
    val cycleDay = (elapsedDays % pattern.cycleDays.toLong()).toInt()
    return if (cycleDay < pattern.workDays) ShiftDayKind.WORK else ShiftDayKind.REST
}
