package app.reelo.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelo.Acc
import app.reelo.BodyFont
import app.reelo.Cream
import app.reelo.Glow
import app.reelo.Ink
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.LocalReduce
import app.reelo.Mascot
import app.reelo.Mood
import app.reelo.PixelFont
import app.reelo.PX
import app.reelo.Prefs
import app.reelo.Sprite
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

val LocalMascot = staticCompositionLocalOf { Mascot.REELO }
val LocalAcc = staticCompositionLocalOf<Acc?> { null }

fun steps(n: Int) = Easing { f -> floor(f * n) / n }

/** Baca prefs dan langganan Live.v, jadi layar ikut segar saat data berubah. */
@Composable
fun prefs(): Prefs { Live.v; return Prefs.get(LocalContext.current) }

@Composable
fun Txt(text: String, size: TextUnit = 13.sp, color: Color = LocalPal.current.fg, bold: Boolean = false, modifier: Modifier = Modifier, align: TextAlign? = null) =
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontFamily = BodyFont, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, lineHeight = size * 1.4f, textAlign = align ?: TextAlign.Unspecified))

@Composable
fun Px(text: String, size: TextUnit = 15.sp, color: Color = LocalPal.current.fg, modifier: Modifier = Modifier, align: TextAlign? = null) =
    BasicText(text, modifier, TextStyle(color = color, fontSize = size * PX, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, lineHeight = size * PX * 1.15f, textAlign = align ?: TextAlign.Unspecified))

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) = Txt(text, 11.5.sp, color, modifier = modifier)

@Composable
fun Lbl(text: String, color: Color = LocalPal.current.muted) =
    BasicText(text.uppercase(), style = TextStyle(color = color, fontSize = 10.5.sp * PX, fontFamily = PixelFont, letterSpacing = 1.2.sp))

@Composable
fun Big(text: String, size: TextUnit = 22.sp, color: Color = LocalPal.current.fg) = Px(text, size, color)

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
fun Top(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Px("<", 22.sp, modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Kembali") { onBack() }.padding(end = 10.dp))
        Px(title, 21.sp, modifier = Modifier.weight(1f))
        trailing()
    }

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.fillMaxWidth().background(p.card, shape).border(2.dp, p.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

fun Modifier.perforated(hole: Color, inset: Dp = 6.dp, h: Dp = 6.dp) = drawBehind {
    val w = 7.dp.toPx(); val hh = h.toPx(); val e = inset.toPx()
    var x = e
    while (x + w <= size.width - e) {
        drawRect(hole, Offset(x, e), Size(w, hh)); drawRect(hole, Offset(x, size.height - e - hh), Size(w, hh)); x += w * 2
    }
}

@Composable
fun Film(modifier: Modifier = Modifier, holes: (Modifier.() -> Modifier)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(
        modifier.fillMaxWidth().background(p.film, RoundedCornerShape(4.dp))
            .drawBehind {
                if (p.night) drawRect(Brush.radialGradient(listOf(Color(0x42FFD68C), Color.Transparent), Offset(size.width / 2, -size.height * .25f), size.width * .9f))
            }
            .let { m -> holes?.invoke(m) ?: m.perforated(p.hole) }.padding(horizontal = 14.dp, vertical = 20.dp),
        content = content,
    )
}

@Composable
fun Frames(n: Int, of: Int) {
    val p = LocalPal.current
    val fill = if (of <= 0) 0 else (n.toFloat() / of * 12).roundToInt().coerceIn(0, 12)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(12) { i ->
            val f = i < fill
            Box(Modifier.weight(1f).height(12.dp).then(if (f) Modifier.background(p.red) else Modifier).border(2.dp, if (f) p.red else p.filmFg))
        }
    }
}

fun Modifier.pixelShadow(night: Boolean, ink: Color) = drawBehind {
    val o = 3.dp.toPx()
    if (night) drawRect(Glow, Offset(-o, -o), Size(size.width + o * 2, size.height + o * 2))
    else drawRect(ink, Offset(o, o), size)
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier.fillMaxWidth(), ghost: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier.pixelShadow(p.night, p.fg).background(if (ghost) p.card else p.red).border(2.dp, if (ghost) p.fg else p.film)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Px(text, 15.sp, if (ghost) p.fg else Cream, align = TextAlign.Center) }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) =
    Box(modifier.border(2.dp, color).padding(horizontal = 7.dp, vertical = 2.dp)) {
        BasicText(text, style = TextStyle(color = color, fontSize = 10.sp * PX, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp))
    }

@Composable
fun Toggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(40.dp, 22.dp).background(if (on) p.teal else p.line).border(2.dp, p.fg)
            .clickable(role = Role.Switch, onClickLabel = label) { onChange(!on) }.padding(3.dp),
    ) { Box(Modifier.size(12.dp).offset(x = if (on) 20.dp else 0.dp).background(if (on) Cream else p.fg)) }
}

@Composable
fun SettingRow(title: String, sub: String? = null, trailing: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) { Txt(title, bold = true); if (sub != null) Small(sub) }
        trailing()
    }

@Composable
fun Stepper(value: Int, step: Int = 5, min: Int = 0, max: Int = 300, suffix: String = "", onChange: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.border(2.dp, p.fg), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp, 28.dp).background(p.card).clickable(onClickLabel = "Kurangi") { onChange((value - step).coerceAtLeast(min)) }, contentAlignment = Alignment.Center) { Px("-", 16.sp) }
        Px("$value$suffix", 15.sp, modifier = Modifier.width(44.dp), align = TextAlign.Center)
        Box(Modifier.size(30.dp, 28.dp).background(p.card).clickable(onClickLabel = "Tambah") { onChange((value + step).coerceAtMost(max)) }, contentAlignment = Alignment.Center) { Px("+", 16.sp) }
    }
}

@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.background(p.card).border(2.dp, p.fg).padding(horizontal = 10.dp, vertical = 7.dp)) { Txt(text, 12.5.sp) }
}

/** Maskot pixel: goyang naik turun sesuai mood dan berkedip. px = ukuran satu pixel sprite. */
@Composable
fun MascotView(mood: Mood, px: Dp, modifier: Modifier = Modifier, mascot: Mascot = LocalMascot.current, acc: Acc? = LocalAcc.current, gray: Boolean = false) {
    val p = LocalPal.current
    val reduce = LocalReduce.current
    var t by remember { mutableLongStateOf(0L) }
    val off = remember { (Math.random() * 3600).toLong() }
    if (!reduce) LaunchedEffect(Unit) { while (true) { t = withFrameMillis { it }; delay(90) } }
    val per = when (mood) { Mood.SEGAR -> 380; Mood.SANTAI -> 520; Mood.CAPEK -> 800; Mood.TELER -> 650; Mood.PINGSAN -> 0 }
    val ph = if (reduce || per == 0) 0 else (((t + off) / per) % 2).toInt()
    val dx = if (mood == Mood.TELER && !reduce) (if (ph == 1) 1 else -1) else 0
    val dy = if (mood != Mood.TELER && ph == 1) -1 else 0
    val blink = !reduce && (t + off) % 3400 < 150
    val g = Sprite.grid(mascot, mood, blink, acc)
    Canvas(modifier.size(px * (Sprite.N + 2))) {
        val s = size.width / (Sprite.N + 2)
        if (p.night && !gray) drawCircle(Brush.radialGradient(listOf(Glow, Color.Transparent), center, size.width * .55f), size.width * .55f)
        for (y in 0 until Sprite.N) for (x in 0 until Sprite.N) {
            val c = g[y][x]; if (c == '\u0000') continue
            var col = Color(Sprite.PAL.getValue(c))
            if (gray) { val l = col.red * .3f + col.green * .59f + col.blue * .11f; col = Color(l, l, l, .55f) }
            drawRect(col, Offset((x + 1 + dx) * s, (y + 1 + dy) * s), Size(s + .5f, s + .5f))
        }
    }
}

/** Angka yang berputar per digit kayak penghitung tiket. */
@Composable
fun Odometer(value: Int, base: TextUnit, color: Color, glow: Boolean = false, lift: (Int) -> Float = { 0f }) {
    val size = base * PX
    val h = with(LocalDensity.current) { size.toDp() }
    val style = TextStyle(fontSize = size, lineHeight = size, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, color = color,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        shadow = if (glow) Shadow(Color(0x99FFC458), blurRadius = 28f) else null)
    Row {
        value.toString().forEachIndexed { i, ch ->
            val a by animateFloatAsState((ch - '0').toFloat(), tween(450, easing = steps(6)), label = "odo")
            Box(Modifier.offset { IntOffset(0, lift(i).dp.roundToPx()) }.height(h).clipToBounds()) {
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset { IntOffset(0, (-a * h.toPx()).roundToInt()) }) {
                    for (d in 0..9) BasicText("$d", Modifier.height(h), style)
                }
            }
        }
    }
}

val ICONS = mapOf(
    "Beranda" to listOf("...#...", "..###..", ".#####.", "#######", ".#...#.", ".#.#.#.", ".#####."),
    "Insight" to listOf("......#", "......#", "...#..#", "...#..#", "#..#..#", "#..#..#", "#######"),
    "Kendali" to listOf("..###..", ".#...#.", "#..#..#", "#..##.#", "#.....#", ".#...#.", "..###.."),
    "Lemari" to listOf("##...##", "###.###", "#######", ".#####.", ".#####.", ".#####.", ".#####."),
    "Saya" to listOf("...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", "##...##"),
)

@Composable
fun PixelIcon(rows: List<String>, color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 7
    rows.forEachIndexed { y, r -> r.forEachIndexed { x, c -> if (c == '#') drawRect(color, Offset(x * s, y * s), Size(s, s)) } }
}

@Composable
fun FilmNav(tab: Int, onTab: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().background(p.film).perforated(p.hole, 4.dp, 5.dp).padding(horizontal = 6.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ICONS.keys.forEachIndexed { i, name ->
            val on = i == tab
            val fg = if (on) Ink else Color(0xFFA3968A)
            Column(
                Modifier.weight(1f).then(if (on && p.night) Modifier.pixelShadow(true, p.fg) else Modifier).background(if (on) p.mus else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = name) { onTab(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { PixelIcon(ICONS.getValue(name), fg); Txt(name, 9.5.sp, fg, bold = on) }
        }
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Muat data di background, tampilkan nilai lama sampai yang baru siap (biar angka nggak kedip ke 0). */
@Composable
fun <T> load(vararg keys: Any?, initial: T, block: () -> T): T {
    var value by remember { androidx.compose.runtime.mutableStateOf(initial) }
    LaunchedEffect(*keys) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() } }
    return value
}

/** Jam animasi bersama: nilai ms yang naik tiap ~60 ms selama [on]. Mati total kalau Kurangi animasi nyala. */
@Composable
fun ticker(on: Boolean = true): Long {
    val reduce = LocalReduce.current
    var t by remember { mutableLongStateOf(0L) }
    if (on && !reduce) LaunchedEffect(Unit) { while (true) { t = withFrameMillis { it }; delay(60) } }
    return t
}

private val BULB_MS = longArrayOf(0, 450, 500, 400)

fun bulbColor(p: app.reelo.Pal, heat: Int) = when (heat) { 3 -> p.red; 2 -> Color(0xFFFF8A3D); else -> p.mus }

/**
 * Lubang film jadi lampu bioskop. Kolom ke-i nyala kalau i < lit * jumlah kolom (lit 0..1 = progres ke jatah).
 * Makin panas makin rame: 0 diam, 1 kelip acak, 2 nyala selang-seling, 3 merah selang-seling lebih cepat.
 * Kecepatan kedip maksimal 2,5 kali per detik (aman buat yang sensitif cahaya).
 * chase 0..1 = kepala lampu putih yang lari keliling pas nembus kelipatan 10; di luar itu isi -1.
 */
fun Modifier.marquee(p: app.reelo.Pal, lit: Float, heat: Int, t: Long, chase: Float, inset: Dp = 6.dp, h: Dp = 6.dp) = drawBehind {
    val w = 7.dp.toPx(); val hh = h.toPx(); val e = inset.toPx()
    val cols = ((size.width - 2 * e + w) / (w * 2)).toInt().coerceAtLeast(1)
    val on = (lit.coerceIn(0f, 1f) * cols).roundToInt()
    val base = bulbColor(p, heat)
    val per = BULB_MS[heat.coerceIn(0, 3)]
    val head = if (chase >= 0f) chase * cols * 4 else -99f // dua putaran keliling (atas lalu bawah balik)
    for (row in 0..1) for (i in 0 until cols) {
        val x = e + i * w * 2; val y = if (row == 0) e else size.height - e - hh
        var c = if (i < on) base else p.filmFg.copy(alpha = .16f) // bohlam mati: redup biar yang nyala kerasa nyala
        if (i < on && per > 0) {
            val k = t / per
            val dim = when (heat) { 1 -> (i * 7 + row * 3 + k) % 5 == 0L; else -> (i + row + k) % 2 == 0L }
            if (dim) c = base.copy(alpha = .35f)
        }
        val pos = if (row == 0) i.toFloat() else (2 * cols - 1 - i).toFloat()
        val d = ((head - pos) % (2 * cols) + 2 * cols) % (2 * cols)
        if (head > -99f && d < 3f) c = Cream.copy(alpha = 1f - d / 4f)
        drawRect(c, Offset(x, y), Size(w, hh))
        if (p.night && i < on) drawRect(c.copy(alpha = .25f), Offset(x - 2.dp.toPx(), y - 2.dp.toPx()), Size(w + 4.dp.toPx(), hh + 4.dp.toPx()))
    }
}

/** Hujan kotak pixel 1,4 detik tiap [key] berubah (selain 0). Nggak nangkep sentuhan. */
@Composable
fun Confetti(key: Int, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    if (key == 0 || LocalReduce.current) return
    val f = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { f.animateTo(1f, tween(1400, easing = steps(28))) }
    val bits = remember(key) {
        val r = java.util.Random(key.toLong())
        List(44) { floatArrayOf(r.nextFloat(), r.nextFloat() * .35f, (r.nextFloat() - .5f) * .3f, .6f + r.nextFloat() * .8f, r.nextInt(4).toFloat()) }
    }
    val cols = listOf(p.mus, p.red, p.teal, Cream)
    if (f.value < 1f) Canvas(modifier.fillMaxSize()) {
        val s = 6.dp.toPx(); val v = f.value
        bits.forEach { (x0, y0, vx, g, ci) ->
            val x = (x0 + vx * v) * size.width
            val y = (y0 - .3f + g * v * v * 1.6f) * size.height
            drawRect(cols[ci.toInt()].copy(alpha = 1f - v * .6f), Offset(x, y), Size(s, s))
        }
    }
}

val GLYPHS = mapOf(
    "jam" to listOf("..###..", ".#...#.", "#..#..#", "#..##.#", "#.....#", ".#...#.", "..###.."),
    "jempol" to listOf("...##..", "...##..", "..###..", "######.", "######.", "######.", ".#####."),
    "piala" to listOf("#######", "#.###.#", ".#####.", "..###..", "...#...", "..###..", ".#####."),
)

/** Tiket bioskop per app: sobekan kiri isi maskot app itu, kanan angka dan bar jatah. */
@Composable
fun Ticket(label: String, n: Int, limit: Int, mascot: Mascot, tint: Color, onClick: () -> Unit) {
    val p = LocalPal.current
    val heat = app.reelo.Stats.heat(n, limit)
    val stub = 70.dp
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(p.card).border(2.dp, p.line)
            .clickable(role = Role.Button, onClickLabel = "Detail $label", onClick = onClick)
            .drawWithContent {
                drawContent()
                val x = stub.toPx(); val r = 7.dp.toPx()
                drawCircle(p.bg, r, Offset(x, 0f)); drawCircle(p.bg, r, Offset(x, size.height))
                var y = r * 1.6f
                while (y < size.height - r * 1.6f) { drawRect(p.line, Offset(x - 1.dp.toPx(), y), Size(2.dp.toPx(), 5.dp.toPx())); y += 9.dp.toPx() }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(stub).fillMaxHeight().background(tint.copy(alpha = .14f)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            MascotView(app.reelo.Stats.mood(n, limit), 1.8.dp, mascot = mascot)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Lbl(mascot.label + " jaga"); Txt(label, 14.sp, bold = true) }
                Px("$n", 26.sp, if (heat >= 2) bulbColor(p, heat) else p.fg)
                Px(" /$limit", 14.sp, p.muted, Modifier.padding(bottom = 3.dp))
            }
            val fill = if (limit <= 0) 10 else (n * 10f / limit).roundToInt().coerceIn(0, 10)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(10) { i -> Box(Modifier.weight(1f).height(6.dp).background(if (i < fill) bulbColor(p, heat) else p.line)) }
            }
        }
    }
}
