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
    var count: Int? = null
    var sleeping = false
    private val t0 = (Math.random() * 3600).toLong()
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(ctx, R.font.pixelify); textSize = ctx.dp(12).toFloat(); color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val fill = Paint()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px * (Sprite.N + 2) + context.dp(10), px * (Sprite.N + 2) + context.dp(10))

    override fun onDraw(c: Canvas) {
        val reduce = p.reduceMotion
        val t = System.currentTimeMillis() + t0
        val per = when (mood) { Mood.SEGAR -> 380; Mood.SANTAI -> 520; Mood.CAPEK -> 800; Mood.TELER -> 650; Mood.PINGSAN -> 0 }
        val ph = if (reduce || per == 0 || sleeping) 0 else ((t / per) % 2).toInt()
        val dx = if (mood == Mood.TELER && !reduce && !sleeping) (if (ph == 1) 1 else -1) else 0
        val dy = if (mood != Mood.TELER && ph == 1) -1 else 0
        val blink = sleeping || (!reduce && t % 3400 < 150)
        val s = px.toFloat()
        Sprite.draw(c, Sprite.grid(p.mascot, mood, blink, p.acc), s, dx * s, context.dp(10) + dy * s)
        count?.let { n ->
            val label = n.toString()
            val w = maxOf(context.dp(22).toFloat(), badge.measureText(label) + context.dp(10))
            val r = width.toFloat(); val bh = context.dp(18).toFloat()
            fill.color = 0xFF2B2118.toInt(); c.drawRect(r - w - 2, 0f, r, bh + 2, fill)
            fill.color = 0xFFE8553D.toInt(); c.drawRect(r - w, 2f, r - 2, bh, fill)
            c.drawText(label, r - w / 2 - 1, bh - context.dp(4), badge)
        }
        if (sleeping) {
            badge.textAlign = Paint.Align.LEFT
            val k = ((t / 400) % 4).toInt()
            c.drawText("z z", context.dp(2).toFloat(), context.dp(12) - k * 2f, badge.apply { alpha = 255 - k * 50 })
            badge.alpha = 255; badge.textAlign = Paint.Align.CENTER
        }
        if (!reduce) postInvalidateDelayed(90)
    }
}

class FloatingMascot(private val ctx: Context, private val p: Prefs, private val line: () -> String, private val onTrash: () -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
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
        text = "x"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 22f
        typeface = ResourcesCompat.getFont(ctx, R.font.pixelify); background = box(0x66000000, Color.WHITE, ctx.dp(3))
    }
    private val trashLp = overlayParams(ctx.dp(64), ctx.dp(64), touchable = false)
    private var trashShown = false
    private var menu: LinearLayout? = null

    private val idle = Runnable { view.sleeping = true; view.invalidate() }
    private val unsay = Runnable { if (sayShown) { wm.removeView(say); sayShown = false } }

    init {
        lp.x = if (p.floatX < 0) screenW - ctx.dp(80) else p.floatX
        lp.y = p.floatY
        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (peek != 0) { peek = 0; snap(); speak("Aku balik!") } else { hop(); speak(if (view.sleeping) "Hoaam... aku ketiduran." else line()) }
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
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; dragging = false; wake(); closeMenu() }
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

    private fun snap() {
        val x = if (lp.x + bw / 2 < screenW / 2) ctx.dp(6) else screenW - bw - ctx.dp(6)
        animateTo(x, lp.y.coerceIn(ctx.dp(40), screenH - bw - ctx.dp(90)))
        p.floatX = x; p.floatY = lp.y
    }

    private fun animateTo(x: Int, y: Int) {
        val fx = lp.x; val fy = lp.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (p.reduceMotion) 0 else 250; interpolator = STEPS5
            addUpdateListener { if (attached) { val f = it.animatedValue as Float; lp.x = (fx + (x - fx) * f).toInt(); lp.y = (fy + (y - fy) * f).toInt(); wm.updateViewLayout(view, lp) } }
        }.start()
    }

    private fun hop() {
        if (p.reduceMotion) return
        view.animate().translationY(-ctx.dp(14).toFloat()).setDuration(180).setInterpolator(STEPS5).withEndAction {
            view.animate().translationY(0f).setDuration(220).setInterpolator(STEPS5).start()
        }.start()
    }

    private fun wake() {
        view.sleeping = false; view.invalidate()
        view.removeCallbacks(idle); view.postDelayed(idle, 10_000)
    }

    fun speak(text: String) {
        say.text = text
        say.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val left = lp.x + bw / 2 < screenW / 2
        sayLp.x = if (left) lp.x + bw + ctx.dp(4) else lp.x - say.measuredWidth - ctx.dp(4)
        sayLp.y = lp.y + ctx.dp(14)
        if (sayShown) wm.updateViewLayout(say, sayLp) else { wm.addView(say, sayLp); sayShown = true }
        view.removeCallbacks(unsay); view.postDelayed(unsay, 2600)
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

    fun show(mood: Mood, count: Int) {
        view.mood = mood; view.count = count
        if (!attached) { wm.addView(view, lp); attached = true; wake() }
        view.invalidate()
    }

    fun hide() {
        if (attached) { wm.removeView(view); attached = false }
        unsay.run(); hideTrash(); closeMenu()
    }

    fun remove() = hide()
}

/** Tombol yang harus ditahan 2 detik. Friksi kecil sebelum lanjut scroll. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HoldButton(ctx: Context, label: String, private val fg: Int, private val bar: Int, private val ms: Long, private val done: () -> Unit) : TextView(ctx) {
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint().apply { color = bar; alpha = 130 }

    init {
        text = label; gravity = Gravity.CENTER; textSize = 15f; setTextColor(fg)
        typeface = ResourcesCompat.getFont(ctx, R.font.pixelify); setPadding(0, ctx.dp(10), 0, ctx.dp(10))
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
    private val ctx: Context, p: Prefs, night: Boolean, title: String, sub: String, allowContinue: Boolean,
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
        val pix = ResourcesCompat.getFont(ctx, R.font.pixelify)
        val body = ResourcesCompat.getFont(ctx, R.font.rubik)
        root.setBackgroundColor(0xB80A060C.toInt())
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.dp(18), ctx.dp(18), ctx.dp(18), ctx.dp(18))
            background = box(card, if (night) 0xFF07060A.toInt() else 0xFF2B2118.toInt(), ctx.dp(3))
            elevation = ctx.dp(8).toFloat()
        }
        val sprite = SpriteView(ctx, p, ctx.dp(5)).apply { mood = Mood.TELER }
        col.addView(sprite)
        fun text(s: String, size: Float, face: android.graphics.Typeface?, color: Int = fg) = TextView(ctx).apply {
            text = s; textSize = size; typeface = face; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        }
        col.addView(text(title, 21f, pix))
        col.addView(text(sub, 13f, body, if (night) 0xFFA89A8C.toInt() else 0xFF7D6B5B.toInt()))
        val breath = text("Tarik napas...", 15f, pix)
        col.addView(breath)
        val full = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(10) }
        col.addView(TextView(ctx).apply {
            text = if (allowContinue) "Udahan" else "Oke"; gravity = Gravity.CENTER; textSize = 15f; typeface = pix; setTextColor(0xFFFFF6E6.toInt())
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
