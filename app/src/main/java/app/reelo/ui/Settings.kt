package app.reelo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.reelo.Db
import app.reelo.Live
import app.reelo.LocalPal
import app.reelo.App
import app.reelo.Mascot
import app.reelo.Mood
import app.reelo.PixelFont
import app.reelo.ReelService
import app.reelo.Swap
import app.reelo.ThemeMode
import app.reelo.Usage

const val STUCK = "Dimatiin sistem. Matikan lalu nyalakan lagi, terus izinkan Mulai otomatis biar nggak kejadian lagi."

fun open(ctx: Context, action: String) = runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

fun batteryGuide(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> "Setelan > Aplikasi > Reelo: nyalakan Mulai otomatis, lalu Penghemat baterai pilih Tanpa batasan."
    "samsung" -> "Setelan > Baterai > Batas penggunaan latar belakang: keluarkan Reelo dari Aplikasi tidur."
    "oppo", "realme", "oneplus" -> "Setelan > Baterai > Reelo: izinkan aktivitas latar belakang dan mulai otomatis."
    "vivo", "iqoo" -> "Setelan > Baterai > Konsumsi daya latar tinggi: izinkan Reelo."
    else -> "Setelan > Aplikasi > Reelo > Baterai: pilih Tidak dibatasi."
}

fun hasInternet(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
    .requestedPermissions?.contains(Manifest.permission.INTERNET) == true

fun notifOk(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Health(ok: Boolean?, title: String, sub: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.padding(top = 4.dp).size(10.dp).background(when (ok) { true -> pal.teal; false -> pal.red; null -> pal.mus }))
        Column(Modifier.weight(1f)) {
            Txt(title, bold = true)
            if (sub != null) Small(sub)
            if (action != null) Pill(action, pal.musInk, Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClick = onAction))
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onTema: () -> Unit) {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val v = Live.v
    val svc = ReelService.isOn(ctx)
    val last = remember(v) { Db.get(ctx).lastTs() }
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    var confirm by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }

    Screen {
        Top("Pengaturan", onBack)
        Card {
            Lbl("Cek kesehatan")
            Health(svc, if (svc) "Penghitung reels aktif" else "Penghitung reels mati",
                if (svc) last?.let { "Terakhir menghitung ${(System.currentTimeMillis() - it) / 60_000} menit lalu" } ?: "Belum ada reel yang terhitung" else STUCK.takeIf { ReelService.enabled(ctx) } ?: "Tanpa ini Reelo nggak bisa ngitung swipe.",
                if (svc) null else "Nyalakan") { open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            Health(Usage.hasAccess(ctx), "Data pemakaian", if (Usage.hasAccess(ctx)) "Screen time terbaca" else "Screen time kosong tanpa izin ini.",
                if (Usage.hasAccess(ctx)) null else "Izinkan") { open(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            Health(if (battery) true else null, if (battery) "Baterai nggak membatasi Reelo" else "Optimasi baterai ${Build.MANUFACTURER}",
                if (battery) null else "HP bisa mematikan Reelo diam-diam. ${batteryGuide()}", if (battery) null else "Buka setelan baterai") {
                open(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            Health(!hasInternet(ctx), if (hasInternet(ctx)) "Punya izin internet" else "Tanpa izin internet", "Dicek langsung dari manifest app ini.")
        }
        Card {
            SettingRow("Tema", when (p.theme) { ThemeMode.AUTO -> "Ikut HP"; ThemeMode.DAY -> "Siang"; ThemeMode.NIGHT -> "Bioskop Malam" }) { Px(">", 18.sp, modifier = Modifier.clickable(onClick = onTema)) }
            Lbl("Maskot")
            Small("Tiap app punya penjaga sendiri. Maskot melayang ganti otomatis pas kamu pindah app.")
            (listOf<App?>(null) + App.entries).forEach { app ->
                val cur = p.mascotFor(app)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(app?.label ?: "Utama", 12.sp, bold = true, modifier = Modifier.width(62.dp))
                    Mascot.entries.forEach { m ->
                        Column(
                            Modifier.weight(1f).border(2.dp, if (cur == m) pal.red else pal.line)
                                .clickable(role = Role.RadioButton, onClickLabel = "${m.label} buat ${app?.label ?: "maskot utama"}") { p.setMascotFor(app, m); Live.bump() }.padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { MascotView(Mood.SEGAR, 1.5.dp, mascot = m); Txt(m.label, 10.sp, bold = cur == m) }
                    }
                }
            }
            SettingRow("Maskot melayang", "Muncul di home screen dan di atas app video") {
                Toggle(p.floatOn, "Maskot melayang") { p.floatOn = it; Live.bump() }
            }
            SettingRow("Target \"Setara dengan\"", "${p.swap.label}, ${p.swap.minutes} menit per ${p.swap.unit}") {
                Pill("Ganti", pal.teal, Modifier.clickable(role = Role.Button) { p.swap = Swap.entries[(p.swap.ordinal + 1) % Swap.entries.size]; Live.bump() })
            }
            SettingRow("Kurangi animasi") { Toggle(p.reduceMotion, "Kurangi animasi") { p.reduceMotion = it; Live.bump() } }
            SettingRow("Hari mulai jam", "Begadang lewat tengah malam masih dihitung ke hari sebelumnya") { Px("04.00", 15.sp) }
        }
        Card {
            SettingRow("Ekspor data (CSV)") { Pill("Ekspor", pal.teal, Modifier.clickable(role = Role.Button) { shareText(ctx, "reelo.csv", "text/csv", Db.get(ctx).csv()) }) }
            SettingRow("Hapus semua data", "Hitungan, lencana, Rol Film, dan setelan") { Pill("Hapus", pal.red, Modifier.clickable(role = Role.Button) { asking = !asking }) }
            if (asking) {
                Small("Ketik HAPUS buat konfirmasi. Ini nggak bisa dibatalkan.")
                BasicTextField(confirm, { confirm = it }, Modifier.fillMaxWidth().border(2.dp, pal.fg).padding(8.dp),
                    textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = PixelFont), singleLine = true, cursorBrush = SolidColor(pal.fg))
                if (confirm.trim() == "HAPUS") Btn("Hapus sekarang") {
                    Db.get(ctx).clear(); p.clearAll(); confirm = ""; asking = false; Live.bump()
                }
            }
        }
        Small("Reelo ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}. Semua data tersimpan di HP ini.")
    }
}

@Composable
fun Tema(onBack: () -> Unit) {
    val p = prefs()
    val pal = LocalPal.current
    Screen {
        Top("Tema", onBack)
        listOf(
            Triple(ThemeMode.AUTO, "Ikut HP", "Ganti sendiri sesuai tema sistem"),
            Triple(ThemeMode.DAY, "Siang", "Kertas tiket, tinta coklat, bayangan pixel"),
            Triple(ThemeMode.NIGHT, "Bioskop Malam", "Gelap, angka dan maskot menyala"),
        ).forEach { (mode, name, sub) ->
            val on = p.theme == mode
            Row(
                Modifier.fillMaxWidth().then(if (on) Modifier.pixelShadow(pal.night, pal.fg) else Modifier).background(pal.card)
                    .border(2.dp, if (on) pal.red else pal.line).clickable(role = Role.RadioButton) { p.theme = mode; Live.bump() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val prev = when (mode) {
                    ThemeMode.AUTO -> Brush.horizontalGradient(0f to Color(0xFFF4EBDD), .5f to Color(0xFFF4EBDD), .5f to Color(0xFF120F16), 1f to Color(0xFF120F16))
                    ThemeMode.DAY -> SolidColor(Color(0xFFF4EBDD))
                    ThemeMode.NIGHT -> SolidColor(Color(0xFF120F16))
                }
                Column(Modifier.size(42.dp, 58.dp).background(prev).border(2.dp, pal.fg).padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth().size(12.dp).background(if (mode == ThemeMode.NIGHT) Color(0xFFFFC458) else Color(0xFFE0503A)))
                }
                Column { Px(name, 16.sp); Small(sub) }
            }
        }
        Card {
            SettingRow("Gelap saat Jam Malam", "${"%02d".format(p.nightStart)}.00 – ${"%02d".format(p.nightEnd)}.00 selalu Bioskop Malam") {
                Toggle(p.nightTheme, "Gelap saat Jam Malam") { p.nightTheme = it; Live.bump() }
            }
        }
        Card { SettingRow("Getar halus", "Tiap geser rol dan tahan tombol") { Toggle(p.haptics, "Getar halus") { p.haptics = it; Live.bump() } } }
    }
}

@Composable
fun Onboarding() {
    val ctx = LocalContext.current
    val p = prefs()
    val pal = LocalPal.current
    val usageOk = Usage.hasAccess(ctx)
    val svcOk = ReelService.isOn(ctx)
    var started by rememberSaveable { mutableStateOf(false) }
    var skipNotif by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Live.bump() }
    val notif = notifOk(ctx) || skipNotif
    val done = listOf(usageOk, svcOk, notif).count { it }

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i -> Box(Modifier.size(14.dp, 10.dp).then(if (i < done) Modifier.background(pal.red) else Modifier).border(2.dp, if (i < done) pal.red else pal.fg)) }
            }
            Small(if (done == 3) "Semua izin beres" else "Izin ${done + 1} dari 3")
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) { MascotView(Mood.SEGAR, 4.dp); Px(p.mascot.label, 18.sp) }
        Msg("Halo! Aku bantu kamu tahu berapa reels yang kamu tonton tiap hari.")
        Msg("Aku butuh 3 izin. Tiap izin aku jelasin dulu buat apa.")
        if (!started) { Btn("Oke, gas") { started = true }; return@Screen }
        Msg("Oke, gas", me = true)
        if (usageOk) Msg("Data pemakaian udah aktif. Screen time kebaca.")
        if (usageOk && svcOk) Msg("Penghitung reels nyala. Aku siap ngitung.")
        when {
            !usageOk -> Perm("Data pemakaian", "Buat screen time dan berapa kali kamu buka TikTok, YouTube, dan Instagram. Cari Reelo di daftar lalu izinkan.", "Buka pengaturan") { open(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            !svcOk -> Perm("Penghitung reels", "Pakai layanan Aksesibilitas. Aku cuma lihat kapan kamu swipe di TikTok, Shorts, dan IG Reels, buat ngitung dan nampilin maskot melayang. Aku nggak baca isi video, caption, atau chat.", "Buka pengaturan") { open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            !notif -> {
                Perm("Notifikasi", "Buat ngingetin pas jatah tinggal sedikit dan buat munculin lagi maskot yang kamu sembunyikan.", "Izinkan") {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Small("Nanti aja", Modifier.clickable(role = Role.Button) { skipNotif = true }.padding(4.dp))
            }
            else -> {
                Msg("Semua siap! Rol pertamamu mulai sekarang.")
                Btn("Mulai") { p.onboarded = true; Live.bump() }
            }
        }
    }
}

@Composable
private fun Msg(text: String, me: Boolean = false) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
        Box(Modifier.fillMaxWidth(if (me) .4f else .86f).background(if (me) pal.fg else pal.card).border(2.dp, if (me) pal.fg else pal.line).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Txt(text, 12.5.sp, if (me) pal.bg else pal.fg)
        }
    }
}

@Composable
private fun Perm(title: String, body: String, action: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().pixelShadow(pal.night, pal.fg).background(pal.card).border(2.dp, pal.fg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, 15.sp, bold = true)
        Small(body)
        Box(Modifier.border(2.dp, pal.teal).padding(8.dp)) { Txt("Reelo nggak punya izin internet. Datamu tetap di HP ini.", 11.5.sp, pal.teal) }
        Btn(action, onClick = onClick)
    }
}
