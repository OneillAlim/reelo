package app.reelo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.reelo.LocalReduce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelo.App
import app.reelo.DayData
import app.reelo.Days
import app.reelo.Db
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.MONAS_M
import app.reelo.Repo
import app.reelo.Stats
import app.reelo.Usage
import java.time.Instant
import java.time.format.TextStyle

@Composable
fun Detail(app: App, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val v = Live.v
    val data = load(v, initial = DayData(Days.today(), emptyList(), emptyMap())) { Repo.day(ctx, Days.today()) }
    val sw = data.swipes.filter { it.app == app }
    val sessions = Stats.sessions(sw)
    val spr = Stats.secondsPerReel(sw)
    val u = data.usage[app]
    val p = prefs()
    val m = p.mascotFor(app)
    val limit = p.limit(app)
    Screen {
        Top(app.label, onBack) { Lbl("Hari ini") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MascotView(Stats.mood(sw.size, limit), 3.dp, mascot = m)
            Bubble(
                "${m.voice} " + when {
                    sw.isEmpty() -> "Belum ada ${app.label} hari ini. Aku jagain."
                    sw.size > limit -> "Udah lewat ${sw.size - limit} dari jatah. Udahan yuk."
                    else -> "Jatah ${app.label} sisa ${limit - sw.size}."
                },
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f)) { Lbl("Reels"); Big("${sw.size}") }
            Card(Modifier.weight(1f)) { Lbl("Menit"); Big("${(u?.ms ?: 0) / 60_000}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f)) { Lbl("Dibuka"); Big("${u?.opens ?: 0}x") }
            Card(Modifier.weight(1f)) { Lbl("Sesi terlama"); Big("${sessions.maxOfOrNull { it.minutes } ?: 0}m") }
        }
        if (app == App.SHORTS) Small("Menit dan dibuka dihitung dari seluruh YouTube, karena Android nggak memisahkan Shorts.")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Lbl("Kecepatan swipe") }
                if (spr != null) Pill(if (spr < 3) "Mode Ngebut" else "Nonton", if (spr < 3) pal.red else pal.teal)
            }
            Big(if (spr == null) "-" else "%.1f".format(ID, spr) + " dtk/reel")
            Small("Di bawah 3 detik artinya kamu lagi nyari yang seru, bukan nonton.")
        }
        Lbl("Sesi hari ini")
        if (sessions.isEmpty()) Small("Belum ada sesi.")
        sessions.take(12).forEach { s ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("${hm(s.start)} – ${hm(s.end)}", bold = true)
                        Small("${s.count} reels · ${s.minutes} menit")
                    }
                    Pill(if (s.binge) "Maraton" else "Santai", if (s.binge) pal.red else pal.teal)
                }
            }
        }
    }
}

fun hm(ts: Long): String = Instant.ofEpochMilli(ts).atZone(Days.zone).format(HM)

@Composable
fun Insight() {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val p = prefs()
    val v = Live.v
    val today = Days.today()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val from = Days.start(days.first())
    val to = Days.end(today)
    val (sw, usage) = load(v, initial = emptyList<app.reelo.Swipe>() to emptyMap<App, app.reelo.AppUsage>()) { Db.get(ctx).range(from, to) to Usage.read(ctx, from, to) }
    val totals = days.map { d -> sw.count { Days.of(it.ts) == d } }
    val heat = Stats.heat(sw, days)
    val limit = p.totalLimit
    val week = sw.size
    val weekMs = usage.values.sumOf { it.ms }
    var pick by remember { mutableIntStateOf(6) }
    val grow by androidx.compose.animation.core.animateFloatAsState(if (sw.isEmpty()) 0f else 1f, androidx.compose.animation.core.tween(if (LocalReduce.current) 0 else 600, easing = steps(6)), label = "grow")

    Screen {
        Top("Insight") { Pill("7 hari terakhir", pal.fg) }
        Card {
            Row { Box(Modifier.weight(1f)) { Lbl("Reels per hari") }; Small("garis = jatah $limit") }
            val max = maxOf(limit * 1.2f, (totals.maxOrNull() ?: 0).toFloat(), 1f)
            Box(Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp)) {
                Canvas(Modifier.fillMaxWidth().height(90.dp).pointerInput(Unit) { detectTapGestures { o -> pick = (o.x / size.width * 7).toInt().coerceIn(0, 6) } }) {
                    val w = size.width / 7
                    totals.forEachIndexed { i, t ->
                        val h = t / max * size.height * grow
                        val c = if (t > limit) pal.red else pal.teal
                        drawRect(if (i == pick) c else c.copy(alpha = .55f), Offset(i * w + w * .15f, size.height - h), Size(w * .7f, h))
                        if (i == pick) drawRect(pal.fg, Offset(i * w + w * .15f, size.height - h - 4.dp.toPx()), Size(w * .7f, 2.dp.toPx()))
                    }
                    val ly = size.height - limit / max * size.height
                    drawLine(pal.red, Offset(0f, ly), Offset(size.width, ly), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    drawLine(pal.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                }
                Row(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
                    days.forEachIndexed { i, d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Small(d.dayOfWeek.getDisplayName(TextStyle.SHORT, ID) + " " + totals[i], Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            val d = days[pick]
            val top = App.entries.maxByOrNull { a -> sw.count { it.app == a && Days.of(it.ts) == d } }
            val topN = sw.count { it.app == top && Days.of(it.ts) == d }
            Txt(
                "${dayName(d, today)}: ${totals[pick]} reels" + if (topN > 0) " · paling banyak ${top!!.label} ($topN)" else "",
                12.sp, bold = true, modifier = Modifier.padding(top = 4.dp),
            )
            Small("Ketuk batang buat lihat hari lain.")
        }
        Card {
            Lbl("Jam rawan")
            val mx = heat.maxOf { r -> r.maxOrNull() ?: 0 }.coerceAtLeast(1)
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                heat.forEachIndexed { r, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Small(days[r].dayOfWeek.getDisplayName(TextStyle.SHORT, ID).take(3), Modifier.width(30.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            row.forEach { c -> Box(Modifier.weight(1f).aspectRatio(1f).background(lerp(pal.line, pal.red, c.toFloat() / mx))) }
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 30.dp)) { listOf("00", "06", "12", "18").forEach { Small(it, Modifier.weight(1f)) } }
            val peak = heat.withIndex().flatMap { (r, row) -> row.withIndex().map { Triple(r, it.index, it.value) } }.maxByOrNull { it.third }
            if (peak != null && peak.third > 0) Small("Paling sering jam ${"%02d".format(peak.second)}.00, terutama ${days[peak.first].dayOfWeek.getDisplayName(TextStyle.FULL, ID)}.")
            else Small("Belum ada data minggu ini.")
        }
        val trig = usage.entries.filter { it.value.opens >= 3 }.maxByOrNull { it.value.afterUnlock.toFloat() / it.value.opens }
        Card {
            Lbl("Pemicu unlock")
            if (trig == null) Small("Butuh beberapa kali buka app dulu buat lihat polanya.")
            else Txt("${trig.value.afterUnlock * 100 / trig.value.opens}% kali buka ${trig.key.label} terjadi kurang dari 1 menit setelah HP dibuka.")
        }
        Card {
            Lbl("Jempol meter")
            val m = Stats.meters(week)
            Big(Stats.formatMeters(m))
            Small(if (m >= MONAS_M) "Sama dengan ${"%.1f".format(ID, m / MONAS_M)}x tinggi Monas. Satu reel = satu layar yang digeser, sekitar 15 cm." else "${(m / MONAS_M * 100).toInt()}% tinggi Monas (132 m). Satu reel = satu layar, sekitar 15 cm.")
        }
        Card {
            Lbl("Setara dengan")
            val unit = weekMs / 60_000 / p.swap.minutes
            Txt("${Stats.formatMinutes(weekMs)} minggu ini = $unit ${p.swap.unit}.", align = TextAlign.Start)
            Small("Ganti target di Pengaturan.")
        }
    }
}
