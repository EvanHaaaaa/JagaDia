package com.example.jagadiaapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JakartaLocationSearchService : LocationSearchEndpoint {

    private val jakartaPlaces = listOf(
        LocationResult(
            name = "Stasiun Klender / Area Klender",
            address = "Jl. Bekasi Timur Raya, Kel. Klender, Kec. Duren Sawit, Jakarta Timur, DKI Jakarta",
            coordinate = LocationCoordinate(-6.2127, 106.9002)
        ),
        LocationResult(
            name = "Area Rute Duren Sawit",
            address = "Kec. Duren Sawit, Jakarta Timur, DKI Jakarta",
            coordinate = LocationCoordinate(-6.221268, 106.906620)
        ),
        LocationResult(
            name = "Kelurahan Kayu Putih",
            address = "Kec. Pulogadung, Jakarta Timur, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1860, 106.8874)
        ),
        LocationResult(
            name = "Kelurahan Rawamangun",
            address = "Kec. Pulogadung, Jakarta Timur, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1987, 106.8878)
        ),
        LocationResult(
            name = "Kelurahan Galur",
            address = "Kec. Johar Baru, Jakarta Pusat, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1752, 106.8652)
        ),
        LocationResult(
            name = "Kelurahan Senen",
            address = "Kec. Senen, Jakarta Pusat, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1755, 106.8430)
        ),
        LocationResult(
            name = "Kelurahan Karet Tengsin",
            address = "Kec. Tanah Abang, Jakarta Pusat, DKI Jakarta",
            coordinate = LocationCoordinate(-6.2041, 106.8203)
        ),
        LocationResult(
            name = "Kelurahan Tanjung Priok",
            address = "Kec. Tanjung Priok, Jakarta Utara, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1107, 106.8846)
        ),
        LocationResult(
            name = "Kelurahan Pasar Minggu",
            address = "Kec. Pasar Minggu, Jakarta Selatan, DKI Jakarta",
            coordinate = LocationCoordinate(-6.2857, 106.8415)
        ),
        LocationResult(
            name = "Kelurahan Gunung (Kebayoran Baru)",
            address = "Kec. Kebayoran Baru, Jakarta Selatan, DKI Jakarta",
            coordinate = LocationCoordinate(-6.2348, 106.7884)
        ),
        LocationResult(
            name = "Kelurahan Tanah Sereal",
            address = "Kec. Tambora, Jakarta Barat, DKI Jakarta",
            coordinate = LocationCoordinate(-6.1584, 106.8098)
        )
    )

    override suspend fun searchJakartaLocations(keyword: String): LocationSearchResponse =
        withContext(Dispatchers.Default) {
            val cleaned = keyword.trim()
            if (cleaned.isEmpty()) {
                return@withContext LocationSearchResponse(
                    query = keyword,
                    results = emptyList(),
                    message = "Lokasi tidak ditemukan"
                )
            }

            // Jika pengguna secara eksplisit mengetik nama daerah di luar Jakarta, kembalikan kosong
            val nonJakartaHints = listOf("bandung", "surabaya", "bogor", "depok", "tangerang", "bekasi kota", "yogyakarta", "bali")
            if (nonJakartaHints.any { cleaned.contains(it, ignoreCase = true) } &&
                !cleaned.contains("jakarta", ignoreCase = true)
            ) {
                return@withContext LocationSearchResponse(
                    query = cleaned,
                    results = emptyList(),
                    message = "Lokasi tidak ditemukan"
                )
            }

            val matched = jakartaPlaces.filter { item ->
                val isInJakarta = item.address.contains("Jakarta", ignoreCase = true)
                val matchesQuery = item.name.contains(cleaned, ignoreCase = true) ||
                        item.address.contains(cleaned, ignoreCase = true)
                isInJakarta && matchesQuery
            }

            if (matched.isEmpty()) {
                LocationSearchResponse(
                    query = cleaned,
                    results = emptyList(),
                    message = "Lokasi tidak ditemukan"
                )
            } else {
                LocationSearchResponse(
                    query = cleaned,
                    results = matched,
                    message = "OK"
                )
            }
        }
}
