"""Dataset Builder for Supervised Risk Classification (Tahap 2).
Integrates spatial clusters from Tahap 1 (DBSCAN) and cleaned incident data
with temporal and environmental features to create a robust classification dataset.
"""

from pathlib import Path
import numpy as np
import pandas as pd
from .config import RANDOM_STATE, RAW_DATA_DIR, PROCESSED_DATA_DIR
from .clustering import EARTH_RADIUS_METERS, haversine_vector_distance

# Coordinates of key Police Stations in Jakarta for spatial distance calculation
JAKARTA_POLICE_STATIONS = np.array([
    [-6.2238, 106.8105],  # Polda Metro Jaya (Semanggi)
    [-6.1856, 106.8373],  # Polres Jakarta Pusat (Kemayoran/Senen)
    [-6.2571, 106.8083],  # Polres Jakarta Selatan (Wijaya)
    [-6.1667, 106.7644],  # Polres Jakarta Barat (Daan Mogot)
    [-6.2255, 106.8835],  # Polres Jakarta Timur (Jatinegara)
    [-6.1264, 106.8967],  # Polres Jakarta Utara (Koja)
    [-6.1752, 106.8651],  # Polsek Johar Baru
    [-6.1608, 106.8024],  # Polsek Tambora
    [-6.2155, 106.8167],  # Polsek Tanah Abang
])


def min_distance_to_points(lat: float, lon: float, reference_points: np.ndarray) -> float:
    """Compute minimum Haversine distance in meters to a set of reference coordinates."""
    if len(reference_points) == 0:
        return 1000.0
    distances = haversine_vector_distance(
        lat, lon, reference_points[:, 0], reference_points[:, 1]
    )
    return float(np.min(distances))


def build_classification_dataset(
    n_samples: int = 2400,
    output_path: Path = None,
) -> pd.DataFrame:
    """Generate a spatio-temporal supervised dataset connecting DBSCAN hotspots and environmental data.

    Returns:
        pd.DataFrame with features and target 'is_high_risk' (0 = Aman, 1 = Rawan).
    """
    np.random.seed(RANDOM_STATE)

    # 1. Load Hotspots from DBSCAN (Tahap 1) if available
    hotspots_path = PROCESSED_DATA_DIR / "cluster_hotspots_summary.csv"
    if hotspots_path.exists():
        df_hotspots = pd.read_csv(hotspots_path)
        hotspot_coords = df_hotspots[["centroid_lat", "centroid_lon"]].values
        hotspot_risks = df_hotspots["risk_score"].values
    else:
        # Fallback to key known centers in Jakarta
        hotspot_coords = np.array([
            [-6.1752, 106.8651],
            [-6.1860, 106.8874],
            [-6.1608, 106.8024],
            [-6.2078, 106.7927],
            [-6.2218, 106.7988],
        ])
        hotspot_risks = np.array([0.9, 0.85, 0.8, 0.75, 0.7])

    # 2. Load Incident points if available
    incident_path = PROCESSED_DATA_DIR / "data_bersih.csv"
    if incident_path.exists():
        df_incidents = pd.read_csv(incident_path)
        real_incident_coords = df_incidents[["lat", "lon"]].values
    else:
        real_incident_coords = hotspot_coords

    # Half samples positive (high risk), half negative (safe background)
    n_pos = n_samples // 2
    n_neg = n_samples - n_pos

    # === POSITIVE SAMPLES (RAWAN: 1) ===
    # Centered around incident clusters and hotspots, skewed to night hours
    pos_idx = np.random.choice(len(real_incident_coords), size=n_pos, replace=True)
    pos_base = real_incident_coords[pos_idx]
    # Small spatial jitter around hotspot (0-300m)
    pos_lats = pos_base[:, 0] + np.random.normal(0, 0.002, size=n_pos)
    pos_lons = pos_base[:, 1] + np.random.normal(0, 0.002, size=n_pos)

    # Night hours heavily favored for positive crime/disorder incidents (18:00 - 05:00)
    night_hours_pool = [18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5]
    day_hours_pool = list(range(6, 18))
    pos_hours = np.random.choice(
        night_hours_pool + day_hours_pool,
        size=n_pos,
        p=[0.75 / len(night_hours_pool)] * len(night_hours_pool)
        + [0.25 / len(day_hours_pool)] * len(day_hours_pool),
    )
    # Poor lighting, higher proportion of local roads / alleys
    pos_light = np.random.beta(a=2, b=5, size=n_pos)  # Skewed towards low lighting (0.1 - 0.4)
    pos_road = np.random.choice(
        ["gang", "lokal", "kolektor", "arteri"],
        size=n_pos,
        p=[0.35, 0.35, 0.20, 0.10],
    )
    pos_days = np.random.choice(7, size=n_pos, p=[0.12, 0.12, 0.12, 0.12, 0.16, 0.18, 0.18])
    pos_targets = np.ones(n_pos, dtype=int)

    # === NEGATIVE SAMPLES (AMAN: 0) ===
    # Scattered in general Jakarta areas, favoring daytime, main roads, and good lighting
    neg_lats = np.random.uniform(-6.32, -6.12, size=n_neg)
    neg_lons = np.random.uniform(106.75, 106.95, size=n_neg)

    # Daytime favored for negative safe points (06:00 - 17:00)
    neg_hours = np.random.choice(
        day_hours_pool + night_hours_pool,
        size=n_neg,
        p=[0.75 / len(day_hours_pool)] * len(day_hours_pool)
        + [0.25 / len(night_hours_pool)] * len(night_hours_pool),
    )
    neg_light = np.random.beta(a=5, b=2, size=n_neg)  # Skewed towards high lighting (0.6 - 0.95)
    neg_road = np.random.choice(
        ["arteri", "kolektor", "lokal", "gang"],
        size=n_neg,
        p=[0.40, 0.35, 0.20, 0.05],
    )
    neg_days = np.random.choice(7, size=n_neg)
    neg_targets = np.zeros(n_neg, dtype=int)

    # Combine
    all_lats = np.concatenate([pos_lats, neg_lats])
    all_lons = np.concatenate([pos_lons, neg_lons])
    all_hours = np.concatenate([pos_hours, neg_hours])
    all_lights = np.concatenate([pos_light, neg_light])
    all_roads = np.concatenate([pos_road, neg_road])
    all_days = np.concatenate([pos_days, neg_days])
    all_targets = np.concatenate([pos_targets, neg_targets])

    # Shuffle
    shuffle_idx = np.random.permutation(n_samples)
    all_lats = all_lats[shuffle_idx]
    all_lons = all_lons[shuffle_idx]
    all_hours = all_hours[shuffle_idx]
    all_lights = all_lights[shuffle_idx]
    all_roads = all_roads[shuffle_idx]
    all_days = all_days[shuffle_idx]
    all_targets = all_targets[shuffle_idx]

    # Calculate spatial distances to DBSCAN hotspots & police stations
    dist_to_hotspots = []
    closest_hotspot_risks = []
    dist_to_police = []

    for lat, lon in zip(all_lats, all_lons):
        # Distance to DBSCAN Hotspot
        hotspot_dists = haversine_vector_distance(
            lat, lon, hotspot_coords[:, 0], hotspot_coords[:, 1]
        )
        min_h_idx = np.argmin(hotspot_dists)
        dist_to_hotspots.append(float(hotspot_dists[min_h_idx]))
        closest_hotspot_risks.append(float(hotspot_risks[min_h_idx]))

        # Distance to nearest police station
        police_dists = haversine_vector_distance(
            lat, lon, JAKARTA_POLICE_STATIONS[:, 0], JAKARTA_POLICE_STATIONS[:, 1]
        )
        dist_to_police.append(float(np.min(police_dists)))

    df = pd.DataFrame({
        "latitude": np.round(all_lats, 6),
        "longitude": np.round(all_lons, 6),
        "hour": all_hours,
        "is_night": ((all_hours >= 18) | (all_hours < 6)).astype(int),
        "day_of_week": all_days,
        "is_weekend": np.isin(all_days, [5, 6]).astype(int),
        "hour_sin": np.round(np.sin(2 * np.pi * all_hours / 24.0), 4),
        "hour_cos": np.round(np.cos(2 * np.pi * all_hours / 24.0), 4),
        "street_light_density": np.round(all_lights, 3),
        "dist_to_hotspot_m": np.round(dist_to_hotspots, 1),
        "closest_hotspot_risk": np.round(closest_hotspot_risks, 2),
        "dist_to_police_m": np.round(dist_to_police, 1),
        "road_type": all_roads,
        "is_high_risk": all_targets,
    })

    if output_path:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(output_path, index=False)
        print(f"Classification dataset saved to: {output_path} ({len(df)} rows)")

    return df


if __name__ == "__main__":
    out_file = PROCESSED_DATA_DIR / "classification_dataset.csv"
    build_classification_dataset(n_samples=2500, output_path=out_file)
