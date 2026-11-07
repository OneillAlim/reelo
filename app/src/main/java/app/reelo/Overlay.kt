package app.reelo

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.hypot

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
private val STEPS5 = android.animation.TimeInterpolator { (it * 5).toInt() / 5f }

private fun overlayParams(w: Int, h: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
    w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
    PixelFormat.TRANSLUCENT,
).apply { gravity = Gravity.TOP or Gravity.START }

private fun box(fill: Int, stroke: Int, w: Int) = GradientDrawable().apply { setColor(fill); setStroke(w, stroke) }

/** Gambar sprite maskot, goyang dan kedip sendiri. Dipakai overlay dan Pause Card. */
@SuppressLint("ViewConstructor")
class SpriteView(ctx: Context, private val p: Prefs, private val px: Int) : View(ctx) {
    var mood = Mood.SEGAR
    var mascot: Mascot = p.mascot
    var count: Int? = null
    var sleeping = false
    /** 1 hadap kanan, -1 hadap kiri (sprite dicerminkan). */
    var facing = 1
    var walking = false
    /** Badge angka kedip cepat sampai waktu ini (pas nembus kelipatan 10). */
    var flashUntil = 0L
    /** 0 tenang, 1 waspada, 2 panas, 3 lewat jatah. Makin tinggi makin sering badge kedip. */
    var heat = 0
    private val t0 = (Math.random() * 3600).toLong()
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(ctx, R.font.jersey); textSize = ctx.dp(14).toFloat(); color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val fill = Paint()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px * (Sprite.N + 2) + context.dp(10), px * (Sprite.N + 2) + context.dp(10))

    override fun onDraw(c: Canvas) {
        val reduce = p.reduceMotion
        val now = System.currentTimeMillis()
        val t = now + t0
        val per = if (walking) 140 else when (mood) { Mood.SEGAR -> 380; Mood.SANTAI -> 520; Mood.CAPEK -> 800; Mood.TELER -> 650; Mood.PINGSAN -> 0 }
        val ph = if (reduce || per == 0 || sleeping) 0 else ((t / per) % 2).toInt()
        val dx = if (mood == Mood.TELER && !reduce && !sleeping && !walking) (if (ph == 1) 1 else -1) else 0
        val dy = if (mood != Mood.TELER && ph == 1) -1 else 0
        val blink = sleeping || (!reduce && t % 3400 < 150)
        val s = px.toFloat()
        c.save()
        if (facing < 0) c.scale(-1f, 1f, (Sprite.N + 2) * s / 2, 0f)
        Sprite.draw(c, Sprite.grid(mascot, mood, blink, p.acc), s, dx * s, context.dp(10) + dy * s)
        c.restore()
        count?.let { n ->
            val label = n.toString()
            val w = maxOf(context.dp(22).toFloat(), badge.measureText(label) + context.dp(10))
            val r = width.toFloat(); val bh = context.dp(18).toFloat()
            val lift = if (reduce) 0f else Bob.lift(now, heat, 0) * context.dp(1)
            c.save(); c.translate(0f, lift + context.dp(3))
            // kedip: 200 ms nyala/mati pas nembus kelipatan 10, lalu makin sering sesuai heat (maks 2,5x per detik)
            val off = !reduce && (now < flashUntil && (now / 200) % 2 == 1L || heat > 0 && (now / BLINK[heat]) % 2 == 1L)
            fill.color = 0xFF2B2118.toInt(); c.drawRect(r - w - 2, 0f, r, bh + 2, fill)
            fill.color = if (off) HOT[heat].second else HOT[heat].first; c.drawRect(r - w, 2f, r - 2, bh, fill)
            c.drawText(label, r - w / 2 - 1, bh - context.dp(4), badge)
            c.restore()
        }
        if (sleeping) {
            badge.textAlign = Paint.Align.LEFT
            val k = ((t / 400) % 4).toInt()
            c.drawText("z z", context.dp(2).toFloat(), context.dp(12) - k * 2f, badge.apply { alpha = 255 - k * 50 })
            badge.alpha = 255; badge.textAlign = Paint.Align.CENTER
        }
        if (!reduce) postInvalidateDelayed(if (walking) 60 else 90)
    }

    companion object {
        private val BLINK = longArrayOf(0, 900, 500, 400)
        private val HOT = listOf(
            0xFFE8553D.toInt() to 0xFFF2B544.toInt(), 0xFFE8553D.toInt() to 0xFFF2B544.toInt(),
            0xFFE8553D.toInt() to 0xFFFF8A3D.toInt(), 0xFFB53B2A.toInt() to 0xFFFF2E2E.toInt(),
        )
    }
}

class FloatingMascot(private val ctx: Context, private val p: Prefs, private val line: () -> String, private val onTrash: () -> Unit, private val undo: () -> String) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    /** Timer tidur/jalan/ngomong. Bukan view.postDelayed: itu nggak bisa dibatalin pas maskot lagi dilepas dari layar. */
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val view = SpriteView(ctx, p, ctx.dp(2) + ctx.dp(1) / 2)
    private val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var attached = false
    private var peek = 0
    private val screenW get() = ctx.resources.displayMetrics.widthPixels
    private val screenH get() = ctx.resources.displayMetrics.heightPixels
    private val bw get() = view.measuredWidth.takeIf { it > 0 } ?: ctx.dp(75)

    private val say = TextView(ctx).apply {
        setTextColor(0xFF2B2118.toInt()); textSize = 12f; maxWidth = ctx.dp(190)
        setPadding(ctx.dp(9), ctx.dp(6), ctx.dp(9), ctx.dp(6)); background = box(0xFFFFF8EC.toInt(), 0xFF2B2118.toInt(), ctx.dp(2))
        typeface = ResourcesCompat.getFont(ctx, R.font.rubik)
    }
    private val sayLp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = false)
    private var sayShown = false
    private val trash = TextView(ctx).apply {
        text = "x"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 26f
        typeface = ResourcesCompat.getFont(ctx, R.font.jersey); background = box(0x66000000, Color.WHITE, ctx.dp(3))
    }
    private val trashLp = overlayParams(ctx.dp(64), ctx.dp(64), touchable = false)
    private var trashShown = false
    private var menu: LinearLayout? = null

    private val rise = Runnable { if (attached && view.sleeping) wake() }
    // tidur abis 45 detik nganggur, bangun sendiri 1,5-2,5 menit kemudian terus jalan-jalan lagi
    private val idle = Runnable { view.sleeping = true; view.invalidate(); h.postDelayed(rise, 90_000L + rnd.nextInt(60_000)) }
    private val patrol = Runnable { roam() }
    private var anim: ValueAnimator? = null
    private var lastCount = -1
    private val rnd = java.util.Random()
    private val unsay = Runnable { if (sayShown) { wm.removeView(say); sayShown = false } }

    init {
        lp.x = if (p.floatX < 0) screenW - ctx.dp(80) else p.floatX
        lp.y = p.floatY
        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                when {
                    peek != 0 -> { peek = 0; snap(); speak("Aku balik!") }
                    view.sleeping -> { hop(34); speak("!"); h.postDelayed({ speak("Hoaam... aku ketiduran.") }, 700) }
                    else -> { hop(); speak(line()) }
                }
                wake(); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                unsay.run(); peek = if (lp.x + bw / 2 < screenW / 2) -1 else 1
                animateTo(if (peek < 0) -bw / 2 else screenW - bw / 2, lp.y); return true
            }
            override fun onLongPress(e: MotionEvent) { if (!dragging) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); openMenu() } }
        })
        view.setOnTouchListener { _, e -> detector.onTouchEvent(e); drag(e); true }
    }

    private var dragging = false
    private var sx = 0f; private var sy = 0f; private var ox = 0; private var oy = 0
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private fun drag(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { anim?.cancel(); view.walking = false; sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; dragging = false; wake(); closeMenu() }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(e.rawX - sx, e.rawY - sy) > slop) { dragging = true; peek = 0; unsay.run(); closeMenu(); showTrash() }
                if (dragging) {
                    lp.x = ox + (e.rawX - sx).toInt(); lp.y = (oy + (e.rawY - sy).toInt()).coerceIn(0, screenH - bw)
                    wm.updateViewLayout(view, lp)
                    val hot = overTrash(); trash.scaleX = if (hot) 1.25f else 1f; trash.scaleY = trash.scaleX
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                val hot = overTrash(); hideTrash(); dragging = false
                if (hot) { hide(); onTrash() } else snap()
            }
        }
    }

    private fun overTrash(): Boolean {
        val cx = lp.x + bw / 2f; val cy = lp.y + bw / 2f
        return hypot(cx - (trashLp.x + ctx.dp(32)), cy - (trashLp.y + ctx.dp(32))) < ctx.dp(70)
    }

    private fun showTrash() {
        if (trashShown) return
        trashLp.x = screenW / 2 - ctx.dp(32); trashLp.y = screenH - ctx.dp(150)
        wm.addView(trash, trashLp); trashShown = true
    }

    private fun hideTrash() { if (trashShown) { wm.removeView(trash); trashShown = false } }

    private var inReel = false

    /**
     * Tinggi yang aman buat maskot. Di app reels, sisi kanan dibatasi ke atas layar (10-26%) karena di bawahnya ada
     * kolom tombol like/komentar/share. Sisi kiri boleh sampai 60% (di bawahnya caption).
     */
    private fun yRange(left: Boolean): IntRange {
        val top = ctx.dp(40) + if (inReel) (screenH * .1f).toInt() else 0
        val bottom = when { !inReel -> screenH - bw - ctx.dp(160); left -> (screenH * .6f).toInt(); else -> (screenH * .26f).toInt() }
        return top..bottom.coerceAtLeast(top + 1)
    }

    private fun isLeft() = lp.x + bw / 2 < screenW / 2

    private fun snap() {
        val left = isLeft()
        val x = if (left) ctx.dp(6) else screenW - bw - ctx.dp(6)
        val y = lp.y.coerceIn(yRange(left))
        animateTo(x, y)
        p.floatX = x; p.floatY = y
    }

    /** Pindah ke (x, y). arc > 0 = lompat melengkung setinggi arc px. Gerak patah-patah biar kerasa pixel. */
    private fun animateTo(x: Int, y: Int, ms: Long = 250, arc: Int = 0, steps: Int = 5, end: () -> Unit = {}) {
        val fx = lp.x; val fy = lp.y
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (p.reduceMotion) 0 else ms
            interpolator = android.animation.TimeInterpolator { (it * steps).toInt() / steps.toFloat() }
            addUpdateListener {
                if (!attached) return@addUpdateListener
                val f = it.animatedValue as Float
                lp.x = (fx + (x - fx) * f).toInt(); lp.y = (fy + (y - fy) * f - arc * 4 * f * (1 - f)).toInt()
                wm.updateViewLayout(view, lp)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (anim === a) { anim = null; end() } }
            })
            start()
        }
    }

    private fun hop(h: Int = 14) {
        if (p.reduceMotion) return
        view.animate().translationY(-ctx.dp(h).toFloat()).setDuration(180).setInterpolator(STEPS5).withEndAction {
            view.animate().translationY(0f).setDuration(220).setInterpolator(STEPS5).start()
        }.start()
    }

    /** Geleng kiri-kanan, dipakai pas jatah hampir habis. */
    private fun shake() {
        if (p.reduceMotion) return
        val d = ctx.dp(4).toFloat()
        ValueAnimator.ofFloat(0f, d, -d, d, -d, 0f).apply {
            duration = 420; interpolator = android.animation.TimeInterpolator { (it * 6).toInt() / 6f }
            addUpdateListener { view.translationX = it.animatedValue as Float }
            start()
        }
    }

    private fun wake() {
        view.sleeping = false; view.invalidate()
        h.removeCallbacks(rise); h.removeCallbacks(idle); h.postDelayed(idle, 45_000)
        schedule()
    }

    private fun schedule() {
        h.removeCallbacks(patrol)
        if (!p.reduceMotion) h.postDelayed(patrol, 6_000L + rnd.nextInt(9_000))
    }

    /**
     * Jalan-jalan sendiri kalau lagi nganggur: nyusurin pinggir layar ke tinggi acak, kadang lompat ke sisi seberang.
     * Berhenti kalau lagi tidur, di-drag, ngintip, ngomong, atau menu kebuka.
     */
    private fun roam() {
        if (!attached || dragging || peek != 0 || menu != null || sayShown || view.sleeping || p.focusUntil > System.currentTimeMillis()) { schedule(); return }
        val left = isLeft()
        if (rnd.nextInt(4) == 0) {
            val x = if (left) screenW - bw - ctx.dp(6) else ctx.dp(6)
            view.facing = if (left) 1 else -1
            animateTo(x, lp.y.coerceIn(yRange(!left)), 700, arc = ctx.dp(90), steps = 10) { save() }
        } else {
            val r = yRange(left)
            val y = r.first + rnd.nextInt(r.last - r.first)
            view.walking = true
            animateTo(lp.x, y, (kotlin.math.abs(y - lp.y) * 4L).coerceIn(400, 2600), steps = 24) { view.walking = false; save() }
        }
        schedule()
    }

    private fun save() { p.floatX = lp.x; p.floatY = lp.y }

    fun speak(text: String) {
        say.text = text
        say.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val left = lp.x + bw / 2 < screenW / 2
        sayLp.x = if (left) lp.x + bw + ctx.dp(4) else lp.x - say.measuredWidth - ctx.dp(4)
        sayLp.y = lp.y + ctx.dp(14)
        if (sayShown) wm.updateViewLayout(say, sayLp) else { wm.addView(say, sayLp); sayShown = true }
        h.removeCallbacks(unsay); h.postDelayed(unsay, 2600)
    }

    private fun openMenu() {
        closeMenu(); unsay.run()
        val m = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun item(label: String, act: () -> Unit) = m.addView(TextView(ctx).apply {
            text = label; textSize = 13f; setTextColor(0xFF2B2118.toInt()); typeface = ResourcesCompat.getFont(ctx, R.font.rubik)
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8)); background = box(0xFFFFF8EC.toInt(), 0xFF2B2118.toInt(), ctx.dp(2))
            setOnClickListener { closeMenu(); act() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(4) })
        item("Buka Reelo") { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        item("Fokus 25 menit") { p.focusUntil = System.currentTimeMillis() + 25 * 60_000; Live.bump(); speak("Oke, 25 menit tanpa reels. Aku jagain.") }
        item("Salah hitung? Coret 1") { speak(undo()) }
        item("Sembunyikan 1 jam") { p.snoozeUntil = System.currentTimeMillis() + 60 * 60_000 }
        val mlp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            m.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            x = if (lp.x + bw / 2 < screenW / 2) lp.x + bw + ctx.dp(4) else lp.x - m.measuredWidth - ctx.dp(4)
            y = lp.y
        }
        m.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) closeMenu(); false }
        wm.addView(m, mlp); menu = m
    }

    private fun closeMenu() { menu?.let { wm.removeView(it) }; menu = null }

    /** heat: 0 tenang, 1 setengah jatah, 2 hampir habis, 3 lewat. */
    /** reel = lagi di TikTok/Shorts/IG. Maskot minggir dari tombol app itu. */
    fun show(mood: Mood, count: Int, mascot: Mascot, heat: Int, reel: Boolean) {
        val prev = lastCount; lastCount = count
        view.mood = mood; view.count = count; view.heat = heat
        if (reel != inReel) { inReel = reel; if (attached && lp.y !in yRange(isLeft())) snap() else if (!attached) lp.y = lp.y.coerceIn(yRange(isLeft())) }
        if (!attached) {
            val greet = mascot != view.mascot
            view.mascot = mascot; wm.addView(view, lp); attached = true; wake(); view.invalidate()
            if (greet) h.postDelayed({ if (attached) speak("${mascot.voice} ${line()}") }, 400)
            return
        }
        if (mascot != view.mascot) { puff(mascot); return }
        if (prev in 0 until count) {
            wake()
            if (count / 10 > prev / 10) view.flashUntil = System.currentTimeMillis() + 1600
            if (heat == 2) shake() else hop(if (heat == 3) 22 else 8)
        }
        view.invalidate()
    }

    /** Ganti maskot pas pindah app: mengecil jadi titik, maskot baru muncul sambil nyapa. */
    private fun puff(next: Mascot) {
        if (p.reduceMotion) { view.mascot = next; view.invalidate(); return }
        view.animate().scaleX(0f).scaleY(0f).setDuration(160).setInterpolator(STEPS5).withEndAction {
            view.mascot = next; wake()
            view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(3f)).withEndAction {
                speak("${next.voice} ${line()}")
            }.start()
        }.start()
    }

    fun hide() {
        anim?.cancel(); view.walking = false; h.removeCallbacks(patrol); h.removeCallbacks(rise)
        if (attached) { wm.removeView(view); attached = false }
        unsay.run(); hideTrash(); closeMenu()
    }

    fun remove() { hide(); h.removeCallbacksAndMessages(null) }
}

/** Tombol yang harus ditahan 2 detik. Friksi kecil sebelum lanjut scroll. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HoldButton(ctx: Context, label: String, private val fg: Int, private val bar: Int, private val ms: Long, private val done: () -> Unit) : TextView(ctx) {
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint().apply { color = bar; alpha = 130 }

    init {
        text = label; gravity = Gravity.CENTER; textSize = 18f; setTextColor(fg)
        typeface = ResourcesCompat.getFont(ctx, R.font.jersey); setPadding(0, ctx.dp(10), 0, ctx.dp(10))
        background = box(Color.TRANSPARENT, fg, ctx.dp(2))
        contentDescription = "$label. Tahan dua detik"
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (progress < 1f) reset()
            }
            true
        }
        setOnClickListener { done() }
    }

    private fun start() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            addUpdateListener { progress = it.animatedValue as Float; invalidate(); if (progress >= 1f) { performHapticFeedback(HapticFeedbackConstants.CONFIRM); done() } }
            start()
        }
    }

    private fun reset() { anim?.cancel(); progress = 0f; invalidate() }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width * progress, height.toFloat(), paint)
        super.onDraw(c)
    }
}

class PauseCard(
    private val ctx: Context, p: Prefs, night: Boolean, title: String, sub: String, allowContinue: Boolean, fgPkg: String?,
    onStop: () -> Unit, onContinue: () -> Unit, private val onClose: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val root = FrameLayout(ctx)
    private val breathe: Runnable

    init {
        val card = if (night) 0xFF1D1823.toInt() else 0xFFFFF8EC.toInt()
        val fg = if (night) 0xFFF3E9DC.toInt() else 0xFF2B2118.toInt()
        val red = if (night) 0xFFFF6B55.toInt() else 0xFFE0503A.toInt()
        val mus = if (night) 0xFFFFC458.toInt() else 0xFFF2B544.toInt()
        val pix = ResourcesCompat.getFont(ctx, R.font.jersey)
        val body = ResourcesCompat.getFont(ctx, R.font.rubik)
        root.setBackgroundColor(0xB80A060C.toInt())
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.dp(18), ctx.dp(18), ctx.dp(18), ctx.dp(18))
            background = box(card, if (night) 0xFF07060A.toInt() else 0xFF2B2118.toInt(), ctx.dp(3))
            elevation = ctx.dp(8).toFloat()
        }
        val sprite = SpriteView(ctx, p, ctx.dp(5)).apply { mood = Mood.TELER; mascot = p.mascotFor(App.of(fgPkg)) }
        col.addView(sprite)
        fun text(s: String, size: Float, face: android.graphics.Typeface?, color: Int = fg) = TextView(ctx).apply {
            text = s; textSize = size; typeface = face; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        }
        col.addView(text(title, 25f, pix))
        col.addView(text(sub, 13f, body, if (night) 0xFFA89A8C.toInt() else 0xFF7D6B5B.toInt()))
        val breath = text("Tarik napas...", 18f, pix)
        col.addView(breath)
        val full = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(10) }
        col.addView(TextView(ctx).apply {
            text = if (allowContinue) "Udahan" else "Oke"; gravity = Gravity.CENTER; textSize = 18f; typeface = pix; setTextColor(0xFFFFF6E6.toInt())
            setPadding(0, ctx.dp(10), 0, ctx.dp(10)); background = box(red, if (night) 0xFF07060A.toInt() else 0xFF2B2118.toInt(), ctx.dp(2))
            setOnClickListener { onStop(); dismiss() }
        }, full)
        if (allowContinue) col.addView(HoldButton(ctx, "Tahan buat lanjut", fg, mus, 2000) { onContinue(); dismiss() }, full)
        root.addView(col, FrameLayout.LayoutParams(ctx.dp(300), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        var inhale = true
        breathe = object : Runnable {
            override fun run() {
                breath.text = if (inhale) "Tarik napas..." else "Hembuskan..."
                if (!p.reduceMotion) sprite.animate().scaleX(if (inhale) 1.08f else .88f).scaleY(if (inhale) 1.08f else .88f).setDuration(2500).setInterpolator { (it * 10).toInt() / 10f }.start()
                inhale = !inhale; root.postDelayed(this, 2500)
            }
        }
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        wm.addView(root, lp)
        breathe.run()
    }

    fun dismiss() {
        if (!root.isAttachedToWindow) return
        root.removeCallbacks(breathe)
        wm.removeView(root)
        onClose()
    }
}

/**
 * Kartu kecil Jaga Api di atas layar: hitung mundur sebelum mulai, lalu progres per teman.
 * Cuma kartunya yang nangkep sentuhan; sisa layar tetap bisa dipakai.
 */
class FlameCard(private val ctx: Context, night: Boolean, private val onClose: () -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val fg = if (night) 0xFFF3E9DC.toInt() else 0xFF2B2118.toInt()
    private val card = if (night) 0xFF1D1823.toInt() else 0xFFFFF8EC.toInt()
    private val mus = if (night) 0xFFFFC458.toInt() else 0xFFF2B544.toInt()
    private val red = if (night) 0xFFFF6B55.toInt() else 0xFFE0503A.toInt()
    private val pix = ResourcesCompat.getFont(ctx, R.font.jersey)
    private val title = TextView(ctx).apply { textSize = 19f; typeface = pix; setTextColor(fg) }
    private val sub = TextView(ctx).apply { textSize = 13f; typeface = ResourcesCompat.getFont(ctx, R.font.rubik); setTextColor(fg) }
    private val bar = FrameLayout(ctx).apply { background = box(0x00000000, fg, ctx.dp(2)) }
    private val fill = View(ctx).apply { setBackgroundColor(mus) }
    private val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        background = box(card, if (night) 0xFF07060A.toInt() else 0xFF2B2118.toInt(), ctx.dp(3)); elevation = ctx.dp(8).toFloat()
        addView(title); addView(sub)
        addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(12)).apply { topMargin = ctx.dp(8) })
        addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(10) })
    }
    private var tick: Runnable? = null

    init {
        bar.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = overlayParams((ctx.resources.displayMetrics.widthPixels * .92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = ctx.dp(40)
        }
        wm.addView(root, lp)
    }

    private fun button(label: String, primary: Boolean, act: () -> Unit) = TextView(ctx).apply {
        text = label; gravity = Gravity.CENTER; textSize = 16f; typeface = pix
        setTextColor(if (primary) 0xFFFFF6E6.toInt() else fg); setPadding(0, ctx.dp(8), 0, ctx.dp(8))
        background = box(if (primary) red else 0x00000000, fg, ctx.dp(2))
        setOnClickListener { act() }
    }

    private fun setButtons(vararg b: Pair<String, () -> Unit>) {
        buttons.removeAllViews()
        b.forEachIndexed { i, (label, act) ->
            buttons.addView(button(label, i == b.lastIndex, act), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = ctx.dp(8) })
        }
    }

    private fun setFill(f: Float) {
        bar.post { fill.layoutParams = fill.layoutParams.apply { width = ((bar.width - ctx.dp(4)) * f.coerceIn(0f, 1f)).toInt() }; (fill.layoutParams as FrameLayout.LayoutParams).setMargins(ctx.dp(2), ctx.dp(2), 0, ctx.dp(2)); fill.requestLayout() }
    }

    /** "Jaga api N teman, mulai 5 detik lagi" + Tunda 1 jam / Mulai. Kalau dibiarin, mulai sendiri. */
    fun countdown(n: Int, onSnooze: () -> Unit, onStart: () -> Unit) {
        var left = 5
        title.text = "🔥 Jaga api $n teman"
        setButtons("Tunda 1 jam" to { stopTick(); onSnooze() }, "Mulai" to { stopTick(); onStart() })
        tick = object : Runnable {
            override fun run() {
                if (left <= 0) { stopTick(); onStart(); return }
                sub.text = "Mulai $left detik lagi. Layar dipinjam sebentar, nanti balik sendiri."
                setFill((5 - left) / 5f); left--
                root.postDelayed(this, 1_000)
            }
        }.also { it.run() }
    }

    private fun stopTick() { tick?.let(root::removeCallbacks); tick = null }

    /** Progres: i dari n, lagi ngirim ke [name]. */
    fun progress(i: Int, n: Int, name: String, onCancel: () -> Unit) {
        title.text = "🔥 Jaga api · ${minOf(i + 1, n)}/$n"
        sub.text = if (i >= n) "Beres, balik ke tadi..." else "Lagi ke $name. Jangan sentuh layar dulu ya."
        setFill(i / n.toFloat().coerceAtLeast(1f))
        if (buttons.childCount != 1) setButtons("Batal" to onCancel)
    }

    fun dismiss() {
        stopTick()
        if (root.isAttachedToWindow) wm.removeView(root)
        onClose()
    }
}
