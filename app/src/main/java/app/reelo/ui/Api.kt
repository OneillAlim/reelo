package app.reelo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reelo.BodyFont
import app.reelo.Db
import app.reelo.Flame
import app.reelo.Friend
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.Mood
import app.reelo.Msg
import app.reelo.ReelService

private val FLAME = listOf(
    listOf("...#...", "..##...", "..###..", ".#####.", ".##.##.", "##...##", ".#####."),
    listOf("....#..", "...##..", "..###..", ".#####.", ".##.##.", "##...##", ".#####."),
)

/** Api pixel yang goyang 2 frame. */
@Composable
fun FlameIcon(size: androidx.compose.ui.unit.Dp = 18.dp, color: androidx.compose.ui.graphics.Color = LocalPal.current.red) {
    val t = ticker()
    PixelIcon(FLAME[((t / 350) % 2).toInt()], color, size)
}

private fun svc() = ReelService.inst

/** Layar utama Jaga Api: status, tombol ke Teman/Pesan/Riwayat, kirim sekarang, tes 1 teman. */
@Composable
fun JagaApi(onBack: () -> Unit, onFriends: () -> Unit, onMsgs: () -> Unit, onLog: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val v = Live.v
    val db = Db.get(ctx)
    val friends = load(v, initial = emptyList<Friend>()) { db.friends() }
    val msgs = load(v, initial = emptyList<Msg>()) { db.msgs() }
    val log = load(v, initial = emptyList<Db.Sent>()) { db.history(60) }
    val live = friends.filter { it.streak > 0 }
    val picked = live.filter { it.on }.take(Flame.MAX_FRIENDS)
    val activeMsgs = msgs.count { it.on }
    val today = Flame.day(System.currentTimeMillis()).toString()
    val doneToday = p.flameLastRun == today
    var warn by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) } // "" = semua, selain itu nama teman
    var info by remember { mutableStateOf<String?>(null) }

    fun run(only: String?) {
        val s = svc() ?: run { info = "Penghitung Reelo lagi mati, nyalain dulu di Aksesibilitas."; return }
        val n = s.flameNow(only)
        info = when { n < 0 -> "Lagi jalan, tunggu bentar."; n == 0 -> "Nggak ada yang perlu dikirim: semua udah kebagian hari ini, atau belum ada pesan aktif."; else -> null }
    }

    Screen {
        Top("Jaga Api", onBack) {
            Toggle(p.flameOn, "Jaga Api") { on -> if (on && !p.flameWarned) warn = true else { p.flameOn = on; Live.bump() } }
        }
        if (warn) Card {
            Txt("Sebelum nyala", 14.sp, pal.red, bold = true)
            Small("• Ngirim pesan otomatis itu melanggar aturan TikTok soal otomasi. Akun bisa dibatasi; pakai dengan risiko sendiri.")
            Small("• Streak butuh dua arah. Reelo cuma ngisi sisimu, temanmu tetap harus kirim juga.")
            Small("• HP yang terkunci nggak bisa dibuka app. Kalau jam 00.00 HP dikunci, Jaga Api jalan pas HP pertama kali kamu buka.")
            Small("• Cuma ke teman yang kamu centang, maksimal 1 pesan per teman per hari, tanpa link.")
            Btn("Ngerti, nyalain") { p.flameWarned = true; p.flameOn = true; warn = false; Live.bump() }
        }
        Film {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box { MascotView(if (p.flameOn) Mood.SEGAR else Mood.SANTAI, 3.dp); Box(Modifier.align(Alignment.BottomEnd)) { FlameIcon(26.dp, pal.mus) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Px(when {
                        live.isEmpty() -> "Pindai Inbox dulu"
                        activeMsgs == 0 -> "Tulis pesan dulu"
                        !p.flameOn -> "Jaga Api lagi mati"
                        doneToday -> "Api hari ini aman"
                        else -> "${picked.size} api aku jagain"
                    }, 20.sp, pal.mus)
                    Txt(when {
                        live.isEmpty() -> "Aku buka Inbox TikTok sebentar buat nyari teman yang punya api."
                        activeMsgs == 0 -> "Minimal satu pesan, nanti aku acak per teman."
                        !p.flameOn -> "Nyalain biar jalan sendiri tiap malam."
                        doneToday -> "Jalan lagi besok jam 00.00, atau pas HP pertama kamu buka."
                        else -> "Jalan jam 00.00, atau pas HP pertama kamu buka setelahnya."
                    }, 12.sp, pal.filmFg)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f), onClick = onFriends) { Lbl("Teman"); Row(verticalAlignment = Alignment.CenterVertically) { Big("${picked.size}"); Px(" /${live.size}", 14.sp, pal.muted, Modifier.padding(top = 4.dp)) }; Small("dicentang >") }
            Card(Modifier.weight(1f), onClick = onMsgs) { Lbl("Pesan"); Big("$activeMsgs"); Small("variasi aktif >") }
        }
        val lastDay = log.firstOrNull()?.day
        if (lastDay != null) Card(onClick = onLog) {
            val day = log.filter { it.day == lastDay }
            val ok = day.count { it.ok }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Lbl(if (lastDay == today) "Hari ini" else "Terakhir · $lastDay")
                    Txt("$ok terkirim" + if (day.size > ok) " · ${day.size - ok} gagal" else "", bold = true)
                }
                Px(">", 20.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Tes 1 teman", Modifier.weight(1f), ghost = true) { picking = !picking; confirm = null }
            Btn("Kirim sekarang", Modifier.weight(1f)) { confirm = ""; picking = false }
        }
        if (picking) Card {
            Lbl("Kirim 1 pesan beneran ke...")
            if (picked.isEmpty()) Small("Belum ada teman yang dicentang.")
            picked.take(12).forEach { f ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { confirm = f.name; picking = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(f.name, modifier = Modifier.weight(1f)); FlameIcon(14.dp); Px(" ${f.streak}", 14.sp)
                }
            }
        }
        confirm?.let { who ->
            Card {
                Txt(if (who.isEmpty()) "Kirim ke ${picked.size} teman sekarang?" else "Kirim 1 pesan ke $who sekarang?", bold = true)
                Small("Layar dipinjam sebentar buat buka TikTok, nanti balik sendiri. Teman yang udah kebagian hari ini dilewati.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Batal", Modifier.weight(1f), ghost = true) { confirm = null }
                    Btn("Kirim", Modifier.weight(1f)) { confirm = null; run(who.ifEmpty { null }) }
                }
            }
        }
        info?.let { Small(it, color = pal.red) }
        Small("Streak butuh dua arah: Reelo ngisi sisimu, temanmu tetap harus bales.")
        if (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            Pill("debug: hitung mundur sekarang", pal.muted, Modifier.clickable { svc()?.flameMaybe(force = true) })
    }
}

/** Daftar teman hasil pindai Inbox: api nyala (bisa dicentang) dan api padam (cuma info). */
@Composable
fun ApiFriends(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val v = Live.v
    val db = Db.get(ctx)
    val friends = load(v, initial = emptyList<Friend>()) { db.friends() }
    var scanning by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    val live = friends.filter { it.streak > 0 && it.name.contains(q.trim(), ignoreCase = true) }
    val dead = friends.filter { it.streak == 0 && it.restoreDays > 0 && it.name.contains(q.trim(), ignoreCase = true) }
    val onCount = friends.count { it.on && it.streak > 0 }

    fun scan() {
        val s = svc() ?: run { info = "Penghitung Reelo lagi mati, nyalain dulu di Aksesibilitas."; return }
        scanning = true; info = null
        s.flameScan { n -> scanning = false; info = if (n == null) "Inbox TikTok nggak kebuka. Coba lagi, atau buka TikTok sekali dulu." else "Ketemu $n teman." }
    }

    Screen {
        Top("Teman", onBack) { Pill(if (scanning) "Lagi pindai..." else "Pindai ulang", pal.teal, Modifier.clickable(enabled = !scanning, role = Role.Button) { scan() }) }
        if (scanning) Small("Aku lagi buka Inbox TikTok. Jangan sentuh layar dulu, nanti balik sendiri.")
        info?.let { Small(it, color = pal.muted) }
        if (friends.isEmpty() && !scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MascotView(Mood.SANTAI, 3.dp)
                Bubble("Belum ada. Ketuk Pindai, aku buka Inbox TikTok sebentar buat nyari teman yang punya api.", Modifier.weight(1f))
            }
            Btn("Pindai Inbox TikTok") { scan() }
        }
        if (friends.size > 8) BasicTextField(q, { q = it }, Modifier.fillMaxWidth().border(2.dp, pal.line).padding(10.dp),
            textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = BodyFont), singleLine = true, cursorBrush = SolidColor(pal.fg),
            decorationBox = { inner -> if (q.isEmpty()) Txt("Cari nama...", 14.sp, pal.muted); inner() })
        if (live.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Lbl("Api nyala · $onCount dicentang") }
                Pill(if (live.all { it.on }) "Kosongkan" else "Pilih semua", pal.musInk, Modifier.clickable(role = Role.Button) {
                    val all = !live.all { it.on }; live.forEach { db.setFriendOn(it.name, all) }; Live.bump()
                })
            }
            Card {
                live.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(f.name, 14.sp, bold = f.on, modifier = Modifier.weight(1f))
                        FlameIcon(14.dp); Px("${f.streak}", 15.sp)
                        Toggle(f.on, "Jaga api ${f.name}") { db.setFriendOn(f.name, it); Live.bump() }
                    }
                }
            }
            if (onCount > Flame.MAX_FRIENDS) Small("Cuma ${Flame.MAX_FRIENDS} teman teratas yang dikirimin tiap malam.", color = pal.red)
        }
        if (dead.isNotEmpty()) {
            Lbl("Api padam · pulihkan di TikTok")
            Card {
                dead.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(f.name, 14.sp, pal.muted, modifier = Modifier.weight(1f)); Small("${f.restoreDays} hari")
                    }
                }
                Small("Api yang udah padam nggak bisa dinyalain lewat pesan. Buka chatnya di TikTok, ada tombol pulihkan.")
            }
        }
        if (p.flameScanTs > 0) Small("Terakhir dipindai ${hm(p.flameScanTs)}. Teman baru dengan api muncul setelah pindai ulang.")
    }
}

/** Pesan buatan sendiri. Tiap malam tiap teman dapat satu yang diacak, bukan yang kemarin. */
@Composable
fun ApiMsgs(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val v = Live.v
    val db = Db.get(ctx)
    val msgs = load(v, initial = emptyList<Msg>()) { db.msgs() }
    val sample = load(v, initial = null as Friend?) { db.friends().firstOrNull { it.streak > 0 } }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var roll by remember { mutableIntStateOf(0) }

    fun save() {
        val t = text.trim()
        err = Flame.invalid(t); if (err != null) return
        editing?.let { db.editMsg(it, t) } ?: db.addMsg(t)
        text = ""; editing = null; Live.bump()
    }

    Screen {
        Top("Pesan", onBack) { Lbl("${msgs.count { it.on }} aktif") }
        if (msgs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MascotView(Mood.SEGAR, 3.dp)
            Bubble("Belum ada pesan. Tulis minimal satu, nanti aku acak per teman biar nggak itu-itu aja.", Modifier.weight(1f))
        }
        Card {
            Lbl(if (editing != null) "Ubah pesan" else "Pesan baru")
            BasicTextField(text, { text = it.take(Flame.MAX_LEN); err = null }, Modifier.fillMaxWidth().border(2.dp, pal.fg).padding(10.dp),
                textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = BodyFont), cursorBrush = SolidColor(pal.fg),
                decorationBox = { inner -> if (text.isEmpty()) Txt("mis. api aman ya {nama} 🔥", 15.sp, pal.muted); inner() })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("{nama}", "{hari}", "🔥", "🫶").forEach { chip -> Pill(chip, pal.teal, Modifier.clickable(role = Role.Button) { text = (text + chip).take(Flame.MAX_LEN) }) }
                Box(Modifier.weight(1f)); Small("${text.length}/${Flame.MAX_LEN}")
            }
            Small("{nama} jadi nama temanmu, {hari} jadi angka api setelah pesan ini.")
            err?.let { Small(it, color = pal.red) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing != null) Btn("Batal", Modifier.weight(1f), ghost = true) { editing = null; text = "" }
                Btn(if (editing != null) "Simpan" else "Tambah", Modifier.weight(1f)) { save() }
            }
        }
        if (msgs.isNotEmpty()) Card {
            msgs.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle(m.on, "Pakai pesan ini") { db.setMsgOn(m.id, it); Live.bump() }
                    Txt(m.text, 13.sp, if (m.on) pal.fg else pal.muted, modifier = Modifier.weight(1f))
                    Px("✎", 16.sp, modifier = Modifier.clickable(onClickLabel = "Ubah") { editing = m.id; text = m.text }.padding(4.dp))
                    Px("✕", 16.sp, pal.red, Modifier.clickable(onClickLabel = "Hapus") { db.deleteMsg(m.id); Live.bump() }.padding(4.dp))
                }
            }
        }
        val preview = remember(msgs, roll) { Flame.pick(msgs, null, java.util.Random()) }
        if (preview != null) Card {
            Lbl("Contoh yang bakal diterima")
            val f = sample
            Txt(Flame.fill(preview.text, f?.name ?: "Zeta", f?.streak ?: 99), 15.sp, bold = true)
            Pill("acak lagi", pal.musInk, Modifier.clickable(role = Role.Button) { roll++ })
        }
    }
}

/** Riwayat per malam: siapa yang kebagian, siapa yang gagal dan kenapa. */
@Composable
fun ApiLog(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val v = Live.v
    val log = load(v, initial = emptyList<Db.Sent>()) { Db.get(ctx).history(300) }
    Screen {
        Top("Riwayat", onBack)
        if (log.isEmpty()) Small("Belum pernah jalan.")
        log.groupBy { it.day }.forEach { (day, rows) ->
            Lbl("$day · ${rows.count { it.ok }}/${rows.size} terkirim")
            Card {
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Px(if (r.ok) "✓" else "✕", 16.sp, if (r.ok) pal.teal else pal.red)
                        Column(Modifier.weight(1f)) { Txt(r.name, 13.sp, bold = true); if (r.note != null) Small(r.note) }
                        Small(hm(r.ts))
                    }
                }
            }
        }
    }
}
