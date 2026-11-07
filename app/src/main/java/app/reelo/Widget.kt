package app.reelo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalTime

/** Ketuk ◆ di pojok widget = ganti gaya latar (Langit, Bioskop, Tiket). */
abstract class ReeloWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx)
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) = Widgets.update(ctx)
    override fun onDeleted(ctx: Context, ids: IntArray) { ids.forEach { Prefs.get(ctx).sp.edit().remove("wstyle_$it").apply() } }
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Widgets.ACTION_STYLE) {
            Widgets.cycle(ctx, i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            Widgets.update(ctx)
        } else super.onReceive(ctx, i)
    }
}

class SmallWidget : ReeloWidget()
class WideWidget : ReeloWidget()
class MarqueeWidget : ReeloWidget()

object Widgets {
    const val ACTION_STYLE = "app.reelo.WIDGET_STYLE"
    private val FX = intArrayOf(R.id.w_fx0, R.id.w_fx1, R.id.w_fx2)
    private val FRAMES = intArrayOf(R.id.w_m0, R.id.w_m1, R.id.w_m2, R.id.w_m3, R.id.w_m4, R.id.w_m5)

    fun style(ctx: Context, id: Int): WStyle =
        WStyle.entries.getOrNull(Prefs.get(ctx).sp.getInt("wstyle_$id", 0)) ?: WStyle.LANGIT

    fun cycle(ctx: Context, id: Int) {
        Prefs.get(ctx).sp.edit().putInt("wstyle_$id", (style(ctx, id).ordinal + 1) % WStyle.entries.size).apply()
    }

    fun update(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        fun ids(c: Class<*>) = mgr.getAppWidgetIds(ComponentName(ctx, c))
        val small = ids(SmallWidget::class.java); val wide = ids(WideWidget::class.java); val marquee = ids(MarqueeWidget::class.java)
        if (small.isEmpty() && wide.isEmpty() && marquee.isEmpty()) return
        val p = Prefs.get(ctx)
        val d = Days.today()
        val sw = Db.get(ctx).range(Days.start(d), Days.end(d))
        val total = sw.size
        val limit = p.totalLimit
        val heat = Stats.heat(total, limit)
        val mood = Stats.mood(total, limit)
        val hour = LocalTime.now().hour
        val dm = ctx.resources.displayMetrics
        val u = 3 * dm.density
        val r = 16 * dm.density
        val open = ReelService.openApp(ctx, false)
        val left = if (total > limit) "lewat ${total - limit}" else "sisa ${limit - total}"
        fun scale(dp: Int) = (dp * dm.density / (Sprite.N + 2)).toInt().coerceAtLeast(2)

        /** Ukuran widget dalam px dari opsi launcher (portrait: lebar minimum, tinggi maksimum). */
        fun size(id: Int, defW: Int, defH: Int): Pair<Int, Int> {
            val o = mgr.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defW
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defH
            return (w * dm.density).toInt() to (h * dm.density).toInt()
        }

        fun RemoteViews.mascot(frames: List<android.graphics.Bitmap>) {
            frames.forEachIndexed { i, b -> setImageViewBitmap(FRAMES[i], b) }
            setInt(R.id.w_flip, "setFlipInterval", WidgetArt.flipMs(heat))
        }

        fun RemoteViews.common(id: Int, cls: Class<*>, style: WStyle, w: Int, h: Int, panel: Boolean) {
            setImageViewBitmap(R.id.w_bg, WidgetArt.scene(style, w, h, u, r, hour, panel))
            val fh = (44 * dm.density).toInt()
            for (f in 0..1) setImageViewBitmap(FX[f], WidgetArt.fx(style, w, fh, u, hour, heat, f))
            setInt(R.id.w_fx, "setFlipInterval", if (style == WStyle.BIOSKOP) WidgetArt.flipMs(heat) else 900)
            setTextColor(R.id.w_style, WidgetArt.ink(style, hour) and 0x99FFFFFF.toInt())
            setOnClickPendingIntent(R.id.w_style, PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, cls).setAction(ACTION_STYLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            setOnClickPendingIntent(R.id.w_root, open)
            setContentDescription(R.id.w_root, "Reelo: $total reels hari ini, $left. Gaya ${style.label}.")
        }

        if (small.isNotEmpty()) {
            val frames = WidgetArt.mascot(p.mascot, mood, p.acc, scale(60))
            small.forEach { id ->
                val st = style(ctx, id)
                val (w, h) = size(id, 110, 110)
                val ink = WidgetArt.ink(st, hour)
                mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_small).apply {
                    common(id, SmallWidget::class.java, st, w, h, panel = false)
                    mascot(frames)
                    setImageViewBitmap(R.id.w_count, WidgetArt.label(ctx, "$total", 52f, if (heat >= 2) WidgetArt.accent(st, hour, heat) else ink))
                    setImageViewBitmap(R.id.w_left, WidgetArt.label(ctx, "$left reels", 15f, ink))
                })
            }
        }

        if (wide.isNotEmpty()) {
            val frames = WidgetArt.mascot(p.mascot, mood, p.acc, scale(68))
            val apps = App.entries.map { a -> Triple(a, sw.count { it.app == a }, p.limit(a)) }
            val minis = apps.map { (a, n, l) -> Sprite.bitmap(p.mascotFor(a), Stats.mood(n, l), 2, p.acc) }
            val rows = listOf(Triple(R.id.w_t1, R.id.w_p1, R.id.w_m1x), Triple(R.id.w_t2, R.id.w_p2, R.id.w_m2x), Triple(R.id.w_t3, R.id.w_p3, R.id.w_m3x))
            wide.forEach { id ->
                val st = style(ctx, id)
                val (w, h) = size(id, 250, 110)
                mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_wide).apply {
                    common(id, WideWidget::class.java, st, w, h, panel = true)
                    mascot(frames)
                    setImageViewBitmap(R.id.w_count, WidgetArt.label(ctx, "$total reels", 26f, if (heat >= 2) WidgetArt.accent(st, hour, heat) else WidgetArt.ink(st, hour)))
                    rows.forEachIndexed { i, (tv, pb, iv) ->
                        val (a, n, l) = apps[i]
                        setImageViewBitmap(iv, minis[i])
                        setTextViewText(tv, "${a.label}  $n/$l")
                        setProgressBar(pb, l.coerceAtLeast(1), n.coerceAtMost(l), false)
                        setColorStateList(pb, "setProgressTintList", android.content.res.ColorStateList.valueOf(
                            when (Stats.heat(n, l)) { 3 -> 0xFFE0503A.toInt(); 2 -> 0xFFFF8A3D.toInt(); else -> 0xFF22897F.toInt() }))
                    }
                })
            }
        }

        if (marquee.isNotEmpty()) {
            val frames = WidgetArt.mascot(p.mascot, mood, p.acc, scale(44))
            marquee.forEach { id ->
                val (w, h) = size(id, 250, 60)
                mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_marquee).apply {
                    setImageViewBitmap(R.id.w_bg, WidgetArt.sign(w, h, u, r))
                    for (f in 0..2) setImageViewBitmap(FX[f], WidgetArt.signFx(w, h, u, heat, f))
                    setInt(R.id.w_fx, "setFlipInterval", WidgetArt.flipMs(heat) / 2 + 150)
                    mascot(frames)
                    setImageViewBitmap(R.id.w_label, WidgetArt.label(ctx, "HARI INI", 13f, 0xFFF6E2B5.toInt()))
                    setImageViewBitmap(R.id.w_count, WidgetArt.label(ctx, "$total reels", 32f, if (heat >= 3) 0xFFFF6B55.toInt() else 0xFFFFC458.toInt(), glow = true))
                    setImageViewBitmap(R.id.w_left, WidgetArt.label(ctx, "$left\ndari $limit", 16f, 0xFFF6E2B5.toInt()))
                    setOnClickPendingIntent(R.id.w_root, open)
                    setContentDescription(R.id.w_root, "Reelo: $total reels hari ini, $left dari $limit.")
                })
            }
        }
    }
}
