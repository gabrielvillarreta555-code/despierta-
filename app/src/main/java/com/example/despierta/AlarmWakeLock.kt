package com.example.despierta

import android.content.Context
import android.os.PowerManager

/** Holds the CPU briefly while AlarmReceiver hands execution to the foreground service. */
internal object AlarmWakeLock {
    private const val LAUNCH_TIMEOUT_MILLIS = 30_000L
    private var launchLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquireForServiceLaunch(context: Context) {
        releaseAfterServiceLaunch()
        launchLock = context.applicationContext
            .getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Despierta:AlarmLaunch")
            .apply {
                setReferenceCounted(false)
                acquire(LAUNCH_TIMEOUT_MILLIS)
            }
        AlarmLog.info("launch_wakelock_acquired", "timeout=$LAUNCH_TIMEOUT_MILLIS")
    }

    @Synchronized
    fun releaseAfterServiceLaunch() {
        runCatching { launchLock?.takeIf { it.isHeld }?.release() }
            .onFailure { AlarmLog.warning("launch_wakelock_release_failed", "", it) }
        launchLock = null
    }
}
