# Changelog

Catatan tiap update, biar siapa pun bisa lanjut dari sini. Yang paling baru di atas.
Tiap entri: apa yang berubah, kenapa, file yang disentuh, cara ngetes, dan sisa kerjaan.

## 2026-11-16 · Hemat baterai: maskot tidur pas layar mati + repo jadi public dengan Release v1.0

**Kenapa.** Bryan minta APK yang dibagiin publik nggak boros baterai, dan repo dibuka buat umum + ada link download.

**Perubahan.**
- **`ReelService.kt`:** receiver `unlock` sekarang juga dengerin `ACTION_SCREEN_OFF`.
  - Layar mati: `float.hide()`, jadi timer jalan-jalan maskot (tiap 6-15 dtk), animasi, sama `updateViewLayout`
    berhenti. Sebelumnya semua itu tetap jalan walau layar mati.
  - `USER_PRESENT` (HP dibuka): `flameMaybe()` kayak dulu, ditambah `refreshFloat()` biar maskot muncul lagi.
  - `refreshFloat()` cek `PowerManager.isInteractive`, jadi maskot nggak ke-attach lagi pas layar mati (misal ada
    reel ke-settle atau prefs berubah).
- Repo `OneillAlim/reelo` jadi **public**, Steven (`stevenwidinata-sys`) diundang ulang (write).
- APK release (debug key, minify, tanpa izin INTERNET) diupload ke GitHub Release `v1.0`.

**Cara ngetes.** Nyalain pembatas reels + maskot, buka TikTok, matiin layar 5 menit, nyalain lagi dan buka kunci:
maskot muncul lagi. Cek baterai: Setelan > Baterai > Reelo, bandingin pemakaian sehari sebelum/sesudah.

**Belum dites di HP.** Build release sukses, tapi belum dicoba di POCO X5 / Xiaomi 14T.

**Sisa.** Kalau masih boros: coba `notificationTimeout` > 0 di `reel_service.xml` (hati-hati, event scroll bisa
digabung dan hitungan `scrollDeltaY` meleset) atau batasi `packageNames` (tapi deteksi pindah app jadi ilang).

## 2026-11-09 (sore) · Jaga Api jadi fitur utama: Beranda + widget fokus ke api, pembatas reels opsional

**Kenapa.** Bryan: orang pakai app ini buat nyalain streak; pembatas reels cuma sampingan. Permintaannya:
- Beranda fokus ke Jaga Api;
- widget nampilin kalau ada api yang belum aman / lupa, dan bisa diketuk buat nyalain;
- maskot (dan semua fitur pembatas) baru muncul kalau pembatas reels dinyalain.

**Perubahan.**
- **`Prefs.limitsOn` default `false`.** Pembatas reels (maskot melayang, jatah, Jam Malam, Pause Card) jadi opsional.
- **Beranda (`ui/Home.kt`):**
  - Bagian utama sekarang `ApiHero`.
    - Kartu film berisi status ("11 api aman" / "3 api belum aman" / "Jaga Api mati" / "Pindai Inbox dulu").
    - Tombol **Nyalain sekarang** = `flameNow(again = false)`, sama kayak tombol widget.
    - Daftar teman hari ini, yang belum aman di atas (merah "belum", yang aman ✓).
  - Bagian reels (odometer, tiket, tantangan, recap, pill "hari waras") cuma tampil kalau pembatas nyala. Kalau mati,
    ada satu kartu toggle "Pembatas reels (opsional)".
  - `ApiCard` lama dihapus.
- **Kendali:** kartu Jaga Api pindah ke paling atas.
- **Widget (`Widget.kt`, `WidgetArt.flame`):** angka besar = api.
  - Ada yang belum aman: angka merah, tulisan "api belum aman · ketuk buat nyalain", maskot lebih heboh. Setelah
    20.00 makin heboh ("Sisa X jam!").
  - Ketuk widget di mana aja = langsung nyalain (aksi `NOW`). Kalau udah aman, ketuk = buka app.
  - Wide tanpa pembatas: panel kanan isinya "Aman 11/12" + bar, "Jalan sendiri 00.00" / "Ketuk = nyalain sekarang",
    dan "Ingetin 20.00 · 23.00" / "Coba ulang HH.MM".
  - Info reels cuma muncul kalau pembatas nyala.
  - Pil 🔥 kecil (`w_flame`) dihapus dari layout, karena udah jadi angka utama.
  - Ganti `limitsOn` langsung update widget.
- **`Db.flameTargets(all)`:** satu sumber "siapa yang dijagain" buat Beranda, widget, dan pengingat. `flamePending`
  dihitung dari sini.

**Bug yang ketemu pas ngetes dan dibenerin.**
- **Abis ngirim, angka api ditebak +1.** Teman Tambahan yang belum ngestreak jadi punya api palsu 🔥1 dan pindah ke
  "API NYALA". Sekarang angka cuma dari yang kebaca di Inbox.
- **Pindai berhenti di halaman pertama** kalau TikTok nolak `SCROLL_FORWARD` sekali (hasil: 6 dari 11), terus
  `saveScan` **nolkan semua yang nggak kebaca**. Sekarang:
  - pindai pakai `scrollDown()` 3x kayak sapuan;
  - kalau list masih bisa turun tapi scroll ditolak, hasilnya dianggap sebagian dan yang nggak kebaca nggak dinolkan
    (`saveScan(found, complete)`).

**Dites di HP (POCO X5).**
- Beranda dan Kendali tampil sesuai.
- Ketiga widget nampilin "api aman" dan "api belum aman".
- Ketuk widget waktu ada Tambahan yang belum aman: TikTok kebuka, sapuan lanjut sampai halaman 6 buat nyari teman
  Tambahan, kirim, balik ke layar utama, notif "Api 1 teman aman 🔥".
- Buka kunci HP lalu Jaga Api langsung jalan: lolos (15.53).

**Belum.**
- Perbaikan pindai belum dites ulang di HP. Layarnya keburu dipakai Bryan (auto-rotate nyala lagi).
- Data teman di HP masih hasil pindai kepotong (6). Pindai ulang sekali buat ngebenerin.

## 2026-11-09 · Jaga Api dites ujung ke ujung di POCO X5, 6 bug dibenerin, daftar Tambahan, pembatas reels bisa dimatiin

**Kenapa.** Bryan minta Jaga Api dites di HP-nya dari awal (pindai Inbox sampai kirim) dan semua edge case dicek. Dia
juga minta:
- pembatas reels bisa dimatiin kalau cuma mau pakai Jaga Api;
- daftar tambahan: kirim ke orang yang belum/nggak ngestreak, biar streak mulai atau nyala lagi.

HP: POCO X5 5G (HyperOS, Android 14), TikTok `trill` versi lebih baru dari survei 7 Nov.

**Bug yang ketemu pas dites dan dibenerin.**
1. **Run otomatis bikin kartu kosong nyangkut dan ngeblok semua run berikutnya.** Di `flameMaybe`, `FlameCard`
   dibikin di dalam `.also { }` dan `flameRun` dipanggil sebelum `flameCard` ke-assign.
   - Akibatnya `flameRun` bikin kartu kedua, dan kartu pertama (kosong) nyangkut di layar.
   - `flameCard != null` terus, jadi coba ulang, tombol widget, dan run besok kebaca "lagi jalan".
   - Bug ini udah ada sejak jalur otomatis dibikin; nggak ketahuan karena tes sebelumnya lewat "Kirim sekarang".
   - Fix: assign dulu, baru jalan.
2. **Pindai kelewat teman (9 dari 11).** Angka api di Inbox dimuat setelah nama. Baris yang kebaca pas angkanya belum
   ada tercatat 0, dan `putIfAbsent` nggak pernah memperbaruinya.
   - Fix: `TikTokBot.settled()` baca ulang tiap 300 ms sampai dua kali baca sama (maks 2,5 dtk).
   - Hasil digabung pakai angka terbesar.
   - Sapuan kirim juga pakai `settled()`.
   - Dites 3x berturut-turut: konsisten 11, cocok dengan Inbox.
3. **Sapuan bisa nolkan api teman.** Fitur "daftar ikut segar" (8 Nov) nimpa angka jadi 0 kalau sekali kebaca sebelum
   dimuat. Contoh: TEMAN turun 11 ke 10 setelah sapuan kering. Fix:
   - selama sapuan angka cuma boleh naik;
   - di akhir sapuan yang selesai normal, yang **selalu** 0 dan kebaca minimal 2x baru di-nolkan.
4. **MIUI ngeblok buka app dari latar.** Log: `MIUILOG- Permission Denied Activity`. Izin MIUI op 10021 ("Open new
   windows while running in the background") default **Deny**.
   - Kalau kamu lagi di layar utama, lolos. Kalau lagi di app lain, TikTok nggak kebuka.
   - Fix:
     - kartu "Siap jaga api?" ngecek op ini di HP Xiaomi (`FlameAlarm.miuiPopup`, refleksi `checkOpNoThrow`);
     - tombolnya langsung ke halaman izin MIUI (`openMiuiPerms`);
     - `TikTokBot.leave()` ngecek 1,5 dtk kemudian, dan kalau masih di TikTok, ke layar utama.
   - Dites: setelah Always allow, simulasi 00.00 dari dalam Settings buka TikTok dan balik ke Settings.
5. **`ReelService.running` bisa salah "mati".** `onDestroy` service lama bisa datang setelah service baru nyambung.
   Sekarang `running` diturunkan dari `inst`.
   - Catatan buat yang ngetes: `uiautomator dump` matiin sementara semua service Aksesibilitas lain. Selama ngetes Reelo,
     baca layar pakai screenshot aja.
6. **Deteksi api padam buta di TikTok baru.** Id subjudul ganti `mux` ke `mw8`. Sekarang teks
   "Restore your N-day Streak" dicari di semua TextView baris itu; `TikTokUi.isSub` dihapus.

**Perbaikan kecil.**
- Toggle punya label buat pembaca layar (`semantics` + `toggleable`).
- Teks peringatan "cuma yang dicentang" dibenerin.
- Window `android` (pilihan "Complete action using") masuk `TRANSIENT`, jadi maskot nggak nongol di atas Reelo.
- Robot balikin Inbox ke atas sebelum keluar.
- Ringkasan riwayat dihitung per orang: yang gagal lalu kekirim di coba ulang nggak dihitung gagal.
- Service nyambung lagi: hapus notif "Reelo lagi mati", lalu langsung `flameMaybe()` buat ngejar.
- Tombol debug "pencet tombol 🔥 widget" ngirim PendingIntent yang sama dengan widget.

**Fitur baru.**
- **Pembatas reels** (`Prefs.limitsOn`, kartu paling atas di Kendali). Kalau dimatiin:
  - nggak ada Pause Card, notif jatah, Jam Malam, Mode Tegas, Fokus, dan maskot melayang;
  - reels tetap dihitung buat statistik;
  - kartu-kartu pembatas di Kendali disembunyiin.
- **Daftar Tambahan** di layar Teman.
  - Pindai sekarang nyimpen semua chat Inbox, bukan cuma yang berapi.
  - "+ Tambah" milih dari chat. Api padam punya tombol "Kirim lagi".
  - Teman yang dicentang (`on`) tetap dikirimin walau angka apinya 0.
  - `sweep(extra = …)`: selama masih ada Tambahan yang belum ketemu, sapuan lanjut sampai bawah Inbox.
  - `flameWant` balikin `Triple`. `Db.flamePending` ikut ngitung Tambahan.

**Hasil tes di HP (9 Nov).**

| Tes | Hasil |
|---|---|
| Pindai | 11 teman berapi, 8 halaman, ±14 dtk, 3x konsisten |
| Pesan kosong | Ditolak |
| Simulasi 00.00 (HP di layar utama) | Alarm tepat 14.32.05; 11 disapu ±4 dtk/orang; 10 ok + 1 "nggak kelihatan terkirim"; notif nyebut jam coba ulang |
| Coba ulang | Bunyi tepat 14.42.57; cuma ngirim ke 1 yang gagal; alarm berikutnya pindah ke 20.00 |
| Update APK di tengah jadwal | Alarm coba ulang kepasang lagi dari prefs |
| Aksesibilitas dimatiin | Notif "Reelo lagi mati"; nyala lagi = notif hilang + langsung ngejar |
| Tombol 🔥 widget (semua udah aman) | Toast, TikTok nggak dibuka |
| Simulasi dari app lain (setelah izin MIUI) | Lolos, balik ke app itu |
| HP terkunci | Notif "🔥 Api nunggu kamu" |
| Pembatas reels mati | Maskot di TikTok 2 window jadi 0 |

**Belum dites.**
- Buka kunci, lalu langsung jalan. Butuh pola kunci Bryan.
- Kirim ke daftar Tambahan. Nunggu Bryan milih orangnya, karena itu pesan beneran ke orang baru.
- Tombol pulihkan api padam. Akun ini nggak punya api padam, jadi layar chat-nya belum bisa disurvei.
- Naruh widget beneran. Launcher POCO nggak nampilin widget Reelo di daftarnya setelah beberapa kali install ulang
  lewat adb. Mungkin perlu restart HP.

## 2026-11-08 · Jaga Api: jadwal 00.00 yang konsisten, notif pengingat, tombol 🔥 di widget

**Kenapa.** Bryan nanya apakah auto kirim tiap 00.00 udah jalan, dan minta:
- jangan sampai lupa streak (ada notif pengingat);
- widget bisa diketuk buat langsung nyalain;
- semuanya konsisten.

Pas dicek, ada 4 lubang:
1. **Timer 00.00 pakai `Handler.postDelayed`.** Jamnya (`uptimeMillis`) berhenti waktu HP tidur, jadi timer yang
   dipasang pagi bisa baru bunyi jam 05.00. Kalau HP udah kebuka sebelum 00.00 dan dipakai lewat tengah malam, nggak
   ada yang memicu.
2. **Satu teman gagal, hari tetap ditandai selesai.** TikTok gagal kebuka juga nggak dicoba ulang.
3. **Nggak ada pengingat** kalau HP terkunci semalaman atau service dimatiin HyperOS.
4. **Widget nggak tahu soal api.**

**Perubahan.**
- **`FlameAlarm.kt` (baru).**
  - Isinya `BroadcastReceiver` non-exported dengan aksi `TICK` (alarm) dan `NOW` (widget + tombol notif).
  - `schedule()` masang satu alarm `setExactAndAllowWhileIdle(RTC_WAKEUP)` ke `Flame.nextAlarm()`. Cadangannya
    `setAndAllowWhileIdle` kalau alarm tepat nggak diizinkan.
  - Dipasang ulang tiap alarm bunyi, tiap service nyambung, tiap app dibuka (`MainActivity.onResume`), tiap
    `flameOn`/`flameAll` berubah, dan tiap selesai jalan.
  - **Tick, kalau belum beres hari ini:**
    - service mati: notif ke Setelan Aksesibilitas;
    - HP terkunci atau layar mati: notif "🔥 N api nunggu kamu";
    - selain itu: `flameMaybe()`.
  - **Tick, kalau udah beres tapi masih ada yang belum** (jam ≥ 20): notif pengingat + tombol "Nyalain sekarang".
    Tombolnya `setAuthenticationRequired`, jadi minta buka kunci dulu.
  - Channel notif baru **"Jaga Api"** (`flame`, prioritas tinggi). Hasil kirim juga pindah ke channel ini.
- **`ReelService`:**
  - `armMidnight`/Handler `midnight` dihapus. `USER_PRESENT` tetap ada.
  - Lagi telepon atau Pause Card nongol: alarm 2 menit lagi (`flameLater`), bukan dilewati.
  - "Tunda 1 jam" sekarang juga masang alarm.
- **Coba ulang (`flameAfter`):**
  - Hari ditandai beres (`flameLastRun`) cuma kalau nol gagal, atau kamu yang pencet Batal.
  - Gagal (termasuk TikTok/Inbox nggak kebuka): `flameTries` naik, coba lagi +10 lalu +20 menit
    (`Flame.retryAt`). Sasarannya cuma yang belum `sentOk`.
  - Setelah 3x: hari ditutup, notif "Masih ada yang gagal" + tombol **Coba lagi**.
  - Notif hasil nyebut jam coba ulangnya.
- **`flameNow(only, again)`:**
  - widget/notif pakai `again = false`, jadi nggak ngirim dobel;
  - hasil `2` = semua udah aman (cuma toast);
  - `-1` juga kalau kartu Jaga Api lagi tampil.
- **Daftar teman ikut segar waktu sapu.** `TikTokBot.sweep(saw = …)` ngelaporin angka api tiap baris yang kebaca.
  Teman yang apinya padam sejak pindai terakhir jadi 0, jadi angka di widget nggak nyangkut.
- **Widget:**
  - pil 🔥 (`w_flame`) di small/wide (pojok kiri atas) dan marquee (sebelah "HARI INI");
  - isinya jumlah yang belum aman, "aman", atau "off" (`WidgetArt.flame`, api pixel `Flame.ICON` yang dipindah dari
    `ui/Api.kt`);
  - ketuk = aksi `NOW`. Kalau Jaga Api mati, ketuk = buka app.
- **`Db.flamePending(day, all)`:** teman berapi yang belum kebagian hari ini.
- **Layar Jaga Api:**
  - Kartu **"Siap jaga api?"** ngecek: Aksesibilitas, notif, baterai Tanpa batasan, alarm tepat waktu, TikTok
    terpasang, udah dipindai, ada pesan aktif. Yang merah bisa diketuk ke setelannya. Kalau semua hijau, cuma satu
    baris.
  - Status nampilin jam coba ulang.
  - Kartu riwayat nampilin jam mulai.
  - Tombol debug diganti **"simulasi 00.00 (1 menit lagi)"**.
- **Manifest:**
  - `USE_EXACT_ALARM` (otomatis diberi buat sideload; kalau ke Play Store ganti `SCHEDULE_EXACT_ALARM`);
  - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
  - receiver `FlameAlarm`.
- `Settings.Health` jadi public biar dipakai ulang. `Flame.untilMidnight` dihapus karena udah nggak dipakai.

**Tes.**
- `flameAlarmPicksNearest`: 00.00:30, 20.00, 23.00, coba ulang lebih dekat, coba ulang yang udah lewat diabaikan.
- `flameRetryStopsAtThree`.
- `./gradlew testDebugUnitTest assembleDebug` hijau.

**Belum dites di HP** (HP lagi dipakai Bryan). Urutan tes di `README.md` bagian "Jaga Api: kapan jalan":
- simulasi 00.00 dengan HP kebuka;
- simulasi 00.00 dengan HP terkunci, terus dibuka;
- `dumpsys alarm` setelah layar mati;
- tombol widget dan tombol notif;
- service dimatiin.

Perlu dilihat juga apakah HyperOS ngeblok TikTok kebuka dari latar (izin "Tampilkan jendela pop-up saat berjalan di
latar belakang"). Kalau iya, kartu siap perlu cek itu juga.

## 2026-11-07 · Jaga Api: kirim 👉 ke semua dites di HP, grup chat dibenerin

**Hasil di HP Bryan.** 23 dari 24 teman berapi dapat 👉, termasuk grup chat. Kecepatan mode sapu sekitar **3 detik per
teman** (7 teman dalam 20 detik). Satu teman tercatat "Pesan nggak kelihatan terkirim"; mungkin cuma cek-nya yang
gagal, jadi perlu dicek manual di chat.

**Bug yang ketemu dan dibenerin.**
- **Grup chat bikin robot nyangkut.** Di grup, tiap pesan nampilin nama pengirim pakai id yang sama (`user_name`),
  jadi layar grup kebaca kayak Inbox. Robot nggak balik, terus "mentok" di daftar pesan grup. Sekarang
  `TikTokBot.isInbox()` ngecek ada tab bawah "Inbox" (`TikTokUi.isTab`) **dan** ada nama chat. Dipakai di `inbox()`,
  `backTo()`, dan tiap putaran `sweep`.
- **Inbox dibaca pas masih transisi** abis balik dari chat, jadi dianggap kosong dan berhenti. Sekarang nunggu sampai
  Inbox + daftarnya kegambar (maks 3 detik), baru dibaca.
- **Scroll ditolak sekali langsung dianggap habis.** Sekarang dicoba 3x, pakai `SCROLL_FORWARD` lalu `SCROLL_DOWN`.
- **Tombol kirim kadang nggak muncul.** Cadangannya: kolom diklik + fokus + isi ulang, terus `ACTION_IME_ENTER`
  (Android 11+).
- **Cek terkirim terlalu ketat buat emoji.** Sekarang dianggap kekirim kalau pesannya muncul **atau** kolom ketiknya
  kosong lagi setelah tombol kirim dipencet.
- **Kirim manual ngelewatin semua orang** kalau hari itu udah dikirimin pesan lain. Sekarang "Kirim sekarang" cuma
  ngelewatin yang udah dapat **pesan yang sama** hari ini (`Db.sentOkWith`). Run otomatis tetap maksimal sekali sehari.
  Kartu konfirmasi nampilin jumlah yang bakal dikirim + yang dilewati.
- **Lebih cepat:**
  - sapuan berhenti setelah 2 halaman tanpa api (chat streak selalu ngumpul di atas);
  - putaran kedua cuma jalan kalau masih ada yang kelewat.

  (Perbaikan ini belum diukur ulang, biar nggak ngirim dobel.)
- **Log debug** `ReeloBot` per halaman: `rows / streak / target / handled`, buat ngecek kalau ada yang kelewat.

## 2026-11-07 · Jaga Api mode sapu: cepat, langsung ke semua yang ngestreak

**Kenapa.** Kirim pertama Bryan jalan (22/22 terkirim) tapi makan 2 menit 43 detik, sekitar 7 detik per orang, karena
tiap teman dicari ulang dari atas Inbox. Bryan mau langsung ke semua orang yang ngestreak, pesannya cukup emoji
nunjuk 👉, dan nggak pakai lama.

**Perubahan.**
- `TikTokBot.sweep()` ganti `send()`: Inbox disapu **sekali** dari atas ke bawah, dan tiap baris yang punya api, belum
  kebagian, dan lolos `want` langsung dikirim. Chat yang barusan dikirim naik ke atas Inbox, makanya tiap kali daftar
  dibaca ulang dan dipilih baris pertama yang belum kebagian. Berhenti kalau 2x scroll nggak nambah nama baru.
- `chat()`:
  - nggak pakai `ACTION_FOCUS` (keyboard nggak kebuka, jadi balik ke Inbox cukup sekali BACK);
  - polling layar tiap 150 ms (dulu 300);
  - jeda tetap antar teman dihapus.

  Perkiraan 2–3 detik per teman (belum diukur, lihat di bawah).
- **Mode "Semua yang ngestreak"** (`Prefs.flameAll`, default nyala): tiap baris Inbox yang ada apinya ikut, termasuk api
  baru yang belum pernah dipindai. Kalau dimatiin, balik ke centang manual. Teman yang ketemu pas nyapu dicatat ke
  tabel `friend` (`Db.touchFriend`) dan angka apinya di-update.
- **Langsung jalan tanpa hitung mundur** (`Prefs.flameCountdown`, default mati). Kartu progres + Batal tetap ada.
  Tombol debug tetap pakai hitung mundur.
- Chip 👉 ditaruh paling depan di layar Pesan.

**Belum dicek di HP.** Kecepatan mode sapu belum diukur, karena semua teman udah kebagian pesan hari ini (aturan
maksimal 1 pesan per teman per hari) dan ngetes lagi berarti ngirim ulang ke 22 orang. Run otomatis pertama: malam ini
00.00, atau pas HP pertama dibuka setelahnya. Hasilnya cek di Riwayat; logcat `ReeloBot` nampilin
`sweep: N dikirim, M halaman`.

**Insiden pas ngetes (biar nggak keulang).** Waktu survei tombol Share, tap koordinat buta di TikTok nyasar karena
TikTok kebuka di Inbox, bukan feed, dan sesuatu kekirim ke satu akun ("Sent to …"). Pelajarannya: jangan tap koordinat
di app chat. Cek isi layar dulu (screenshot/dump), dan kalau bisa pakai aksi ke node.

## 2026-11-07 · Jaga Api: pindai dites di HP + perbaikan

**Dicek di Xiaomi 14T.** Pindai Inbox jalan sekitar 15 detik (8 halaman, 60 chat) dan hasilnya cocok dengan Inbox:
- 22 api nyala, angkanya sama (termasuk streak grup);
- 4 api padam muncul di bagian "pulihkan di TikTok".

**Perbaikan.**
- **TikTok dikira nggak ter-install.** Android 11+ nyembunyiin app lain dari `getLaunchIntentForPackage` kecuali
  dideklarasikan. Ditambah `<queries>` buat `com.ss.android.ugc.trill` dan `com.zhiliaoapp.musically` di manifest.
- **Teman hasil pindai langsung dicentang semua.** Sekarang teman baru masuk dengan `on = 0`, jadi kamu yang milih.
- **Abis pindai/kirim, Reelo balik ke Kendali.** Activity dibuat ulang pas balik dari TikTok, jadi layar yang lagi
  dibuka (`Sub`) hilang. Sekarang disimpan lewat `rememberSaveable` sebagai teks (`keyOf`/`subOf` di
  `MainActivity.kt`), dan pindah layar lewat `go(...)`.
- **Log debug robot:** `adb logcat -s ReeloBot` nampilin langkah dan jumlah baris per halaman. Nama teman dan isi pesan
  nggak pernah ditulis.

**Belum.** Kirim pesan (Tes 1 teman) belum dicoba, karena nunggu Bryan milih teman buat dites.

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
