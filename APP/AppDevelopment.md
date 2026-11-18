# JagaDia: Aplikasi Android

Aplikasi Android JagaDia (Kotlin). Gambaran umum proyek dan aturan Git ada di [README utama](../README.md).

---

## Menjalankan

### Prasyarat

- **Android Studio** versi yang mendukung Android Gradle Plugin 9.4.1
- Android SDK **API 37**
- Emulator atau HP Android 7.0+ (API 24+)
- Koneksi internet (tile peta dan routing diambil online)

Gradle wrapper (9.6.0) dan JDK untuk Gradle akan diunduh otomatis saat sync pertama, jadi sync pertama bisa agak lama.

### Langkah

1. Clone repo, lalu di Android Studio pilih **Open** dan buka folder **`APP/`** (bukan folder root repo).
2. Daftar di [openrouteservice.org](https://openrouteservice.org/) dan ambil API key gratis.
3. Tambahkan key ke `APP/local.properties`:

   ```properties
   sdk.dir=<biarkan seperti yang dibuat Android Studio>
   ORS_API_KEY=isi_api_key_kamu
   ```

4. Klik **Sync Project with Gradle Files**, lalu **Run**.

`local.properties` bersifat pribadi per anggota dan sudah di-ignore Git. **Jangan pernah di-commit.**

### Masalah umum

| Gejala                                                          | Penyebab / solusi                                                                                                              |
|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Gradle sync gagal soal `local.properties`                       | `build.gradle.kts` membaca file ini. Pastikan ada di `APP/` (Android Studio biasanya membuatnya otomatis saat project dibuka). |
| Routing gagal / error 401 atau 403 (setelah routing tersambung) | `ORS_API_KEY` kosong atau salah. Cek isi `local.properties`, lalu sync ulang.                                                  |
| Peta kosong atau abu-abu                                        | Cek koneksi internet. Style peta diambil dari OpenFreeMap.                                                                     |
| Onboarding tidak muncul lagi                                    | Onboarding hanya tampil sekali. Hapus data aplikasi (App info > Storage > Clear data) untuk melihatnya lagi.                   |

---

## Struktur Kode

```
APP/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/jagadiaapp/
│       │   ├── MainActivity.kt        # Splash 1,5 detik, lalu ke onboarding atau peta
│       │   ├── OnboardingActivity.kt  # 4 halaman intro + izin lokasi
│       │   └── MapActivity.kt         # Peta, layer zona risiko, layer rute
│       ├── res/
│       │   ├── layout/                # activity_main, activity_onboarding, activity_map
│       │   ├── drawable/              # Ikon dan background (XML vector/shape)
│       │   └── values/                # colors, strings, themes (+ values-night)
│       └── assets/risk_zones.json     # Data zona risiko (dummy)
├── gradle/libs.versions.toml          # Daftar versi dependency
├── build.gradle.kts
└── settings.gradle.kts
```

Package: `com.example.jagadiaapp`. Izin di manifest: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.

### Alur layar

```
MainActivity (splash)
   ├── belum pernah onboarding ──> OnboardingActivity (4 halaman) ──> MapActivity
   └── sudah onboarding ─────────────────────────────────────────────> MapActivity
```

Status onboarding disimpan di SharedPreferences `jagadia`, kunci `onboarding_done`. Halaman terakhir onboarding meminta izin lokasi; hasilnya (diizinkan atau tidak) tetap lanjut ke peta.

---

## Peta dan Routing

| Hal               | Detail                                                                                        |
|-------------------|-----------------------------------------------------------------------------------------------|
| Library           | MapLibre Native Android SDK 13.7.1 (`org.maplibre.gl:android-sdk`)                            |
| Style peta        | `https://tiles.openfreemap.org/styles/positron`                                               |
| Kamera awal       | Lat `-6.219990`, Lng `106.903173`, zoom 16                                                    |
| Layer zona risiko | Source `risk-src`, layer `risk-zone` (CircleLayer blur 1.0, opacity 0,55, radius dalam meter) |
| Layer rute        | Source `route-src`, layer `route-line` (LineLayer biru, lebar 6)                              |
| Routing           | OpenRouteService, key lewat `BuildConfig.ORS_API_KEY`                                         |

**Format data zona risiko** (`assets/risk_zones.json`): GeoJSON `FeatureCollection` berisi `Point`. Properti: `radius` (meter, default 100) dan skor 0.0 sampai 1.0 per waktu: `risk_now`, `risk_1h`, `risk_night` (0 hijau, 0,5 kuning, 1 merah). Chip Sekarang / +1 jam / Malam ini di bottom sheet memilih properti mana yang dipakai.

```json
{
   "type": "Feature",
   "properties": { "radius": 110, "risk_now": 0.85, "risk_1h": 0.9, "risk_night": 1.0 },
   "geometry": { "type": "Point", "coordinates": [106.9020, -6.2172] }
}
```

**Yang belum jadi:** `MapActivity.onDestinationPicked(latLng)` masih TODO. Rencananya: minta rute ke ORS, lalu panggil `showRoute(geoJson)`. Fungsi `showRoute` mengharapkan `FeatureCollection` yang fitur pertamanya berupa `LineString`, lalu menggambar garisnya dan mengarahkan kamera ke rute.

---

## Git untuk Bagian Ini

Aturan lengkap ada di [README utama](../README.md#panduan-git-branch-dan-commit). Ringkasnya:

- Branch per fitur atau page: `feature-fe-<nama-page>` untuk tampilan, `feature-be-<nama-fitur>` untuk logika/data/API.
- Dibuat dari `main`, digabung ke `main` lewat Pull Request.
- Commit: `tipe(area): ringkasan singkat`, contoh `feat(map): tampilkan posisi pengguna`.