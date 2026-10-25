# Plan MVP AppFin

Tanggal: 23 Oktober 2026. Status: implementasi MVP dan revamp UI tersedia pada source. Build/test sebelumnya berlaku untuk versi sebelum revamp; revamp terbaru belum dibuild/ditest sesuai instruksi pengguna.

## Progres implementasi

- Setup Kotlin/Compose, repository abstraction, Preferences DataStore, service dan overlay: diimplementasikan.
- Single-app otomatisasi: pengguna mengonfirmasi berhasil di perangkat pada 23 Oktober 2026; gate untuk melanjutkan batch terpenuhi.
- Batch, deduplikasi, pengecualian, timeout, Cancel dan laporan per aplikasi: diimplementasikan.
- Revamp UI utama menjadi tiga tab dan halaman Pengaturan, item list ringkas serta preferensi tampilan tersimpan: diimplementasikan pada source, belum build/test.
- Build debug, unit test dan lint: dijalankan; lihat README dan laporan di folder build untuk perintah serta hasil.
- Kompatibilitas semua OEM/versi Android belum diklaim. Bahasa/perangkat lain memerlukan pengujian tambahan.

## Target

Aplikasi Android untuk force stop user app melalui otomatisasi Settings, tanpa root dan tanpa Shizuku. Gunakan Kotlin dan Jetpack Compose dengan UI sederhana. Prioritas pertama adalah membuktikan satu aplikasi dapat dihentikan pada perangkat target ketika accessibility overlay tampil, kemudian memperluas ke antrean.

Dasar rancangan: [knowledge](../KNOWLEDGE.md) dan [studi source Killapps](killapps-study.md).

## Pendekatan otomatisasi yang disetujui

- **Jalur dasar umum:** buka App Info target melalui intent `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` dengan URI `package:<target>`, lalu temukan tombol melalui elemen Accessibility, termasuk resource ID jika tersedia dan teks tombol. Fondasi ini tidak dikunci ke satu merek HP.
- **Fallback bahasa MVP:** dukung teks **Paksa berhenti** dan **Force stop**, dengan normalisasi kapitalisasi serta spasi. Pencarian tetap dibatasi pada halaman App Info target yang tervalidasi; teks saja tidak cukup untuk mengizinkan klik.
- Validasi dialog force stop sebelum mengeklik tombol konfirmasi positif. Jika halaman, tombol, atau dialog tidak dapat dikenali secara andal, akhiri target dengan hasil gagal/belum terverifikasi, tanpa menebak klik.
- Aturan OEM khusus ditambahkan kemudian jika pengujian menunjukkan jalur umum gagal. Pendekatan umum bukan jaminan kompatibilitas seluruh perangkat; perangkat yang tersedia menjadi titik awal validasi.

## Scope versi pertama

- Status Accessibility Service dan tombol aktivasi berada di halaman Pengaturan, dengan penjelasan fungsi izin.
- Daftar aplikasi dengan pencarian, refresh, serta tab Active / Terhenti / Dikecualikan. Item ringkas: ikon, nama, package, ikon info dan menu ⋮ untuk Force stop serta pengecualian.
- Ikon info per item membuka App Info bawaan melalui ACTION_APPLICATION_DETAILS_SETTINGS dengan URI package target. Bisa digunakan tanpa Accessibility, dinonaktifkan selama sesi berjalan, dan memicu refresh setelah kembali dari Settings.
- Tombol Hentikan semua hanya di tab Active; batch hanya user app yang tidak stopped dan tidak dikecualikan, terlepas dari pencarian.
- Preferensi Tampilkan aplikasi sistem berada di Pengaturan, default nonaktif, tersimpan melalui SettingsRepository. Preferensi Tampilkan package name juga di Pengaturan, default aktif.
- Tab Active berisi aplikasi belum stopped yang tidak dikecualikan; Terhenti berisi stopped yang tidak dikecualikan; Dikecualikan berisi seluruh pengecualian. Pengecualian diprioritaskan agar kategori tidak tumpang tindih. Active tidak berarti proses pasti sedang running; penjelasannya di Pengaturan.
- Pengecualian tersimpan, dapat diubah dari setiap item. AppFin dan Settings selalu dikecualikan oleh engine.
- Konfirmasi sebelum batch, menampilkan jumlah target dan dampak pada notifikasi/background work.
- Accessibility overlay sederhana: nama aplikasi, jumlah target diproses, progress bar, Batalkan.
- Ringkasan sesi dan hasil per aplikasi: berhasil terverifikasi, sudah berhenti, dilewati, timeout, gagal, belum terverifikasi, atau dibatalkan.

Ditunda: multi-select, batch aplikasi sistem, clear recents, background-kill tambahan, RAM/CPU dashboard, jadwal otomatis, animasi/blur, dan desain visual khusus. Tidak perlu backend, akun, atau database untuk MVP.

Daftar aplikasi tidak diberi label "sedang berjalan" karena data aplikasi terpasang bukan bukti proses aktif. Pencarian hanya memfilter tampilan; aksi batch tetap menggunakan seluruh aplikasi yang memenuhi syarat, dengan jumlah target eksplisit pada konfirmasi.

## UI minimum

Satu Activity dengan dua halaman Compose Material 3; navigasi Back dan state pencarian/tab dipertahankan:

1. App bar utama: AppFin, refresh dan gear Pengaturan.
2. Kolom pencarian dan tiga tab: Active / Terhenti / Dikecualikan.
3. Item list ringkas, dengan ikon info serta menu ⋮; checkbox per-item dan filter pada halaman utama dihapus.
4. Tombol bawah Hentikan semua hanya pada tab Active. Hasil sesi dibuka melalui tombol terpisah; ringkasan detail berada di dialog hasil.
5. Halaman Pengaturan: status dan aktivasi Accessibility, preferensi aplikasi sistem serta package name. Kedua preferensi tersimpan di DataStore melalui kontrak repository.
6. Jika Accessibility belum tersambung, aksi stop mengarahkan pengguna ke Pengaturan. Perubahan pengecualian memindahkan baris ke tab yang sesuai setelah penyimpanan berhasil.

Saat sesi berjalan, blokir start sesi kedua. Overlay memiliki tombol Batalkan yang tetap dapat dijangkau. Jika layar utama kembali dibuka, tampilkan state sesi yang sama dari sumber state bersama.

Overlay versi awal boleh memakai layout Android View sederhana yang dipasang melalui WindowManager dari service; layar aplikasi tetap Jetpack Compose. Ini mengurangi setup lifecycle ComposeView pada service untuk MVP. Tampilan overlay dapat dipindahkan ke Compose kemudian jika diperlukan.

## Arsitektur

Gunakan satu modul `app`, pemisahan package berdasarkan tanggung jawab, dan dependency injection manual. Hindari singleton engine yang menyimpan Activity atau listener UI jangka panjang.

| Komponen | Tanggung jawab |
| --- | --- |
| `AppRepository` | Kontrak pembacaan aplikasi terpasang, tanpa mengekspos PackageManager atau ApplicationInfo kepada konsumen. |
| `PackageManagerAppRepository` | Implementasi AppRepository: membaca aplikasi di dispatcher IO, memetakan status stopped/sistem, dan menerapkan pilihan visibilitas aplikasi sistem. |
| `ExclusionRepository` | Kontrak untuk mengamati dan mengubah pengecualian; tidak mengekspos tipe DataStore atau Room. |
| `DataStoreExclusionRepository` | Implementasi awal ExclusionRepository menggunakan Preferences DataStore. |
| `SettingsRepository` | Kontrak pengaturan aplikasi yang dibutuhkan; implementasi awal DataStoreSettingsRepository menggunakan Preferences DataStore. |
| `MainViewModel` | Menggabungkan daftar, pencarian, pengecualian, koneksi service, dan state sesi untuk Compose. |
| `SessionCoordinator` | Menjadi jalur start/cancel serta sumber StateFlow status koneksi dan sesi; tidak menyimpan Activity. |
| `AppKillerService` | Memiliki lifecycle engine dan overlay, menerima event, memilih window Settings, dan membersihkan sesi ketika terputus. |
| `ForceStopEngine` | Mengelola satu sesi aktif, snapshot antrean, transisi state, timeout dan hasil per target. |
| `SettingsAutomation` | Membuka App Info, memilih node yang tervalidasi, mengeklik force stop/konfirmasi, dan mengamati hasil. |
| `ProgressOverlay` | Attach/detach TYPE_ACCESSIBILITY_OVERLAY, menampilkan progres dan meneruskan Cancel. |

State dan efek otomatisasi diserialisasi pada main thread; pembacaan daftar/preferences dilakukan di luar main thread. Engine memakai scope milik service dan pekerjaan per sesi yang dapat dibatalkan. Tidak menggunakan WorkManager untuk menjalankan otomatisasi interaktif ini.

### Abstraksi penyimpanan yang disetujui

ViewModel dan komponen core hanya menerima kontrak repository lewat constructor injection. Detail Preferences DataStore berada di implementasi data; tidak ada Preferences.Key, DataStore, DAO, atau entity Room dalam kontrak konsumen.

Kesepakatan ini berlaku untuk seluruh akses data, bukan hanya pengecualian: `AppRepository` membungkus PackageManager, `ExclusionRepository` membungkus penyimpanan pengecualian, dan `SettingsRepository` membungkus pengaturan yang dibutuhkan. ViewModel dan engine tidak mengakses PackageManager, DataStore atau DAO secara langsung. Setiap kontrak mengikuti tanggung jawabnya; tidak memakai satu repository generik untuk semua data.

Jika fitur riwayat ditambahkan nanti, gunakan `SessionHistoryRepository` dengan implementasi Room. Kontrak riwayat dan penyimpanannya belum perlu dibuat pada MVP yang hanya menyimpan hasil sesi aktif di memori.

Kontrak minimum untuk pengecualian:

```kotlin
interface ExclusionRepository {
    val excludedPackages: Flow<Set<String>>
    suspend fun setExcluded(packageName: String, excluded: Boolean)
}
```

- Gunakan `DataStoreExclusionRepository` pada MVP dan satu instance DataStore per file pada proses aplikasi.
- Implementasi melakukan perubahan set secara atomik. Error baca/tulis diteruskan sebagai kegagalan yang dapat ditangani; jangan mengganti kegagalan baca dengan set kosong karena dapat menghilangkan perlindungan pengecualian saat batch.
- Snapshot antrean dibuat setelah pengecualian berhasil dimuat. Kegagalan memuat pengecualian mencegah start batch dan ditampilkan ke pengguna.
- Pengecualian wajib AppFin dan Settings tetap ditegakkan engine, terpisah dari preferensi pengguna.
- Jika ada pengaturan lain, buat kontrak `SettingsRepository` dengan model pengaturan sesuai kebutuhan; tidak perlu generic CRUD/base repository atau interface tambahan sebelum dipakai.
- Jika nanti beralih ke Room, implementasikan kontrak yang sama melalui `RoomExclusionRepository` dan ganti wiring di app container. ViewModel dan aturan engine tetap menggunakan kontrak yang sama.
- Pergantian backend tetap memerlukan migrasi data lama: salin pengecualian DataStore ke Room secara idempoten dan pastikan migrasi sukses sebelum memakai Room. Abstraksi tidak otomatis memindahkan data pengguna.
- Gunakan fake/in-memory repository untuk test konsumen, serta contract test implementasi untuk observasi nilai dan update pengecualian yang konsisten.

## Aturan engine

Alur: `Idle → OpeningAppInfo → FindingForceStop → AwaitingConfirmation → VerifyingResult → Next → Finished`, dengan jalur failure/cancellation pada setiap tahap.

- Buat snapshot antrean, deduplikasi, lalu terapkan pengecualian pada engine, bukan hanya UI.
- Cek kembali ketersediaan target sebelum membuka Settings. Aplikasi terhapus dilaporkan unavailable; tidak dihitung already stopped.
- Setiap sesi dan target mempunyai identitas; callback/timeout lama tidak boleh memajukan target baru.
- Identitas sesi saja tidak membuktikan asal event Android. Validasi ulang package/window, halaman App Info target dan dialog sebelum tiap aksi; hentikan target sebagai unverified bila konteks tidak dapat dipastikan.
- Pilih root window Settings secara eksplisit agar node overlay tidak dianggap halaman target.
- Terapkan jalur dasar umum dan fallback **Paksa berhenti / Force stop** yang disetujui di atas, lalu validasi selector pada perangkat yang tersedia. Resource internal Settings hanya bantuan, bukan kontrak stabil.
- Aktifkan pelaporan view ID jika memakai ID selector. Jangan memakai daftar ID tombol generik tanpa validasi fungsi tombol.
- Periksa hasil ACTION_CLICK. Nilai true hanya menunjukkan aksi diterima, bukan bukti force stop selesai.
- Gunakan deadline per tahap yang dapat disetel; nilai awal diuji di spike perangkat, bukan menyalin delay referensi secara buta.
- Timeout tidak dihitung sukses. Tombol disabled tidak otomatis berarti aplikasi sudah berhenti.
- Baca ulang sinyal stopped atau bukti UI yang tervalidasi. Bila bukti tidak cukup, hasilnya unverified.
- Cancel menghentikan aksi selanjutnya dan antrean tersisa; tidak dapat membatalkan force stop yang sudah terjadi. Lepaskan overlay dan terbitkan hasil terminal tepat sekali.
- Sesi tidak dilanjutkan otomatis setelah process death. Jika proses mati saat berlangsung, sesi dianggap terputus; penyimpanan riwayat lintas proses di luar MVP.
- Jika overlay gagal dipasang, hentikan sesi dan tampilkan error. Semua jalur terminal melakukan cleanup yang idempoten.

## Tahapan pelaksanaan

### 1. Setup dan spike satu aplikasi

- Buat proyek Kotlin, Compose Material 3, ViewModel, coroutine/StateFlow, dan DataStore; pilih versi stable yang kompatibel saat implementasi.
- Tentukan application ID, minimum SDK dan target SDK setelah memeriksa lingkungan serta perangkat target.
- Daftarkan service dan konfigurasi event/window retrieval.
- Buat layar sementara untuk status service dan satu target pengujian.
- Buktikan buka App Info → klik Force stop → konfirmasi → observasi hasil.
- Pasang overlay minimal dan ulangi pembuktian dengan overlay aktif serta Cancel.

**Selesai jika:** satu target nonkritis berhasil dihentikan berulang pada perangkat yang dicatat, hasil tidak positif palsu, dan window Settings tetap dapat diakses saat overlay tampil. Jangan memperluas batch sebelum ini terbukti.

### 2. Engine antrean dan lifecycle

- Pisahkan state machine dari Android selector melalui interface agar dapat diuji dengan fake adapter.
- Tambahkan antrean, exclusion enforcement, deduplikasi, deadline, identitas sesi/target dan hasil per aplikasi.
- Hubungkan start/cancel serta StateFlow, tangani service disconnect dan cleanup overlay.

**Selesai jika:** antrean kecil selesai atau berhenti dengan hasil tepat; target bermasalah tidak membuat proses macet; sesi lama tidak mengganggu sesi baru.

### 3. UI Compose fungsional

- Tambahkan daftar user app, pencarian, refresh, toggle pengecualian dan persistensinya.
- Definisikan kontrak `ExclusionRepository`, implementasi DataStore, dan wiring manual; konsumen hanya mengetahui interface tersebut.
- Hubungkan Force stop per item dan batch dengan engine yang sama.
- Tambahkan konfirmasi jumlah target, progres dan ringkasan hasil.
- Tampilkan aksesibilitas tidak aktif, daftar kosong dan kegagalan baca aplikasi secara jelas.

**Selesai jika:** semua flow utama bisa dipakai dari UI tanpa bantuan debug, dengan desain Material 3 standar.

### 4. Validasi MVP dan APK debug

- Unit test terarah untuk state machine: klik gagal, timeout, open Settings gagal, target terhapus, event basi, exclusion, cancel, start ganda, disconnect dan cleanup.
- Uji perangkat untuk single/batch, bahasa Indonesia/Inggris, tombol disabled, overlay attach/cancel cepat, serta kembali ke aplikasi selama sesi berjalan.
- Jalankan build dan pemeriksaan yang relevan, hasilkan APK debug, dan catat matriks kompatibilitas yang benar-benar diuji.

**Selesai jika:** tidak ada aksi klik lanjutan setelah cancel/disconnect, tidak ada overlay tertinggal, dan setiap target memiliki hasil yang tidak menyesatkan. Kompatibilitas hanya diklaim untuk perangkat/versi yang diuji.

## Hal yang perlu dipastikan sebelum implementasi

- Merek/model perangkat, versi Android, bahasa Settings, dan ketersediaannya untuk uji langsung. Emulator AOSP membantu, tetapi tidak menggantikan uji OEM target.
- Distribusi awal diasumsikan APK debug untuk penggunaan/pengujian pribadi. Bila target Google Play, evaluasi kebijakan Accessibility dan package visibility sebelum menentukan rilis.
- Strategi package visibility dipilih agar memenuhi kebutuhan daftar user app; jangan menyalin QUERY_ALL_PACKAGES dan semua izin dari referensi tanpa evaluasi.
- Gunakan accessibility overlay melalui service context; jangan jadikan izin draw over apps sebagai syarat tambahan secara otomatis.

Tidak ada estimasi waktu tetap sebelum spike perangkat, karena variasi UI Settings merupakan ketidakpastian utama.
