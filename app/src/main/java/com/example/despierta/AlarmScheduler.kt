package com.example.despierta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.Calendar
import java.util.TimeZone

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AlarmManager::class.java)

    /** Replaces any prior PendingIntent for this alarm. A snooze takes precedence. */
    fun schedule(alarm: AlarmModel, nowMillis: Long = System.currentTimeMillis()): ScheduleResult {
        cancel(alarm.id)

        val snoozeAt = alarm.snoozedUntilMillis
        val triggerAt = when {
            snoozeAt != null && snoozeAt > nowMillis -> snoozeAt
            alarm.enabled -> nextTriggerMillis(alarm, nowMillis)
            else -> {
                AlarmLog.info("schedule_skipped", "id=${alarm.id} reason=disabled")
                return ScheduleResult.NOT_SCHEDULED
            }
        }

        val firingIntent = requireNotNull(
            alarmPendingIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT)
        )
        val showIntent = PendingIntent.getActivity(
            appContext,
            alarm.id,
            Intent(appContext, MainActivity::class.java)
                .setAction(ACTION_SHOW_ALARMS)
                .setData(alarmUri(alarm.id, "show"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (canScheduleExactAlarms()) {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    firingIntent
                )
                AlarmLog.info(
                    "schedule_exact",
                    "id=${alarm.id} triggerAt=$triggerAt snooze=${snoozeAt != null}"
                )
                ScheduleResult.EXACT
            } else {
                // Android 12/12L can reach this state until the user grants special
                // access. Keep a best-effort alarm while the UI explains the setting.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, firingIntent)
                AlarmLog.warning(
                    "schedule_inexact",
                    "id=${alarm.id} triggerAt=$triggerAt reason=exact_permission_missing"
                )
                ScheduleResult.INEXACT_PERMISSION_REQUIRED
            }
        } catch (error: SecurityException) {
            AlarmLog.error("schedule_security_error", "id=${alarm.id}", error)
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, firingIntent)
            }.onFailure {
                AlarmLog.error("schedule_fallback_failed", "id=${alarm.id}", it)
            }
            ScheduleResult.INEXACT_PERMISSION_REQUIRED
        } catch (error: RuntimeException) {
            AlarmLog.error("schedule_failed", "id=${alarm.id}", error)
            ScheduleResult.FAILED
        }
    }

    fun snooze(alarm: AlarmModel, minutes: Int = DEFAULT_SNOOZE_MINUTES): ScheduleResult {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val snoozed = alarm.copy(snoozedUntilMillis = triggerAt)
        AlarmStore(appContext).save(snoozed)
        AlarmLog.info("snooze_saved", "id=${alarm.id} triggerAt=$triggerAt minutes=$minutes")
        return schedule(snoozed)
    }

    fun reconcile(alarms: List<AlarmModel>) {
        alarms.forEach { schedule(it) }
        AlarmLog.info(
            "reconcile_complete",
            "count=${alarms.count { it.enabled || it.snoozedUntilMillis != null }}"
        )
    }

    fun cancel(id: Int) {
        alarmPendingIntent(id, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            manager.cancel(pending)
            pending.cancel()
            AlarmLog.info("schedule_cancelled", "id=$id")
        }
    }

    fun hasPendingIntent(id: Int): Boolean =
        alarmPendingIntent(id, PendingIntent.FLAG_NO_CREATE) != null

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun alarmPendingIntent(id: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        appContext,
        id,
        Intent(appContext, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_ALARM)
            .setData(alarmUri(id, "fire"))
            .putExtra(EXTRA_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val ACTION_FIRE_ALARM = "com.example.despierta.action.FIRE_ALARM"
        const val ACTION_SHOW_ALARMS = "com.example.despierta.action.SHOW_ALARMS"
        const val DEFAULT_SNOOZE_MINUTES = 5

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
    }
}

enum class ScheduleResult {
    EXACT,
    INEXACT_PERMISSION_REQUIRED,
    NOT_SCHEDULED,
    FAILED
}
