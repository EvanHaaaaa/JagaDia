"""Data loading, cleaning, and preprocessing pipeline for JagaDia.
Supports sample dataset generation for immediate pipeline testing.
"""

from pathlib import Path
import numpy as np
import pandas as pd
from .config import RAW_DATA_DIR, PROCESSED_DATA_DIR, RANDOM_STATE
from .features import extract_temporal_features

def generate_mock_crime_dataset(n_samples: int = 1500, output_path: Path = None) -> pd.DataFrame:
    """Generate a realistic mock spatio-temporal dataset for testing pipelines.
    Center: DKI Jakarta / Jabodetabek coordinates (e.g., lat: -6.2088, lon: 106.8456).
    """
    np.random.seed(RANDOM_STATE)
    
    # Coordinates centered around Jakarta
    center_lat, center_lon = -6.2088, 106.8456
    latitudes = np.random.normal(center_lat, 0.05, n_samples)
    longitudes = np.random.normal(center_lon, 0.05, n_samples)
    
    # Timestamps over the past 6 months
    start_date = pd.Timestamp("2026-01-01")
    end_date = pd.Timestamp("2026-06-30")
    start_u = start_date.value // 10**9
    end_u = end_date.value // 10**9
    random_timestamps = pd.to_datetime(np.random.randint(start_u, end_u, n_samples), unit="s")
    
    # Environmental mock attributes
    street_light_density = np.random.uniform(0.1, 1.0, n_samples)  # 0.1: sangat gelap, 1.0: terang benderang
    dist_to_police = np.random.exponential(scale=1200, size=n_samples)  # meter
    road_types = np.random.choice(["arteri", "kolektor", "lokal", "gang"], size=n_samples, p=[0.2, 0.3, 0.3, 0.2])
    
    df = pd.DataFrame({
        "latitude": latitudes,
        "longitude": longitudes,
        "timestamp": random_timestamps,
        "street_light_density": street_light_density,
        "dist_to_police_station": dist_to_police,
        "road_type": road_types,
    })
    
    df = extract_temporal_features(df, "timestamp")
    
    # Ground truth simulation: high risk probability increases at night, low lighting, far from police
    risk_score = (
        0.35 * df["is_night"]
        + 0.25 * (1.0 - df["street_light_density"])
        + 0.20 * (df["dist_to_police_station"] / 3000).clip(0, 1)
        + 0.10 * (df["road_type"].isin(["gang", "lokal"]).astype(int))
        + np.random.normal(0, 0.1, n_samples)
    )
    df["is_high_risk"] = (risk_score > 0.55).astype(int)
    
    if output_path:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(output_path, index=False)
        print(f"Mock dataset saved to: {output_path}")
        
    return df

def load_data(file_path: Path) -> pd.DataFrame:
    """Load dataset from CSV or Parquet."""
    if not file_path.exists():
        raise FileNotFoundError(f"Dataset not found at {file_path}")
    if file_path.suffix == ".parquet":
        return pd.read_parquet(file_path)
    return pd.read_csv(file_path)

if __name__ == "__main__":
    mock_file = RAW_DATA_DIR / "sample_crime_data.csv"
    generate_mock_crime_dataset(n_samples=2000, output_path=mock_file)
