package com.example.despierta

import android.util.Log

object AlarmLog {
    const val TAG = "DespiertaAlarm"

    fun info(event: String, details: String) = Log.i(TAG, "$event $details")

    fun warning(event: String, details: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, "$event $details") else Log.w(TAG, "$event $details", error)
    }

    fun error(event: String, details: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, "$event $details") else Log.e(TAG, "$event $details", error)
    }
}
