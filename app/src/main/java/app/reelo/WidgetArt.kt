package app.reelo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Gaya latar widget. Ketuk tombol kecil di pojok widget buat ganti. */
enum class WStyle(val label: String) { LANGIT("Langit"), BIOSKOP("Bioskop"), TIKET("Tiket") }

/**
 * Gambar widget digambar sendiri pakai kotak-kotak pixel (satu "pixel seni" = [u] px), biar nyambung sama maskot.
 * Latar digambar sekali per update; yang gerak (maskot, bohlam, bintang) dibikin beberapa frame kecil lalu
 * diputar ViewFlipper, karena widget nggak bisa jalanin animasi sendiri.
 */
object WidgetArt {
    private val p = Paint()

    /**
     * Teks pakai Jersey 15, digambar jadi bitmap. Launcher (mis. HyperOS) nggak selalu mau pakai font custom di
     * TextView widget, jadi angka besar dan label pixel dibikin gambar biar pasti pixel.
     */
    fun label(ctx: android.content.Context, text: String, sizeSp: Float, color: Int, glow: Boolean = false): Bitmap {
        val dm = ctx.resources.displayMetrics
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.jersey)
            textSize = sizeSp * dm.scaledDensity; this.color = color
            if (glow) setShadowLayer(textSize / 5, 0f, 0f, color and 0x99FFFFFF.toInt()) else setShadowLayer(0.01f, 0f, 2 * dm.density, 0x66000000)
        }
        val lines = text.split('\n')
        val fm = tp.fontMetrics
        val lh = fm.descent - fm.ascent
        val pad = (if (glow) tp.textSize / 4 else 2 * dm.density)
        val w = (lines.maxOf { tp.measureText(it) } + pad * 2).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, (lh * lines.size + pad * 2).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        lines.forEachIndexed { i, l -> c.drawText(l, pad, pad - fm.ascent + i * lh, tp) }
        return bmp
    }

    private fun night(hour: Int) = hour >= 19 || hour < 5

    /** Warna langit atas/bawah sesuai jam: pagi, siang, senja, malam. */
    private fun sky(hour: Int): Pair<Int, Int> = when (hour) {
        in 5..9 -> 0xFF8FD3F0.toInt() to 0xFFFFE6B3.toInt()
        in 10..14 -> 0xFF5AB0E8.toInt() to 0xFFC4E8FF.toInt()
        in 15..18 -> 0xFFE9765A.toInt() to 0xFFFFD27A.toInt()
        else -> 0xFF1B1640.toInt() to 0xFF43357A.toInt()
    }

    /** Warna teks yang kebaca di atas latar ini. */
    fun ink(style: WStyle, hour: Int) = when (style) {
        WStyle.LANGIT -> if (night(hour)) 0xFFFFF6E6.toInt() else 0xFF2B2118.toInt()
        WStyle.BIOSKOP -> 0xFFFFF6E6.toInt()
        WStyle.TIKET -> 0xFF2B2118.toInt()
    }

    fun accent(style: WStyle, hour: Int, heat: Int) = when {
        heat >= 3 -> 0xFFFF5A47.toInt()
        style == WStyle.TIKET -> 0xFFE0503A.toInt()
        style == WStyle.LANGIT && !night(hour) -> 0xFFB53B2A.toInt()
        else -> 0xFFFFC458.toInt()
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    private fun Canvas.px(x: Int, y: Int, w: Int, h: Int, u: Float, c: Int) { p.color = c; drawRect(x * u, y * u, (x + w) * u, (y + h) * u, p) }

    /** Latar penuh ukuran widget, sudut membulat [r] px. panel = kotak kertas di kanan buat teks (widget lebar). */
    fun scene(style: WStyle, w: Int, h: Int, u: Float, r: Float, hour: Int, panel: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
        val gw = (w / u).toInt() + 1; val gh = (h / u).toInt() + 1
        when (style) {
            WStyle.LANGIT -> {
                val (top, bot) = sky(hour)
                val bands = 7
                for (i in 0 until bands) c.px(0, gh * i / bands, gw, gh / bands + 1, u, mix(top, bot, i / (bands - 1f)))
                if (night(hour)) {
                    c.px(gw - 9, 2, 4, 4, u, 0xFFFFF3C4.toInt()); c.px(gw - 8, 1, 2, 1, u, 0xFFFFF3C4.toInt()); c.px(gw - 8, 6, 2, 1, u, 0xFFFFF3C4.toInt())
                    c.px(gw - 7, 2, 3, 3, u, top) // bulan sabit
                } else {
                    val sun = if (hour in 15..18) 0xFFFFB347.toInt() else 0xFFFFD84D.toInt()
                    c.px(gw - 9, 2, 4, 4, u, sun); c.px(gw - 8, 1, 2, 6, u, sun); c.px(gw - 10, 3, 6, 2, u, sun)
                    listOf(3 to 4, gw / 2 - 4 to 2).forEach { (x, y) -> c.px(x, y + 1, 7, 2, u, 0xFFFFFFFF.toInt()); c.px(x + 2, y, 3, 1, u, 0xFFFFFFFF.toInt()) }
                }
                // bukit dua lapis
                val far = if (night(hour)) 0xFF2A2150.toInt() else 0xFF7FBF6A.toInt()
                val near = if (night(hour)) 0xFF150F2C.toInt() else 0xFF4E9A4A.toInt()
                for (x in 0 until gw) {
                    val a = (gh * .78f + 2 * kotlin.math.sin(x / 5.0)).toInt(); c.px(x, a, 1, gh - a, u, far)
                    val b = (gh * .86f + 1.5 * kotlin.math.sin(x / 3.0 + 1)).toInt(); c.px(x, b, 1, gh - b, u, near)
                }
                // gedung bioskop kecil di bukit, jendelanya nyala kalau malam
                val bx = 3; val by = (gh * .70f).toInt(); val wall = if (night(hour)) 0xFF3B2C55.toInt() else 0xFFE8D2B0.toInt()
                c.px(bx, by, 10, gh - by, u, wall)
                c.px(bx - 1, by - 2, 12, 2, u, 0xFFB5302A.toInt())
                for (i in 0 until 5) c.px(bx + i * 2, by - 3, 1, 1, u, if (night(hour)) 0xFFFFD34D.toInt() else 0xFFB5302A.toInt())
                val win = if (night(hour)) 0xFFFFD34D.toInt() else 0xFF6B5A4A.toInt()
                c.px(bx + 2, by + 2, 2, 2, u, win); c.px(bx + 6, by + 2, 2, 2, u, win); c.px(bx + 4, by + 5, 2, gh - by - 5, u, 0xFF2B2118.toInt())
            }
            WStyle.BIOSKOP -> {
                c.px(0, 0, gw, gh, u, 0xFF240D12.toInt())
                c.px(gw / 6, 3, gw - gw / 3, gh - 6, u, 0xFF33161C.toInt()) // layar remang
                val cw = (gw / 7).coerceAtLeast(3)
                for (x in 0 until cw) {
                    val col = if (x % 2 == 0) 0xFFB5302A.toInt() else 0xFF8E211D.toInt()
                    c.px(x, 0, 1, gh, u, col); c.px(gw - 1 - x, 0, 1, gh, u, col)
                }
                c.px(0, 0, gw, 3, u, 0xFFB5302A.toInt())
                for (x in 0 until gw step 4) c.px(x + 1, 3, 2, 1, u, 0xFF8E211D.toInt()) // rumbai
                c.px(0, gh - 2, gw, 2, u, 0xFF120609.toInt())
            }
            WStyle.TIKET -> {
                c.px(0, 0, gw, gh, u, 0xFFFFF3DC.toInt())
                for (y in 0 until gh) for (x in 0 until gw) if ((x + y) % 6 == 0) c.px(x, y, 1, 1, u, 0xFFF6E6C8.toInt())
                val band = (gw / 6).coerceAtLeast(4)
                c.px(0, 0, band, gh, u, 0xFFE0503A.toInt())
                for (y in 1 until gh step 3) c.px(band + 1, y, 1, 2, u, 0xFFD9C3A0.toInt()) // garis sobek
                for (y in 2 until gh - 2 step 5) c.px(1, y, 2, 2, u, 0xFFFFF3DC.toInt()) // lubang
                c.px(band, 0, 1, gh, u, 0xFFB53B2A.toInt())
            }
        }
        if (panel) {
            val x0 = (gw * .38f).toInt(); val col = if (style == WStyle.TIKET) 0xF2FFFAF0.toInt() else 0xD9FFF8EC.toInt()
            c.px(x0, 3, gw - x0 - 3, gh - 6, u, 0xFF2B2118.toInt())
            c.px(x0 + 1, 4, gw - x0 - 5, gh - 8, u, col)
        }
        return bmp
    }

    /**
     * Lapisan yang kedip, setinggi [h] px dari atas: bohlam di Bioskop, bintang di Langit malam, kilau di Tiket.
     * frame 0/1 gantian. Bohlam ikut panas: kuning, oranye, merah.
     */
    fun fx(style: WStyle, w: Int, h: Int, u: Float, hour: Int, heat: Int, frame: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gw = (w / u).toInt() + 1; val gh = (h / u).toInt() + 1
        when (style) {
            WStyle.BIOSKOP -> {
                val on = when (heat) { 3 -> 0xFFFF4A3A.toInt(); 2 -> 0xFFFF8A3D.toInt(); else -> 0xFFFFD34D.toInt() }
                for ((i, x) in (2 until gw - 2 step 3).withIndex()) c.px(x, 1, 1, 1, u, if ((i + frame) % 2 == 0) on else 0xFF5A2A20.toInt())
            }
            WStyle.LANGIT -> if (night(hour)) {
                val stars = listOf(7 to 3, 12 to 6, 17 to 2, 23 to 5, 30 to 2, 37 to 6, 44 to 3, 52 to 2, 60 to 5, 68 to 2)
                stars.filter { it.first < gw - 11 && it.second < gh }.forEachIndexed { i, (x, y) ->
                    if ((i + frame) % 3 != 0) c.px(x, y, 1, 1, u, 0xFFFFF6E6.toInt())
                    else { c.px(x - 1, y, 3, 1, u, 0xFFFFE9A8.toInt()); c.px(x, y - 1, 1, 3, u, 0xFFFFE9A8.toInt()) }
                }
            } else {
                // burung kecil yang kepak
                listOf(gw / 3, gw / 3 + 5).forEach { x -> if (frame == 0) { c.px(x, 4, 1, 1, u, 0xFF2B2118.toInt()); c.px(x + 2, 4, 1, 1, u, 0xFF2B2118.toInt()); c.px(x + 1, 5, 1, 1, u, 0xFF2B2118.toInt()) } else c.px(x, 5, 3, 1, u, 0xFF2B2118.toInt()) }
            }
            WStyle.TIKET -> {
                val x = if (frame == 0) gw - 6 else gw - 10; val y = if (frame == 0) 2 else 4
                c.px(x, y - 1, 1, 3, u, 0xFFF2B544.toInt()); c.px(x - 1, y, 3, 1, u, 0xFFF2B544.toInt())
            }
        }
        return bmp
    }

    /** Papan marquee bioskop buat widget 4x1: papan gelap, bingkai merah, tempat bohlam di pinggir. */
    fun sign(w: Int, h: Int, u: Float, r: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
        val gw = (w / u).toInt() + 1; val gh = (h / u).toInt() + 1
        c.px(0, 0, gw, gh, u, 0xFFB5302A.toInt())
        c.px(3, 3, gw - 6, gh - 6, u, 0xFF1A1014.toInt())
        for (y in 4 until gh - 4 step 2) c.px(4, y, gw - 8, 1, u, 0xFF21151A.toInt())
        return bmp
    }

    /** Bohlam keliling papan marquee, gantian nyala tiap frame kayak lampu kejar-kejaran. */
    fun signFx(w: Int, h: Int, u: Float, heat: Int, frame: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gw = (w / u).toInt(); val gh = (h / u).toInt()
        val on = when (heat) { 3 -> 0xFFFF4A3A.toInt(); 2 -> 0xFFFF8A3D.toInt(); else -> 0xFFFFD34D.toInt() }
        val off = 0xFF6A2620.toInt()
        val ring = buildList {
            for (x in 2 until gw - 2 step 3) add(x to 1)
            for (y in 4 until gh - 2 step 3) add(gw - 2 to y)
            for (x in gw - 3 downTo 2 step 3) add(x to gh - 2)
            for (y in gh - 4 downTo 3 step 3) add(1 to y)
        }
        ring.forEachIndexed { i, (x, y) -> c.px(x, y, 1, 1, u, if ((i + frame) % 3 == 0) off else on) }
        return bmp
    }

    /** 6 frame maskot: diam, lompat, diam, lompat, diam, kedip. */
    fun mascot(m: Mascot, mood: Mood, acc: Acc?, scale: Int): List<Bitmap> {
        val n = (Sprite.N + 2) * scale
        val plan = listOf(0 to false, -1 to false, 0 to false, -1 to false, 0 to false, 0 to true)
        return plan.map { (dy, blink) ->
            Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).also {
                Sprite.draw(Canvas(it), Sprite.grid(m, mood, blink && mood.ordinal < 2, acc), scale.toFloat(), 0f, (dy + 1) * scale.toFloat())
            }
        }
    }

    /** Makin dekat jatah, maskot makin heboh. */
    fun flipMs(heat: Int) = when (heat) { 0 -> 800; 1 -> 600; 2 -> 420; else -> 300 }
}
