package com.voicebubble

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Which app you're typing in, so the right style is used. */
object AppDetector {
    private val ignore = setOf("com.android.systemui")

    /** Package name of the app on screen, or null when unknown. */
    fun current(c: Context): String? {
        // Autotype version: the accessibility service sees it directly.
        InsertService.instance?.let { s ->
            runCatching { s.rootInActiveWindow?.packageName?.toString() }.getOrNull()?.let { return it }
        }
        if (!hasUsageAccess(c)) return null
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 3 * 3600_000L, now) }.getOrNull() ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName !in ignore) last = e.packageName
        }
        return last
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** True when it can tell apps apart (otherwise everything counts as Other). */
    fun canDetect(c: Context) = InsertService.isOn || hasUsageAccess(c)
}
