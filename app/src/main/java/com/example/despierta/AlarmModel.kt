package com.example.despierta

import android.content.Context
import android.util.Log
import android.media.RingtoneManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShiftPattern(
    val startDateMillis: Long,
    val workDays: Int,
    val restDays: Int,
    val exceptions: Set<Long> = emptySet()
)

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
            val alarms = readLocked().filterNot { it.id == alarm.id } + alarm
            writeLocked(alarms.sortedWith(compareBy({ it.hour }, { it.minute })))
        }
    }

    fun delete(id: Int) {
        synchronized(lock) { writeLocked(readLocked().filterNot { it.id == id }) }
    }

    fun newAlarm(): AlarmModel {
        val usedIds = getAll().mapTo(mutableSetOf()) { it.id }
        var alarm = AlarmModel()
        while (alarm.id in usedIds) alarm = AlarmModel()
        return alarm
    }

    private fun readLocked(): List<AlarmModel> = runCatching {
        val source = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        List(source.length()) { index ->
            val item = source.getJSONObject(index)
            val jsonDays = item.optJSONArray("days") ?: JSONArray()
            val shiftPatternJson = item.optJSONObject("shiftPattern")
            val shiftPattern = if (shiftPatternJson != null) {
                val exceptionsArray = shiftPatternJson.optJSONArray("exceptions") ?: JSONArray()
                val exceptions = (0 until exceptionsArray.length()).map { exceptionsArray.getLong(it) }.toSet()
                ShiftPattern(
                    startDateMillis = shiftPatternJson.getLong("startDateMillis"),
                    workDays = shiftPatternJson.getInt("workDays"),
                    restDays = shiftPatternJson.getInt("restDays"),
                    exceptions = exceptions
                )
            } else null
            AlarmModel(
                id = item.getInt("id"),
                hour = item.getInt("hour"),
                minute = item.getInt("minute"),
                label = item.optString("label", "Alarma"),
                days = (0 until jsonDays.length()).map { jsonDays.getInt(it) }.toSet(),
                shiftPattern = shiftPattern,
                soundUri = item.optString("soundUri", defaultAlarmUri()),
                enabled = item.optBoolean("enabled", true),
                mathChallengeEnabled = item.optBoolean("mathChallengeEnabled", false),
                snoozedUntilMillis = if (item.isNull("snoozedUntilMillis")) {
                    null
                } else {
                    item.optLong("snoozedUntilMillis").takeIf { it > 0L }
                }
            )
        }
    }.onFailure {
        Log.e(AlarmLog.TAG, "store_read_failed", it)
    }.getOrDefault(emptyList())

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
                        put("startDateMillis", pattern.startDateMillis)
                        put("workDays", pattern.workDays)
                        put("restDays", pattern.restDays)
                        put("exceptions", JSONArray(pattern.exceptions.sorted()))
                    }
                    put("shiftPattern", patternObj)
                } ?: put("shiftPattern", JSONObject.NULL)
                put("soundUri", alarm.soundUri)
                put("enabled", alarm.enabled)
                put("mathChallengeEnabled", alarm.mathChallengeEnabled)
                put("snoozedUntilMillis", alarm.snoozedUntilMillis ?: JSONObject.NULL)
            })
        }
        if (!prefs.edit().putString(KEY_ITEMS, array.toString()).commit()) {
            Log.e(AlarmLog.TAG, "store_write_failed count=${alarms.size}")
        }
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private val lock = Any()

        fun defaultAlarmUri(): String =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    }
}
