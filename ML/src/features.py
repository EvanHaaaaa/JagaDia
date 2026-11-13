"""Feature engineering module for spatio-temporal crime prediction.
Provides functions to extract temporal indicators, spatial proximities, and risk indices.
"""

import numpy as np
import pandas as pd
from typing import Optional

def extract_temporal_features(df: pd.DataFrame, timestamp_col: str = "timestamp") -> pd.DataFrame:
    """Extract temporal attributes from a datetime column.

    Features created:
    - hour (0-23)
    - day_of_week (0=Monday, 6=Sunday)
    - is_weekend (1 if Saturday or Sunday, else 0)
    - is_night (1 if between 18:00 and 06:00, else 0)
    - month (1-12)
    """
    df = df.copy()
    if not np.issubdtype(df[timestamp_col].dtype, np.datetime64):
        df[timestamp_col] = pd.to_datetime(df[timestamp_col])

    df["hour"] = df[timestamp_col].dt.hour
    df["day_of_week"] = df[timestamp_col].dt.dayofweek
    df["is_weekend"] = df["day_of_week"].apply(lambda d: 1 if d in [5, 6] else 0)
    df["is_night"] = df["hour"].apply(lambda h: 1 if (h >= 18 or h < 6) else 0)
    df["month"] = df[timestamp_col].dt.month

    # Cyclical encoding for hour (useful for neural networks & tree splits)
    df["hour_sin"] = np.sin(2 * np.pi * df["hour"] / 24.0)
    df["hour_cos"] = np.cos(2 * np.pi * df["hour"] / 24.0)

    return df

def calculate_haversine_distance(
    lat1: np.ndarray, lon1: np.ndarray, lat2: float, lon2: float
) -> np.ndarray:
    """Calculate distance in meters between coordinates using Haversine formula."""
    R = 6371000  # Radius of earth in meters
    phi1 = np.radians(lat1)
    phi2 = np.radians(lat2)
    delta_phi = np.radians(lat2 - lat1)
    delta_lambda = np.radians(lon2 - lon1)

    a = (
        np.sin(delta_phi / 2.0) ** 2
        + np.cos(phi1) * np.cos(phi2) * np.sin(delta_lambda / 2.0) ** 2
    )
    c = 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))
    return R * c
