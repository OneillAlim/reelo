package app.reelo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelo.Acc
import app.reelo.App
import app.reelo.Badge
import app.reelo.Days
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.Mood
import app.reelo.Stats
import kotlinx.coroutines.delay

@Composable
fun Kendali() {
    val p = prefs()
    val pal = LocalPal.current
    val today = Days.today()
    val wk = Days.weekStart(today)
    val satuLagi = (0..6L).sumOf { p.satuLagi(wk.plusDays(it)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.focusUntil) { while (p.focusUntil > now) { delay(1000); now = System.currentTimeMillis() } }

    Screen {
        Top("Kendali") { Lbl("Total ${p.totalLimit}") }
        Card {
            Lbl("Jatah reels per hari")
            App.entries.forEach { app ->
                SettingRow(app.label) { Stepper(p.limit(app)) { p.setLimit(app, it); Live.bump() } }
            }
            Small("Dihitung dalam jumlah reels, bukan menit. Maskot mulai capek pas jatah tinggal sepertiga.")
        }
        Card {
            SettingRow("Jam Malam", "${"%02d".format(p.nightStart)}.00 – ${"%02d".format(p.nightEnd)}.00 · jatah cuma ${p.nightLimit}") {
                Toggle(p.nightOn, "Jam Malam") { p.nightOn = it; Live.bump() }
            }
            if (p.nightOn) {
                SettingRow("Mulai") { Stepper(p.nightStart, 1, 0, 23, ".00") { p.nightStart = it; Live.bump() } }
                SettingRow("Selesai") { Stepper(p.nightEnd, 1, 0, 23, ".00") { p.nightEnd = it; Live.bump() } }
                SettingRow("Jatah malam") { Stepper(p.nightLimit) { p.nightLimit = it; Live.bump() } }
            }
        }
        Card {
            SettingRow("Mode Tegas", "Tutup app otomatis saat jatah habis") { Toggle(p.strict, "Mode Tegas") { p.strict = it; Live.bump() } }
        }
        Card {
            val left = p.focusUntil - now
            if (left > 0) {
                SettingRow("Lagi fokus", "Sisa ${left / 60_000 + 1} menit. Tiap reel langsung dapat Pause Card.") {
                    Pill("Stop", pal.red, Modifier.clickable(role = Role.Button) { p.focusUntil = 0; Live.bump() })
                }
            } else {
                SettingRow("Fokus 25 menit", "Selama fokus, reel pertama langsung dihentikan") {
                    Pill("Mulai", pal.teal, Modifier.clickable(role = Role.Button) { p.focusUntil = System.currentTimeMillis() + 25 * 60_000; now = System.currentTimeMillis(); Live.bump() })
                }
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) { Txt("Counter \"Satu Lagi\"", bold = true, modifier = Modifier.weight(1f)); Big("$satuLagi") }
            if (satuLagi > 0) Px((1..satuLagi).chunked(5).joinToString("  ") { c -> "|".repeat(c.size) }, 18.sp, pal.red)
            Small("Kali kamu tahan Lanjut setelah jatah habis, minggu ini.")
        }
    }
}

@Composable
fun Lemari() {
    val p = prefs()
    val pal = LocalPal.current
    val stage = Stats.stage(p.bestStreak)
    var trying by remember { mutableStateOf<Acc?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
    Screen {
        Top("Lemari") { Pill("${p.rol} Rol Film", pal.musInk) }
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { MascotView(Mood.SEGAR, 6.dp, acc = trying ?: p.acc) }
            if (trying != null) Small("Lagi nyobain ${trying!!.label}. Kumpulin rol buat beli beneran.", Modifier.fillMaxWidth())
        }
        Small("Dapat 20 Rol Film tiap hari kamu di bawah jatah. Nggak bisa dibeli pakai uang.")
        Acc.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val owned = p.owns(a)
                    val worn = p.acc == a
                    val locked = !owned && stage < a.needStage
                    val status = when {
                        worn -> "dipakai"
                        owned -> "punya · ketuk buat pakai"
                        locked -> "butuh evolusi Sinema"
                        p.rol >= a.price -> "beli ${a.price} rol"
                        else -> "kurang ${a.price - p.rol} rol"
                    }
                    Column(
                        Modifier.weight(1f).background(pal.card).border(2.dp, if (worn) pal.red else pal.line)
                            .clickable(role = Role.Button) {
                                trying = null
                                when {
                                    owned -> p.acc = if (worn) null else a
                                    !locked && p.rol >= a.price -> { p.rol -= a.price; p.own(a); p.acc = a; p.grant(Badge.GAYA); confetti++ }
                                    else -> trying = a // belum kebeli: cobain dulu di maskot besar
                                }
                                Live.bump()
                            }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MascotView(Mood.SANTAI, 2.dp, acc = a, gray = locked)
                        Px(a.label, 13.sp, align = TextAlign.Center)
                        Small(status)
                    }
                }
            }
        }
    }
    Confetti(confetti)
    }
}
