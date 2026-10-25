# Knowledge AppFin

Dicatat pada 23 Oktober 2026 dari percakapan yang dilampirkan pengguna.
Dokumen ini menyimpan konteks, keputusan dan perkembangan implementasi. Status verifikasi dicatat terpisah dari perubahan source yang belum dibuild/ditest.

## Tujuan

Membuat aplikasi Android sederhana yang menampilkan daftar aplikasi terpasang dan menyediakan aksi force stop per aplikasi, kemudian mendukung force stop semua user app.

## Keputusan yang sudah disetujui

- Verifikasi rename 25 Oktober 2026: `:app:assembleDebug` berhasil dengan applicationId APK `com.tellolabs.appfin`. Instalasi ke Realme RMX1971 (`f7fd5060`) berhasil. Unit test dilewati sesuai permintaan pengguna.

- Pada 25 Oktober 2026 package utama diganti menjadi `com.tellolabs.appfin` (applicationId, namespace, source/test/debug, konfigurasi Accessibility, dan smoke script). Package aplikasi fixture tetap `dev.appfin.fixture` dan `dev.appfin.fixture.second`. Perubahan rename belum dibuild/diinstall sesuai instruksi pengguna. Android akan memperlakukan package baru sebagai aplikasi terpisah dari instalasi lama, dengan data dan aktivasi Accessibility tersendiri.

- Verifikasi 25 Oktober 2026 setelah pengguna meminta build/install: `:app:assembleDebug`, `:app:testDebugUnitTest`, dan `:app:lintDebug` berhasil; 25 test lulus, lint 0 error/17 warning. APK terbaru berhasil diinstall ke Realme RMX1971 (Android 11, serial `f7fd5060`) dan MainActivity dibuka dengan status `ok`. Otomatisasi force stop dan tampilan launcher belum diuji pada perangkat ini.

- Nama aplikasi tetap **AppFin**. Pada 25 Oktober 2026 pengguna memilih konsep logo ketiga: dua balok putih/lavender pada latar ungu. Diterapkan sebagai launcher vector/adaptive icon dan monochrome; referensi di `docs/branding/`. Belum dibuild atau diuji di perangkat.

- Pengguna meminta ikon info pada setiap item daftar untuk membuka App Info bawaan Android. Implementasi memakai intent package target dan memperbarui daftar setelah kembali. Source ditambahkan tanpa build/test sesuai instruksi sebelumnya.

- Revamp UI yang disetujui: tab **Active · Terhenti · Dikecualikan**, dengan pengecualian diprioritaskan agar tidak ada item yang muncul di dua kategori. Active berarti belum stopped, bukan bukti proses running.
- Gear membuka halaman Pengaturan. Status/aktivasi Accessibility dan preferensi tampilan dipindahkan ke sana. Aplikasi sistem default tidak ditampilkan; package name default ditampilkan. Kedua pengaturan disimpan lewat SettingsRepository/DataStore.
- Item list ringkas memakai ikon info dan menu ⋮ untuk Force stop atau mengubah pengecualian. Hentikan semua hanya tampil di tab Active; batch tetap hanya user app non-stopped yang tidak dikecualikan.
- Checkbox filter stopped dan excluded digantikan oleh tab. Checkbox aplikasi sistem dan switch package name di halaman utama dihapus; fungsi keduanya dipertahankan di Pengaturan. Instruksi tidak menjalankan build/test masih berlaku.

- Penyimpanan MVP menggunakan Preferences DataStore di balik kontrak repository. ViewModel dan engine tidak bergantung langsung pada DataStore; implementasinya dapat diganti ke Room melalui wiring dependensi, disertai migrasi data jika dibutuhkan. Detail kontrak ada di [plan MVP](docs/mvp-plan.md).
- Abstraksi berlaku pada seluruh akses data sesuai tanggung jawab: AppRepository untuk aplikasi terpasang, ExclusionRepository untuk pengecualian, dan SettingsRepository untuk pengaturan yang dibutuhkan. Jika riwayat ditambahkan, gunakan SessionHistoryRepository dengan Room. Tidak menggunakan repository generik untuk semua data.
- Pada 23 Oktober 2026 pengguna mengonfirmasi otomatisasi force stop satu aplikasi berhasil dan meminta melanjutkan tahap berikutnya. Implementasi batch dilanjutkan setelah verifikasi ini.

- Tidak menggunakan root.
- Tidak menggunakan Shizuku.
- Otomatisasi halaman App Info di Settings menggunakan Accessibility Service.
- Menggunakan `TYPE_ACCESSIBILITY_OVERLAY` untuk menampilkan loader selama otomatisasi berjalan. Pengguna secara eksplisit menyetujui pilihan ini.
- Pengguna tetap harus mengaktifkan Accessibility secara manual; izin draw over apps biasa tidak menggantikan akses tersebut.
- Pengguna memilih Kotlin + Jetpack Compose dengan UI sederhana dan prioritas fungsional. Rencana implementasi tersedia di [docs/mvp-plan.md](docs/mvp-plan.md).
- Pengguna menyetujui jalur dasar umum: membuka App Info melalui intent Android, lalu mencari tombol berdasarkan elemen Accessibility dan teks, dengan fallback **Paksa berhenti / Force stop**. Validasi halaman/dialog tetap wajib sebelum klik; penyesuaian OEM ditambahkan bila diperlukan berdasarkan pengujian.

## Rancangan MVP

Scope berikut diusulkan dalam percakapan dan masih dapat diperinci saat implementasi:

- Daftar user app berisi ikon, nama, package name, dan pencarian.
- Tombol **Force stop** per aplikasi.
- Aksi **Force stop all** untuk user app.
- Daftar pengecualian; AppFin sendiri otomatis dilewati.
- Overlay loader berisi aplikasi yang sedang diproses, progres, dan tombol **Batalkan**.
- Timeout per aplikasi serta laporan berhasil/gagal agar antrean tidak macet.

Pemilihan beberapa aplikasi juga sempat diusulkan sebagai pengembangan, tetapi belum ditetapkan untuk MVP.

## Alur yang direncanakan

1. Pengguna mengaktifkan Accessibility Service milik AppFin.
2. Pengguna memilih aksi force stop per aplikasi atau semua user app.
3. Service menampilkan accessibility overlay dengan progres dan kontrol pembatalan.
4. Service membuka App Info aplikasi target di Settings.
5. Service mencoba menemukan dan mengeklik **Paksa berhenti / Force stop**, lalu mengonfirmasi dialog melalui elemen UI.
6. Proses dilanjutkan ke aplikasi berikutnya, sesuai pengecualian dan timeout.
7. Overlay ditutup dan hasil ditampilkan setelah antrean selesai atau dibatalkan.

## Batasan dan hal yang perlu dibuktikan

- Ini adalah otomatisasi UI Settings; bukan akses langsung ke API force stop aplikasi lain.
- Keberhasilan membaca elemen Settings dan mengeklik tombol saat overlay aktif harus diuji di perangkat target.
- Perbedaan merek, versi Android, bahasa sistem, dan tampilan Settings dapat memerlukan penyesuaian pencarian elemen UI.
- Loader belum dijamin menutupi seluruh transisi Settings secara mulus pada semua perangkat.
- Aplikasi yang di-force stop dapat berhenti menerima notifikasi dan menjalankan pekerjaan background sampai dibuka kembali.
- Tanpa Accessibility, fallback yang dibahas adalah membuka App Info dan meminta pengguna menekan Force stop secara manual.

## Usulan teknis yang belum dikonfirmasi

- Perangkat yang sudah diamati: Infinix X6895 Android 16 bahasa Indonesia dan Samsung SM-A546E Android 16 bahasa Inggris. Pengguna melaporkan hasil Berhenti terverifikasi di Samsung; struktur dialog dicatat di docs/samsung-dialog-inspection.md. Revamp UI terbaru belum diuji pada perangkat tersebut.
- Konteks lampiran awal belum berisi implementasi; sejak itu MVP telah diimplementasikan. Panduan build, arsitektur dan pengujian ada di [README.md](README.md).

## Referensi dari percakapan

Studi lanjutan source Killapps tersedia di [docs/killapps-study.md](docs/killapps-study.md). Fokusnya `AppKillerService`, `ForceStopEngine`, dan lifecycle `ProgressOverlay`; UI aplikasi ditunda sesuai instruksi pengguna. Studi ini memuat alur aktual, temuan risiko, serta rancangan core AppFin yang disarankan, bukan keputusan implementasi final.

Referensi berikut disalin dari percakapan sumber dan belum diverifikasi ulang saat penyimpanan knowledge:

- [Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service)
- [AccessibilityWindowInfo dan accessibility overlay](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo)
- [Batasan untrusted touch events pada Android 12](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events)
- [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager)
- [Shizuku setup](https://shizuku.rikka.app/guide/setup/) — hanya konteks opsi yang tidak dipilih.
