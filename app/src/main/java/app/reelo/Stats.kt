package app.reelo

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class App(val label: String, val pkgs: Set<String>) {
    TIKTOK("TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    SHORTS("Shorts", setOf("com.google.android.youtube")),
    IG("IG Reels", setOf("com.instagram.android"));

    companion object {
        fun of(pkg: CharSequence?): App? = entries.firstOrNull { pkg?.toString() in it.pkgs }
    }
}

data class Swipe(val app: App, val ts: Long)

data class Session(val app: App, val start: Long, val end: Long, val count: Int) {
    val minutes get() = ((end - start) / 60_000).toInt()
    val binge get() = end - start >= 15 * 60_000
}

enum class Mood(val label: String) { SEGAR("Segar"), SANTAI("Santai"), CAPEK("Capek"), TELER("Teler"), PINGSAN("Pingsan") }

const val DAY_START_HOUR = 4
const val METERS_PER_REEL = 0.15
const val MONAS_M = 132.0

object Days {
    var zone: ZoneId = ZoneId.systemDefault()
    fun of(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).minusHours(DAY_START_HOUR.toLong()).toLocalDate()
    fun start(d: LocalDate): Long = d.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    fun end(d: LocalDate): Long = start(d.plusDays(1))
    fun today(now: Long = System.currentTimeMillis()) = of(now)
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun hourOf(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).hour
}

object Stats {
    fun mood(count: Int, limit: Int): Mood {
        val r = count.toDouble() / limit.coerceAtLeast(1)
        return when {
            r < 1.0 / 3 -> Mood.SEGAR
            r < 2.0 / 3 -> Mood.SANTAI
            r < 1.0 -> Mood.CAPEK
            r < 1.5 -> Mood.TELER
            else -> Mood.PINGSAN
        }
    }

    fun sessions(swipes: List<Swipe>, gapMs: Long = 120_000): List<Session> {
        val out = mutableListOf<Session>()
        swipes.groupBy { it.app }.forEach { (app, list) ->
            var start = -1L
            var last = -1L
            var n = 0
            for (s in list.sortedBy { it.ts }) {
                if (start >= 0 && s.ts - last > gapMs) {
                    out += Session(app, start, last, n); start = -1
                }
                if (start < 0) { start = s.ts; n = 0 }
                last = s.ts; n++
            }
            if (start >= 0) out += Session(app, start, last, n)
        }
        return out.sortedByDescending { it.start }
    }

    fun secondsPerReel(swipes: List<Swipe>, gapMs: Long = 120_000): Double? {
        val gaps = swipes.groupBy { it.app }.values.flatMap { l ->
            l.sortedBy { it.ts }.zipWithNext { a, b -> b.ts - a.ts }.filter { it <= gapMs }
        }
        return if (gaps.isEmpty()) null else gaps.average() / 1000
    }

    fun meters(count: Int) = count * METERS_PER_REEL

    fun formatMeters(m: Double) = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000).replace('.', ',')

    fun formatMinutes(ms: Long): String {
        val min = (ms / 60_000).toInt()
        return if (min < 60) "${min}m" else "${min / 60}j ${"%02d".format(min % 60)}m"
    }

    fun heat(swipes: List<Swipe>, days: List<LocalDate>): Array<IntArray> {
        val idx = days.withIndex().associate { it.value to it.index }
        val grid = Array(days.size) { IntArray(24) }
        swipes.forEach { s -> idx[Days.of(s.ts)]?.let { grid[it][Days.hourOf(s.ts)]++ } }
        return grid
    }

    /** Hari waras berturut-turut sampai kemarin. Satu hari jebol per minggu (Senin-Minggu) ditambal Freeze otomatis. */
    fun streak(counts: Map<LocalDate, Int>, today: LocalDate, firstDay: LocalDate, limit: Int): Pair<Int, Int> {
        val usedFreeze = mutableSetOf<LocalDate>()
        var n = 0
        var d = today.minusDays(1)
        while (!d.isBefore(firstDay)) {
            val over = (counts[d] ?: 0) > limit
            if (over) {
                val wk = Days.weekStart(d)
                if (wk in usedFreeze) break
                usedFreeze += wk
            } else n++
            d = d.minusDays(1)
        }
        val freezeLeft = if (Days.weekStart(today) in usedFreeze) 0 else 1
        return n to freezeLeft
    }

    fun stage(best: Int) = when { best >= 30 -> 2; best >= 14 -> 1; else -> 0 }
}

/**
 * Ngitung reel baru, bukan jumlah gerakan. Event scroll dari pager/list reels dijumlah, lalu pas berhenti
 * dibagi tinggi halaman jadi langkah (+1 maju, -1 mundur, 0 kalau geser lalu balik).
 *
 * Kalau service bisa baca isi reel (username, caption), sidik jarinya (fp) yang nentuin: reel yang pernah
 * kelihatan hari ini nggak dihitung lagi, mau balik lewat scroll atau setelah buka notif. Kalau fp null,
 * pakai posisi terjauh yang pernah dicapai.
 */
class SwipeCounter {
    private class S { var idx = 0; var best = 0; var px = 0; var page = 0 }
    private val st = HashMap<App, S>()
    private val seen = HashMap<App, LinkedHashSet<Int>>()
    private val last = HashMap<App, Int?>()

    fun scroll(app: App, dy: Int, page: Int) {
        val s = st.getOrPut(app) { S() }
        s.px += dy; if (page > 0) s.page = page
    }

    private fun see(app: App, fp: Int?): Boolean {
        if (fp == null) return false
        val set = seen.getOrPut(app) { LinkedHashSet() }
        if (!set.add(fp)) return false
        if (set.size > 500) set.remove(set.first())
        return true
    }

    fun settle(app: App, fp: Int? = null): Int {
        val s = st[app] ?: return 0
        if (s.page <= 0) return 0
        val f = s.px.toFloat() / s.page
        val n = Math.round(f)
        if (kotlin.math.abs(f - n) > 0.25f) return 0 // jari masih nahan di tengah geser
        s.idx += n
        s.px = 0
        val fresh = (s.idx - s.best).coerceAtLeast(0)
        s.best = maxOf(s.best, s.idx)
        // pindah halaman tapi fp sama: yang kebaca cuma tombol, bukan isi reel
        val prev = last.put(app, fp)
        if (fp == null || n != 0 && fp == prev) return fresh
        val new = see(app, fp)
        return if (n > 0 && new) fresh.coerceIn(1, n) else 0
    }

    fun reset(app: App) { st.remove(app) }

    fun newDay() = seen.clear()
}
