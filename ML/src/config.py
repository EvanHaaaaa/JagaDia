"""Configuration module for ML pipeline.
Defines paths, random seeds, spatio-temporal parameters, and feature definitions.
"""

from pathlib import Path

# Paths
BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
RAW_DATA_DIR = DATA_DIR / "raw"
PROCESSED_DATA_DIR = DATA_DIR / "processed"
EXTERNAL_DATA_DIR = DATA_DIR / "external"
MODELS_DIR = BASE_DIR / "models"
REPORTS_DIR = BASE_DIR / "reports"

# Random Seed for Reproducibility
RANDOM_STATE = 42

# Spatio-Temporal Parameters
NIGHT_HOURS = list(range(18, 24)) + list(range(0, 6))  # 18.00 - 06.00
SPATIAL_GRID_SIZE_METERS = 200  # Resolution for spatial binning / grid cells

# Feature Definitions
TEMPORAL_FEATURES = [
    "hour",
    "day_of_week",
    "is_weekend",
    "is_night",
    "month",
]

ENVIRONMENTAL_FEATURES = [
    "street_light_density",      # Indeks / kerapatan penerangan jalan
    "dist_to_police_station",    # Jarak ke pos polisi terdekat (meter)
    "road_type",                 # Kategori jalan (arteri, kolektor, lokal, gang)
    "population_density",        # Kepadatan penduduk / aktivitas sekitar
]

TARGET_COLUMN = "is_high_risk"  # 1: Rawan (Zona Merah), 0: Aman
