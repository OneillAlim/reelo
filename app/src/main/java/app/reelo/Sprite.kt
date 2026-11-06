package app.reelo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.floor
import kotlin.math.roundToInt

enum class Mascot(val label: String, val kind: String) {
    REELO("Reelo", "Gulungan film"), POPI("Popi", "Ember popcorn"), TIKO("Tiko", "Tiket bioskop"), TELO("Telo", "TV tabung")
}

enum class Acc(val label: String, val price: Int, val needStage: Int = 0) {
    BARET("Baret", 40), KACAMATA("Kacamata 3D", 60), SYAL("Syal", 80, needStage = 1), MAHKOTA("Mahkota", 120)
}

/** Generator pixel 24x24, port langsung dari prototype. Warna disimpan sebagai char, lalu dipetakan ke PAL. */
object Sprite {
    const val N = 24
    val PAL = mapOf(
        'k' to 0xFF2B2118.toInt(), 'r' to 0xFFE8553D.toInt(), 'R' to 0xFFB53B2A.toInt(), 'y' to 0xFFF2B544.toInt(),
        'Y' to 0xFFCC8E22.toInt(), 'w' to 0xFFFFF6E6.toInt(), 'c' to 0xFFE6D3BA.toInt(), 't' to 0xFF2FA39A.toInt(),
        'T' to 0xFF1E7A72.toInt(), 'p' to 0xFFF59AA0.toInt(), 'g' to 0xFF9A8B7C.toInt(), 'b' to 0xFF5B3F2C.toInt(),
        's' to 0xFFCFEFEA.toInt(),
    )

    private class G {
        val px = Array(N) { CharArray(N) }
        fun set(x: Int, y: Int, c: Char) { if (x in 0 until N && y in 0 until N) px[y][x] = c }
        fun disc(cx: Double, cy: Double, r: Double, c: Char) {
            for (y in 0 until N) for (x in 0 until N) if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) px[y][x] = c
        }
        fun box(x0: Int, y0: Int, x1: Int, y1: Int, c: Char) { for (y in y0..y1) for (x in x0..x1) set(x, y, c) }
        fun outline(): G {
            val o = G()
            for (y in 0 until N) for (x in 0 until N) {
                o.px[y][x] = px[y][x]
                if (px[y][x] == '\u0000' && listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) ->
                        val nx = x + dx; val ny = y + dy
                        nx in 0 until N && ny in 0 until N && px[ny][nx] != '\u0000'
                    }) o.px[y][x] = 'k'
            }
            return o
        }
    }

    private class Body(val g: G, val fx: Int, val fy: Int, val cx: Int, val top: Int)

    private fun build(m: Mascot): Body = when (m) {
        Mascot.REELO -> {
            val g = G(); g.disc(12.0, 13.0, 8.0, 'r')
            listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1).forEach { (a, b) -> g.disc(12 + a * 4.3, 13 + b * 4.3, 1.5, 'b') }
            g.disc(12.0, 13.0, 4.2, 'w'); g.box(16, 19, 22, 21, 'b')
            for (x in 17..22 step 2) { g.set(x, 19, 'c'); g.set(x, 21, 'c') }
            Body(g.outline(), 9, 11, 12, 5)
        }
        Mascot.POPI -> {
            val g = G()
            listOf(Triple(7.0, 9.0, 2.6), Triple(11.0, 6.5, 3.2), Triple(15.5, 7.5, 2.8), Triple(18.0, 10.0, 2.0), Triple(5.5, 10.5, 1.8), Triple(12.5, 9.0, 2.5))
                .forEach { (x, y, r) -> g.disc(x, y, r, 'w') }
            listOf(10 to 6, 15 to 7, 7 to 9, 13 to 9, 17 to 10, 12 to 5).forEach { (x, y) -> g.set(x, y, 'y') }
            for (y in 10..21) {
                val t = (y - 10) / 11.0
                val x0 = (4 + t * 2).roundToInt(); val x1 = (19 - t * 2).roundToInt()
                for (x in x0..x1) g.set(x, y, if (floor(x / 2.0).toInt() % 2 == 1) 'w' else 'r')
            }
            g.box(7, 12, 16, 19, 'c'); g.box(8, 13, 15, 18, 'w')
            Body(g.outline(), 9, 14, 12, 4)
        }
        Mascot.TIKO -> {
            val g = G(); g.box(3, 7, 20, 18, 'y')
            g.disc(3.0, 12.5, 2.2, '\u0000'); g.disc(20.0, 12.5, 2.2, '\u0000')
            for (y in 8..17 step 2) g.set(16, y, 'Y')
            listOf(18 to 10, 17 to 11, 18 to 11, 19 to 11, 18 to 12).forEach { (x, y) -> g.set(x, y, 'r') }
            val o = g.outline(); o.box(7, 20, 7, 21, 'k'); o.box(12, 20, 12, 21, 'k'); o.set(8, 21, 'k'); o.set(13, 21, 'k')
            Body(o, 7, 10, 10, 7)
        }
        Mascot.TELO -> {
            val g = G(); g.box(4, 8, 19, 19, 't'); g.box(6, 10, 15, 17, 's')
            g.box(17, 10, 18, 11, 'y'); g.box(17, 13, 18, 14, 'r'); g.box(17, 16, 18, 16, 'T')
            val o = g.outline()
            listOf(9 to 7, 8 to 6, 8 to 5, 7 to 4, 14 to 7, 15 to 6, 15 to 5, 16 to 4).forEach { (x, y) -> o.set(x, y, 'k') }
            o.set(7, 3, 'r'); o.set(16, 3, 'r'); o.box(6, 21, 7, 21, 'k'); o.box(16, 21, 17, 21, 'k')
            Body(o, 8, 11, 12, 8)
        }
    }

    private val OPEN = listOf(0 to 0, 0 to 1, 5 to 0, 5 to 1)
    private val HALF = listOf(0 to 1, 1 to 1, 4 to 1, 5 to 1)

    private fun face(b: Body, mood: Mood, blink: Boolean) {
        fun draw(l: List<Pair<Int, Int>>, c: Char = 'k') = l.forEach { (x, y) -> b.g.set(b.fx + x, b.fy + y, c) }
        val eyesOpen = mood == Mood.SEGAR || mood == Mood.SANTAI
        when (mood) {
            Mood.SEGAR -> {
                b.g.set(b.fx - 1, b.fy + 2, 'p'); b.g.set(b.fx + 6, b.fy + 2, 'p')
                draw(listOf(1 to 3, 2 to 3, 3 to 3, 4 to 3)); draw(listOf(2 to 4, 3 to 4), 'R')
            }
            Mood.SANTAI -> draw(listOf(1 to 3, 2 to 4, 3 to 4, 4 to 3))
            Mood.CAPEK -> { draw(HALF); draw(listOf(2 to 4, 3 to 4)); draw(listOf(7 to -1, 7 to 0, 6 to 0), 't') }
            Mood.TELER -> { draw(HALF); draw(listOf(1 to 4, 2 to 3, 3 to 4, 4 to 3)); draw(listOf(0 to 2, 1 to 2, 4 to 2, 5 to 2), 'g') }
            Mood.PINGSAN -> {
                draw(listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1, 4 to -1, 6 to -1, 5 to 0, 4 to 1, 6 to 1))
                draw(listOf(2 to 3, 3 to 3, 2 to 4, 3 to 4)); b.g.set(b.fx + 3, b.fy + 5, 'p')
            }
        }
        if (eyesOpen) draw(if (blink) HALF else OPEN)
    }

    private fun acc(b: Body, a: Acc) {
        val g = b.g; val cx = b.cx; val top = b.top; val x = b.fx; val y = b.fy
        when (a) {
            Acc.BARET -> {
                for (i in cx - 5..cx + 4) g.set(i, top - 1, 'R')
                for (i in cx - 4..cx + 3) g.set(i, top - 2, 'r')
                for (i in cx - 2..cx + 1) g.set(i, top - 3, 'r')
                g.set(cx, top - 4, 'k')
            }
            Acc.KACAMATA -> {
                for (i in x - 2..x + 7) g.set(i, y - 1, 'k')
                g.box(x - 1, y, x + 1, y + 1, 'r'); g.box(x + 4, y, x + 6, y + 1, 't')
                g.set(x + 2, y, 'k'); g.set(x + 3, y, 'k'); g.set(x - 2, y, 'k'); g.set(x + 7, y, 'k')
            }
            Acc.SYAL -> {
                for (i in x - 2..x + 7) g.set(i, y + 6, if (i % 2 == 0) 'r' else 'w')
                g.set(x + 6, y + 7, 'r'); g.set(x + 6, y + 8, 'w'); g.set(x + 7, y + 7, 'w')
            }
            Acc.MAHKOTA -> {
                g.box(cx - 4, top - 2, cx + 3, top - 1, 'y')
                listOf(cx - 4, cx - 1, cx, cx + 3).forEach { g.set(it, top - 3, 'y'); g.set(it, top - 4, 'y') }
                g.set(cx - 1, top - 2, 'r'); g.set(cx, top - 2, 'r')
            }
        }
    }

    private val cache = HashMap<String, Array<CharArray>>()

    fun grid(m: Mascot, mood: Mood, blink: Boolean = false, a: Acc? = null): Array<CharArray> =
        cache.getOrPut("$m|$mood|$blink|$a") {
            val b = build(m); face(b, mood, blink); if (a != null) acc(b, a); b.g.px
        }

    fun bitmap(m: Mascot, mood: Mood, scale: Int, a: Acc? = null): Bitmap {
        val bmp = Bitmap.createBitmap((N + 2) * scale, (N + 2) * scale, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), grid(m, mood, false, a), scale.toFloat(), 0f, 0f)
        return bmp
    }

    private val paint = Paint()

    fun draw(c: Canvas, g: Array<CharArray>, s: Float, ox: Float, oy: Float) {
        for (y in 0 until N) for (x in 0 until N) {
            val p = g[y][x]; if (p == '\u0000') continue
            paint.color = PAL.getValue(p)
            c.drawRect(ox + (x + 1) * s, oy + (y + 1) * s, ox + (x + 2) * s, oy + (y + 2) * s, paint)
        }
    }
}
