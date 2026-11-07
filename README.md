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
- Recap 7 hari yang bisa dibagikan sebagai gambar, widget 2x2 dan 4x2
- Tema Siang / Bioskop Malam / Ikut HP, otomatis gelap saat Jam Malam
- Cek kesehatan (service, izin, optimasi baterai per merek), ekspor CSV, hapus data
- Tanpa izin INTERNET. Semua data cuma ada di HP.

## Lampu dan kedip
Satu skala "panas" (`Stats.heat`) dipakai di Beranda, maskot melayang, dan tiket:

| heat | kondisi | lampu marquee | badge maskot melayang |
|---|---|---|---|
| 0 | < 50% jatah | kuning, diam | merah, diam |
| 1 | 50–79% | kuning, kelip acak | kedip pelan (900 ms) |
| 2 | 80–100% | oranye, nyala selang-seling | kedip oranye (500 ms) |
| 3 | lewat jatah | merah, selang-seling + angka berdenyut merah | kedip merah (400 ms) |

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
