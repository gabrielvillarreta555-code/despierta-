package com.example.despierta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.TimeZone

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AlarmManager::class.java)

    /** Replaces any prior PendingIntent for this alarm. A snooze takes precedence. */
    fun schedule(alarm: AlarmModel, nowMillis: Long = System.currentTimeMillis()): ScheduleResult {
        cancel(alarm.id)

        val snoozeAt = alarm.snoozedUntilMillis
        if ((snoozeAt == null || snoozeAt <= nowMillis) && !alarm.enabled) {
            AlarmLog.info("schedule_skipped", "id=${alarm.id} reason=disabled")
            return ScheduleResult.NOT_SCHEDULED
        }

        var resolvedTriggerAt: Long? = null
        var resolvedFiringIntent: PendingIntent? = null
        return try {
            // Keep trigger calculation inside the guarded block. Invalid persisted data
            // must never crash the UI, boot receiver, or reconciliation of other alarms.
            val triggerAt = if (snoozeAt != null && snoozeAt > nowMillis) {
                snoozeAt
            } else {
                nextTriggerMillis(alarm, nowMillis)
            }
            resolvedTriggerAt = triggerAt
            val firingIntent = requireNotNull(
                alarmPendingIntent(
                    alarm.id,
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    triggerAt
                )
            )
            resolvedFiringIntent = firingIntent
            val showIntent = PendingIntent.getActivity(
                appContext,
                alarm.id,
                Intent(appContext, MainActivity::class.java)
                    .setAction(ACTION_SHOW_ALARMS)
                    .setData(alarmUri(alarm.id, "show"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (canScheduleExactAlarms()) {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    firingIntent
                )
                AlarmScheduleRegistry(appContext).record(alarm.id, triggerAt)
                AlarmLog.info(
                    "schedule_exact",
                    "id=${alarm.id} triggerAt=$triggerAt snooze=${snoozeAt != null}"
                )
                ScheduleResult.EXACT
            } else {
                // Android 12/12L can reach this state until the user grants special
                // access. Keep a best-effort alarm while the UI explains the setting.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, firingIntent)
                AlarmScheduleRegistry(appContext).record(alarm.id, triggerAt)
                AlarmLog.warning(
                    "schedule_inexact",
                    "id=${alarm.id} triggerAt=$triggerAt reason=exact_permission_missing"
                )
                ScheduleResult.INEXACT_PERMISSION_REQUIRED
            }
        } catch (error: SecurityException) {
            AlarmLog.error("schedule_security_error", "id=${alarm.id}", error)
            val triggerAt = resolvedTriggerAt
            val firingIntent = resolvedFiringIntent
            if (triggerAt != null && firingIntent != null) {
                val fallbackSucceeded = runCatching {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, firingIntent)
                    AlarmScheduleRegistry(appContext).record(alarm.id, triggerAt)
                }.onFailure {
                    AlarmLog.error("schedule_fallback_failed", "id=${alarm.id}", it)
                }.isSuccess
                if (!fallbackSucceeded) return ScheduleResult.FAILED
            }
            ScheduleResult.INEXACT_PERMISSION_REQUIRED
        } catch (error: RuntimeException) {
            AlarmLog.error("schedule_failed", "id=${alarm.id}", error)
            ScheduleResult.FAILED
        }
    }

    fun snooze(alarm: AlarmModel, minutes: Int = DEFAULT_SNOOZE_MINUTES): ScheduleResult {
        if (minutes !in 1..MAX_SNOOZE_MINUTES) {
            AlarmLog.error("snooze_rejected", "id=${alarm.id} minutes=$minutes")
            return ScheduleResult.FAILED
        }
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val snoozed = alarm.copy(snoozedUntilMillis = triggerAt)
        val store = AlarmStore(appContext)
        store.save(snoozed)
        AlarmLog.info("snooze_saved", "id=${alarm.id} triggerAt=$triggerAt minutes=$minutes")
        val result = schedule(snoozed)
        if (result == ScheduleResult.FAILED) {
            // Do not display a snooze that AlarmManager failed to register.
            store.save(alarm)
            AlarmLog.error("snooze_rolled_back", "id=${alarm.id}")
        }
        return result
    }

    fun reconcile(alarms: List<AlarmModel>) {
        alarms.forEach { alarm ->
            runCatching { schedule(alarm) }
                .onFailure { AlarmLog.error("reconcile_alarm_failed", "id=${alarm.id}", it) }
        }
        AlarmLog.info(
            "reconcile_complete",
            "count=${alarms.count { it.enabled || it.snoozedUntilMillis != null }}"
        )
    }

    fun cancel(id: Int) {
        runCatching {
            alarmPendingIntent(id, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
                manager.cancel(pending)
                pending.cancel()
                AlarmLog.info("schedule_cancelled", "id=$id")
            }
            AlarmScheduleRegistry(appContext).clear(id)
        }.onFailure {
            // Cancellation failures must not crash alarm editing or prevent other alarms from
            // being reconciled. A stale delivery is still rejected against the stored model.
            AlarmLog.error("schedule_cancel_failed", "id=$id", it)
        }
    }

    fun hasPendingIntent(id: Int): Boolean =
        alarmPendingIntent(id, PendingIntent.FLAG_NO_CREATE) != null

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun alarmPendingIntent(
        id: Int,
        flags: Int,
        triggerAtMillis: Long = 0L
    ): PendingIntent? = PendingIntent.getBroadcast(
        appContext,
        id,
        Intent(appContext, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_ALARM)
            .setData(alarmUri(id, "fire"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_EXPECTED_TRIGGER_AT, triggerAtMillis),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_EXPECTED_TRIGGER_AT = "expected_trigger_at"
        const val ACTION_FIRE_ALARM = "com.example.despierta.action.FIRE_ALARM"
        const val ACTION_SHOW_ALARMS = "com.example.despierta.action.SHOW_ALARMS"
        const val DEFAULT_SNOOZE_MINUTES = 5
        private const val MAX_SNOOZE_MINUTES = 24 * 60

        internal fun nextTriggerMillis(
            alarm: AlarmModel,
            nowMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long {
            val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
            val candidate = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            alarm.shiftPattern?.let {
                return nextShiftTriggerMillis(alarm, nowMillis, timeZone)
            }

            return fallbackTrigger(candidate, now, alarm)
        }

        private fun nextShiftTriggerMillis(
            alarm: AlarmModel,
            nowMillis: Long,
            timeZone: TimeZone
        ): Long {
            val shift = requireNotNull(alarm.shiftPattern)
            require(shift.isValid) { "Patrón de turno inválido para la alarma ${alarm.id}" }
            require(alarm.hour in 0..23 && alarm.minute in 0..59) {
                "Hora inválida para la alarma ${alarm.id}"
            }

            val zoneId = timeZone.toZoneId()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            val startDate = LocalDate.ofEpochDay(shift.startEpochDay)
            var candidateDate = now.toLocalDate()

            repeat(MAX_SHIFT_SEARCH_DAYS) {
                val triggerAt = candidateDate
                    .atTime(alarm.hour, alarm.minute)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val daysSinceStart = ChronoUnit.DAYS.between(startDate, candidateDate)
                if (
                    triggerAt > nowMillis &&
                    daysSinceStart >= 0 &&
                    candidateDate.toEpochDay() !in shift.exceptionEpochDays
                ) {
                    val cycleDay = (daysSinceStart % shift.cycleDays).toInt()
                    if (cycleDay < shift.workDays) return triggerAt
                }
                candidateDate = candidateDate.plusDays(1)
            }
            throw IllegalArgumentException(
                "No se encontró un día válido de turno para la alarma ${alarm.id}"
            )
        }

        private fun fallbackTrigger(candidate: Calendar, now: Calendar, alarm: AlarmModel): Long {
            if (alarm.days.isEmpty()) {
                if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
                return candidate.timeInMillis
            }

            repeat(8) {
                if (candidate.after(now) && candidate.get(Calendar.DAY_OF_WEEK) in alarm.days) {
                    return candidate.timeInMillis
                }
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            error("No se encontró un día válido para la alarma ${alarm.id}")
        }

        private fun alarmUri(id: Int, operation: String): Uri =
            Uri.parse("despierta://alarm/$id/$operation")

        private const val MAX_SHIFT_SEARCH_DAYS = 3_660
    }
}

enum class ScheduleResult {
    EXACT,
    INEXACT_PERMISSION_REQUIRED,
    NOT_SCHEDULED,
    FAILED
}
