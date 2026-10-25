# Release signing

Build signed APK memakai JDK 17:

```sh
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.
Unit test tidak dijalankan oleh perintah ini.

Konfigurasi lokal ada di `keystore.properties`, dengan `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`. Key ada di `signing/appfin-release.jks` (format PKCS12, RSA 3072, alias `appfin-release`). Kedua lokasi diabaikan Git dan hanya dapat dibaca/ditulis pemilik file.

Cadangkan key dan properties secara aman di luar repository. Gunakan key yang sama untuk update berikutnya. Tanpa konfigurasi lokal, build menghasilkan APK unsigned.

APK release memakai sertifikat berbeda dari debug yang sebelumnya diinstall. Instalasi release tidak bisa menimpa debug dengan package sama; uninstall debug terlebih dahulu akan menghapus data lokal dan memerlukan aktivasi Accessibility kembali. Jangan uninstall otomatis saat membuat build.
