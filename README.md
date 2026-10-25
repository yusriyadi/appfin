# AppFin

MVP Android untuk force stop aplikasi melalui Accessibility Service, tanpa root dan tanpa Shizuku. UI menggunakan Kotlin, Jetpack Compose, dan Material 3.

## Fitur

- Halaman utama ringkas: pencarian, refresh, tab **Active · Terhenti · Dikecualikan**, dan ikon gear Pengaturan.
- **Active**: belum berstatus stopped dan tidak dikecualikan. **Terhenti**: stopped dan tidak dikecualikan. **Dikecualikan**: semua aplikasi dalam pengecualian, termasuk yang stopped. Setiap aplikasi masuk tepat satu tab.
- Item list berisi ikon, nama, package name, ikon info, dan menu ⋮ untuk Force stop atau mengubah pengecualian.
- Ikon info membuka App Info bawaan Android tanpa memerlukan Accessibility. Daftar diperbarui setelah kembali dari Settings.
- Tombol Hentikan semua hanya pada tab Active. Batch tetap hanya user app yang tidak stopped dan tidak dikecualikan.
- Halaman Pengaturan memuat status/aktivasi Accessibility serta preferensi aplikasi sistem (default nonaktif) dan package name (default aktif), disimpan melalui SettingsRepository/DataStore.
- Accessibility overlay berisi progres dan Batalkan; hasil per aplikasi tersedia lewat tombol Hasil sesi.

Active bukan bukti proses sedang running. Penjelasan ini tersedia di Pengaturan. Pencarian hanya memfilter tampilan; batch tetap menggunakan seluruh target yang memenuhi syarat. AppFin dan Settings selalu dilindungi oleh engine. Jika akses Accessibility belum aktif, aksi menghentikan aplikasi mengarahkan pengguna ke Pengaturan.

Revamp UI terbaru hanya diperbarui pada source; build/test belum dijalankan sesuai instruksi pengguna. APK dan laporan build lama tidak mencerminkan perubahan UI ini.

## Build dan menjalankan

Gunakan JDK 17 dan Android SDK platform 36. Minimum Android 8 (API 26); target API 36. Versi dependency dipatok di version catalog agar build dapat direproduksi dengan toolchain yang telah diuji.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Buka AppFin, baca penjelasan akses, lalu aktifkan layanan **AppFin — Force stop** melalui Settings → Accessibility. Tidak membutuhkan izin draw over apps terpisah. Setelah kembali ke AppFin, status harus **Accessibility aktif**.

APK berada di `app/build/outputs/apk/debug/app-debug.apk`. Aplikasi ini belum disiapkan untuk distribusi Google Play. Izin QUERY_ALL_PACKAGES dipakai untuk cakupan seluruh user app, termasuk yang tidak mempunyai launcher entry; pengecualian lint hanya diterapkan pada izin ini dengan alasan tersebut.

## Arsitektur

Satu modul produksi `app`, dengan wiring manual di `AppContainer`:

| Kontrak/komponen | Implementasi dan tugas |
| --- | --- |
| AppRepository | PackageManagerAppRepository: daftar aplikasi, ikon dan stopped flag. |
| ExclusionRepository | DataStoreExclusionRepository: package yang dikecualikan. |
| SettingsRepository | DataStoreSettingsRepository: pengaturan tampilan. |
| MainViewModel | State UI, error, refresh dan perintah sesi. |
| SessionCoordinator | Koneksi service dan StateFlow sesi bersama. |
| AppKillerService | Lifecycle engine, event Accessibility dan overlay. |
| ForceStopEngine | Antrean deduplikasi, state per tahap, timeout, hasil, cancellation. |
| SettingsAutomation | Pemilihan window Settings, validasi target/dialog, klik dan cleanup target. |
| ProgressOverlay | Window TYPE_ACCESSIBILITY_OVERLAY milik service. |

ViewModel dan engine tidak mengakses DataStore/DAO/PackageManager secara langsung. Penggantian penyimpanan cukup melalui implementasi repository serta wiring; migrasi data pengguna tetap perlu dikerjakan tersendiri.

Perintah engine dan operasi window diserialisasi pada main thread. Satu coroutine memiliki satu sesi; timeout tidak berupa callback yang bisa memajukan antrean baru. Event Accessibility membangunkan loop, tetapi setiap aksi membaca ulang tree; polling terbatas menjadi fallback jika event terlewat. Node tidak disimpan lintas langkah.

## Pemeriksaan

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Test mencakup hasil terverifikasi, klik gagal, timeout, pengecualian/protected packages, target hilang, kegagalan overlay/Settings, cancellation, disconnect, start ulang, urutan/deduplikasi batch, penyimpanan, dan race saat pemuatan state UI.

### Smoke test perangkat dengan fixture

Modul `test-target` hanya alat uji, bukan bagian APK produksi. Dua variannya menghasilkan aplikasi kosong yang aman dihentikan. Driver batch berada di `app/src/debug`, dilindungi izin DUMP untuk pemanggilan shell, dan hanya menerima dua package fixture tetap. Driver ini tidak ada pada release.

```sh
./gradlew :app:assembleDebug :test-target:assembleDebug :test-target:assembleSecond
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r test-target/build/outputs/apk/debug/test-target-debug.apk
adb install -r test-target/build/outputs/apk/second/test-target-second.apk
# Aktifkan Accessibility AppFin melalui Settings sebelum menjalankan script.
python3 tools/smoke_fixture_batch.py --serial SERIAL_PERANGKAT
python3 tools/smoke_fixture_batch.py --serial SERIAL_PERANGKAT --cancel
```

Script hanya membuka/menghentikan fixture, membaca hasil driver debug, memeriksa stopped flag dan memastikan overlay sudah dilepas. Script tidak mengubah izin Accessibility atau pengecualian pengguna. Pastikan kedua fixture tidak dikecualikan sebelum tes. Report debug bersifat sementara di cache aplikasi; riwayat sesi lintas proses belum menjadi fitur produk.

## Batas verifikasi

Jalur dasar memakai intent App Info dan elemen Accessibility, dengan teks **Paksa berhenti / Force stop**. Selector dialog AOSP dan variasi Settings yang diamati pada Infinix tersedia. Perbedaan OEM/versi/bahasa dapat memerlukan penyesuaian. Label aplikasi duplikat memerlukan identitas package yang terbaca pada halaman, agar engine tidak menebak target.

Force stop dapat menunda notifikasi/background work sampai aplikasi dibuka kembali. Cancel menghentikan aksi berikutnya; aksi yang sudah dijalankan tidak dapat dibatalkan. Hasil sesi hanya disimpan di memori dan tidak otomatis dilanjutkan setelah proses mati.

Lihat [plan MVP](docs/mvp-plan.md), [knowledge](KNOWLEDGE.md), dan [studi referensi Killapps](docs/killapps-study.md).
