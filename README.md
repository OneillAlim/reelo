# Reelo

Android app that counts the reels you watch on TikTok, YouTube Shorts, and Instagram Reels, with a pixel mascot that gets tired the more you scroll.

Design prototype (screens, themes, mascots): https://claude.ai/artifact/Ckkk4GhgeE3skRZW6YnFYm

## Fitur
- Hitung reels per app (AccessibilityService) + screen time, berapa kali dibuka, pemicu unlock (UsageStatsManager)
- Beranda: odometer (ketuk buat muter ulang), rol hari yang bisa digeser, lampu marquee di lubang film,
  tiket per app dengan maskotnya, papan skor, Tantangan hari ini, colek/tahan maskot (joget), konfeti
- Insight: grafik 7 hari (batangnya bisa diketuk), heatmap jam rawan, jempol meter, "setara dengan"
- Kendali: jatah per app (dalam reels), Jam Malam, Mode Tegas, Fokus 25 menit, counter "Satu Lagi"
- Pause Card overlay dengan tombol tahan 2 detik, notifikasi pas jatah tinggal 20%
- Maskot melayang di luar app: ketuk, ketuk 2x (ngumpet), tahan (menu + "Salah hitung? Coret 1"), seret (nempel pinggir),
  buang (sembunyi), jalan-jalan sendiri kalau nganggur, lompat ke sisi seberang, tidur setelah 45 detik,
  ganti karakter pas pindah app, badge angka kedip makin sering kalau makin dekat jatah
- 4 maskot pixel (Reelo, Popi, Tiko, Telo) x 5 mood, tiap app punya maskot sendiri (default TikTok = Tiko,
  Shorts = Telo, IG = Popi, bisa diganti di Pengaturan), kostum dari Rol Film, evolusi, streak + Freeze, 12 lencana
- Recap 7 hari yang bisa dibagikan sebagai gambar
- Widget 2x2, 4x2, dan papan marquee 4x1: latar pixel (Langit yang ikut jam, Bioskop, Tiket; ketuk ◆ buat ganti),
  maskot goyang + bintang/bohlam kelip lewat ViewFlipper, makin dekat jatah makin heboh
- Jaga Api TikTok: kirim pesan otomatis ke teman yang udah punya streak, tiap 00.00 (atau pas HP pertama dibuka).
  Pesan buatan sendiri diacak per teman, ada hitung mundur 5 detik + Tunda, dan riwayat. Selector TikTok ada di
  `TikTokUi` (`TikTokBot.kt`). Melanggar aturan otomasi TikTok, jadi pakai dengan risiko sendiri.
  Jadwalnya lewat AlarmManager (`FlameAlarm.kt`, tetap tepat walau HP tidur). Ada coba ulang kalau gagal, notif
  "buka HP" kalau terkunci, pengingat 20.00/23.00, dan tombol 🔥 di widget buat langsung nyalain.
- Tema Siang / Bioskop Malam / Ikut HP, otomatis gelap saat Jam Malam
- Cek kesehatan (service, izin, optimasi baterai per merek), ekspor CSV, hapus data
- Tanpa izin INTERNET. Semua data cuma ada di HP. Teks di layar reel (nama akun, caption) cuma dipakai buat sidik jari
  anti dobel: diubah jadi hash, teksnya dibuang; hash-nya disimpan sampai jam 04.00 biar restart nggak bikin dobel. (Build debug nge-log potongannya ke logcat buat
  debugging; build release nggak.)

## Lampu dan kedip
Satu skala "panas" (`Stats.heat`) dipakai di Beranda, maskot melayang, dan tiket:

| heat | kondisi | lampu marquee | angka naik-turun (`Bob`) | badge maskot melayang |
|---|---|---|---|---|
| 0 | < 50% jatah | kuning, diam | pelan, 1,6 dtk, 2 dp | merah, diam |
| 1 | 50–79% | kuning, kelip acak | 1,1 dtk, 3 dp | kedip pelan (900 ms) |
| 2 | 80–100% | oranye, nyala selang-seling | 0,7 dtk, 4 dp | kedip oranye (500 ms) |
| 3 | lewat jatah | merah, selang-seling + angka berdenyut merah | kenceng, 0,45 dtk, 5 dp | kedip merah (400 ms) |

Angka naik-turun per digit dengan geser seperempat gelombang, jadi kelihatan kayak ombak. Badge di maskot melayang ikut naik-turun dengan aturan yang sama.

Tiap nembus kelipatan 10 reels: lampu putih lari keliling rol dua putaran, angka kedip, maskot teriak (sekali per
kelipatan per hari, dicatat di `Prefs.milestone`). Kedip paling cepat 2,5 kali per detik, biar aman buat yang sensitif
cahaya. Semua animasi mati kalau **Kurangi animasi** nyala.

## Build
Butuh Android SDK 37 dan JDK 17+ (JDK bawaan Android Studio cukup).

```
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Izin buat testing lewat adb:
```
adb shell appops set app.reelo GET_USAGE_STATS allow
adb shell settings put secure enabled_accessibility_services app.reelo/app.reelo.ReelService
```

## Cara ngitung
Reelo ngitung **reel baru**, bukan jumlah gerakan jempol.
1. Event scroll dari pager reels dijumlah. Pas layar berhenti (350 ms), totalnya dibagi tinggi halaman jadi langkah
   (+1 maju, -1 mundur, 0 kalau geser nggak jadi).
2. Abis itu service baca **sidik jari** reel yang lagi di tengah layar: semua teks/contentDescription di halaman itu
   (username, caption, judul lagu), tanpa angka dan tanpa kata yang bisa berubah sendiri (play/pause, like, follow,
   durasi). Lihat `Detectors.clean`.
3. Reel dihitung cuma kalau langkahnya maju **dan** sidik jarinya belum pernah kelihatan hari ini. Jadi scroll ke
   atas lalu balik ke bawah, buka notif lalu balik, atau keluar-masuk app nggak bikin reel yang sama kehitung lagi.
4. Kalau sidik jari nggak kebaca (null, atau sama persis padahal pindah halaman), balik ke cara lama: cuma posisi
   yang lebih jauh dari yang pernah dicapai.

## Kalau hitungan meleset
Semua aturan "lagi di layar reels" ada di `app/src/main/java/app/reelo/Detectors.kt`.
Build debug nulis tiap event scroll ke logcat. Buka layar reels, swipe, lalu lihat:
```
adb logcat -s ReeloScroll
```
Kalau `reel=false` di layar reels, sesuaikan id/kelasnya di `Detectors.kt`. Tiap swipe harusnya diakhiri `settle +1`
(maju ke reel baru) atau `settle +0` (mundur, nonton ulang, atau geser nggak jadi). Baris `settle` juga nampilin
`fp=` dan potongan teks yang dipakai. Kalau reel beda tapi fp-nya sama terus, berarti teksnya kebuang semua:
cek `VOLATILE` di `Detectors.kt`.

Dites di POCO X5 5G (HyperOS, Android 14), 7 Nov 2026:

| App | View yang di-scroll | Jarak 1 reel |
|---|---|---|
| TikTok (`trill`) | `viewpager` (ViewPager) | 2221 px |
| YouTube Shorts | `reel_recycler` (RecyclerView) | 2130 px |
| Instagram Reels | `clips_viewer_view_pager` (ViewPager) | 2130 px |

Dites ulang di Xiaomi 14T (HyperOS, Android 15, 1220x2712), 7 Nov 2026: id yang sama, jarak 1 reel sekitar
2400 px (TikTok) dan 2285 px (Shorts/IG). Sidik jari kebaca di ketiga app.

Di HyperOS/MIUI, nyalakan **Mulai otomatis** buat Reelo. Kalau nggak, sistem bisa matiin penghitungnya diam-diam.
Beranda bakal nampilin peringatan kalau itu kejadian.

## Jaga Api: kapan jalan
Semua jadwal ada di `FlameAlarm.kt`. Cuma ada 1 alarm terpasang, yaitu waktu terdekat dari `Flame.nextAlarm()`:
- 00.00:30;
- jadwal coba ulang;
- pengingat 20.00 dan 23.00.

| Kejadian | Yang terjadi |
|---|---|
| 00.00, HP kebuka | Langsung jalan |
| 00.00, HP terkunci | Notif "🔥 N api nunggu kamu". Jalan pas HP dibuka (`USER_PRESENT`). Android nggak ngizinin app buka kunci |
| 00.00, lagi telepon / Pause Card muncul | Dicoba lagi 2 menit kemudian |
| Ada yang gagal / TikTok nggak kebuka | Coba ulang +10 dan +20 menit, cuma ke yang belum kebagian. Maksimal 3x, abis itu notif + tombol "Coba lagi" |
| 20.00 / 23.00 masih ada yang belum | Notif pengingat + tombol "Nyalain sekarang" |
| Service Aksesibilitas mati | Notif "Reelo lagi mati" + tombol ke Setelan Aksesibilitas |
| Ketuk 🔥 di widget | Kirim ke yang belum kebagian hari ini; kalau semua udah, cuma toast |

**Izin.**
- Alarm pakai `USE_EXACT_ALARM`, yang otomatis diberi buat app yang di-install sendiri. Kalau app ini masuk Play
  Store, ganti ke `SCHEDULE_EXACT_ALARM`: user harus ngizinin, dan kartu "Siap jaga api?" udah ngecek itu.
- Di HyperOS, baterai Reelo harus **Tanpa batasan** + **Mulai otomatis**.
- Di Xiaomi/POCO/Redmi, Izin lainnya > **Open new windows while running in the background** (op 10021) harus
  **Always allow**. Kalau nggak, TikTok nggak bisa dibuka pas kamu lagi di app lain. Kartu "Siap jaga api?" ngecek ini.
- Waktu ngetes, jangan pakai `uiautomator dump`, karena itu matiin sementara service Aksesibilitas Reelo. Pakai
  screenshot.

**Ngetes tanpa nunggu tengah malam.** Di build debug, layar Jaga Api punya tombol "debug: simulasi 00.00 (1 menit
lagi)". Cek alarm yang kepasang:
```
adb shell dumpsys alarm | grep -A3 app.reelo
```
