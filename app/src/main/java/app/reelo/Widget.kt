package app.reelo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx)
}

class WideWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx)
}

object Widgets {
    fun update(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val small = mgr.getAppWidgetIds(ComponentName(ctx, SmallWidget::class.java))
        val wide = mgr.getAppWidgetIds(ComponentName(ctx, WideWidget::class.java))
        if (small.isEmpty() && wide.isEmpty()) return
        val p = Prefs.get(ctx)
        val d = Days.today()
        val sw = Db.get(ctx).range(Days.start(d), Days.end(d))
        val total = sw.size
        val limit = p.totalLimit
        val bmp = Sprite.bitmap(p.mascot, Stats.mood(total, limit), 6, p.acc)
        val open = ReelService.openApp(ctx, false)
        if (small.isNotEmpty()) mgr.updateAppWidget(small, RemoteViews(ctx.packageName, R.layout.widget_small).apply {
            setImageViewBitmap(R.id.w_mascot, bmp)
            setTextViewText(R.id.w_count, "$total")
            setTextViewText(R.id.w_left, if (total > limit) "lewat ${total - limit}" else "sisa ${limit - total} reels")
            setOnClickPendingIntent(R.id.w_root, open)
        })
        if (wide.isNotEmpty()) mgr.updateAppWidget(wide, RemoteViews(ctx.packageName, R.layout.widget_wide).apply {
            setImageViewBitmap(R.id.w_mascot, bmp)
            setTextViewText(R.id.w_count, "$total reels")
            val rows = listOf(Triple(R.id.w_t1, R.id.w_p1, App.TIKTOK), Triple(R.id.w_t2, R.id.w_p2, App.SHORTS), Triple(R.id.w_t3, R.id.w_p3, App.IG))
            val icons = listOf(R.id.w_m1, R.id.w_m2, R.id.w_m3)
            rows.forEachIndexed { i, (tv, pb, app) ->
                val n = sw.count { it.app == app }; val l = p.limit(app)
                setImageViewBitmap(icons[i], Sprite.bitmap(p.mascotFor(app), Stats.mood(n, l), 2, p.acc))
                setTextViewText(tv, "${app.label}  $n/$l")
                setProgressBar(pb, l.coerceAtLeast(1), n.coerceAtMost(l), false)
            }
            setOnClickPendingIntent(R.id.w_root, open)
        })
    }
}
