# Reelo

Android app that counts the reels you watch on TikTok, YouTube Shorts, and Instagram Reels, with a pixel mascot that gets tired the more you scroll.

Design prototype (screens, themes, mascots): https://claude.ai/artifact/Ckkk4GhgeE3skRZW6YnFYm

## Fitur
- Hitung reels per app (AccessibilityService) + screen time, berapa kali dibuka, pemicu unlock (UsageStatsManager)
- Beranda dengan odometer dan rol hari yang bisa digeser, empty state, colek maskot
- Insight: grafik 7 hari, heatmap jam rawan, jempol meter, "setara dengan"
- Kendali: jatah per app (dalam reels), Jam Malam, Mode Tegas, Fokus 25 menit, counter "Satu Lagi"
- Pause Card overlay dengan tombol tahan 2 detik, notifikasi pas jatah tinggal 20%
- Maskot melayang di luar app: ketuk, ketuk 2x (ngumpet), tahan (menu), seret (nempel pinggir), buang (sembunyi), tidur kalau didiamkan
- 4 maskot pixel (Reelo, Popi, Tiko, Telo) x 5 mood, kostum dari Rol Film, evolusi, streak + Freeze, 12 lencana
- Recap 7 hari yang bisa dibagikan sebagai gambar, widget 2x2 dan 4x2
- Tema Siang / Bioskop Malam / Ikut HP, otomatis gelap saat Jam Malam
- Cek kesehatan (service, izin, optimasi baterai per merek), ekspor CSV, hapus data
- Tanpa izin INTERNET. Semua data cuma ada di HP.

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

## Kalau hitungan meleset
Semua aturan "lagi di layar reels" ada di `app/src/main/java/app/reelo/Detectors.kt`.
Kalau TikTok/YouTube/Instagram update dan hitungannya jadi 0 atau dobel, buka layar reels-nya lalu jalankan
`adb shell uiautomator dump` dan cari `resource-id` dari view yang di-scroll, terus sesuaikan file itu.

Yang sudah dicek di emulator: YouTube Shorts (`reel_*`). TikTok dan Instagram belum, karena butuh HP asli yang login.
