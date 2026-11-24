package com.example.jagadiaapp

import org.maplibre.android.geometry.LatLng

data class LocationCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}

data class LocationResult(
    val name: String,
    val address: String,
    val coordinate: LocationCoordinate
)

data class LocationSearchResponse(
    val query: String,
    val results: List<LocationResult>,
    val message: String
)

/**
 * Kontrak endpoint pencarian lokasi khusus wilayah Jakarta.
 * GET /api/v1/locations/search?q={keyword}
 */
interface LocationSearchEndpoint {
    suspend fun searchJakartaLocations(keyword: String): LocationSearchResponse
}