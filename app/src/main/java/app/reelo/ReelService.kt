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
    private val bot by lazy { TikTokBot(this) }
    private var flameCard: FlameCard? = null
    private val unlock = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_OFF) float?.hide() else { flameMaybe(); refreshFloat() } }
    }

    override fun onServiceConnected() {
        p = Prefs.get(this); db = Db.get(this)
        p.sp.registerOnSharedPreferenceChangeListener(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, "Pengingat jatah", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_FLOAT, "Maskot", NotificationManager.IMPORTANCE_LOW))
        float = FloatingMascot(this, p, ::line, ::hideToNotification, ::undo)
        p.seenStr.split('|', limit = 2).takeIf { it.size == 2 && it[0] == day.toString() }?.let { counter.load(it[1]) }
        refreshFloat()
        inst = this
        androidx.core.content.ContextCompat.registerReceiver(this, unlock, android.content.IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_OFF) }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        FlameAlarm.schedule(this)
        // nyambung lagi setelah dimatiin sistem: hapus notif "Reelo lagi mati", kejar kalau hari ini belum jalan
        if (!FlameAlarm.locked(this)) FlameAlarm.clear(this) // kalau masih terkunci, notif "buka HP" biarin
        flameMaybe()
        Live.bump()
    }

    override fun onDestroy() {
        if (::p.isInitialized) p.sp.unregisterOnSharedPreferenceChangeListener(this)
        float?.remove(); pause?.dismiss(); flameCard?.dismiss()
        h.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlock) }
        if (inst === this) inst = null
        bot.destroy()
        Live.bump()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key in setOf("floatOn", "snoozeUntil", "mascotName", "mascotTiktok", "mascotShorts", "mascotIg", "accName", "reduceMotion", "limitsOn")) refreshFloat()
        if (key == "flameOn" || key == "flameAll" || key == "limitsOn") { FlameAlarm.schedule(this); Widgets.update(this) }
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
        if (bot.running) return // robot Jaga Api lagi scroll Inbox, bukan kamu
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
        if (bot.running || !p.limitsOn) return
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

    // ---- Jaga Api ----

    /**
     * Siapa yang mau dikirimin: mode "semua yang ngestreak" = tiap baris Inbox yang ada apinya (termasuk api baru),
     * selain itu cuma yang dicentang. Ditambah yang dicentang walau belum/nggak ngestreak (daftar Tambahan, buat
     * mulai atau nyalain ulang streak). Yang udah kebagian hari ini selalu dilewati. [only] = Tes 1 teman.
     * Hasil: (mau dikirimin?, perkiraan jumlah, nama yang dikirimin walau tanpa api).
     */
    private fun flameWant(only: String?, again: Boolean = false): Triple<(String) -> Boolean, Int, Set<String>> {
        val day = Flame.day(System.currentTimeMillis())
        // otomatis: maksimal sekali sehari. Manual: lewati cuma yang udah dapat pesan yang sama hari ini.
        val done = if (again) db.sentOkWith(day, db.msgs().filter { it.on }.map { it.id }) else db.sentOk(day)
        val friends = db.friends()
        val picked = friends.filter { it.on }.map { it.name }.toSet()
        val all = p.flameAll && only == null
        val want = { name: String -> name !in done && (if (only != null) name == only else all || name in picked) }
        val est = friends.count { (it.streak > 0 || it.on || it.name == only) && want(it.name) }.coerceAtMost(Flame.MAX_FRIENDS)
        return Triple(want, est, picked + listOfNotNull(only))
    }

    private fun flameLater(at: Long) { p.flameRetryAt = at; FlameAlarm.schedule(this) }

    private fun hasMsg() = db.msgs().any { it.on }

    /**
     * Cek apakah Jaga Api harus jalan sekarang: nyala, belum jalan hari ini, nggak ditunda, HP kebuka, nggak lagi telepon.
     * Dipanggil alarm [FlameAlarm] (00.00 dan coba ulang) dan tiap HP dibuka. Langsung jalan, atau hitung mundur dulu kalau setelannya nyala.
     * [force] = tombol tes di build debug.
     */
    fun flameMaybe(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (!p.flameOn || !Flame.due(p.flameLastRun, now) || now < p.flameSnoozeUntil || now < p.flameRetryAt)) return
        if (bot.running || flameCard != null) return
        if (FlameAlarm.locked(this)) return // jalan pas HP dibuka (USER_PRESENT)
        // lagi telepon atau Pause Card lagi nongol: jangan diganggu, alarm 2 menit lagi
        if (pause != null || getSystemService(android.media.AudioManager::class.java).mode != android.media.AudioManager.MODE_NORMAL) { flameLater(now + 2 * 60_000); return }
        if (!hasMsg()) return
        val (_, est) = flameWant(null)
        if (est == 0 && !p.flameAll) return // belum ada yang dicentang; dicek lagi pas HP dibuka berikutnya
        float?.hide()
        // flameCard di-assign dulu sebelum flameRun: kalau nggak, flameRun bikin kartu kedua dan kartu ini nyangkut kosong
        // di layar, terus semua run berikutnya (coba ulang, widget, besok) kebaca "lagi jalan"
        val card = FlameCard(this, night(), onClose = { flameCard = null; refreshFloat() })
        flameCard = card
        if (p.flameCountdown || force) card.countdown(est, onSnooze = { System.currentTimeMillis().plus(60 * 60_000).let { p.flameSnoozeUntil = it; flameLater(it) }; card.dismiss() }, onStart = { flameRun(null) })
        else flameRun(null)
    }

    /**
     * Kirim sekarang. Dari layar Jaga Api ([again] = true): teman yang udah kebagian hari ini tetap dikirimin lagi
     * (layar udah nanya dulu), kecuali yang udah dapat pesan yang sama. Dari widget/notif ([again] = false): cuma yang
     * belum kebagian sama sekali. -1 = lagi jalan, 0 = nggak ada pesan aktif, 2 = semua udah aman, 1 = jalan.
     */
    fun flameNow(only: String? = null, again: Boolean = true): Int {
        if (bot.running || flameCard != null) return -1
        if (!hasMsg()) return 0
        if (!again && flameWant(only).second == 0 && db.friends().any { it.streak > 0 || it.on }) return 2
        flameRun(only, again)
        return 1
    }

    private fun flameRun(only: String?, again: Boolean = false) {
        val today = Flame.day(System.currentTimeMillis())
        val (want, est, extra) = flameWant(only, again)
        val msgs = db.msgs(); val rnd = java.util.Random()
        float?.hide()
        val card = flameCard ?: FlameCard(this, night(), onClose = { flameCard = null; refreshFloat() }).also { flameCard = it }
        FlameAlarm.clear(this)
        var ok = 0; var n = 0; var stopped = false
        // angka api yang kebaca di Inbox, biar widget nggak nyangkut. Selama jalan cuma boleh naik: TikTok nampilin nama
        // duluan dan angka belakangan, jadi satu kali kebaca 0 belum tentu padam. Yang sepanjang sapuan selalu 0 (dan
        // kebaca minimal 2x) baru di-nolkan di akhir, kalau sapuannya selesai normal. Sisanya dibenerin pas pindai.
        val last = db.friends().associate { it.name to it.streak }.toMutableMap()
        val best = HashMap<String, Int>(); val zeros = HashMap<String, Int>()
        bot.sweep(
            want = { name -> n < Flame.MAX_FRIENDS && want(name) },
            pick = { name, _ -> Flame.pick(msgs, db.lastMsg(name), rnd)?.let { m -> m.id to m.text } },
            total = est, back = fgPkg,
            progress = { i, t, name -> card.progress(i, t, name) { stopped = true; bot.stop() } },
            each = { name, streak, id, note ->
                n++
                db.logSent(today, name, id, note == null, note)
                // angka api nggak ditebak (+1): naiknya baru kalau dia bales, dan teman Tambahan bakal kebaca punya api
                // palsu. Angka cuma diambil dari yang kebaca di Inbox (saw).
                if (note == null) ok++
            },
            extra = extra,
            saw = { name, s ->
                best[name] = maxOf(best[name] ?: 0, s); if (s == 0) zeros[name] = (zeros[name] ?: 0) + 1
                if (s > 0 && last[name] != s) { last[name] = s; db.touchFriend(name, s) }
            },
            done = { cancelled ->
                // cancelled tanpa Batal = TikTok/Inbox nggak kebuka, dihitung gagal juga
                if (!cancelled) best.forEach { (name, s) -> if (s == 0 && (zeros[name] ?: 0) >= 2 && (last[name] ?: 0) > 0) db.touchFriend(name, 0) }
                val bad = n - ok + if (cancelled && !stopped) 1 else 0
                val retry = if (only == null) flameAfter(today, bad, stopped) else null
                card.dismiss()
                Live.bump(); Widgets.update(this)
                notifyFlame(ok, n, stopped, cancelled && !stopped, retry)
            })
    }

    /**
     * Abis jalan: hari ditandai beres kalau nol gagal atau kamu yang Batal. Kalau ada yang gagal, coba ulang otomatis
     * (+10, +20 menit, cuma ke yang belum kebagian) sampai [Flame.MAX_TRIES]x. Hasil = jam coba ulang, null = udahan.
     */
    private fun flameAfter(day: java.time.LocalDate, bad: Int, stopped: Boolean): Long? {
        var retry: Long? = null
        if (bad == 0 || stopped) p.flameTries = ""
        else {
            val t = Flame.tries(p.flameTries, day) + 1
            p.flameTries = "$day|$t"
            retry = Flame.retryAt(t, System.currentTimeMillis())
        }
        p.flameRetryAt = retry ?: 0
        if (retry == null) p.flameLastRun = day.toString()
        FlameAlarm.schedule(this)
        return retry
    }

    /** Dari layar Teman: pindai Inbox. done(jumlah) atau null kalau gagal. */
    fun flameScan(done: (Int?) -> Unit) {
        if (bot.running) { done(null); return }
        bot.scan(fgPkg) { found, complete ->
            if (found != null) { db.saveScan(found, complete); p.flameScanTs = System.currentTimeMillis() }
            Live.bump(); done(found?.count { it.streak > 0 })
        }
    }

    private fun notifyFlame(ok: Int, n: Int, stopped: Boolean, broke: Boolean, retry: Long?) {
        val title = when {
            broke && n == 0 -> "TikTok nggak kebuka"
            n == 0 -> "Semua api udah aman hari ini"
            ok == n -> "Api $ok teman aman 🔥"
            else -> "Api $ok dari $n teman aman"
        }
        val at = retry?.let { java.time.Instant.ofEpochMilli(it).atZone(Days.zone).toLocalTime().toString().take(5).replace(':', '.') }
        val text = when {
            stopped -> "Dibatalin di tengah jalan."
            at != null -> "Ada yang gagal, aku coba lagi jam $at."
            broke || ok < n -> "Masih ada yang gagal. Ketuk Coba lagi, atau lihat alasannya di Riwayat."
            else -> "Tinggal tunggu mereka bales."
        }
        FlameAlarm.notify(this, title, text, openApp(this, false), action = !stopped && at == null && (broke || ok < n), id = NOTIF_FLAME, label = "Coba lagi")
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
        val visible = p.floatOn && p.limitsOn && now >= p.snoozeUntil && fgPkg != packageName && pause == null && flameCard == null && getSystemService(android.os.PowerManager::class.java).isInteractive
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
        const val NOTIF_FLAME = 2
        /** Service yang lagi nyambung, buat layar Jaga Api (pindai/kirim). null kalau penghitung mati. */
        @Volatile var inst: ReelService? = null
        private val SNOOZE = Any()
        private const val SETTLE_MS = 350L
        /** Jendela yang nongol sebentar di atas app (notif shade, dialog izin). Jangan dianggap keluar dari reels. */
        private val TRANSIENT = setOf("android", "com.android.systemui", "com.google.android.permissioncontroller", "com.miui.securitycenter")

        /** true cuma kalau service beneran nyambung. HyperOS/MIUI kadang matiin prosesnya tapi pengaturan tetap nyala. */
        // dari inst, bukan flag sendiri: pas Aksesibilitas di-set ulang, onDestroy service lama bisa datang
        // setelah service baru nyambung, dan flag biasa bakal ke-reset jadi false padahal service hidup
        val running get() = inst != null

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
