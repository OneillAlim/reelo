package app.reelo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelo.App
import app.reelo.DayData
import app.reelo.Days
import app.reelo.Db
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.Repo
import app.reelo.Stats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

val ID = Locale.forLanguageTag("id")
val DATE_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", ID)
val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm", ID)

fun greeting(h: Int = LocalTime.now().hour) = when (h) { in 4..10 -> "Pagi!"; in 11..14 -> "Siang!"; in 15..17 -> "Sore!"; else -> "Malam!" }

fun dayName(d: LocalDate, today: LocalDate) = when (ChronoUnit.DAYS.between(d, today)) {
    0L -> "Hari ini"; 1L -> "Kemarin"; else -> d.format(DATE_LONG)
}

private val POKES = listOf("Hehe, geli.", "Aku nggak ke mana-mana kok.", "Geser rolnya buat lihat kemarin.", "Colek lagi, aku lompat lagi.")

@Composable
fun Home(onApp: (App) -> Unit, onRecap: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val v = Live.v
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val today = Days.today()
    val first = remember(v) { Repo.firstDay(ctx) }
    val maxBack = ChronoUnit.DAYS.between(first, today).toInt().coerceIn(0, 60)
    var di by remember { mutableIntStateOf(0) }
    val date = today.minusDays(di.toLong())
    val loaded = load<DayData?>(v, di, initial = null) { Repo.day(ctx, date) }
    val data = loaded ?: DayData(date, emptyList(), emptyMap())
    val streak = load(v, initial = 0) { Repo.streak(ctx).first }
    val limit = p.totalLimit
    val n = data.total
    val mood = Stats.mood(n, limit)
    var line by remember(v, di) { mutableIntStateOf(-1) }
    val jump = remember { Animatable(0f) }
    val flash = remember { Animatable(1f) }
    var drag by remember { mutableFloatStateOf(0f) }

    fun go(d: Int) {
        val nd = (di + d).coerceIn(0, maxBack)
        if (nd == di) return
        di = nd
        if (p.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { flash.snapTo(.55f); flash.animateTo(1f, tween(300, easing = steps(3))) }
    }

    val say = when {
        line >= 0 -> POKES[line % POKES.size]
        loaded == null -> "Bentar, aku hitung dulu..."
        di == 0 && n == 0 -> "Belum ada reels hari ini. Rol masih kosong."
        di == 0 && n < limit / 3 -> "Baru $n reels. Mantap!"
        di == 0 && n < limit -> "Udah $n reels. Jempolnya pegel nggak?"
        di == 0 -> "Ini reels ke-$n. Napas dulu yuk."
        n > limit -> "${dayName(date, today)} tembus $n. Hari ini pasti bisa lebih santai."
        else -> "${dayName(date, today)}: $n reels, masih di bawah jatah."
    }

    Screen {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Lbl(today.format(DATE_LONG)); Px(greeting(), 21.sp) }
            Pill("$streak hari waras", pal.teal)
        }
        val empty = loaded != null && di == 0 && n == 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.offset { IntOffset(0, jump.value.dp.roundToPx()) }
                    .clickable(role = Role.Button, onClickLabel = "Colek maskot") {
                        line = if (line < 0) 0 else line + 1
                        scope.launch { jump.animateTo(-14f, tween(180, easing = steps(2))); jump.animateTo(0f, tween(220, easing = steps(2))) }
                    },
            ) { MascotView(mood, if (empty) 5.dp else 4.dp) }
            Bubble(say, Modifier.weight(1f))
        }
        Film(
            Modifier.offset { IntOffset(drag.roundToInt().coerceIn(-50.dp.roundToPx(), 50.dp.roundToPx()), 0) }.alpha(flash.value)
                .pointerInput(maxBack, di) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (drag > 40.dp.toPx()) go(1) else if (drag < -40.dp.toPx()) go(-1); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { _, dx -> drag += dx }
                },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Px("<", 18.sp, if (di < maxBack) pal.filmFg else pal.filmFg.copy(alpha = .25f), Modifier.clickable(enabled = di < maxBack, onClickLabel = "Hari sebelumnya") { go(1) }.padding(end = 8.dp))
                Lbl(dayName(date, today), pal.filmFg)
                Box(Modifier.weight(1f))
                Txt("jatah $limit", 11.sp, pal.filmFg)
                Px(">", 18.sp, if (di > 0) pal.filmFg else pal.filmFg.copy(alpha = .25f), Modifier.clickable(enabled = di > 0, onClickLabel = "Hari berikutnya") { go(-1) }.padding(start = 8.dp))
            }
            Odometer(n, 52.sp, pal.mus, glow = pal.night)
            Frames(n, limit)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Txt(if (n > limit) "Lewat ${n - limit} dari jatah" else "Sisa ${limit - n} reels", 11.sp, pal.filmFg, modifier = Modifier.weight(1f))
                if (maxBack > 0) Txt("geser rol", 11.sp, pal.filmFg.copy(alpha = .6f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            App.entries.forEach { app ->
                val dot = when (app) { App.TIKTOK -> pal.fg; App.SHORTS -> pal.red; App.IG -> pal.teal }
                Card(Modifier.weight(1f), onClick = { onApp(app) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).background(dot)); Small(app.label)
                    }
                    Big("${data.count(app)}")
                    Small("dari ${p.limit(app)}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f)) { Lbl("Screen time"); Big(Stats.formatMinutes(data.screenMs)) }
            Card(Modifier.weight(1f)) { Lbl("Jempol meter"); Big(Stats.formatMeters(Stats.meters(n))) }
        }
        if (maxBack >= 1) Card(onClick = onRecap) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Txt("Recap 7 hari", bold = true); Small("Rangkuman yang bisa kamu bagikan") }
                Px(">", 18.sp)
            }
        }
        if (empty && !first.isEqual(today)) Small("Kemarin kamu berhenti di ${load(v, initial = 0) { Db.get(ctx).count(Days.start(today.minusDays(1)), Days.start(today)) }}. Mau coba lebih sedikit?", Modifier.fillMaxWidth(), pal.muted)
    }
}
