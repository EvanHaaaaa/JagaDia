# JagaDia

> Rute tercepat belum tentu teraman.

JagaDia adalah aplikasi Android yang membantu pengguna memilih perjalanan dengan mempertimbangkan keamanan jalan. Aplikasi menampilkan area rawan di peta dan (target akhir) membandingkan waktu, jarak, dan skor aman dari beberapa pilihan rute, sementara keputusan tetap di tangan pengguna.

Proyek Kelompok 4 · PPTI 24.

---

## Daftar Isi

1. [Dokumentasi per Bagian](#dokumentasi-per-bagian)
2. [Status Fitur](#status-fitur)
3. [Keputusan Teknologi](#keputusan-teknologi)
4. [Struktur Folder](#struktur-folder)
5. [Panduan Git: Branch dan Commit](#panduan-git-branch-dan-commit)
6. [Tim](#tim)

---

## Dokumentasi per Bagian

| Bagian                | Isi                                          | Panduan                                            |
|-----------------------|----------------------------------------------|----------------------------------------------------|
| Aplikasi Android      | Setup Android Studio, API key, struktur kode | [APP/AppDevelopment.md](APP/AppDevelopment.md)     |
| Machine Learning      | Setup Python, dataset, model                 | [ML/ModelsDevelopment.md](ML/ModelsDevelopment.md) |
| Git (branch & commit) | Aturan dan alur kerja untuk semua anggota    | [Di bawah](#panduan-git-branch-dan-commit)         |

---

## Status Fitur

- [x] Splash screen, lalu masuk ke onboarding atau peta
- [x] Onboarding 4 halaman + permintaan izin lokasi
- [x] Peta (MapLibre) dengan posisi pengguna
- [x] Layer zona risiko di peta (masih data dummy `risk_zones.json`)
- [ ] Pilih tujuan di peta, lalu minta rute ke OpenRouteService (`onDestinationPicked` masih TODO)
- [ ] Perbandingan rute: waktu, jarak, skor aman
- [ ] Model Machine Learning prediksi risiko berdasarkan lokasi dan waktu (folder `ML/`)

---

## Keputusan Teknologi

| Komponen | Pilihan | Catatan |
|---|---|---|
| Platform & bahasa | Android native, **Kotlin** | `minSdk 24` (Android 7.0), `targetSdk` / `compileSdk 37` |
| UI | XML layout + AppCompat, Material Components, ConstraintLayout | Belum memakai Jetpack Compose |
| Library peta | **MapLibre Native Android SDK 13.7.1** | Open source, gratis |
| Tile / style peta | **OpenFreeMap** (style `liberty`) | Gratis, tanpa API key |
| Routing | **OpenRouteService (ORS)** | Gratis; API key dibaca dari `local.properties` lewat `BuildConfig.ORS_API_KEY` |
| Data zona rawan | **GeoJSON** (`assets/risk_zones.json`) | Properti `risk` 0.0 sampai 1.0 diwarnai hijau, kuning, merah |
| Machine Learning | **Python** (folder `ML/`) | Library ditambahkan ke `ML/req.txt` saat dipakai |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.6.0 | Versi dependency ada di `APP/gradle/libs.versions.toml` |

Alasan memilih MapLibre + OpenFreeMap + ORS: semuanya gratis dan tidak memerlukan kartu kredit, berbeda dengan Google Maps Platform.

---

## Struktur Folder

```
JagaDia/
├── APP/                  # Aplikasi Android (buka folder ini di Android Studio)
│   └── AppDevelopment.md
├── ML/                   # Pengembangan model Machine Learning
│   └── ModelsDevelopment.md
└── README.md             # File ini: gambaran umum + aturan Git
```

Folder khusus backend belum ada. Jika nanti dibutuhkan (misalnya API untuk menyajikan prediksi model), buat foldernya lewat branch `feature-be-...` seperti aturan di bawah.

---

## Panduan Git: Branch dan Commit

Tujuan: semua anggota paham cara **membuat branch, commit, dan mengirim perubahan ke GitHub**.

### Struktur branch

```
main                          <- branch utama, selalu stabil dan bisa di-build
 ├── feature-ml               <- semua pekerjaan Machine Learning (folder ML/)
 ├── feature-fe-<nama-page>   <- frontend: satu branch per fitur / page
 └── feature-be-<nama-fitur>  <- backend: satu branch per fitur
```

| Branch | Dipakai untuk | Dibuat dari | Digabung ke |
|---|---|---|---|
| `main` | Versi stabil | - | - |
| `feature-ml` | Machine Learning (`ML/`) | `main` | `main`, via Pull Request tiap ada hasil yang stabil |
| `feature-fe-<nama-page>` | Frontend: tampilan, layout XML, Activity UI. Contoh: `feature-fe-onboarding`, `feature-fe-map` | `main` | `main`, via Pull Request |
| `feature-be-<nama-fitur>` | Backend: logika, data, API. Contoh: `feature-be-routing`, `feature-be-risk-zones` | `main` | `main`, via Pull Request |

### Aturan branch

- **Jangan commit atau push langsung ke `main`.** Semua perubahan masuk lewat Pull Request.
- Satu branch = satu fitur atau satu page. Selesai satu bagian, langsung di-merge ke `main` (jangan ditumpuk lama), lalu hapus branch-nya. Dengan begitu `main` ikut berkembang seiring progress.
- Nama branch: huruf kecil, pisahkan kata dengan tanda hubung (`-`), tanpa spasi.
- Buat branch dari `main` yang sudah di-pull terbaru.
- `feature-ml` adalah branch tetap untuk ML. Tarik perubahan terbaru dari `main` secara berkala (`git merge main`) supaya tidak jauh tertinggal.
- Sebelum membuat Pull Request, pastikan aplikasi masih bisa di-build dan minta minimal 1 anggota lain untuk me-review.

### Aturan commit

Format: `tipe(area): ringkasan singkat`

| Tipe | Dipakai untuk |
|---|---|
| `feat` | Fitur atau page baru |
| `fix` | Perbaikan bug |
| `refactor` | Merapikan kode tanpa mengubah perilaku |
| `docs` | README atau dokumentasi |
| `chore` | Konfigurasi, dependency, file pendukung |

- Ringkasan dalam kalimat perintah, huruf kecil, maksimal sekitar 70 karakter.
- Satu commit = satu perubahan yang utuh. Commit sering, jangan ditumpuk jadi satu commit besar.
- Jangan commit kode yang membuat build gagal.

Contoh:

```
feat(onboarding): tambah halaman izin lokasi
fix(map): perbaiki warna layer zona risiko
feat(routing): panggil OpenRouteService saat tujuan dipilih
feat(ml): tambah notebook eksplorasi data
docs: perbarui README
```

### Alur kerja langkah demi langkah

**Sekali saja (setup awal):**

```bash
git clone <url-repo-github>
cd JagaDia
git config user.name "Nama Kamu"
git config user.email "email-github-kamu@example.com"
```

**Setiap mengerjakan fitur atau page baru (frontend / backend):**

```bash
# 1. Ambil main terbaru
git checkout main
git pull origin main

# 2. Buat branch baru untuk fitur
git checkout -b feature-be-routing

# 3. Kerjakan, lalu cek perubahan dan commit
git status
git add APP/app/src/main/java/com/example/jagadiaapp/MapActivity.kt
git commit -m "feat(routing): panggil OpenRouteService saat tujuan dipilih"

# 4. Kirim ke GitHub (pertama kali pakai -u, berikutnya cukup: git push)
git push -u origin feature-be-routing
```

5. Buka GitHub, klik **Compare & pull request**, pastikan **base: `main`** dan **compare: branch kamu**, tulis deskripsi singkat, minta review, lalu **Merge** setelah disetujui.
6. Setelah merge, rapikan dan sinkronkan:

```bash
git checkout main
git pull origin main
git branch -d feature-be-routing
```

**Mengerjakan Machine Learning (`feature-ml`):**

```bash
git checkout feature-ml
git pull origin feature-ml
git merge main                  # ambil perubahan terbaru dari main
# ...kerjakan, lalu:
git add ML/
git commit -m "feat(ml): tambah preprocessing data"
git push origin feature-ml
```

Saat ada hasil yang stabil (notebook atau model yang jalan), buat Pull Request `feature-ml` ke `main`.

**Jika branch kamu tertinggal dari `main`:**

```bash
git fetch origin
git merge origin/main
```

Jika muncul konflik, buka file yang bertanda `<<<<<<<`, pilih isi yang benar, hapus tanda konflik, lalu `git add <file>` dan `git commit`. Kalau ragu, tanyakan ke anggota tim sebelum push.

### Jangan di-commit

- `local.properties` dan API key apa pun (ORS_API_KEY)
- Folder hasil build dan cache: `build/`, `.gradle/`, `.idea/`, `.kotlin/`, file `.apk`
- Virtual environment Python (`.venv/`), `__pycache__/`, `.ipynb_checkpoints/`
- Dataset atau model berukuran besar (simpan di drive tim, tulis tautannya di dokumentasi)

---

## Tim

Kelompok 4 · PPTI 24

| Nama     | Peran                        | GitHub          |
|----------|------------------------------|-----------------|
| Agatha   | Product Owner & UI/UX Design | agathadys       |
| Evan     | Scrum Master                 | EvanHaaaaa      |
| Gerald   | Machine Learning             | GeraldC3lloz    |
| Kenny    | Backend                      | AnnonymusKenoii |
| Mariesza | Frontend                     | MarieszaGuppy   |
