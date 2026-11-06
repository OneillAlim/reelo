package app.reelo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.reelo.App
import app.reelo.Badge
import app.reelo.Days
import app.reelo.Db
import app.reelo.Ink
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.Mascot
import app.reelo.Mood
import app.reelo.Prefs
import app.reelo.R
import app.reelo.Repo
import app.reelo.Sprite
import app.reelo.Stats
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Saya(onSettings: () -> Unit, onRecap: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val v = Live.v
    val (streak, freeze) = load(v, initial = 0 to 1) { Repo.streak(ctx) }
    val stage = Stats.stage(p.bestStreak)
    Screen {
        Top("Saya") { Pill("Pengaturan", pal.fg, Modifier.clickable(role = Role.Button, onClick = onSettings)) }
        Film {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Odometer(streak, 40.sp, pal.mus, pal.night)
                    Txt("hari waras berturut-turut", 11.sp, pal.filmFg)
                }
                Pill("$freeze Freeze", pal.filmFg)
            }
            Frames(minOf(streak, 7), 7)
            Txt("Satu hari jebol per minggu otomatis ditambal Freeze. Terbaik: ${p.bestStreak} hari.", 11.sp, pal.filmFg, modifier = Modifier.padding(top = 6.dp))
        }
        Card {
            Lbl("Evolusi ${p.mascot.label}")
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                listOf(Triple("Mini", 0, 2.dp), Triple("Sinema", 14, 3.dp), Triple("Emas", 30, 4.dp)).forEachIndexed { i, (name, need, px) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MascotView(Mood.SEGAR, px, acc = when (i) { 1 -> app.reelo.Acc.BARET; 2 -> app.reelo.Acc.MAHKOTA; else -> p.acc }, gray = stage < i)
                        Txt(name, bold = true)
                        Small(if (stage >= i) if (stage == i) "sekarang" else "lewat" else "${need - p.bestStreak} hari lagi")
                    }
                }
            }
            Small("Evolusi naik dari streak terbaik. Sinema membuka Syal di Lemari.")
        }
        Lbl("Lencana ${p.badges.size} / ${Badge.entries.size}")
        Badge.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { b ->
                    val got = b in p.badges
                    val bg = if (!got) Color(0xFF9A8B7C) else when (b.ordinal % 3) { 0 -> Color(0xFFF2B544); 1 -> Color(0xFF2FA39A); else -> Color(0xFFE8553D) }
                    Column(
                        Modifier.weight(1f).background(pal.card).border(2.dp, pal.line).padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.size(32.dp).background(bg.copy(alpha = if (got) 1f else .4f)), contentAlignment = Alignment.Center) { Px(b.glyph, 13.sp, Ink) }
                        Txt(b.label, 10.5.sp, if (got) pal.fg else pal.muted, bold = got, align = TextAlign.Center)
                        Txt(b.hint, 9.sp, pal.muted, align = TextAlign.Center)
                    }
                }
            }
        }
        Btn("Lihat recap 7 hari", onClick = onRecap)
    }
}

data class RecapData(val from: LocalDate, val to: LocalDate, val total: Int, val prev: Int, val peakHour: Int?, val fav: App?, val favPct: Int, val badges: Int)

fun recapData(ctx: Context): RecapData {
    val to = Days.today().minusDays(1)
    val from = to.minusDays(6)
    val db = Db.get(ctx)
    val sw = db.range(Days.start(from), Days.end(to))
    val prev = db.count(Days.start(from.minusDays(7)), Days.start(from))
    val peak = sw.groupingBy { Days.hourOf(it.ts) }.eachCount().maxByOrNull { it.value }?.key
    val fav = sw.groupingBy { it.app }.eachCount().maxByOrNull { it.value }
    return RecapData(from, to, sw.size, prev, peak, fav?.key, if (sw.isEmpty()) 0 else fav!!.value * 100 / sw.size, Prefs.get(ctx).badges.size)
}

private val RANGE = DateTimeFormatter.ofPattern("d MMM", ID)

private val STORY_BG = Color(0xFF1A1220)
private val STORY_FG = Color(0xFFF6E7C8)

@Composable
fun Recap(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val r = load(Live.v, initial = null) { recapData(ctx) } ?: return
    Column(Modifier.fillMaxSize().background(STORY_BG).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(4) { Box(Modifier.weight(1f).height(3.dp).background(if (it < 2) STORY_FG else STORY_FG.copy(alpha = .3f))) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Px("<", 22.sp, STORY_FG, Modifier.clickable(onClickLabel = "Kembali", onClick = onBack).padding(end = 10.dp))
            Px("Reelo Recap", 15.sp, STORY_FG, Modifier.weight(1f))
            Txt("${r.from.format(RANGE)} – ${r.to.format(RANGE)}", 11.sp, STORY_FG.copy(alpha = .7f))
        }
        MascotView(Stats.mood(r.total / 7, p.totalLimit), 5.dp)
        Txt("7 hari terakhir kamu nonton", 13.sp, STORY_FG.copy(alpha = .8f))
        Px("${r.total}", 64.sp, Color(0xFFF2B544))
        val diff = if (r.prev > 0) (r.total - r.prev) * 100 / r.prev else null
        Txt(
            when { diff == null -> "reels. Minggu depan bisa dibandingkan."; diff <= 0 -> "reels, ${-diff}% lebih sedikit dari minggu sebelumnya."; else -> "reels, $diff% lebih banyak dari minggu sebelumnya." },
            13.sp, if (diff != null && diff <= 0) Color(0xFF3DB5AA) else STORY_FG,
        )
        listOf(
            "Jam rawan" to (r.peakHour?.let { "%02d.00".format(it) } ?: "-"),
            "App favorit" to (r.fav?.let { "${it.label} ${r.favPct}%" } ?: "-"),
            "Jempol jalan" to Stats.formatMeters(Stats.meters(r.total)),
            "Lencana" to "${r.badges} / ${Badge.entries.size}",
        ).forEach { (k, value) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Txt(k, 13.sp, STORY_FG, modifier = Modifier.weight(1f)); Px(value, 16.sp, STORY_FG)
            }
        }
        Btn("Bagikan") { shareRecap(ctx, r, p.mascot) }
    }
}

fun shareRecap(ctx: Context, r: RecapData, m: Mascot) {
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFF1A1220.toInt())
    val px = ResourcesCompat.getFont(ctx, R.font.pixelify)
    val body = ResourcesCompat.getFont(ctx, R.font.rubik)
    val fg = 0xFFF6E7C8.toInt()
    fun text(s: String, x: Float, y: Float, size: Float, color: Int = fg, face: android.graphics.Typeface? = body, right: Boolean = false) =
        c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; if (right) textAlign = Paint.Align.RIGHT })
    val hole = Paint().apply { color = 0xFF0D0A10.toInt() }
    var x = 30f
    while (x < w - 30) { c.drawRect(x, 24f, x + 40, 54f, hole); c.drawRect(x, h - 54f, x + 40, h - 24f, hole); x += 80 }
    text("Reelo Recap", 90f, 170f, 56f, face = px)
    text("${r.from.format(RANGE)} – ${r.to.format(RANGE)}", w - 90f, 170f, 36f, 0xB3F6E7C8.toInt(), right = true)
    c.drawBitmap(Sprite.bitmap(m, Stats.mood(r.total / 7, Prefs.get(ctx).totalLimit), 12), 70f, 210f, null)
    text("7 hari terakhir kamu nonton", 90f, 600f, 42f, 0xCCF6E7C8.toInt())
    text("${r.total}", 80f, 800f, 220f, 0xFFF2B544.toInt(), px)
    text("reels", 90f, 870f, 44f)
    listOf(
        "Jam rawan" to (r.peakHour?.let { "%02d.00".format(it) } ?: "-"),
        "App favorit" to (r.fav?.let { "${it.label} ${r.favPct}%" } ?: "-"),
        "Jempol jalan" to Stats.formatMeters(Stats.meters(r.total)),
    ).forEachIndexed { i, (k, v) ->
        val y = 990f + i * 90
        c.drawRect(90f, y - 60, w - 90f, y - 57, Paint().apply { color = 0x40F6E7C8 })
        text(k, 90f, y, 40f); text(v, w - 90f, y, 46f, face = px, right = true)
    }
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "reelo-recap.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Bagikan recap"))
    if (Prefs.get(ctx).grant(Badge.BERBAGI)) Live.bump()
}

fun shareText(ctx: Context, name: String, mime: String, content: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, name).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Ekspor data"))
}
