package app.reelo

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

class ReelService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val counter = SwipeCounter()
    private val h = Handler(Looper.getMainLooper())
    private lateinit var p: Prefs
    private lateinit var db: Db
    private var float: FloatingMascot? = null
    private var pause: PauseCard? = null
    private var fgPkg: String? = null
    private val nudged = mutableSetOf<String>()
    private val nextPause = mutableMapOf<String, Int>()

    override fun onServiceConnected() {
        p = Prefs.get(this); db = Db.get(this)
        p.sp.registerOnSharedPreferenceChangeListener(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, "Pengingat jatah", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_FLOAT, "Maskot", NotificationManager.IMPORTANCE_LOW))
        float = FloatingMascot(this, p, ::line, ::hideToNotification)
        refreshFloat()
        Live.bump()
    }

    override fun onDestroy() {
        if (::p.isInitialized) p.sp.unregisterOnSharedPreferenceChangeListener(this)
        float?.remove(); pause?.dismiss()
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key in setOf("floatOn", "snoozeUntil", "mascotName", "accName", "reduceMotion")) refreshFloat()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = rootInActiveWindow?.packageName?.toString() ?: return
                if (pkg != fgPkg) { fgPkg = pkg; refreshFloat() }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(e)
        }
    }

    private fun onScroll(e: AccessibilityEvent) {
        val app = App.of(e.packageName) ?: return
        val src = e.source ?: return
        val r = Rect().also { src.getBoundsInScreen(it) }
        val ratio = r.height().toFloat() / resources.displayMetrics.heightPixels
        if (!Detectors.isReel(app, src.viewIdResourceName, e.className?.toString() ?: src.className?.toString(), ratio)) return
        if (rootInActiveWindow?.packageName != e.packageName) return
        val now = System.currentTimeMillis()
        if (!counter.onScroll(app, now, e.fromIndex)) return
        db.add(app, now)
        Live.bump()
        Widgets.update(this)
        refreshFloat()
        checkLimits(app, now)
    }

    private fun today(): Pair<LocalDate, Map<App, Int>> {
        val d = Days.today()
        val sw = db.range(Days.start(d), Days.end(d))
        return d to App.entries.associateWith { a -> sw.count { it.app == a } }
    }

    private fun checkLimits(app: App, now: Long) {
        val (d, counts) = today()
        val n = counts.getValue(app)
        val limit = p.limit(app)
        if (p.focusUntil > now) {
            show("Lagi fokus", "Masih ${(p.focusUntil - now) / 60_000 + 1} menit lagi. Reels-nya nanti aja ya.", focus = true)
            return
        }
        val hour = LocalTime.now().hour
        if (p.inNight(hour)) {
            val startDay = if (p.nightStart > p.nightEnd && hour < p.nightEnd) LocalDate.now().minusDays(1) else LocalDate.now()
            val ns = startDay.atTime(p.nightStart, 0).atZone(Days.zone).toInstant().toEpochMilli()
            val nn = db.count(ns, now + 1)
            if (gate("$d|night", nn, p.nightLimit)) { show("Jam Malam", "Udah $nn reels sejak ${"%02d".format(p.nightStart)}.00. Waktunya tidur."); return }
        }
        val warn = ceil(limit * .8).toInt()
        if (n == warn && limit > 0 && nudged.add("$d|$app")) nudge(app, n, limit)
        if (gate("$d|$app", n, limit)) show("Jatah ${app.label} habis", "$n reels hari ini. Total ${counts.values.sum()} dari ${p.totalLimit}.")
    }

    private fun gate(key: String, n: Int, limit: Int): Boolean {
        val at = nextPause[key] ?: limit
        if (n < at || limit <= 0 && n == 0) return false
        nextPause[key] = n + 5
        return true
    }

    private fun show(title: String, sub: String, focus: Boolean = false) {
        if (pause != null) return
        if (p.strict) performGlobalAction(GLOBAL_ACTION_HOME)
        float?.hide()
        pause = PauseCard(this, p, night(), title, sub, allowContinue = !p.strict, onStop = {
            performGlobalAction(GLOBAL_ACTION_HOME)
            if (!p.strict && p.grant(Badge.TAHAN_GODAAN)) Live.bump()
        }, onContinue = {
            p.addSatuLagi(Days.today())
            if (focus) p.focusUntil = 0
            Live.bump()
        }, onClose = { pause = null; refreshFloat() })
    }

    private fun nudge(app: App, n: Int, limit: Int) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val n2 = NotificationCompat.Builder(this, CH_NUDGE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Jatah ${app.label} tinggal ${limit - n}")
            .setContentText("${p.mascot.label} mulai capek. Udah $n dari $limit reels.")
            .setContentIntent(openApp(this, false)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(this).notify(app.ordinal + 10, n2) }
    }

    private fun hideToNotification() {
        p.floatOn = false
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(this, CH_FLOAT).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${p.mascot.label} disembunyikan").setContentText("Ketuk buat munculin lagi.")
            .setContentIntent(openApp(this, true)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_FLOAT, n) }
    }

    private fun night() = wantNight(p, (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES, LocalTime.now().hour)

    private fun refreshFloat() {
        val f = float ?: return
        val now = System.currentTimeMillis()
        val visible = p.floatOn && now >= p.snoozeUntil && fgPkg != packageName && pause == null
        if (p.snoozeUntil > now) { h.removeCallbacksAndMessages(SNOOZE); h.postAtTime({ refreshFloat() }, SNOOZE, android.os.SystemClock.uptimeMillis() + (p.snoozeUntil - now)) }
        if (!visible) { f.hide(); return }
        val (_, counts) = today()
        val total = counts.values.sum()
        val app = App.of(fgPkg)
        f.show(Stats.mood(total, p.totalLimit), if (app != null) counts.getValue(app) else total)
    }

    private fun line(): String {
        val (_, counts) = today()
        val total = counts.values.sum(); val limit = p.totalLimit
        val app = App.of(fgPkg)
        if (app != null) {
            val n = counts.getValue(app); val l = p.limit(app)
            return when { n >= l -> "Ini reel ke-$n di ${app.label}. Napas dulu yuk."; l - n <= 5 -> "Jatah ${app.label} tinggal ${l - n}."; else -> "$n reels di ${app.label}. Aman, lanjut santai." }
        }
        return when {
            total == 0 -> "Belum ada reels hari ini. Aku jagain dari sini."
            total >= limit -> "Hari ini udah $total reels. Istirahat dulu ya."
            total >= limit * 2 / 3 -> "Udah $total reels. Jempolnya pegel?"
            else -> "Baru $total reels hari ini. Mantap!"
        }
    }

    companion object {
        const val CH_NUDGE = "nudge"
        const val CH_FLOAT = "float"
        const val NOTIF_FLOAT = 1
        private val SNOOZE = Any()

        fun isOn(ctx: Context): Boolean {
            val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.split(':').any { it.startsWith("${ctx.packageName}/") }
        }

        fun openApp(ctx: Context, unhide: Boolean): PendingIntent = PendingIntent.getActivity(
            ctx, if (unhide) 1 else 0,
            Intent(ctx, MainActivity::class.java).putExtra("unhide", unhide).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
