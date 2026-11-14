package com.example.jagadiaapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

// Semua yang ada di bawah ini berada DI DALAM kurung kurawal class MapActivity.
class MapActivity : AppCompatActivity() {

    // ---------- 1. PROPERTI (di bagian paling atas class) ----------
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) map?.style?.let { enableLocation(it) }
    }

    // ---------- 2. onCreate ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this) // wajib sebelum setContentView
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { m ->
            map = m

            m.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(-6.219990, 106.903173)) // Pusat wilayah target
                    .zoom(13.0)
                    .build()

                addRiskLayer(style)   // zona dulu
                addRouteLayer(style)  // rute sesudahnya, supaya tampil di atas zona

                // Data dummy zona (opsional): file assets/risk_zones.json
                runCatching {
                    val json = assets.open("risk_zones.json").bufferedReader().use { it.readText() }
                    style.getSourceAs<GeoJsonSource>("risk-src")?.setGeoJson(json)
                }

                // Izin lokasi
                val hasFine = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) enableLocation(style)
            }

            m.addOnMapClickListener { latLng ->
                onDestinationPicked(latLng)
                true
            }
        }
    }

    // ---------- 3. FUNGSI-FUNGSI BUATANMU (urutan bebas, asal di dalam class) ----------
    private fun onDestinationPicked(latLng: LatLng) {
        // TODO: minta rute ke latLng, lalu panggil showRoute(geoJson)
    }

    private fun addRiskLayer(style: Style) {
        style.addSource(GeoJsonSource("risk-src"))
        style.addLayer(
            FillLayer("risk-fill", "risk-src").withProperties(
                PropertyFactory.fillOpacity(0.35f),
                PropertyFactory.fillColor(
                    Expression.interpolate(
                        Expression.linear(), Expression.get("risk"),
                        Expression.stop(0.0, Expression.color(Color.parseColor("#2ECC71"))),
                        Expression.stop(0.5, Expression.color(Color.parseColor("#F1C40F"))),
                        Expression.stop(1.0, Expression.color(Color.parseColor("#E74C3C")))
                    )
                )
            )
        )
    }

    private fun addRouteLayer(style: Style) {
        style.addSource(GeoJsonSource("route-src"))
        style.addLayer(
            LineLayer("route-line", "route-src").withProperties(
                PropertyFactory.lineColor("#1A73E8"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    fun showRoute(geoJson: String) {
        map?.style?.getSourceAs<GeoJsonSource>("route-src")?.setGeoJson(geoJson)

        val line = FeatureCollection.fromJson(geoJson)
            .features()?.firstOrNull()?.geometry() as? LineString
        val pts = line?.coordinates()
            ?.map { LatLng(it.latitude(), it.longitude()) } ?: return
        if (pts.size < 2) return

        map?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 120)
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation(style: Style) {
        map?.locationComponent?.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(this@MapActivity, style).build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }

    // ---------- 4. LIFECYCLE (wajib diteruskan ke MapView) ----------
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
} // <-- kurung kurawal penutup class. Tidak boleh ada fungsi di bawah ini.