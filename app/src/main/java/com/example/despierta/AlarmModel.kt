package com.example.despierta

import android.content.Context
import android.util.Log
import android.media.RingtoneManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ShiftPattern(
    /** Civil date that starts day zero of the cycle, independent from time and DST. */
    val startEpochDay: Long,
    val workDays: Int,
    val restDays: Int,
    /** Civil dates on which this alarm must not ring. */
    val exceptionEpochDays: Set<Long> = emptySet()
) {
    val cycleDays: Int get() = workDays + restDays

    val isValid: Boolean
        get() = workDays >= 1 &&
            restDays >= 1 &&
            cycleDays in 2..MAX_CYCLE_DAYS &&
            isValidEpochDay(startEpochDay) &&
            exceptionEpochDays.size <= MAX_EXCEPTIONS &&
            exceptionEpochDays.all(::isValidEpochDay)

    companion object {
        const val MAX_CYCLE_DAYS = 366
        const val MAX_EXCEPTIONS = 3_660

        fun from(
            startDate: LocalDate,
            workDays: Int,
            restDays: Int,
            exceptions: Set<LocalDate> = emptySet()
        ): ShiftPattern = ShiftPattern(
            startEpochDay = startDate.toEpochDay(),
            workDays = workDays,
            restDays = restDays,
            exceptionEpochDays = exceptions.mapTo(mutableSetOf(), LocalDate::toEpochDay)
        )

        internal fun invalid(): ShiftPattern = ShiftPattern(Long.MIN_VALUE, 0, 0)

        private fun isValidEpochDay(value: Long): Boolean =
            runCatching { LocalDate.ofEpochDay(value) }.isSuccess
    }
}

data class AlarmModel(
    val id: Int = newAlarmId(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Alarma",
    val days: Set<Int> = emptySet(), // Calendar.SUNDAY..SATURDAY
    val shiftPattern: ShiftPattern? = null,
    val soundUri: String = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString(),
    val enabled: Boolean = true,
    val mathChallengeEnabled: Boolean = false,
    val snoozedUntilMillis: Long? = null
)

private fun newAlarmId(): Int = (UUID.randomUUID().hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

class AlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun getAll(): List<AlarmModel> = synchronized(lock) { readLocked() }

    fun find(id: Int) = getAll().firstOrNull { it.id == id }

    fun save(alarm: AlarmModel) {
        synchronized(lock) {
            val current = readLockedOrNull()
            if (current == null) {
                Log.e(AlarmLog.TAG, "store_save_aborted id=${alarm.id} reason=unrecoverable_root")
                return
            }
            if (alarm.id <= 0 || alarm.hour !in 0..23 || alarm.minute !in 0..59) {
                Log.e(AlarmLog.TAG, "store_save_rejected id=${alarm.id} reason=invalid_alarm")
                return
            }
            val safeAlarm = if (alarm.enabled && alarm.shiftPattern?.isValid == false) {
                Log.e(AlarmLog.TAG, "store_alarm_disabled id=${alarm.id} reason=invalid_shift")
                alarm.copy(enabled = false)
            } else {
                alarm
            }
            val alarms = current.filterNot { it.id == alarm.id } + safeAlarm
            writeLocked(alarms.sortedWith(compareBy({ it.hour }, { it.minute })))
        }
    }

    fun delete(id: Int) {
        synchronized(lock) {
            val current = readLockedOrNull()
            if (current == null) {
                Log.e(AlarmLog.TAG, "store_delete_aborted id=$id reason=unrecoverable_root")
                return
            }
            writeLocked(current.filterNot { it.id == id })
        }
    }

    fun newAlarm(): AlarmModel {
        val usedIds = getAll().mapTo(mutableSetOf()) { it.id }
        var alarm = AlarmModel()
        while (alarm.id in usedIds) alarm = AlarmModel()
        return alarm
    }

    private fun readLocked(): List<AlarmModel> = readLockedOrNull() ?: emptyList()

    private fun readLockedOrNull(): List<AlarmModel>? {
        val primary = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val source = runCatching { JSONArray(primary) }.getOrElse { primaryError ->
            Log.e(AlarmLog.TAG, "store_read_failed reason=invalid_root", primaryError)
            val backup = prefs.getString(KEY_ITEMS_BACKUP, null) ?: return null
            runCatching { JSONArray(backup) }
                .onSuccess { Log.w(AlarmLog.TAG, "store_recovered_from_backup") }
                .onFailure { Log.e(AlarmLog.TAG, "store_backup_failed", it) }
                .getOrNull() ?: return null
        }

        val alarmsById = linkedMapOf<Int, AlarmModel>()
        repeat(source.length()) { index ->
            runCatching { parseAlarm(source.getJSONObject(index)) }
                .onSuccess { alarm ->
                    if (alarmsById.put(alarm.id, alarm) != null) {
                        // A duplicated identifier would map to one PendingIntent. Keep the latest
                        // stored entry so the UI and AlarmManager agree on a single alarm.
                        Log.w(AlarmLog.TAG, "store_duplicate_replaced id=${alarm.id} index=$index")
                    }
                }
                .onFailure {
                    // A damaged entry must not hide the rest of the user's alarms.
                    Log.e(AlarmLog.TAG, "store_item_skipped index=$index", it)
                }
        }
        return alarmsById.values.toList()
    }

    private fun parseAlarm(item: JSONObject): AlarmModel {
        val id = item.getInt("id").also { require(it > 0) { "invalid alarm id" } }
        val hour = item.getInt("hour").also { require(it in 0..23) { "invalid hour" } }
        val minute = item.getInt("minute").also { require(it in 0..59) { "invalid minute" } }
        val jsonDays = item.optJSONArray("days") ?: JSONArray()
        val days = (0 until jsonDays.length())
            .map { jsonDays.getInt(it) }
            .filter { it in 1..7 }
            .toSet()

        val hasShift = item.has("shiftPattern") && !item.isNull("shiftPattern")
        val shiftPattern = if (hasShift) {
            runCatching { parseShiftPattern(item.getJSONObject("shiftPattern")) }
                .onFailure { Log.e(AlarmLog.TAG, "store_shift_invalid id=$id", it) }
                .getOrElse { ShiftPattern.invalid() }
        } else {
            null
        }
        val storedEnabled = item.optBoolean("enabled", true)
        val enabled = storedEnabled && shiftPattern?.isValid != false
        if (storedEnabled && !enabled) {
            Log.e(AlarmLog.TAG, "store_alarm_disabled id=$id reason=invalid_shift")
        }

        return AlarmModel(
            id = id,
            hour = hour,
            minute = minute,
            label = item.optString("label", "Alarma"),
            days = days,
            shiftPattern = shiftPattern,
            soundUri = item.optString("soundUri", defaultAlarmUri()),
            enabled = enabled,
            mathChallengeEnabled = item.optBoolean("mathChallengeEnabled", false),
            snoozedUntilMillis = if (item.isNull("snoozedUntilMillis")) {
                null
            } else {
                item.optLong("snoozedUntilMillis").takeIf { it > 0L }
            }
        )
    }

    private fun parseShiftPattern(item: JSONObject): ShiftPattern {
        val isLegacy = !item.has("startEpochDay")
        val startEpochDay = if (isLegacy) {
            instantToEpochDay(item.getLong("startDateMillis"))
        } else {
            item.getLong("startEpochDay")
        }
        val exceptionKey = if (item.has("exceptionEpochDays")) {
            "exceptionEpochDays"
        } else {
            "exceptions"
        }
        val exceptionsArray = item.optJSONArray(exceptionKey) ?: JSONArray()
        val exceptionEpochDays = (0 until exceptionsArray.length()).mapTo(mutableSetOf()) { index ->
            val stored = exceptionsArray.getLong(index)
            if (isLegacy) instantToEpochDay(stored) else stored
        }
        return ShiftPattern(
            startEpochDay = startEpochDay,
            workDays = item.optInt("workDays", 0),
            restDays = item.optInt("restDays", 0),
            exceptionEpochDays = exceptionEpochDays
        )
    }

    private fun instantToEpochDay(value: Long): Long = Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

    private fun writeLocked(alarms: List<AlarmModel>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("label", alarm.label)
                put("days", JSONArray(alarm.days.sorted()))
                alarm.shiftPattern?.let { pattern ->
                    val patternObj = JSONObject().apply {
                        put("startEpochDay", pattern.startEpochDay)
                        put("workDays", pattern.workDays)
                        put("restDays", pattern.restDays)
                        put("exceptionEpochDays", JSONArray(pattern.exceptionEpochDays.sorted()))
                    }
                    put("shiftPattern", patternObj)
                } ?: put("shiftPattern", JSONObject.NULL)
                put("soundUri", alarm.soundUri)
                put("enabled", alarm.enabled)
                put("mathChallengeEnabled", alarm.mathChallengeEnabled)
                put("snoozedUntilMillis", alarm.snoozedUntilMillis ?: JSONObject.NULL)
            })
        }
        val serialized = array.toString()
        // Keep an independently named copy so a malformed primary value can be
        // recovered without discarding every alarm.
        val editor = prefs.edit()
            .putString(KEY_ITEMS, serialized)
            .putString(KEY_ITEMS_BACKUP, serialized)
        if (!editor.commit()) {
            Log.e(AlarmLog.TAG, "store_write_failed count=${alarms.size}")
        }
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_ITEMS_BACKUP = "items_backup"
        private val lock = Any()

        fun defaultAlarmUri(): String =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    }
}
