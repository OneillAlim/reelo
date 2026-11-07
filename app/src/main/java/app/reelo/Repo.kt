package app.reelo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Versi data. Service dan activity menaikkan angka ini, UI baca ulang saat berubah. */
object Live {
    var v by mutableIntStateOf(0)
    fun bump() { v++ }
}

data class DayData(val date: LocalDate, val swipes: List<Swipe>, val usage: Map<App, AppUsage>) {
    val total get() = swipes.size
    fun count(app: App) = swipes.count { it.app == app }
    val screenMs get() = usage.values.sumOf { it.ms }
}

object Repo {
    fun day(ctx: Context, d: LocalDate): DayData {
        val from = Days.start(d); val to = Days.end(d)
        return DayData(d, Db.get(ctx).range(from, to), Usage.read(ctx, from, to))
    }

    fun firstDay(ctx: Context): LocalDate {
        val p = Prefs.get(ctx)
        if (p.firstDay.isEmpty()) p.firstDay = Days.today().toString()
        return LocalDate.parse(p.firstDay)
    }

    fun streak(ctx: Context): Pair<Int, Int> {
        val p = Prefs.get(ctx); val today = Days.today(); val first = firstDay(ctx)
        return Stats.streak(totals(ctx, first, today), today, first, p.totalLimit)
    }

    /** Total per hari. Hari "buta" (penghitung mati) dianggap lewat jatah, biar nggak jadi streak gratis. */
    private fun totals(ctx: Context, first: LocalDate, today: LocalDate): Map<LocalDate, Int> {
        val p = Prefs.get(ctx)
        return Db.get(ctx).dailyTotals(first, today) + p.blind.associateWith { p.totalLimit + 1 }
    }

    /**
     * Hari dengan 0 reels padahal TikTok/IG lama kebuka = penghitungnya mati (HyperOS suka begitu), bukan libur.
     * ponytail: tebakan dari screen time; kalau perlu pasti, catat jam hidup service per hari.
     */
    private fun blind(ctx: Context, d: LocalDate): Boolean {
        val u = Usage.read(ctx, Days.start(d), Days.end(d))
        return (u[App.TIKTOK]?.ms ?: 0) >= 5 * 60_000 || (u[App.IG]?.ms ?: 0) >= 15 * 60_000
    }

    /** Dipanggil tiap app dibuka: kasih Rol Film buat hari yang lolos, update streak terbaik, cek lencana. */
    fun refreshGame(ctx: Context) {
        val p = Prefs.get(ctx); val db = Db.get(ctx)
        val today = Days.today(); val first = firstDay(ctx)
        val totals = db.dailyTotals(first, today)
        var d = if (p.rewardedUntil.isEmpty()) first else LocalDate.parse(p.rewardedUntil).plusDays(1)
        while (d.isBefore(today)) {
            val n = totals[d] ?: 0
            if (n == 0 && blind(ctx, d)) { p.markBlind(d); p.rewardedUntil = d.toString(); d = d.plusDays(1); continue }
            if (n <= p.totalLimit) p.rol += 20
            if (Challenge.of(d).check(d, db.range(Days.start(d), Days.end(d)), p.totalLimit, System.currentTimeMillis()) == Challenge.State.OK) p.rol += Challenge.ROL
            if (d.isAfter(first)) {
                if (n == 0) p.grant(Badge.JEMPOL_LIBUR)
                if (n <= p.totalLimit / 2) p.grant(Badge.SETENGAH)
                val morning = db.count(Days.start(d), d.atTime(9, 0).atZone(Days.zone).toInstant().toEpochMilli())
                if (morning == 0) p.grant(Badge.PAGI_BERSIH)
                if (p.nightOn) {
                    val ns = d.atTime(p.nightStart, 0).atZone(Days.zone).toInstant().toEpochMilli()
                    val ne = d.plusDays(if (p.nightStart > p.nightEnd) 1 else 0).atTime(p.nightEnd, 0).atZone(Days.zone).toInstant().toEpochMilli()
                    if (ne < System.currentTimeMillis() && db.count(ns, ne) == 0) p.grant(Badge.MALAM_NOL)
                }
            }
            p.rewardedUntil = d.toString()
            d = d.plusDays(1)
        }
        if (p.focusUntil in 1..System.currentTimeMillis()) { p.grant(Badge.FOKUS); p.focusUntil = 0 }
        val (s, _) = Stats.streak(totals(ctx, first, today), today, first, p.totalLimit)
        if (s > p.bestStreak) p.bestStreak = s
        if (p.bestStreak >= 7) p.grant(Badge.SEMINGGU)
        if (p.bestStreak >= 14) p.grant(Badge.DUA_MINGGU)
        if (p.bestStreak >= 30) p.grant(Badge.SEBULAN)
        val wk = Days.weekStart(today)
        val last = (1..7L).sumOf { totals[wk.minusDays(it)] ?: 0 }
        val prev = (8..14L).sumOf { totals[wk.minusDays(it)] ?: 0 }
        if (!wk.minusDays(14).isBefore(first) && prev > 0 && last < prev * 0.8) p.grant(Badge.JEMPOL_HEMAT)
    }
}
