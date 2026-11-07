# Changelog

Catatan tiap update, biar siapa pun bisa lanjut dari sini. Yang paling baru di atas.
Tiap entri: apa yang berubah, kenapa, file yang disentuh, cara ngetes, dan sisa kerjaan.

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
