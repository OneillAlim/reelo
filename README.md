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

## Cara ngitung
Reelo ngitung **reel baru**, bukan jumlah gerakan jempol. Event scroll dari pager reels dijumlah, terus pas layar
berhenti, totalnya dibagi tinggi halaman. Yang dihitung cuma posisi yang lebih jauh dari yang pernah dicapai,
jadi geser setengah lalu balik, swipe mundur, dan nonton ulang nggak nambah hitungan.

## Kalau hitungan meleset
Semua aturan "lagi di layar reels" ada di `app/src/main/java/app/reelo/Detectors.kt`.
Build debug nulis tiap event scroll ke logcat. Buka layar reels, swipe, lalu lihat:
```
adb logcat -s ReeloScroll
```
Kalau `reel=false` di layar reels, sesuaikan id/kelasnya di `Detectors.kt`. Tiap swipe harusnya diakhiri `settle +1`
(maju ke reel baru) atau `settle +0` (mundur, nonton ulang, atau geser nggak jadi).

Dites di POCO X5 5G (HyperOS, Android 14), 7 Nov 2026:

| App | View yang di-scroll | Jarak 1 reel |
|---|---|---|
| TikTok (`trill`) | `viewpager` (ViewPager) | 2221 px |
| YouTube Shorts | `reel_recycler` (RecyclerView) | 2130 px |
| Instagram Reels | `clips_viewer_view_pager` (ViewPager) | 2130 px |

Di HyperOS/MIUI, nyalakan **Mulai otomatis** buat Reelo. Kalau nggak, sistem bisa matiin penghitungnya diam-diam.
Beranda bakal nampilin peringatan kalau itu kejadian.
