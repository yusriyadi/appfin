# Studi Killapps untuk AppFin

Tanggal: 23 Oktober 2026. Scope: analisis statis core automation dan lifecycle overlay; UI aplikasi ditunda sesuai instruksi pengguna. Tidak ada kode aplikasi yang diimplementasikan atau dijalankan pada perangkat.

## Sumber dan versi

- Repository: https://github.com/DestroyerDarkNess/Killapps
- Commit yang dibaca: `1ed8587be396f80fea9b098cfa8c2de32c6e1ef5`.
- Semua lokasi source di bawah relatif terhadap `src/app/src/main/`.
- Lisensi repository: MIT, copyright 2026 Destroyer. Jika nanti menyalin kode atau bagian substansial, pertahankan pemberitahuan copyright dan lisensinya.

Analisis mengikuti source, bukan klaim README. Deskripsi seperti overlay selalu di atas seluruh system UI atau optimasi performa belum dibuktikan melalui pengujian.

## 1. AppKillerService

[Source](https://github.com/DestroyerDarkNess/Killapps/blob/1ed8587be396f80fea9b098cfa8c2de32c6e1ef5/src/app/src/main/java/com/killapps/AppKillerService.java)

- Mewarisi `AccessibilityService`, menyimpan instance statis saat `onServiceConnected()` (baris 47–52).
- Ketika engine berjalan, mengambil `event.getSource()`, dengan fallback `getRootInActiveWindow()`, lalu meneruskan event dan node ke engine (54–64).
- Source event dapat berupa subtree, bukan seluruh window. Untuk AppFin, pemilihan root window Settings perlu eksplisit, terutama ketika overlay menjadi window aktif.
- Menyediakan global action Back dan Home (87–99).
- Memiliki fungsi tambahan membuka Recents, mencari Clear all/Close all, mengeklik, lalu Home (114–267). Ini bukan mekanisme force stop utama dan tidak perlu masuk MVP AppFin.
- `onInterrupt()` hanya mencatat log. `onDestroy()` hanya menghapus instance statis; keduanya tidak membersihkan antrean engine atau overlay (67–77).

Konfigurasi `res/xml/accessibility_config.xml` berlangganan window-state/content changes, mengaktifkan pembacaan konten dan interactive windows, dengan notification timeout 100 ms. Tidak ada pembatasan package. Tidak ada `flagReportViewIds`, walaupun engine memakai pencarian view ID.

## 2. ForceStopEngine

[Source](https://github.com/DestroyerDarkNess/Killapps/blob/1ed8587be396f80fea9b098cfa8c2de32c6e1ef5/src/app/src/main/java/com/killapps/ForceStopEngine.java)

Engine singleton berbasis `Handler` pada main looper. Alur state aktual:

```text
IDLE → OPENING_SETTINGS → WAITING_FORCE_STOP → WAITING_CONFIRM
                                             ↓
                          IDLE / aplikasi berikutnya
```

`OPENING_SETTINGS` juga bisa langsung menuju `WAITING_CONFIRM` bila tombol segera ditemukan.

1. `start()` menyalin antrean, menghapus package sendiri dan Settings, menampilkan overlay, lalu menjadwalkan proses pertama setelah 300 ms (88–122).
2. `processNextApp()` melewati package yang memiliki `FLAG_STOPPED`; package tidak ditemukan juga dianggap stopped (145–216, 552–561).
3. Membuka `ACTION_APPLICATION_DETAILS_SETTINGS` dengan URI `package:<target>`, flag New task, Clear task, dan No animation (221–234).
4. Timeout halaman Settings: 4 detik. Jika tidak ditemukan tombol, target dilewati.
5. Deteksi tombol berurutan: cache teks yang berhasil → string `force_stop` dari resource Settings → enam fallback bahasa. Pencarian memakai API text search, lalu rekursi tree, dengan node atau parent langsung yang clickable (281–324, 478–547).
6. Klik force stop, pindah ke `WAITING_CONFIRM`, dan pasang timeout konfirmasi 3 detik (329–379).
7. Deteksi konfirmasi berdasarkan daftar view ID, kemudian teks OK/Aceptar/Accept atau teks force stop yang tersimpan. Setelah klik, counter ditambah dan target berikutnya dijadwalkan setelah 300 ms (384–438).
8. Setelah antrean habis, masih ada fase `killBackgroundProcesses()` untuk seluruh aplikasi terpasang, kemudian Clear recents. Kedua fase ini terpisah dari otomatisasi force stop pilihan pengguna (148–184, 450–473).

Fallback teks belum mencakup bahasa Indonesia. Membaca resource Settings dapat membantu, tetapi nama resource internal dan struktur UI OEM belum dijamin sama. Cabang Xiaomi/Huawei/Samsung dalam source adalah heuristik, bukan bukti kompatibilitas perangkat.

`getInstalledUserApps()` menyaring aplikasi sistem, package sendiri, dan `FLAG_STOPPED`. Hasilnya adalah aplikasi terpasang yang lolos filter, bukan daftar proses yang terbukti sedang berjalan (588–602).

## 3. ProgressOverlay

[Source](https://github.com/DestroyerDarkNess/Killapps/blob/1ed8587be396f80fea9b098cfa8c2de32c6e1ef5/src/app/src/main/java/com/killapps/ProgressOverlay.java)

- Menerima context; memilih `TYPE_ACCESSIBILITY_OVERLAY` hanya jika context merupakan `AccessibilityService`. Jika tidak, fallback ke application overlay/phone (58–67).
- Engine memprioritaskan context service, tetapi bisa fallback ke context pemanggil jika service tidak tersedia (engine 114–117).
- Memasang window full-screen dengan `FLAG_WATCH_OUTSIDE_TOUCH`, `FLAG_LAYOUT_IN_SCREEN`, `FLAG_BLUR_BEHIND`, format translucent, dan blur radius pada Android 12+ (69–81).
- `addView`, `removeView`, dan update progres dipost ke main handler (83–123).
- Tombol Cancel langsung memanggil singleton engine `stop()` (49–52).
- Overlay hanya menampilkan progres, counter, nama aplikasi, dan Cancel. Tidak ada pengukuran RAM/CPU dalam kelas ini.

MainActivity masih mensyaratkan `Settings.canDrawOverlays()` sebelum mulai (MainActivity 212–226), walaupun jalur normal memakai accessibility overlay. Untuk AppFin, gunakan service context secara konsisten dan evaluasi kebutuhan izin berdasarkan tipe window yang benar-benar digunakan.

## Temuan yang perlu diperbaiki sebelum adopsi

| Dampak | Lokasi | Pemicu dan konsekuensi | Arah perbaikan AppFin |
| --- | --- | --- | --- |
| Tinggi | Engine 329–348, 359–370, 397–438 | Hasil `performAction()` diabaikan; timeout konfirmasi juga menambah closed count. Klik gagal atau dialog tidak muncul tetap dapat dilaporkan berhasil. | Bedakan hasil klik, timeout, dan hasil force stop terverifikasi; jangan menghitung timeout sebagai sukses. |
| Tinggi | Engine 60–65, 384–424; Service 54–64 | Daftar konfirmasi mencakup `button2` dan action ID generik tanpa validasi dialog/package. Saat kandidat yang salah tersedia, engine dapat mengeklik aksi selain konfirmasi lalu menghitung sukses. | Validasi window, package Settings, dialog force stop dan target; hanya pilih aksi positif yang diketahui. |
| Tinggi | Engine 240–258; service config | Event dari package mana pun diterima; tidak ada validasi identitas halaman target. Event lama/overlay dapat diproses pada state target baru. | Filter window/package dan kaitkan event serta timeout dengan session ID dan target aktif. |
| Tinggi | Engine 204–215, 229–232 | Bila membuka Settings melempar exception, next app tetap dijadwalkan, sementara pemanggil juga memasang timeout target lama. Timeout yang tertinggal dapat menaikkan indeks dan melompati target berikutnya. | Satu jalur penyelesaian per target; batalkan seluruh callback target sebelum transisi. |
| Sedang | Service 67–77; Engine 127–140 | Saat service mati, engine tidak diberhentikan. Cancel hanya menghapus timeout terakhir dan tidak mengirim terminal callback. Pemanggil tidak menerima hasil cancelled dan callback tertunda masih tersisa. | Service memiliki lifecycle engine; cleanup idempoten, batalkan semua pekerjaan, terbitkan hasil terminal tepat sekali. |
| Sedang | Engine 160–174 | `mRunning=false` sebelum Clear recents selesai memungkinkan sesi baru mulai; callback sesi lama masih mengakses overlay/listener global yang bisa sudah milik sesi baru. | Selesaikan cleanup sebelum menerima sesi baru, atau isolasi resource dan callback per sesi. |
| Sedang | ProgressOverlay 58–105 | `show()` menjadwalkan addView, sedangkan `hide()` langsung keluar jika `mShown=false`. Show lalu hide sebelum queue diproses dapat meninggalkan overlay terpasang. | Serialisasi attach/detach pada main thread dengan state desired visibility dan cleanup lifecycle. |
| Sedang | accessibility_config.xml; Engine 397–398 | Engine mengandalkan view ID tanpa mengaktifkan `flagReportViewIds`. Jalur deteksi berdasarkan ID belum dikonfigurasi lengkap. | Aktifkan pelaporan view ID, tetap sediakan fallback terkontrol. |
| Sedang | Engine 450–473 | Fase background-kill menyasar seluruh package, melampaui daftar pilihan. Jumlah panggilan API tidak membuktikan jumlah aplikasi berhenti. | Hilangkan fase ini dari MVP; pertahankan scope antrean pengguna. |

Dokumentasi [FLAG_REPORT_VIEW_IDS](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REPORT_VIEW_IDS) menjelaskan kebutuhan opt-in pelaporan ID. Dokumentasi [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)) membatasi background-process kill pada package sendiri di Android 14+, sehingga fase tambahan itu bukan solusi force stop aplikasi lain.

Dokumentasi [TYPE_ACCESSIBILITY_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY) mendukung window milik accessibility service tanpa mengubah kemampuan service menginspeksi window yang tertutup. Ini tidak membuktikan semua transisi dan lapisan system UI akan selalu tertutup pada setiap perangkat.

## Rekomendasi rancangan AppFin

Pertahankan pembagian tiga tanggung jawab, dengan core yang dapat diuji tanpa UI produk:

- **AppKillerService:** lifecycle owner, pemilihan window Settings, adapter event dan global action. Ketika service terputus, hentikan sesi dan lepas overlay.
- **ForceStopEngine:** satu sesi aktif, antrean immutable/deduplicated, pengecualian AppFin dan Settings, state eksplisit, timeout per tahap, cancellation, serta hasil per aplikasi.
- **SettingsAutomation:** adapter terpisah untuk selector resource ID/teks/OEM dan validasi halaman serta dialog. Jangan menyimpan node lintas event.
- **ProgressOverlay:** adapter presentasi untuk state progres dan event Cancel; desain visual ditunda. Kegagalan attach harus dilaporkan ke engine agar proses tidak berjalan tertutup tanpa kontrol pengguna.

State yang disarankan: `Idle → OpeningAppInfo → FindingForceStop → AwaitingConfirmation → VerifyingResult → Next → Finished`, dengan jalur `Cancelled` dan `Failed` pada setiap tahap. Hasil per target dapat berupa `StoppedVerified`, `AlreadyStopped`, `Unavailable`, `TimedOut`, `Failed`, `Unverified`, atau `Cancelled`; jangan menyamakan tombol disabled dengan pasti already stopped.

Verifikasi hasil perlu memakai sinyal yang tersedia pada perangkat, misalnya stopped flag yang dibaca ulang atau perubahan halaman App Info yang tervalidasi. Jika tidak ada sinyal andal, laporkan unverified. Sukses mengirim klik tidak cukup.

## Verifikasi berikutnya saat implementasi

- Uji state machine: timeout tidak sukses, klik gagal, open-settings gagal, event basi, deduplikasi, cancel lalu start baru, dan service disconnect.
- Uji overlay: attach lalu cancel cepat, detach berulang, kegagalan addView, dan pemilihan root Settings saat overlay tampil.
- Uji perangkat: bahasa Indonesia/Inggris, OEM dan versi Android target, dialog berbeda, tombol disabled, serta package terhapus ketika antrean berjalan.
- Merek/model dan versi Android perangkat target masih belum diketahui.

Belum dilakukan build, test otomatis, atau uji perangkat. Temuan di atas berasal dari pembacaan source pada commit yang dicatat.
