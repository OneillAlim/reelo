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
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    private var day = Days.today()
    private val pagers = HashMap<App, AccessibilityNodeInfo>()
    private val nudged = mutableSetOf<String>()
    private val nextPause = mutableMapOf<String, Int>()
    private val debug by lazy { applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    override fun onServiceConnected() {
        p = Prefs.get(this); db = Db.get(this)
        p.sp.registerOnSharedPreferenceChangeListener(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, "Pengingat jatah", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_FLOAT, "Maskot", NotificationManager.IMPORTANCE_LOW))
        float = FloatingMascot(this, p, ::line, ::hideToNotification, ::undo)
        p.seenStr.split('|', limit = 2).takeIf { it.size == 2 && it[0] == day.toString() }?.let { counter.load(it[1]) }
        refreshFloat()
        running = true
        Live.bump()
    }

    override fun onDestroy() {
        if (::p.isInitialized) p.sp.unregisterOnSharedPreferenceChangeListener(this)
        float?.remove(); pause?.dismiss()
        h.removeCallbacksAndMessages(null)
        running = false
        Live.bump()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key in setOf("floatOn", "snoozeUntil", "mascotName", "mascotTiktok", "mascotShorts", "mascotIg", "accName", "reduceMotion")) refreshFloat()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = rootInActiveWindow?.packageName?.toString() ?: return
                if (pkg == fgPkg || pkg in TRANSIENT || pkg == ime()) return
                App.of(fgPkg)?.let { counter.reset(it) }
                fgPkg = pkg; refreshFloat()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(e)
        }
    }

    private fun onScroll(e: AccessibilityEvent) {
        val app = App.of(e.packageName) ?: return
        val src = e.source ?: return
        val r = Rect().also { src.getBoundsInScreen(it) }
        val ratio = r.height().toFloat() / resources.displayMetrics.heightPixels
        val reel = Detectors.isReel(app, src.viewIdResourceName, e.className?.toString() ?: src.className?.toString(), ratio)
        if (debug) Log.d("ReeloScroll", "$app id=${src.viewIdResourceName} cls=${e.className} r=${"%.2f".format(ratio)} idx=${e.fromIndex} dy=${e.scrollDeltaY} reel=$reel")
        if (!reel || rootInActiveWindow?.packageName != e.packageName) return
        counter.scroll(app, e.scrollDeltaY, r.height())
        pagers[app] = src
        h.removeCallbacksAndMessages(app)
        h.postAtTime({ settle(app) }, app, SystemClock.uptimeMillis() + SETTLE_MS)
    }

    private fun settle(app: App) {
        Days.today().let { if (it != day) { day = it; counter.newDay() } }
        val parts = page(app)
        val fp = parts?.let(Detectors::fingerprint)
        val n = counter.settle(app, fp)
        if (fp != null) p.seenStr = "$day|${counter.dump()}"
        if (debug) Log.d("ReeloScroll", "$app settle +$n fp=$fp ${parts?.mapNotNull(Detectors::clean)?.distinct()?.take(6)}")
        if (n <= 0) return
        val now = System.currentTimeMillis()
        repeat(n) { db.add(app, now) }
        Live.bump()
        Widgets.update(this)
        refreshFloat()
        checkLimits(app, now)
    }

    /** Teks di halaman pager yang nutupin tengah layar, yaitu reel yang lagi diputar. */
    private fun page(app: App): List<CharSequence?>? {
        val src = pagers[app]?.takeIf { it.refresh() } ?: return null
        val cx = resources.displayMetrics.widthPixels / 2; val cy = resources.displayMetrics.heightPixels / 2
        val r = Rect()
        val cur = (0 until src.childCount).firstNotNullOfOrNull { i -> src.getChild(i)?.takeIf { it.getBoundsInScreen(r); r.contains(cx, cy) } } ?: return null
        val out = ArrayList<CharSequence?>()
        val q = ArrayDeque(listOf(cur))
        var budget = 120
        while (q.isNotEmpty() && budget-- > 0) {
            val n = q.removeFirst()
            if (n.className?.contains("SeekBar") != true) { out += n.text; out += n.contentDescription }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return out
    }

    private fun ime() = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

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
        if (n in warn until limit && nudged.add("$d|$app")) nudge(app, n, limit)
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
        if (p.strict) leave()
        float?.hide()
        pause = PauseCard(this, p, night(), title, sub, allowContinue = !p.strict, fgPkg = fgPkg, onStop = {
            leave()
            if (!p.strict && p.grant(Badge.TAHAN_GODAAN)) Live.bump()
        }, onContinue = {
            p.addSatuLagi(Days.today())
            if (focus) p.focusUntil = 0
            Live.bump()
        }, onClose = { pause = null; refreshFloat() })
    }

    /** BACK dulu biar player-nya ketutup. Kalau langsung HOME, YouTube masuk PiP dan Shorts-nya tetap muter. */
    private fun leave() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        h.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 300)
    }

    private fun nudge(app: App, n: Int, limit: Int) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val n2 = NotificationCompat.Builder(this, CH_NUDGE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Jatah ${app.label} tinggal ${limit - n}")
            .setContentText("${p.mascot.label} mulai capek. Udah $n dari $limit reels.")
            .setContentIntent(openApp(this, false)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(this).notify(app.ordinal + 10, n2) }
    }

    private fun undo(): String {
        val app = App.of(fgPkg) ?: return "Buka TikTok, Shorts, atau IG dulu, biar aku tahu yang mana."
        if (!db.removeLast(app, Days.start(Days.today()))) return "Belum ada ${app.label} yang bisa dicoret hari ini."
        Live.bump(); Widgets.update(this); refreshFloat()
        return "Oke, satu ${app.label} aku coret."
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
        if (p.snoozeUntil > now) { h.removeCallbacksAndMessages(SNOOZE); h.postAtTime({ refreshFloat() }, SNOOZE, SystemClock.uptimeMillis() + (p.snoozeUntil - now)) }
        if (!visible) { f.hide(); return }
        val (_, counts) = today()
        val app = App.of(fgPkg)
        val n = if (app != null) counts.getValue(app) else counts.values.sum()
        val limit = if (app != null) p.limit(app) else p.totalLimit
        f.show(Stats.mood(n, limit), n, p.mascotFor(app), Stats.heat(n, limit), reel = app != null)
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
        private const val SETTLE_MS = 350L
        /** Jendela yang nongol sebentar di atas app (notif shade, dialog izin). Jangan dianggap keluar dari reels. */
        private val TRANSIENT = setOf("com.android.systemui", "com.google.android.permissioncontroller", "com.miui.securitycenter")

        /** true cuma kalau service beneran nyambung. HyperOS/MIUI kadang matiin prosesnya tapi pengaturan tetap nyala. */
        @Volatile var running = false

        fun isOn(ctx: Context) = running && enabled(ctx)

        fun enabled(ctx: Context): Boolean {
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
