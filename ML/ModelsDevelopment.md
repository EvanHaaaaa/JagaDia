# JagaDia - Machine Learning Development & Experimentation Log

Dokumen ini mencatat rancangan, arsitektur, log eksperimen, dan petunjuk teknis pengembangan Machine Learning untuk proyek **JagaDia (Spatio-Temporal Crime Prediction & Safe Route Recommendation)**.

---

## 1. Problem Formulation (Formulasi Masalah)
- **Tujuan:** Memprediksi probabilitas tingkat kerawanan (*crime risk probability*) pada suatu koordinat jalan dalam dimensi ruang dan waktu (*spatio-temporal*).
- **Target Variabel ($y$):**
  - Binary Classification: `is_high_risk` (0: Aman / Zona Hijau, 1: Rawan / Zona Merah).
  - Atau Continuous Risk Score ($[0.0, 1.0]$) yang digunakan oleh sistem navigasi aplikasi mobile untuk menghitung bobot penalti rute (*routing safety weight*).
- **Prioritas Metrik:**
  - **Recall & PR-AUC (Average Precision):** Dalam aplikasi keselamatan diri, kesalahan *False Negative* (daerah rawan yang tidak terdeteksi) jauh lebih berbahaya daripada *False Positive* (peringatan berlebih). Oleh karena itu, model memprioritaskan Recall $\ge 85\%$.

---

## 2. Struktur Folder `ML/`

```
ML/
├── data/
│   ├── raw/             # Data mentah (kriminalitas, cuaca, penerangan jalan)
│   ├── processed/       # Data hasil feature extraction & train/test split
│   └── external/        # Data spasial tambahan (pos polisi, openstreetmap poi)
├── models/              # Model terlatih yang siap digunakan (.joblib / .onnx / .pt)
├── notebooks/           # Jupyter Notebook untuk eksplorasi data (EDA)
├── src/                 # Modul kode terstruktur
│   ├── __init__.py
│   ├── config.py        # Parameter spasial, waktu, dan path direktori
│   ├── data_pipeline.py # ETL data & generator data uji/mock
│   ├── features.py      # Ekstraksi fitur waktu (jam, malam/siang, siklikal) & jarak spasial
│   ├── train_baseline.py# Pipeline training Random Forest & XGBoost
│   └── evaluate.py      # Evaluasi metrik, Confusion Matrix, dan tuning threshold
├── .gitignore           # Menjaga data besar & cache agar tidak masuk git
├── req.txt              # Daftar dependensi library ML
└── ModelsDevelopment.md # Dokumentasi ini
```

---

## 3. Langkah Setup Lingkungan (Environment Setup)

### Langkah 1: Buat Virtual Environment
Buka terminal di root proyek atau di dalam folder `ML/`:
```bash
cd ML
python -m venv .venv
```

### Langkah 2: Aktifkan Virtual Environment
- **Windows (PowerShell):**
  ```powershell
  .venv\Scripts\Activate.ps1
  ```
- **Windows (Command Prompt):**
  ```cmd
  .venv\Scripts\activate.bat
  ```

### Langkah 3: Install Dependensi
```bash
pip install -r req.txt
```

---

## 4. Cara Menjalankan Pipeline & Baseline Model

### 1. Generate Data Mock / Test Pipeline
Jika belum memiliki dataset riil:
```bash
python -m src.data_pipeline
```
*Akan membuat file `data/raw/sample_crime_data.csv` dengan koordinat Jabodetabek dan distribusi malam hari.*

### 2. Melatih Baseline Model (Random Forest & XGBoost)
```bash
python -m src.train_baseline
```
*Script ini akan membagi train/test, melakukan encoding, melatih model, dan menyimpan bobot model ke folder `models/`.*

### 3. Evaluasi Performa Model
```bash
python -m src.evaluate
```

---

## 5. Rencana Rekayasa Fitur (Feature Engineering)

| Kategori Fitur | Nama Fitur | Deskripsi |
|---|---|---|
| **Temporal** | `hour`, `hour_sin`, `hour_cos` | Jam kejadian dengan transformasi siklikal. |
| **Temporal** | `is_night` | Indikator jam malam (18.00 - 06.00). |
| **Temporal** | `day_of_week`, `is_weekend` | Pola perbedaan hari kerja vs akhir pekan. |
| **Spasial/Lingkungan** | `street_light_density` | Indeks penerangan jalan dari data tata kota/laporan. |
| **Spasial/Lingkungan** | `dist_to_police_station` | Jarak euclid/haversine ke pos pengamanan terdekat. |
| **Spasial/Lingkungan** | `road_type` | Klasifikasi jalan (gang sempit, lokal, arteri). |
| **Spasial/Lingkungan** | `historical_crime_density` | Kepadatan riwayat kejadian dalam radius 500m (KDE). |

---

## 6. Roadmap Model Eksperimen

1. **Sprint 1 (Baseline):** 
   - [x] Template struktur modul `ML/`
   - [x] Baseline script Tabular: Random Forest & XGBoost
   - [ ] Integrasi dataset riil (Kepolisian / Open Data Jakarta)
2. **Sprint 2 (Spatial Enhancement):**
   - [ ] Implementasi Spatial Binning (Uber H3 Hexagon / S2 Geometry)
   - [ ] Kernel Density Estimation (KDE) untuk hotspot mapping
3. **Sprint 3 (Deep Learning & Advanced):**
   - [ ] Eksperimen Spatio-Temporal Graph Neural Network (ST-GNN) memanfaatkan topologi jalan
   - [ ] Ekspor model ke format ONNX agar inferensi di backend FastAPI sangat cepat (<20ms per titik).

---

## 7. Catatan Eksperimen (Experiment Tracker)

| ID | Tanggal | Model | Fitur Digunakan | ROC-AUC | F1-Score | Catatan |
|---|---|---|---|---|---|---|
| EXP-001 | 2026-11-13 | Random Forest Baseline | Temporal + Mock Env | - | - | Initial baseline setup |
| EXP-002 | - | XGBoost Baseline | Temporal + Mock Env | - | - | Menunggu running dengan venv lokal |
