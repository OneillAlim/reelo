# Changelog

Catatan tiap update, biar siapa pun bisa lanjut dari sini. Yang paling baru di atas.
Tiap entri: apa yang berubah, kenapa, file yang disentuh, cara ngetes, dan sisa kerjaan.

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
