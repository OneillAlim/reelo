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

/** Misi kecil harian. Dipilih dari tanggal, jadi semua orang dapat misi yang sama di hari yang sama. */
enum class Challenge(val title: String, val hint: String) {
    PAGI("Pagi tanpa reels", "Nggak scroll sebelum jam 10.00"),
    SETENGAH("Setengah jatah", "Total hari ini maksimal separuh jatah"),
    MALAM("Malam bersih", "Nol reels setelah jam 21.00"),
    SATU_APP("Satu app aja", "Cuma buka reels di satu app"),
    JEDA("Tanpa maraton", "Nggak ada sesi yang lebih dari 15 menit");

    enum class State { ON, OK, FAIL }

    /** OK = pasti lolos, ON = masih jalan, FAIL = gagal. [now] dipakai buat tahu harinya udah lewat jam batas atau belum. */
    fun check(d: LocalDate, sw: List<Swipe>, limit: Int, now: Long): State {
        val over = now >= Days.end(d)
        fun at(h: Int) = d.atTime(h, 0).atZone(Days.zone).toInstant().toEpochMilli()
        val fail = when (this) {
            PAGI -> sw.any { it.ts < at(10) }
            SETENGAH -> sw.size * 2 > limit
            MALAM -> sw.any { it.ts >= at(21) }
            SATU_APP -> sw.map { it.app }.distinct().size > 1
            JEDA -> Stats.sessions(sw).any { it.binge }
        }
        return when {
            fail -> State.FAIL
            over || this == PAGI && now >= at(10) -> State.OK
            else -> State.ON
        }
    }

    companion object { const val ROL = 10; fun of(d: LocalDate) = entries[Math.floorMod(d.toEpochDay(), entries.size.toLong()).toInt()] }
}

/**
 * Angka yang naik-turun pelan pas masih dikit, makin kenceng makin dekat jatah. Bentuknya patah 4 langkah biar kerasa pixel.
 * Hasilnya dp ke atas (negatif = naik). i = urutan digit, biar tiap digit gelombangnya geser dikit kayak ombak.
 */
object Bob {
    val PERIOD = longArrayOf(1600, 1100, 700, 450)
    val AMP = floatArrayOf(2f, 3f, 4f, 5f)
    private val WAVE = floatArrayOf(0f, .5f, 1f, .5f)
    fun lift(t: Long, heat: Int, i: Int): Float {
        val h = heat.coerceIn(0, 3); val per = PERIOD[h]
        val ph = ((t % per) * 4 / per + i) % 4
        return -AMP[h] * WAVE[ph.toInt()]
    }
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

    /** Seberapa "panas" hitungan: 0 di bawah 50%, 1 sampai 80%, 2 sampai jatah, 3 lewat jatah. Dipakai buat lampu dan kedip. */
    fun heat(n: Int, limit: Int) = when { limit <= 0 -> if (n > 0) 3 else 0; n > limit -> 3; n * 5 >= limit * 4 -> 2; n * 2 >= limit -> 1; else -> 0 }

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

    /** Simpan/pulihin sidik jari (angka hash, bukan teks) biar restart service nggak bikin reel lama kehitung lagi. */
    fun dump(): String = seen.entries.joinToString(";") { (a, s) -> "${a.name}:${s.joinToString(",")}" }

    fun load(s: String) {
        s.split(';').forEach { part ->
            val app = App.entries.firstOrNull { it.name == part.substringBefore(':') } ?: return@forEach
            seen.getOrPut(app) { LinkedHashSet() } += part.substringAfter(':').split(',').mapNotNull { it.toIntOrNull() }
        }
    }
}
