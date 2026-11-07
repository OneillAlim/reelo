package app.reelo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import app.reelo.Bob
import app.reelo.Challenge
import app.reelo.Cream
import app.reelo.LocalReduce
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import app.reelo.ReelService
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

private val POKES = listOf("Hehe, geli.", "Aku nggak ke mana-mana kok.", "Geser rolnya buat lihat kemarin.", "Colek lagi, aku lompat lagi.", "Tahan aku lama-lama, aku joget.")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(onApp: (App) -> Unit, onRecap: () -> Unit, onApi: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val reduce = LocalReduce.current
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
    val heat = Stats.heat(n, limit)
    var line by remember(v, di) { mutableIntStateOf(-1) }
    var cheer by remember { mutableStateOf<String?>(null) }
    val jump = remember { Animatable(0f) }
    val flash = remember { Animatable(1f) }
    val chase = remember { Animatable(-1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var flip by remember { mutableStateOf(false) }
    var spin by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }

    fun go(d: Int) {
        val nd = (di + d).coerceIn(0, maxBack)
        if (nd == di) return
        di = nd
        if (p.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { flash.snapTo(.55f); flash.animateTo(1f, tween(300, easing = steps(3))) }
    }

    // Tiap nembus kelipatan 10 hari ini: lampu lari keliling sekali + maskot teriak. Dicatat biar nggak diulang.
    LaunchedEffect(n, loaded != null, di) {
        if (chase.value >= 0f) chase.snapTo(-1f) // kalau animasi sebelumnya kepotong
        if (loaded == null || di != 0 || n < 10) return@LaunchedEffect
        val k = n / 10
        val seen = p.milestone.split('|').let { if (it.size == 2 && it[0] == today.toString()) it[1].toIntOrNull() ?: 0 else 0 }
        if (k <= seen) return@LaunchedEffect
        p.milestone = "$today|$k"
        cheer = if (n > limit) "Tembus ${k * 10}. Lampunya udah merah, lho." else "Yeay, nembus ${k * 10} reels! Sisa ${limit - n} lagi."
        if (p.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        chase.snapTo(0f); chase.animateTo(1f, tween(1800, easing = steps(48))); chase.snapTo(-1f)
    }

    fun dance() {
        if (p.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        line = POKES.size - 1
        scope.launch {
            repeat(if (reduce) 0 else 6) { i ->
                flip = !flip
                jump.animateTo(if (i % 2 == 0) -10f else 0f, tween(140, easing = steps(2)))
            }
            jump.snapTo(0f); flip = false
        }
    }

    val say = when {
        cheer != null && di == 0 && line < 0 -> cheer!!
        line >= 0 -> POKES[line % POKES.size]
        loaded == null -> "Bentar, aku hitung dulu..."
        di == 0 && n == 0 -> "Belum ada reels hari ini. Rol masih kosong."
        di == 0 && p.inNight(java.time.LocalTime.now().hour) -> "Udah lewat jam ${"%02d".format(p.nightStart)}.00. Reels-nya lanjut besok ya."
        di == 0 && n < limit / 3 -> "Baru $n reels. Mantap!"
        di == 0 && n < limit -> "Udah $n reels. Jempolnya pegel nggak?"
        di == 0 -> "Ini reels ke-$n. Napas dulu yuk."
        n > limit -> "${dayName(date, today)} tembus $n. Hari ini pasti bisa lebih santai."
        else -> "${dayName(date, today)}: $n reels, masih di bawah jatah."
    }
    val t = ticker()

    Box(Modifier.fillMaxSize()) {
        Screen {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Lbl(today.format(DATE_LONG)); Px(greeting(), 24.sp) }
                Pill("$streak hari waras", pal.teal)
            }
            if (!ReelService.isOn(ctx)) Card(onClick = { open(ctx, android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS) }) {
                Txt("Penghitung lagi mati", 14.sp, pal.red, bold = true)
                Small(if (ReelService.enabled(ctx)) STUCK else "Reels yang kamu tonton sekarang nggak kehitung. Ketuk buat nyalain.")
            }
            val empty = loaded != null && di == 0 && n == 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.offset { IntOffset(0, jump.value.dp.roundToPx()) }
                        .graphicsLayer { scaleX = if (flip) -1f else 1f }
                        .combinedClickable(role = Role.Button, onClickLabel = "Colek maskot", onLongClickLabel = "Joget", onLongClick = ::dance) {
                            line = if (line < 0) 0 else (line + 1) % (POKES.size - 1)
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
                holes = { marquee(pal, if (limit <= 0) 1f else n.toFloat() / limit, heat, t, chase.value) },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Px("<", 20.sp, if (di < maxBack) pal.filmFg else pal.filmFg.copy(alpha = .25f), Modifier.clickable(enabled = di < maxBack, onClickLabel = "Hari sebelumnya") { go(1) }.padding(end = 8.dp))
                    Lbl(dayName(date, today), pal.filmFg)
                    Box(Modifier.weight(1f))
                    Txt("jatah $limit", 11.sp, pal.filmFg)
                    Px(">", 20.sp, if (di > 0) pal.filmFg else pal.filmFg.copy(alpha = .25f), Modifier.clickable(enabled = di > 0, onClickLabel = "Hari berikutnya") { go(-1) }.padding(start = 8.dp))
                }
                val numCol = when {
                    chase.value >= 0f && (t / 200) % 2 == 0L -> Cream
                    heat == 3 && (t / 900) % 2 == 0L -> pal.red
                    else -> pal.mus
                }
                Box(Modifier.clickable(onClickLabel = "Putar ulang angka") { spin++ }) {
                    Odometer(if (spin > 0 && spinning(spin)) 0 else n, 64.sp, numCol, glow = pal.night || heat >= 2) { i -> if (di == 0 && n > 0) Bob.lift(t, heat, i) else 0f }
                }
                Frames(n, limit)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Txt(if (n > limit) "Lewat ${n - limit} dari jatah" else "Sisa ${limit - n} reels", 11.sp, pal.filmFg, modifier = Modifier.weight(1f))
                    if (maxBack > 0) Txt("geser rol", 11.sp, pal.filmFg.copy(alpha = .6f))
                }
            }
            Lbl("Tiket hari ${if (di == 0) "ini" else "itu"}")
            App.entries.forEach { app ->
                val tint = when (app) { App.TIKTOK -> pal.fg; App.SHORTS -> pal.red; App.IG -> pal.teal }
                Ticket(app.label, data.count(app), p.limit(app), p.mascotFor(app), tint) { onApp(app) }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Score("jam", "Screen time", Stats.formatMinutes(data.screenMs), Modifier.weight(1f))
                    Box(Modifier.width(2.dp).height(36.dp).background(pal.line))
                    Score("jempol", "Jempol meter", Stats.formatMeters(Stats.meters(n)), Modifier.weight(1f).padding(start = 12.dp))
                }
            }
            if (di == 0) ChallengeCard(date, data, limit) { confetti++ }
            if (di == 0) ApiCard(onApi)
            if (maxBack >= 1) Card(onClick = onRecap) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Txt("Recap 7 hari", bold = true); Small("Rangkuman yang bisa kamu bagikan") }
                    Px(">", 20.sp)
                }
            }
            if (empty && !first.isEqual(today)) Small("Kemarin kamu berhenti di ${load(v, initial = 0) { Db.get(ctx).count(Days.start(today.minusDays(1)), Days.start(today)) }}. Mau coba lebih sedikit?", Modifier.fillMaxWidth(), pal.muted)
        }
        Confetti(confetti)
    }
}

/** true selama 120 ms pertama setelah [spin] berubah, biar Odometer muter dari 0 lagi. */
@Composable
private fun spinning(spin: Int): Boolean {
    var on by remember(spin) { mutableStateOf(true) }
    LaunchedEffect(spin) { delay(120); on = false }
    return on
}

/** Kartu Jaga Api di Beranda: status singkat, ketuk buat buka. */
@Composable
private fun ApiCard(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val v = Live.v
    val (on, total) = load(v, initial = 0 to 0) { Db.get(ctx).friends().filter { it.streak > 0 }.let { l -> l.count { it.on } to l.size } }
    val today = app.reelo.Flame.day(System.currentTimeMillis()).toString()
    val sentToday = load(v, initial = 0) { Db.get(ctx).sentOk(app.reelo.Flame.day(System.currentTimeMillis())).size }
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlameIcon(22.dp)
            Column(Modifier.weight(1f)) {
                Lbl("Jaga Api TikTok")
                Txt(when {
                    total == 0 -> "Nyalain streak otomatis tiap 00.00"
                    !p.flameOn -> "Mati · $on teman dicentang"
                    p.flameLastRun == today -> "Hari ini $sentToday api aman"
                    else -> "$on teman · jalan jam 00.00"
                }, 14.sp, bold = true)
            }
            Px(">", 20.sp)
        }
    }
}

@Composable
private fun Score(glyph: String, label: String, value: String, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelIcon(GLYPHS.getValue(glyph), pal.musInk, 20.dp)
        Column { Lbl(label); Big(value, 24.sp) }
    }
}

@Composable
private fun ChallengeCard(d: java.time.LocalDate, data: DayData, limit: Int, onWin: () -> Unit) {
    val pal = LocalPal.current
    val c = Challenge.of(d)
    val p = prefs()
    val st = c.check(d, data.swipes, limit, System.currentTimeMillis())
    LaunchedEffect(st) { if (st == Challenge.State.OK && p.cheered != d.toString()) { p.cheered = d.toString(); onWin() } }
    val (tag, col) = when (st) { Challenge.State.OK -> "BERES" to pal.teal; Challenge.State.FAIL -> "GAGAL" to pal.red; Challenge.State.ON -> "JALAN" to pal.musInk }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelIcon(GLYPHS.getValue("piala"), col, 22.dp)
            Column(Modifier.weight(1f)) {
                Lbl("Tantangan hari ini")
                Txt(c.title, 14.sp, bold = true)
                Small(when (st) {
                    Challenge.State.OK -> "Lolos! +${Challenge.ROL} Rol Film masuk besok jam 04.00."
                    Challenge.State.FAIL -> "Besok ada tantangan baru. ${c.hint}."
                    Challenge.State.ON -> c.hint
                })
            }
            Pill(tag, col)
        }
    }
}
