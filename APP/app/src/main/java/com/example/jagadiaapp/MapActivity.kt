package com.example.jagadiaapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

class MapActivity : AppCompatActivity() {

    // ---------- 1. PROPERTI ----------
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null

    private lateinit var topBar: View
    private lateinit var sheet: View
    private lateinit var alertCard: View
    private lateinit var tvRiskTitle: TextView
    private lateinit var tvRiskBadge: TextView
    private lateinit var chipNow: View
    private lateinit var chip1h: View
    private lateinit var chipTonight: View

    private var zonesJson: String = ""
    private var timeMode = TimeMode.TONIGHT

    // Pendengar lokasi dari GPS provider (yang diisi Extended Controls emulator)
    private var gpsListener: LocationListener? = null

    // key = nama properti di risk_zones.json
    private enum class TimeMode(val key: String, val title: String) {
        NOW("risk_now", "Risiko saat ini di sekitarmu"),
        PLUS_1H("risk_1h", "Risiko 1 jam ke depan"),
        TONIGHT("risk_night", "Risiko meningkat malam ini")
    }

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

        bindViews()
        setupEdgeToEdge()
        setupUi()

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { m ->
            map = m
            setupMapChrome(m)

            // Style terang supaya blob zona kelihatan jelas (sesuai desain)
            m.setStyle("https://tiles.openfreemap.org/styles/positron") { style ->
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(-6.219990, 106.903173)) // Pusat wilayah target
                    .zoom(16.0)
                    .build()

                addRiskLayer(style)   // zona dulu
                addRouteLayer(style)  // rute sesudahnya, supaya tampil di atas zona

                // Data dummy zona: assets/risk_zones.json
                runCatching {
                    zonesJson = assets.open("risk_zones.json").bufferedReader().use { it.readText() }
                    style.getSourceAs<GeoJsonSource>("risk-src")?.setGeoJson(zonesJson)
                }
                setTimeMode(timeMode)

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

    // ---------- 3. UI ----------
    private fun bindViews() {
        topBar = findViewById(R.id.topBar)
        sheet = findViewById(R.id.sheet)
        alertCard = findViewById(R.id.alertCard)
        tvRiskTitle = findViewById(R.id.tvRiskTitle)
        tvRiskBadge = findViewById(R.id.tvRiskBadge)
        chipNow = findViewById(R.id.chipNow)
        chip1h = findViewById(R.id.chip1h)
        chipTonight = findViewById(R.id.chipTonight)
    }

    /** Peta tampil sampai ke bawah status bar & nav bar, UI digeser sesuai inset. */
    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true

        val topBase = topBar.paddingTop
        val navBar = findViewById<View>(R.id.bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.updatePadding(top = topBase + bars.top)
            navBar.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun setupUi() {
        findViewById<View>(R.id.btnAlertClose).setOnClickListener { alertCard.visibility = View.GONE }

        chipNow.setOnClickListener { setTimeMode(TimeMode.NOW) }
        chip1h.setOnClickListener { setTimeMode(TimeMode.PLUS_1H) }
        chipTonight.setOnClickListener { setTimeMode(TimeMode.TONIGHT) }

        findViewById<EditText>(R.id.etSearch).setOnEditorActionListener { v, _, _ ->
            onSearchSubmitted(v.text.toString())
            false
        }

        findViewById<View>(R.id.navMap).isSelected = true

        // Bottom nav: Peta sedang aktif. TODO: buka halaman Riwayat & Laporkan.
        findViewById<View>(R.id.navHistory).setOnClickListener { /* TODO */ }
        findViewById<View>(R.id.navReport).setOnClickListener { /* TODO */ }
    }

    /** Logo & atribusi MapLibre dipindah ke bawah search bar supaya tidak ketutup bottom sheet. */
    private fun setupMapChrome(m: MapLibreMap) {
        m.uiSettings.isCompassEnabled = false
        m.uiSettings.logoGravity = Gravity.TOP or Gravity.START
        m.uiSettings.attributionGravity = Gravity.TOP or Gravity.START

        topBar.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            val top = bottom + dp(6)
            m.uiSettings.setLogoMargins(dp(16), top, 0, 0)
            m.uiSettings.setAttributionMargins(dp(16 + 84), top, 0, 0) // geser kalau tabrakan dengan logo
        }
    }

    private fun setTimeMode(mode: TimeMode) {
        timeMode = mode
        chipNow.isSelected = mode == TimeMode.NOW
        chip1h.isSelected = mode == TimeMode.PLUS_1H
        chipTonight.isSelected = mode == TimeMode.TONIGHT

        // Ganti properti yang dipakai untuk warna blob
        map?.style?.getLayerAs<CircleLayer>("risk-zone")
            ?.setProperties(PropertyFactory.circleColor(riskColorExpression(mode.key)))

        tvRiskTitle.text = mode.title
        showLevel(maxRisk(mode.key))
    }

    private fun showLevel(risk: Double) {
        val (label, bg, fg) = when {
            risk >= 0.7 -> Triple("Rawan", "#FBE0E0", "#D64545")
            risk >= 0.4 -> Triple("Waspada", "#FDEFD3", "#B7791F")
            else -> Triple("Aman", "#D7F0E3", "#1F7A4D")
        }
        tvRiskBadge.text = label
        tvRiskBadge.setTextColor(Color.parseColor(fg))
        tvRiskBadge.background.mutate().setTint(Color.parseColor(bg))
    }

    /** Risiko tertinggi di data (dummy). TODO: ganti dengan hasil prediksi untuk lokasi pengguna. */
    private fun maxRisk(key: String): Double = runCatching {
        FeatureCollection.fromJson(zonesJson).features()
            ?.mapNotNull { it.getNumberProperty(key)?.toDouble() }
            ?.maxOrNull()
    }.getOrNull() ?: 0.0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- 4. FUNGSI PETA ----------
    private fun onSearchSubmitted(query: String) {
        // TODO: geocoding `query`, lalu panggil onDestinationPicked(latLng)
    }

    private fun onDestinationPicked(latLng: LatLng) {
        // TODO: minta rute ke latLng, lalu panggil showRoute(geoJson)
    }

    private fun riskColorExpression(key: String): Expression =
        Expression.interpolate(
            Expression.linear(), Expression.get(key),
            Expression.stop(0.0, Expression.color(Color.parseColor("#2E9E6B"))),
            Expression.stop(0.5, Expression.color(Color.parseColor("#F5A623"))),
            Expression.stop(1.0, Expression.color(Color.parseColor("#E04B4B")))
        )

    /**
     * Zona = blob lembut (CircleLayer + blur), bukan poligon kotak.
     * Ukuran ditentukan properti `radius` (meter) per titik di risk_zones.json,
     * dan ikut membesar/mengecil mengikuti zoom supaya tetap mewakili jarak nyata.
     * Mau zona lebih kecil/besar: ubah `radius` di JSON, atau ubah circleBlur/circleOpacity di bawah.
     */
    private fun addRiskLayer(style: Style) {
        style.addSource(GeoJsonSource("risk-src"))

        // meter -> piksel. Tile MapLibre 512px, lintang Jakarta (~-6.2):
        //   zoom 10 = ~76 m/px, zoom 20 = ~0.074 m/px
        val radiusMeters = Expression.toNumber(
            Expression.coalesce(Expression.get("radius"), Expression.literal(100))
        )
        val radiusPx = Expression.interpolate(
            Expression.exponential(2), Expression.zoom(),
            Expression.stop(10, Expression.product(radiusMeters, Expression.literal(1.0 / 76.0))),
            Expression.stop(20, Expression.product(radiusMeters, Expression.literal(13.48)))
        )

        val layer = CircleLayer("risk-zone", "risk-src").withProperties(
            PropertyFactory.circleColor(riskColorExpression(timeMode.key)),
            PropertyFactory.circleRadius(radiusPx),
            PropertyFactory.circleBlur(1.0f),       // 1.0 = pinggiran paling halus
            PropertyFactory.circleOpacity(0.55f)
        )

        // Taruh di bawah label jalan supaya nama jalan tetap terbaca
        val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol) else style.addLayer(layer)
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
        val lc = map?.locationComponent ?: return
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(this, style)
                .useDefaultLocationEngine(false) // lokasi disuplai manual dari GPS di bawah
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE      // ganti ke CameraMode.TRACKING kalau kamera mau mengikuti titik biru
        lc.renderMode = RenderMode.COMPASS
        startGpsUpdates()
    }

    /** Ambil lokasi langsung dari GPS_PROVIDER lalu dorong ke titik biru. */
    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        if (gpsListener != null) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w("GPS_CHECK", "GPS provider mati. Nyalakan Location di Settings emulator.")
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d("GPS_CHECK", "update ${location.provider}: ${location.latitude}, ${location.longitude}")
                map?.locationComponent?.forceLocationUpdate(location)
            }

            // Wajib ada untuk Android lama (di bawah API 30)
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        gpsListener = listener
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)

        // Posisi GPS terakhir yang tersimpan (kalau ada) langsung dipakai
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            Log.d("GPS_CHECK", "last GPS: ${it.latitude}, ${it.longitude}")
            map?.locationComponent?.forceLocationUpdate(it)
        }
    }

    private fun stopGpsUpdates() {
        gpsListener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        gpsListener = null
    }

    // ---------- 5. LIFECYCLE (wajib diteruskan ke MapView) ----------
    override fun onStart() {
        super.onStart()
        mapView.onStart()
        if (map?.locationComponent?.isLocationComponentActivated == true) startGpsUpdates()
    }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() {
        super.onStop()
        mapView.onStop()
        stopGpsUpdates()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
