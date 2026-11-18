"""Safe Route Recommender Module (Tahap 3).
Based on COMP657701 Machine Learning - Session 13-14 (Recommender System).

Implements Content-Based Recommendation using Multi-Criteria Safety Cost Scoring:
    Total Cost(r) = Distance(r) + alpha * sum(Risk_prob(p_i) * delta_d_i)

Where:
- r is candidate route in S (set of items)
- alpha is user safety preference sensitivity (User Profile)
- Risk_prob(p_i) is the crime/disorder risk probability from Tahap 2 ML model
- Output is ranked routes and GeoJSON LineString compatible with Android MapActivity
"""

import json
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import joblib
import numpy as np
import pandas as pd

from .config import MODELS_DIR, PROCESSED_DATA_DIR
from .clustering import EARTH_RADIUS_METERS, haversine_vector_distance
from .dataset_builder import JAKARTA_POLICE_STATIONS

# User Persona Safety Presets (Session 13-14: User Profiles)
SAFETY_PRESETS = {
    "prefer_cepat": 0.2,       # Prioritas jarak terpendek / tercepat
    "seimbang": 1.0,           # Kompromi seimbang antara jarak dan keamanan
    "sangat_aman": 3.0,        # Prioritas utama keamanan (Persona Perempuan & Komuter Malam)
}


def load_best_classifier():
    """Load the trained classifier from Tahap 2."""
    model_path = MODELS_DIR / "best_risk_classifier.joblib"
    if not model_path.exists():
        raise FileNotFoundError(
            f"Model {model_path} tidak ditemukan. Silakan jalankan Tahap 2 terlebih dahulu."
        )
    return joblib.load(model_path)


def evaluate_route_waypoints(
    waypoints: List[List[float]],
    classifier_pipeline,
    hour: int = 21,
    day_of_week: int = 5,
    road_type: str = "lokal",
    street_light_density: float = 0.5,
    hotspots_path: Optional[Path] = None,
) -> pd.DataFrame:
    """Evaluate crime risk probabilities along a series of route coordinates [[lat, lon], ...].

    Returns:
        DataFrame containing each waypoint, segment length, and predicted risk probability.
    """
    if hotspots_path is None:
        hotspots_path = PROCESSED_DATA_DIR / "cluster_hotspots_summary.csv"

    if hotspots_path.exists():
        df_hotspots = pd.read_csv(hotspots_path)
        hotspot_coords = df_hotspots[["centroid_lat", "centroid_lon"]].values
        hotspot_risks = df_hotspots["risk_score"].values
    else:
        hotspot_coords = np.array([[-6.1752, 106.8651], [-6.1860, 106.8874]])
        hotspot_risks = np.array([0.90, 0.82])

    wp_array = np.array(waypoints)
    n_pts = len(wp_array)

    # 1. Calculate segment lengths between consecutive points in meters
    segment_lengths = [0.0]
    for i in range(1, n_pts):
        dist = haversine_vector_distance(
            wp_array[i - 1, 0], wp_array[i - 1, 1],
            np.array([wp_array[i, 0]]), np.array([wp_array[i, 1]])
        )[0]
        segment_lengths.append(float(dist))

    # 2. Extract spatio-temporal features for each waypoint
    dist_to_hotspot_list = []
    hotspot_risk_list = []
    dist_to_police_list = []

    for lat, lon in wp_array:
        # Distance to closest DBSCAN hotspot
        h_dists = haversine_vector_distance(lat, lon, hotspot_coords[:, 0], hotspot_coords[:, 1])
        min_idx = np.argmin(h_dists)
        dist_to_hotspot_list.append(float(h_dists[min_idx]))
        hotspot_risk_list.append(float(hotspot_risks[min_idx]))

        # Distance to police
        p_dists = haversine_vector_distance(lat, lon, JAKARTA_POLICE_STATIONS[:, 0], JAKARTA_POLICE_STATIONS[:, 1])
        dist_to_police_list.append(float(np.min(p_dists)))

    is_night = 1 if (hour >= 18 or hour < 6) else 0
    is_weekend = 1 if day_of_week in [5, 6] else 0

    df_pts = pd.DataFrame({
        "latitude": wp_array[:, 0],
        "longitude": wp_array[:, 1],
        "hour": hour,
        "is_night": is_night,
        "day_of_week": day_of_week,
        "is_weekend": is_weekend,
        "hour_sin": np.sin(2 * np.pi * hour / 24.0),
        "hour_cos": np.cos(2 * np.pi * hour / 24.0),
        "street_light_density": street_light_density,
        "dist_to_hotspot_m": dist_to_hotspot_list,
        "closest_hotspot_risk": hotspot_risk_list,
        "dist_to_police_m": dist_to_police_list,
        "road_type": road_type,
    })

    # Predict risk probabilities using Tahap 2 ML pipeline
    probs = classifier_pipeline.predict_proba(df_pts)[:, 1]

    df_pts["segment_length_m"] = segment_lengths
    df_pts["risk_probability"] = np.round(probs, 4)
    return df_pts


def score_and_rank_routes(
    candidate_routes: List[Dict],
    classifier_pipeline,
    alpha: float = 1.0,
    hour: int = 21,
    day_of_week: int = 5,
) -> Tuple[pd.DataFrame, Dict]:
    """Score candidate routes using Content-Based Utility / Total Cost Model and rank them.

    Args:
        candidate_routes: List of dicts, each with 'route_id', 'name', 'road_type', 'street_light', 'waypoints'.
        classifier_pipeline: Trained model from Tahap 2.
        alpha: User security sensitivity parameter (Session 13-14 User Profile).
        hour: Travel hour (0-23).
        day_of_week: Day of week (0-6).

    Returns:
        df_ranking: Ranked DataFrame of candidate routes.
        best_route: Dict containing details and GeoJSON of top recommended route.
    """
    evaluated_list = []

    for route in candidate_routes:
        r_id = route["route_id"]
        r_name = route["name"]
        road_t = route.get("road_type", "lokal")
        s_light = route.get("street_light_density", 0.5)
        waypoints = route["waypoints"]

        df_wp = evaluate_route_waypoints(
            waypoints,
            classifier_pipeline,
            hour=hour,
            day_of_week=day_of_week,
            road_type=road_t,
            street_light_density=s_light,
        )

        total_dist_m = float(df_wp["segment_length_m"].sum())
        # Risk penalty = sum(risk_i * segment_length_i)
        weighted_risk_penalty = float(
            (df_wp["risk_probability"] * df_wp["segment_length_m"]).sum()
        )
        mean_risk = (
            weighted_risk_penalty / total_dist_m if total_dist_m > 0 else float(df_wp["risk_probability"].mean())
        )
        max_risk = float(df_wp["risk_probability"].max())
        danger_pts = int((df_wp["risk_probability"] >= 0.65).sum())

        # Formula: Total Cost = Distance + alpha * Risk_Penalty
        # Risk penalty dikalikan skala meter agar sebanding
        scaled_risk_penalty = weighted_risk_penalty * 2.0
        total_cost = total_dist_m + (alpha * scaled_risk_penalty)

        # Safety Score: 0 (sangat bahaya) sampai 100 (sangat aman)
        safety_score = max(0.0, min(100.0, 100.0 * (1.0 - mean_risk)))

        # Estimasi durasi (asumsi rata-rata kecepatan motor di Jakarta: 30 km/jam = 500 m/menit)
        duration_min = round(total_dist_m / 450.0, 1)

        evaluated_list.append({
            "route_id": r_id,
            "route_name": r_name,
            "road_type": road_t,
            "distance_km": round(total_dist_m / 1000.0, 2),
            "distance_m": round(total_dist_m, 1),
            "duration_min": duration_min,
            "mean_risk_prob": round(mean_risk, 3),
            "max_risk_prob": round(max_risk, 3),
            "high_risk_points_count": danger_pts,
            "total_cost": round(total_cost, 1),
            "safety_score": round(safety_score, 1),
            "waypoints": waypoints,
            "wp_eval_df": df_wp,
        })

    df_ranking = pd.DataFrame(evaluated_list).sort_values(by="total_cost", ascending=True).reset_index(drop=True)
    df_ranking["recommendation_rank"] = df_ranking.index + 1

    best_idx = 0
    best_route_row = df_ranking.iloc[best_idx]

    # Convert best route to GeoJSON LineString (matches Android MapActivity format)
    geojson_line = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {
                    "route_id": best_route_row["route_id"],
                    "route_name": best_route_row["route_name"],
                    "distance_km": float(best_route_row["distance_km"]),
                    "duration_min": float(best_route_row["duration_min"]),
                    "safety_score": float(best_route_row["safety_score"]),
                    "mean_risk": float(best_route_row["mean_risk_prob"]),
                    "alpha_used": float(alpha),
                    "rank": int(best_route_row["recommendation_rank"]),
                },
                "geometry": {
                    "type": "LineString",
                    # MapLibre/GeoJSON format: [longitude, latitude]
                    "coordinates": [[round(pt[1], 6), round(pt[0], 6)] for pt in best_route_row["waypoints"]],
                },
            }
        ],
    }

    best_route = {
        "summary": best_route_row.to_dict(),
        "geojson": geojson_line,
    }

    return df_ranking, best_route


def create_sample_jakarta_candidate_routes() -> List[Dict]:
    """Generate 3 realistic candidate routes in Jakarta (e.g. Senen/Johar Baru to Menteng).
    - Route 1: Jalur Pintas Gang/Lokal (Pendek tapi menembus hotspot rawan tawuran & pencahayaan minim).
    - Route 2: Jalur Arteri Utama (Sedikit memutar, terang, dekat pos polisi, sangat aman).
    - Route 3: Jalur Kolektor Menengah (Jalur alternatif kompromi).
    """
    # Route 1: Jalan Tembus Melalui Johar Baru (Lewat Hotspot Tawuran)
    # Start: Stasiun Senen (-6.1754, 106.8451) -> Finish: Cikini/Menteng (-6.1950, 106.8420)
    route_shortcut = {
        "route_id": "R01_SHORTCUT",
        "name": "Jalur Pintas Johar Baru (Gang & Lokal)",
        "road_type": "gang",
        "street_light_density": 0.20,
        "waypoints": [
            [-6.1754, 106.8451],
            [-6.1752, 106.8520],
            [-6.1751, 106.8651],  # Dekat cluster hotspot tawuran Galur (Risk ~0.90)
            [-6.1820, 106.8630],
            [-6.1880, 106.8550],
            [-6.1950, 106.8420],
        ],
    }

    # Route 2: Jalur Arteri Protokol (Jl. Kramat Raya / Salemba Raya)
    # Jalan protokol lebar, penerangan terang, dekat pos polisi & fasilitas publik
    route_arteri = {
        "route_id": "R02_ARTERI_SAFE",
        "name": "Jalur Protokol Kramat - Salemba (Arteri Terang)",
        "road_type": "arteri",
        "street_light_density": 0.85,
        "waypoints": [
            [-6.1754, 106.8451],
            [-6.1800, 106.8455],
            [-6.1850, 106.8460],  # Dekat Polres Jakarta Pusat
            [-6.1910, 106.8465],
            [-6.1945, 106.8450],
            [-6.1950, 106.8420],
        ],
    }

    # Route 3: Jalur Alternatif Kolektor (Jl. Kwitang - Diponegoro)
    route_collector = {
        "route_id": "R03_KOLEKTOR_MID",
        "name": "Jalur Kolektor Kwitang (Kolektor)",
        "road_type": "kolektor",
        "street_light_density": 0.60,
        "waypoints": [
            [-6.1754, 106.8451],
            [-6.1780, 106.8390],
            [-6.1840, 106.8385],
            [-6.1900, 106.8390],
            [-6.1950, 106.8420],
        ],
    }

    return [route_shortcut, route_arteri, route_collector]
