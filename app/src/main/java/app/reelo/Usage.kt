package app.reelo

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

data class AppUsage(val ms: Long, val opens: Int, val afterUnlock: Int)

/** Screen time per app dari event resume/pause. YouTube dihitung semuanya, karena sistem nggak bedain Shorts. */
object Usage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun read(ctx: Context, from: Long, to: Long): Map<App, AppUsage> {
        if (!hasAccess(ctx)) return emptyMap()
        val ev = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
        val ms = HashMap<App, Long>()
        val opens = HashMap<App, Int>()
        val unlockOpens = HashMap<App, Int>()
        var fgApp: App? = null
        var fgSince = 0L
        var lastPkg: String? = null
        var unlockAt = -1L
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlockAt = e.timeStamp
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val app = App.of(e.packageName)
                    if (app != fgApp) {
                        fgApp?.let { ms.merge(it, e.timeStamp - fgSince, Long::plus) }
                        fgApp = app; fgSince = e.timeStamp
                    }
                    if (app != null && lastPkg != e.packageName) {
                        opens.merge(app, 1, Int::plus)
                        if (unlockAt >= 0 && e.timeStamp - unlockAt < 60_000) unlockOpens.merge(app, 1, Int::plus)
                    }
                    lastPkg = e.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE || App.of(e.packageName) == fgApp) {
                        fgApp?.let { ms.merge(it, e.timeStamp - fgSince, Long::plus) }
                        fgApp = null
                        if (e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) lastPkg = null
                    }
                }
            }
        }
        fgApp?.let { ms.merge(it, minOf(to, System.currentTimeMillis()) - fgSince, Long::plus) }
        return App.entries.associateWith { AppUsage(ms[it] ?: 0, opens[it] ?: 0, unlockOpens[it] ?: 0) }
    }
}
