package com.example.jagadiaapp

import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Melacak posisi pengguna pada sebuah rute dan menghitung sisa rute di depannya.
 * Tidak bergantung pada peta, jadi mudah diuji.
 *
 * @param offRouteMeters jarak maksimal dari garis rute sebelum dianggap keluar jalur
 * @param arriveMeters   sisa jarak yang dianggap sudah sampai tujuan
 * @param searchWindow   berapa segmen ke depan yang dicek tiap update (menghindari lompat mundur)
 */
class RouteTracker(
    private val offRouteMeters: Double = 60.0,
    private val arriveMeters: Double = 20.0,
    private val searchWindow: Int = 80
) {
    enum class Status { NO_ROUTE, ON_ROUTE, OFF_ROUTE, ARRIVED }

    class Result(
        val status: Status,
        /** Sisa rute: titik pertama = posisi pengguna yang sudah menempel ke garis rute. */
        val remaining: List<LatLng>,
        val remainingMeters: Double
    )

    private var points: List<LatLng> = emptyList()
    private var started = false      // sudah pernah menempel ke rute?
    private var segIndex = 0         // segmen (titik i ke i+1) tempat pengguna berada
    private var segT = 0.0           // posisi di dalam segmen itu (0..1)
    private var lastRemaining: List<LatLng> = emptyList()

    val hasRoute: Boolean get() = points.size >= 2

    fun setRoute(route: List<LatLng>) {
        points = route
        started = false
        segIndex = 0
        segT = 0.0
        lastRemaining = emptyList()
    }

    fun clear() = setRoute(emptyList())

    fun update(pos: LatLng): Result {
        if (!hasRoute) return Result(Status.NO_ROUTE, emptyList(), 0.0)

        // Update pertama: cari di seluruh rute. Berikutnya: hanya dari posisi terakhir ke depan.
        val lastSeg = points.size - 2
        val from = if (started) segIndex else 0
        val to = if (started) min(lastSeg, segIndex + searchWindow) else lastSeg

        var bestSeg = from
        var bestDist = Double.MAX_VALUE
        var bestT = 0.0
        for (i in from..to) {
            val (t, d) = project(pos, points[i], points[i + 1])
            if (d < bestDist) {
                bestDist = d
                bestSeg = i
                bestT = t
            }
        }

        if (bestDist > offRouteMeters) {
            // Jauh dari garis rute: jangan dipotong, tampilkan sisa terakhir (atau rute penuh)
            val keep = if (lastRemaining.isNotEmpty()) lastRemaining else points
            return Result(Status.OFF_ROUTE, keep, length(keep))
        }

        // Di dalam segmen yang sama, posisi tidak boleh mundur karena GPS bergoyang
        val t = if (started && bestSeg == segIndex) max(bestT, segT) else bestT
        started = true
        segIndex = bestSeg
        segT = t

        val a = points[bestSeg]
        val b = points[bestSeg + 1]
        val snap = LatLng(
            a.latitude + (b.latitude - a.latitude) * t,
            a.longitude + (b.longitude - a.longitude) * t
        )

        val remaining = ArrayList<LatLng>(points.size - bestSeg)
        remaining.add(snap)
        for (j in bestSeg + 1 until points.size) remaining.add(points[j])
        lastRemaining = remaining

        val meters = length(remaining)
        val status = if (meters <= arriveMeters) Status.ARRIVED else Status.ON_ROUTE
        return Result(status, remaining, meters)
    }

    // ---------- hitungan jarak (pendekatan datar, cukup akurat untuk jarak pendek) ----------

    private fun length(route: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until route.size - 1) sum += distance(route[i], route[i + 1])
        return sum
    }

    private fun distance(a: LatLng, b: LatLng): Double {
        val kx = 111320.0 * cos(Math.toRadians(a.latitude))
        return hypot((b.longitude - a.longitude) * kx, (b.latitude - a.latitude) * 110540.0)
    }

    /** Proyeksikan titik p ke segmen a-b. Mengembalikan (t di 0..1, jarak dalam meter). */
    private fun project(p: LatLng, a: LatLng, b: LatLng): Pair<Double, Double> {
        val kx = 111320.0 * cos(Math.toRadians(a.latitude))
        val ky = 110540.0
        val bx = (b.longitude - a.longitude) * kx
        val by = (b.latitude - a.latitude) * ky
        val px = (p.longitude - a.longitude) * kx
        val py = (p.latitude - a.latitude) * ky
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        return Pair(t, hypot(px - t * bx, py - t * by))
    }
}
