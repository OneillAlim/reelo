# Changelog

Catatan tiap update, biar siapa pun bisa lanjut dari sini. Yang paling baru di atas.
Tiap entri: apa yang berubah, kenapa, file yang disentuh, cara ngetes, dan sisa kerjaan.

## 2026-11-07 · Jaga Api: nyalain streak TikTok otomatis (kode jadi, belum dites di HP)

> Status: build + unit test hijau. **Pindai dan kirim belum dicoba di HP** (HP lagi terkunci). Cara ngetes ada di bawah.

**Apa.** Reelo bisa ngirim pesan ke teman TikTok yang udah punya streak, otomatis mulai jam 00.00. Kalau jam segitu
HP terkunci, Jaga Api jalan pas HP pertama kali dibuka setelahnya. Pesannya buatan sendiri dan diacak per teman.

**Batasan yang ditulis jujur di app** (kartu peringatan muncul pas pertama kali dinyalain):
- melanggar aturan TikTok soal otomasi, jadi akun bisa dibatasi;
- streak dua arah, Reelo cuma ngisi sisi kamu;
- HP terkunci nggak bisa dibuka app;
- cuma ke teman yang dicentang, maksimal 1 pesan per teman per hari (`Db.sentOk`), maksimal 40 teman
  (`Flame.MAX_FRIENDS`), pesan tanpa link (`Flame.invalid`);
- nggak ada trik buat nyamar jadi manusia.

**File baru.**
- `Flame.kt`: logika murni (`cleanName`, `parseStreak`, `restoreDays`, `fill` {nama}/{hari}, `pick` yang
  menghindari pesan kemarin, `invalid`, hari api ganti jam **00.00** lewat `Flame.day`, `untilMidnight`). Data class
  `Friend`, `Msg`.
- `TikTokBot.kt`: robot berbasis coroutine Main.
  - `scan()`: buka TikTok, tab Inbox, baca baris, scroll sampai nggak ada nama baru.
  - `send()`: per teman: cari baris, buka chat, `ACTION_SET_TEXT` ke `EditText`, klik tombol desc `Send`/`Kirim`,
    cek pesannya muncul, `BACK`.
  - Selesai/batal: buka lagi app sebelumnya, kalau nggak ada ke HOME.
  - Semua penanda layar ada di `object TikTokUi`, disurvei di TikTok 47.6.3:
    - `user_name` = nama;
    - `mux` = status (api padam = "Restore your N-day Streak");
    - angka api = TextView angka di kanan nama;
    - badge belum dibaca = `View`, bukan `TextView`, jadi nggak ketuker.
- `ui/Api.kt`: layar Jaga Api (status + toggle + Tes 1 teman + Kirim sekarang), Teman (pindai, centang, pilih semua,
  cari, api padam dipisah), Pesan (tulis/ubah/hapus/centang, sisipan {nama} {hari} 🔥, contoh acak), Riwayat
  (per hari ✓/✕ + alasan).
- `FlameCard` (di `Overlay.kt`): kartu atas layar, hitung mundur 5 detik (Tunda 1 jam / Mulai), lalu progres per
  teman + Batal.

**Perubahan.**
- `Db` versi 2 (`onUpgrade` cuma nambah tabel `friend`, `msg`, `sent`; tabel `swipe` aman). Upsert ditulis
  `INSERT OR IGNORE` + `UPDATE` karena minSdk 28. "Hapus semua data" ikut ngosongin tabel baru.
- `Prefs`: `flameOn`, `flameWarned`, `flameSnoozeUntil`, `flameLastRun`, `flameScanTs`.
- `ReelService`:
  - timer `Handler` ke 00.00 (`armMidnight`) + receiver `USER_PRESENT`, dua-duanya manggil `flameMaybe()`;
  - `flameMaybe()` jalan kalau: nyala, belum jalan hari ini, nggak ditunda, HP kebuka (`KeyguardManager`), nggak lagi
    telepon (`AudioManager`), dan ada yang perlu dikirim;
  - selama robot jalan: hitungan reels, Pause Card, Jam Malam, Mode Tegas, dan maskot melayang dimatiin;
  - `ReelService.inst` dipakai layar buat manggil `flameScan` / `flameNow`;
  - notifikasi hasil (`NOTIF_FLAME`).
- Pintu masuk: kartu "Jaga Api TikTok" di Beranda (di bawah Tantangan) dan di Kendali.

**Tes.** `StatsTest.flameParsing`, `flamePickAvoidsYesterday`, `flameDayStartsAtMidnight`.

**Cara ngetes di HP (belum dilakukan).**
1. Kendali > Jaga Api > Teman > Pindai. Hasilnya harus sama dengan Inbox: api nyala + angka, api padam dipisah.
2. Pesan: tambah 1–2 pesan.
3. **Tes 1 teman** ke teman yang setuju dikirimin pesan tes. Ini kirim beneran. Cek di chat, dan cek Riwayat.
4. Build debug punya tombol "debug: hitung mundur sekarang" buat ngetes kartu hitung mundur tanpa nunggu 00.00.
5. Kunci HP, buka lagi setelah 00.00, terus pastiin hitung mundur muncul.
6. Kalau gagal "Kolom pesan nggak muncul" / "Tombol kirim nggak ketemu": layar chat TikTok beda dari asumsi. Dump layar
   chat lalu sesuaikan `TikTokUi.SEND` atau cara cari `EditText` di `TikTokBot.sendOne`.

## 2026-11-07 · Widget baru: latar pixel, gerak, 3 gaya, papan marquee

**Kenapa.** Bryan nggak suka widget lama (kotak krem polos, font nggak pixel). Dia minta ada latar, bisa gerak lucu,
dan tampilannya bisa beda-beda.

**Perubahan.**
- `WidgetArt.kt` (baru) gambar semua grafis widget pakai kotak pixel (1 pixel seni = 3 dp):
  - `scene(style, …)`: latar penuh dengan sudut bulat 16 dp. Ada 3 gaya (`WStyle`):
    - **Langit:** warnanya ngikutin jam (pagi 5–9, siang 10–14, senja 15–18, malam). Ada matahari + awan atau
      bulan, bukit dua lapis, dan gedung bioskop kecil yang jendelanya nyala pas malam.
    - **Bioskop:** tirai merah di kiri-kanan, rumbai, layar remang.
    - **Tiket:** kertas krem bergaris, pita merah berlubang, garis sobek.
  - `fx(...)`: lapisan yang kedip di atas widget: bintang (Langit malam), burung kepak (Langit siang), bohlam yang
    warnanya ikut heat (Bioskop), kilau (Tiket).
  - `mascot(...)`: 6 frame maskot (diam, lompat, diam, lompat, diam, kedip).
  - `sign` + `signFx`: papan marquee dengan bohlam keliling yang kejar-kejaran (3 frame).
  - `label(...)`: teks Jersey 15 digambar jadi bitmap. Ternyata launcher HyperOS nggak mau pakai `@font/jersey` di
    TextView widget, jadi angka dan label pixel harus jadi gambar.
- **Animasi:** widget nggak bisa jalanin animasi sendiri, jadi frame-nya diputar `ViewFlipper` (`autoStart`). Kecepatan
  maskot ikut heat (`WidgetArt.flipMs`: 800/600/420/300 ms), jadi makin dekat jatah makin heboh.
- **Ganti gaya:** tombol ◆ di pojok kanan bawah widget kecil dan lebar. Gaya disimpan per widget (`wstyle_<id>` di
  prefs) lewat broadcast `app.reelo.WIDGET_STYLE` ke provider, dan dihapus pas widget dibuang (`onDeleted`).
- **Widget ketiga:** `MarqueeWidget` 4x1 "Reelo papan bioskop", berisi maskot, "HARI INI", angka glow kuning
  (merah kalau lewat jatah), dan sisa.
- Semua provider turunan `ReeloWidget`. Ukuran gambar diambil dari `getAppWidgetOptions`, dan digambar ulang pas
  widget di-resize (`onAppWidgetOptionsChanged`).
- Widget lebar: panel kertas di kanan buat baris per app (maskot mini per app + bar warna sesuai heat app itu).

**Lainnya yang dibenerin.**
- **Teks privasi nggak akurat lagi.** Sejak ada sidik jari, Reelo memang membaca teks di layar reel. Deskripsi
  aksesibilitas (`a11y_desc`) dan teks izin di onboarding sekarang jujur: teks diubah jadi hash, teksnya dibuang,
  nggak disimpan dan nggak dikirim.
- **Sidik jari hilang tiap restart service.** Restart karena update app atau HyperOS bikin reel lama bisa kehitung
  lagi. Sekarang hash-nya disimpan di `Prefs.seenStr` (format `tanggal|dump`) dan dipulihin pas service nyambung,
  cuma kalau tanggalnya sama (`SwipeCounter.dump/load`). Tes: `StatsTest.seenSurvivesRestart`.
- **Gambar recap:** teks Jersey di gambar share ikut dikali `PX`.
- **Maskot melayang di home screen nutupin dock.** Batas bawahnya sekarang 160 dp (dulu 90).

**Dicek di HP (Xiaomi 14T).**
- Widget kecil gaya Langit malam: bintang kelip dan maskot goyang (dicek lewat rekaman layar).
- Tombol ◆: Langit → Bioskop → Tiket → Langit.

**Belum dicek.** Widget lebar dan papan marquee belum dipasang di HP, dan Langit versi pagi/siang/senja baru keliatan
di jam itu. Kalau ada yang aneh, gambarnya di `WidgetArt.scene` / `fx`.

## 2026-11-07 · Dicek di HP + perbaikan

Semua yang di entri sebelumnya ("Perlu dicek di HP") udah dicek di Xiaomi 14T (Android 15), Siang dan Malam:
Beranda, Insight, Saya, Pengaturan, Tema, Lemari, maskot melayang di home screen dan TikTok, Pause Card, menu Coret 1.

**Perbaikan dari hasil cek.**
- **Font pixel kekecilan.** Jersey 15 lebih kecil dari Pixelify di sp yang sama. Ditambah konstanta `PX = 1.2f`
  (`Theme.kt`), yang dikaliin di `Px`, `Lbl`, `Pill`, dan `Odometer`. Teks pixel di overlay (Pause Card, tombol tahan,
  tong sampah) juga dinaikin.
- **Bohlam mati nggak kontras.** Dulu warnanya krem terang (`p.hole`), sekarang `filmFg` 16% biar redup.
- **Maskot nutupin tombol TikTok.** Di app reels, `FloatingMascot.yRange()` batasin tinggi maskot:
  - sisi kanan cuma 10–26% layar (di atas kolom like/komentar/share);
  - sisi kiri 10–60% (di atas caption).
  Pas masuk app reels, maskot langsung minggir (`snap()`). Lompat ke seberang juga mendarat di rentang sisi tujuan.
- **Maskot tidur selamanya.** Setelah tidur 45 detik dia nggak bangun lagi. Sekarang bangun sendiri 1,5–2,5 menit
  kemudian, dan juga bangun tiap hitungan nambah.
- **Maskot ketiduran pas lagi dipegang.** Timer tidur yang dipasang pakai `view.postDelayed` sebelum maskot dilepas
  (misalnya pas Pause Card muncul) nggak bisa dibatalin lewat `view.removeCallbacks` selama view lepas dari window,
  jadi tetap jalan. Semua timer `FloatingMascot` sekarang pakai `Handler` sendiri (`h`).
- **Nggak nyapa kalau masuk app dari Reelo** (waktu itu maskotnya lagi disembunyiin). Sekarang nyapa juga pas
  dipasang ulang dengan karakter yang beda.
- **Baris Tema di Pengaturan** cuma bisa ditap di ikon ">" yang kecil. Sekarang seluruh barisnya.

**Fitur baru (permintaan Bryan).** Angka naik-turun: `Bob.lift(t, heat, digit)` di `Stats.kt`. Pelan kalau masih
dikit, makin kenceng dan makin tinggi kalau makin dekat jatah (tabel di README "Lampu dan kedip"). Dipakai di
odometer Beranda (per digit, kayak ombak) dan badge maskot melayang. Tes: `StatsTest.bobGetsFasterWithHeat`.

**Catatan dari data asli.** Ada 32 hitungan TikTok dalam satu menit (20.46). Setelah dicek di log, itu flick
beneran: 17 reel beda dalam ~5 detik, tiap reel judul lagunya beda. Jadi tiap reel tetap dihitung, sesuai aturan
"reel baru". Kalau nanti mau reel yang lewat di bawah ~1 detik nggak dihitung, tempatnya di `SwipeCounter.settle`
(n > 1 dalam satu settle).

**Cara ngetes overlay tanpa ganggu HP yang lagi dipakai.** Cek dulu app yang lagi di depan sebelum ngirim tap:
`adb shell dumpsys activity activities | grep -m1 topResumedActivity`. Posisi maskot melayang bisa dibaca dari
`adb shell dumpsys window windows | grep -m1 ty=ACCESSIBILITY_OVERLAY`, yaitu `mAttrs={(x,y)...`.

## 2026-11-07 · Maskot per app, maskot jalan-jalan, lampu marquee, font baru, Beranda baru

> Status: udah dicek di HP, lihat entri "Dicek di HP + perbaikan" di atas.

**Font.** Pixelify Sans diganti **Jersey 15** (OFL, Google Fonts), soalnya angka 5 di Pixelify kebaca kayak 8.
File `res/font/pixelify.ttf` diganti nama jadi `res/font/jersey.ttf`, dan semua referensinya ikut diganti (`Theme.PixelFont`,
overlay, widget, gambar recap). Ukuran angka di widget dinaikin sedikit karena Jersey lebih ramping.

**Maskot per app.**
- `Prefs.mascotTiktok/mascotShorts/mascotIg` + `mascotFor(app)` / `setMascotFor(app, m)`. Default: TikTok = Tiko,
  Shorts = Telo, IG = Popi. Maskot utama (null) tetap Reelo.
- `Mascot.voice`: kalimat khas tiap karakter ("Kriuk!", "Kami kembali ke siaran!", dst).
- Maskot ini dipakai di:
  - maskot melayang (ganti otomatis pas pindah app, ada animasi "puf" lalu nyapa),
  - Pause Card,
  - tiket di Beranda,
  - header Detail app,
  - baris widget lebar.
- Pengaturan: satu baris pilihan per app (Utama/TikTok/Shorts/IG Reels).

**Maskot melayang (`Overlay.kt`).**
- Jalan-jalan sendiri tiap 6–15 detik kalau nganggur: nyusurin pinggir ke tinggi acak, atau (1 dari 4) lompat
  melengkung ke sisi seberang sambil hadap arah lompat.
- Nggak jalan-jalan kalau lagi tidur, di-drag, ngintip, ngomong, menu kebuka, atau Fokus.
- Tidur setelah 45 detik (dulu 10). Dicolek pas tidur: kaget, lompat tinggi + "!".
- Reaksi tiap hitungan nambah: lompat kecil; heat 2 geleng; heat 3 lompat tinggi.
- Badge angka kedip sesuai heat, dan kedip cepat 1,6 detik tiap nembus kelipatan 10.
- Mood dan angka sekarang per app (jatah app itu), bukan total.
- Menu tahan: **"Salah hitung? Coret 1"**, yaitu hapus 1 hitungan terakhir app yang lagi kebuka (`Db.removeLast`).

**Beranda (`ui/Home.kt`, komponen di `ui/Kit.kt`).**
- Lubang film di kartu rol jadi lampu marquee (`Modifier.marquee`): yang nyala sebanding progres ke jatah, pola
  kedip ikut heat (tabel di README "Lampu dan kedip"). Tiap nembus kelipatan 10: lampu putih lari keliling, angka kedip,
  maskot teriak, haptic.
- Odometer lebih besar (60 sp), ketuk buat muter ulang. Angka berdenyut merah kalau lewat jatah.
- Tiga kartu kecil per app diganti `Ticket`: sobekan kiri isi maskot app (mood per jatah app), kanan angka + bar 10 kotak.
- Screen time + jempol meter digabung jadi satu papan skor dengan ikon pixel (`GLYPHS`).
- **Tantangan hari ini** (`Challenge` di `Stats.kt`):
  - 5 misi, dipilih dari tanggal;
  - status JALAN/BERES/GAGAL;
  - lolos = +10 Rol Film, dicairin di `Repo.refreshGame` buat hari yang udah lewat;
  - konfeti sekali per hari pas BERES.
- Tahan maskot = joget (balik badan + lompat 6x). Colek = kalimat berikutnya.

**Lainnya.**
- Pindah tab: geser kayak frame film (`AnimatedContent`, steps(4)), arahnya ikut urutan tab.
- Insight: batang grafik 7 hari tumbuh pas dibuka dan bisa diketuk (nampilin total + app terbanyak hari itu).
- Lemari: ketuk kostum yang belum kebeli = cobain di maskot besar; beli = konfeti.
- `Stats.heat(n, limit)` jadi satu sumber "seberapa panas" buat lampu, badge, dan tiket.

**Tes.** `StatsTest.challengeStates` (status tantangan + batas heat). Semua tes lama tetap lolos.

**Perlu dicek di HP (belum sempat).**
- Tampilan Beranda Siang/Malam: lampu marquee, tiket (notch bulat pakai warna `bg`, cek di tema Malam yang gradien),
  ukuran font Jersey di semua layar.
- Maskot melayang: jalan-jalan nggak nutupin tombol penting app (batas 40 dp atas, 90 dp bawah), animasi puf pas
  pindah TikTok ↔ IG, menu "Coret 1".
- Widget lebar: ikon maskot 22 dp per baris.

**Sengaja belum dibuat.** Tarik-ke-bawah buat refresh di Beranda (data udah live lewat `Live.bump`, jadi belum perlu),
dan layar debug sidik jari (cukup pakai `adb logcat -s ReeloScroll`).

## 2026-11-07 · Hitungan anti dobel pakai sidik jari reel

**Masalah.** Scroll ke atas lalu balik ke bawah ke reel yang sama kadang nambah hitungan. Penyebabnya, penghitung lama
cuma pakai jarak scroll + posisi terjauh. Posisinya direset tiap ganti package (buka notif shade, keyboard, dialog
izin), dan kalau ada event scroll yang ke-drop, posisinya ikut geser.

**Perubahan.**
- `SwipeCounter.settle(app, fp)` (`Stats.kt`) sekarang nerima sidik jari reel:
  - reel dihitung cuma kalau maju **dan** fp belum pernah kelihatan hari ini;
  - set `seen` per app maksimal 500 entri, dan di-clear tiap ganti hari (jam 04.00);
  - fp null, atau fp sama padahal pindah halaman, balik ke hitungan posisi lama.
- `ReelService.page(app)` membaca teks di halaman pager yang nutupin tengah layar (maksimal 120 node, SeekBar dilewati).
- `Detectors.clean` / `Detectors.fingerprint`: buang angka (termasuk `1,2K`, `3 rb`, `4 jt`) dan kata yang bisa berubah
  sendiri (play/pause, like, follow, subscribe, durasi), lalu urutkan dan hash.
- Ganti package ke `systemui`, keyboard aktif, atau dialog izin nggak lagi dianggap keluar dari app (`TRANSIENT` di `ReelService`).
- Log debug `ReeloScroll` sekarang nampilin `fp=` dan potongan teksnya.

**Tes.**
- `StatsTest.fingerprintStopsRecount`: urutan A, B, C, B, C, D cuma nambah 4.
- `StatsTest.fingerprintIgnoresVolatileText`: tes buat teks yang berubah sendiri.
- Di Xiaomi 14T (Android 15), lihat `adb logcat -s ReeloScroll`:
  - **TikTok:** naik-turun ke reel lama +0, reel baru +1. Halaman LIVE juga kebaca.
  - **Shorts:** fp dari `Go to channel @...`.
  - **IG:** mundur 2 lalu maju 2 = +0, maju ke reel baru = +1.

**Catatan / sisa.**
- Dua video beruntun dari akun yang sama tanpa caption dan dengan lagu yang sama bisa dapat fp sama, jadi yang kedua
  nggak kehitung. Jarang terjadi, dan kalau terjadi hasilnya kurang hitung, bukan lebih.
- Kalau TikTok/YouTube/IG ganti bahasa UI, kata di `VOLATILE` mungkin perlu ditambah.
