# AppFin logo

Pilihan pengguna: konsep ketiga, dua balok putih/lavender di atas latar ungu.

- `appfin-logo-selected.png`: referensi konsep yang dipilih, dibuat dengan imagegen.
- `appfin-logo.svg`: adaptasi vektor untuk identitas aplikasi.
- Launcher Android memakai VectorDrawable foreground/background, adaptive icon, dan foreground monokrom untuk themed icons. Bentuk mask mengikuti launcher perangkat.

Warna: ungu `#590CCB`, putih `#FFFFFF`, lavender `#D8C4FF`.
Foreground berada di area tengah viewport 108 × 108 agar tetap terbaca saat launcher memakai mask lingkaran atau rounded square.

Pada 25 Oktober 2026, build debug berhasil, 25 unit test lulus, dan lint selesai tanpa error (17 warning). APK berhasil diinstall ke Realme RMX1971 (Android 11) dan MainActivity berhasil dibuka. Tampilan ikon pada launcher dan otomatisasi force stop belum diverifikasi pada perangkat ini.
