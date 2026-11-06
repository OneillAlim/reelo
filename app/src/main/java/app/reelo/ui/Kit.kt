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
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, lineHeight = size * 1.15f, textAlign = align ?: TextAlign.Unspecified))

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) = Txt(text, 11.5.sp, color, modifier = modifier)

@Composable
fun Lbl(text: String, color: Color = LocalPal.current.muted) =
    BasicText(text.uppercase(), style = TextStyle(color = color, fontSize = 10.5.sp, fontFamily = PixelFont, letterSpacing = 1.5.sp))

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
fun Film(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(
        modifier.fillMaxWidth().background(p.film, RoundedCornerShape(4.dp))
            .drawBehind {
                if (p.night) drawRect(Brush.radialGradient(listOf(Color(0x42FFD68C), Color.Transparent), Offset(size.width / 2, -size.height * .25f), size.width * .9f))
            }
            .perforated(p.hole).padding(horizontal = 14.dp, vertical = 20.dp),
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
        BasicText(text, style = TextStyle(color = color, fontSize = 10.sp, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp))
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
fun Odometer(value: Int, size: TextUnit, color: Color, glow: Boolean = false) {
    val h = with(LocalDensity.current) { size.toDp() }
    val style = TextStyle(fontSize = size, lineHeight = size, fontFamily = PixelFont, fontWeight = FontWeight.SemiBold, color = color,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        shadow = if (glow) Shadow(Color(0x99FFC458), blurRadius = 28f) else null)
    Row {
        value.toString().forEach { ch ->
            val a by animateFloatAsState((ch - '0').toFloat(), tween(450, easing = steps(6)), label = "odo")
            Box(Modifier.height(h).clipToBounds()) {
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
