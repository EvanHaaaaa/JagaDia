package com.example.jagadiaapp

import android.Manifest
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnLayout
import kotlin.math.abs
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

    // Panel detail yang ditarik dari handle
    private lateinit var rootView: View
    private lateinit var detailSheet: View
    private lateinit var detailContent: View
    private lateinit var scrim: View
    private lateinit var myLocationBtn: View
    private lateinit var backCallback: OnBackPressedCallback
    private var sheetProgress = 0f        // 0 = tertutup, 1 = terbuka penuh
    private var expandedHeightPx = 0
    private var sheetAnimator: ValueAnimator? = null

    // Rute aktif: garis di peta memendek mengikuti posisi pengguna
    private val routeTracker = RouteTracker()
    private var followUser = false   // kamera ikut posisi saat rute aktif

    private var zonesJson: String = ""
    private var timeMode = TimeMode.TONIGHT

    // Pendengar lokasi dari GPS provider (yang diisi Extended Controls emulator)
    private var gpsListener: LocationListener? = null
    private var lastGps: Location? = null

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

        mapView = findViewById<MapView>(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { m ->
            map = m
            setupMapChrome(m)

            // Peta digeser tangan -> berhenti mengikuti (tombol Lokasiku untuk mengikuti lagi)
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) followUser = false
            }

            // Style terang supaya blob zona kelihatan jelas (sesuai desain)
            m.setStyle("https://tiles.openfreemap.org/styles/positron") { style ->
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(-6.219990, 106.903173)) // Pusat wilayah target
                    .zoom(16.0)
                    .build()

                addRiskLayer(style)   // zona dulu
                addRouteLayer(style)  // rute sesudahnya, supaya tampil di atas zona
                addDestinationLayer(style)

                // Rute uji: assets/route.json (nanti diganti hasil OpenRouteService lewat showRoute)
                runCatching {
                    val json = assets.open("route.json").bufferedReader().use { it.readText() }
                    val pts = parseRoutePoints(json)
                    if (pts.size >= 2) setRoute(pts, follow = true)
                }

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
        topBar = findViewById<View>(R.id.topBar)
        sheet = findViewById<View>(R.id.sheet)
        alertCard = findViewById<View>(R.id.alertCard)
        tvRiskTitle = findViewById<TextView>(R.id.tvRiskTitle)
        tvRiskBadge = findViewById<TextView>(R.id.tvRiskBadge)
        chipNow = findViewById<View>(R.id.chipNow)
        chip1h = findViewById<View>(R.id.chip1h)
        chipTonight = findViewById<View>(R.id.chipTonight)

        rootView = findViewById<View>(R.id.root)
        detailSheet = findViewById<View>(R.id.detailSheet)
        detailContent = findViewById<View>(R.id.detailContent)
        scrim = findViewById<View>(R.id.scrim)
        myLocationBtn = findViewById<View>(R.id.btnMyLocation)

        // Show Drop Down
        searchDropdownCard = findViewById<View>(R.id.searchDropdownCard)
        searchDropdownList = findViewById<LinearLayout>(R.id.searchDropdownList)
    }

    /** Peta tampil sampai ke bawah status bar & nav bar, UI digeser sesuai inset. */
    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true

        val topBase = topBar.paddingTop
        val navBar = findViewById<View>(R.id.bottomNav)
        val detailBase = detailContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _: View, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.updatePadding(top = topBase + bars.top)
            navBar.updatePadding(bottom = bars.bottom)
            detailContent.updatePadding(bottom = detailBase + bars.bottom)
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

        findViewById<View>(R.id.btnMyLocation).setOnClickListener { centerOnMyLocation() }

        findViewById<View>(R.id.navMap).isSelected = true

        // Bottom nav: Peta sedang aktif. TODO: buka halaman Riwayat & Laporkan.
        findViewById<View>(R.id.navHistory).setOnClickListener { /* TODO */ }
        findViewById<View>(R.id.navReport).setOnClickListener { /* TODO */ }

        setupSheetDrag()
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

    // ---------- PANEL DETAIL (tarik handle ke atas) ----------
    private fun setupSheetDrag() {
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = animateSheetTo(0f)
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        rootView.doOnLayout {
            val compactH = rootView.height - sheet.top
            expandedHeightPx = maxOf((rootView.height * 0.62f).toInt(), compactH + dp(260))
            detailSheet.layoutParams = detailSheet.layoutParams.apply { height = expandedHeightPx }
            applySheetProgress(0f)
        }

        attachSheetDrag(findViewById(R.id.handleZone))
        attachSheetDrag(findViewById(R.id.detailHandleZone))
        scrim.setOnClickListener { animateSheetTo(0f) }
    }

    /** Jarak (px) yang ditempuh panel dari posisi kompak sampai terbuka penuh. */
    private fun sheetRange(): Float =
        (expandedHeightPx - (rootView.height - sheet.top)).toFloat().coerceAtLeast(1f)

    /** p = 0 (panel kompak) sampai 1 (panel detail terbuka penuh). */
    private fun applySheetProgress(p: Float) {
        sheetProgress = p
        detailSheet.translationY = (1f - p) * sheetRange()
        detailSheet.alpha = (p * 2.5f).coerceAtMost(1f)
        detailSheet.visibility = if (p <= 0f) View.INVISIBLE else View.VISIBLE

        scrim.alpha = p * 0.75f
        scrim.visibility = if (p <= 0f) View.GONE else View.VISIBLE

        // Kartu peringatan dan tombol lokasi memudar saat panel naik
        val fade = 1f - (p * 2f).coerceAtMost(1f)
        alertCard.alpha = fade
        myLocationBtn.alpha = fade

        backCallback.isEnabled = p > 0f
    }

    private fun animateSheetTo(target: Float) {
        sheetAnimator?.cancel()
        sheetAnimator = ValueAnimator.ofFloat(sheetProgress, target).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { applySheetProgress(it.animatedValue as Float) }
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSheetDrag(zone: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startRawY = 0f
        var startProgress = 0f
        var downTime = 0L
        var tracker: VelocityTracker? = null

        // Pakai koordinat layar (rawY) supaya kecepatan benar walau view-nya ikut bergerak
        fun feed(ev: MotionEvent) {
            val copy = MotionEvent.obtain(ev)
            copy.setLocation(ev.rawX, ev.rawY)
            tracker?.addMovement(copy)
            copy.recycle()
        }

        zone.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheetAnimator?.cancel()
                    startRawY = ev.rawY
                    startProgress = sheetProgress
                    downTime = ev.eventTime
                    tracker = VelocityTracker.obtain()
                    feed(ev)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    feed(ev)
                    val p = (startProgress + (startRawY - ev.rawY) / sheetRange()).coerceIn(0f, 1f)
                    applySheetProgress(p)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    feed(ev)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null

                    val isTap = ev.actionMasked == MotionEvent.ACTION_UP &&
                            abs(ev.rawY - startRawY) < slop && ev.eventTime - downTime < 300
                    val target = when {
                        isTap -> {
                            v.performClick()
                            if (startProgress < 0.5f) 1f else 0f   // ketuk handle = buka / tutup
                        }
                        vy < -800f -> 1f                            // lempar ke atas
                        vy > 800f -> 0f                             // lempar ke bawah
                        else -> if (sheetProgress > 0.5f) 1f else 0f
                    }
                    animateSheetTo(target)
                    true
                }
                else -> false
            }
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
        lifecycleScope.launch {
            val response = locationSearchEndpoint.searchJakartaLocations(query)
            searchDropdownList.removeAllViews()

            if (response.results.isEmpty()) {
                searchDropdownCard.visibility = View.GONE
                Toast.makeText(this@MapActivity, response.message, Toast.LENGTH_SHORT).show()
                return@launch
            }

            response.results.forEach { item ->
                val row = LinearLayout(this@MapActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    isClickable = true
                    isFocusable = true
                    val outValue = android.util.TypedValue()
                    this.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }

                val tvName = TextView(this@MapActivity).apply {
                    text = item.name
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

                val tvAddressAndCoord = TextView(this@MapActivity).apply {
                    text = "${item.address}\n(${item.coordinate.latitude}, ${item.coordinate.longitude})"
                    setTextColor(Color.parseColor("#4B5563"))
                    textSize = 12f
                }

                row.addView(tvName)
                row.addView(tvAddressAndCoord)

                row.setOnClickListener {
                    val latLng = item.coordinate.toLatLng()
                    findViewById<EditText>(R.id.etSearch).setText(item.name)
                    searchDropdownCard.visibility = View.GONE
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16.0), 600)
                    onDestinationPicked(latLng)
                }

                searchDropdownList.addView(row)
            }

            searchDropdownCard.visibility = View.VISIBLE
        }
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

    /** Dipakai untuk hasil OpenRouteService: tampilkan rute dan pas-kan kamera ke seluruh rute. */
    fun showRoute(geoJson: String) {
        val pts = parseRoutePoints(geoJson)
        if (pts.size < 2) return
        setRoute(pts, follow = false)
        map?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 120)
        )
    }

    // ---------- RUTE AKTIF: garis memendek saat bergerak ----------

    /** Ambil titik-titik LineString dari GeoJSON (FeatureCollection atau satu Feature). */
    private fun parseRoutePoints(geoJson: String): List<LatLng> {
        val line = runCatching {
            FeatureCollection.fromJson(geoJson).features()
                ?.firstNotNullOfOrNull { it.geometry() as? LineString }
        }.getOrNull() ?: runCatching {
            Feature.fromJson(geoJson).geometry() as? LineString
        }.getOrNull()
        return line?.coordinates()?.map { LatLng(it.latitude(), it.longitude()) } ?: emptyList()
    }

    private fun setRoute(points: List<LatLng>, follow: Boolean) {
        routeTracker.setRoute(points)
        drawRoute(points)

        val dest = points.last()
        map?.style?.getSourceAs<GeoJsonSource>("dest-src")
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude)))

        followUser = follow
        lastGps?.let { updateRouteProgress(it) }
    }

    private fun drawRoute(points: List<LatLng>) {
        val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        map?.style?.getSourceAs<GeoJsonSource>("route-src")
            ?.setGeoJson(Feature.fromGeometry(line))
    }

    private fun clearRoute() {
        routeTracker.clear()
        followUser = false
        val empty = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        map?.style?.getSourceAs<GeoJsonSource>("route-src")?.setGeoJson(empty)
        map?.style?.getSourceAs<GeoJsonSource>("dest-src")?.setGeoJson(empty)
    }

    /** Dipanggil tiap lokasi baru: potong bagian rute yang sudah dilewati. */
    private fun updateRouteProgress(location: Location) {
        if (!routeTracker.hasRoute) return
        val result = routeTracker.update(LatLng(location.latitude, location.longitude))
        when (result.status) {
            RouteTracker.Status.ON_ROUTE -> {
                drawRoute(result.remaining)
                if (followUser) {
                    map?.animateCamera(CameraUpdateFactory.newLatLng(result.remaining.first()), 800)
                }
            }
            RouteTracker.Status.ARRIVED -> {
                clearRoute()
                Toast.makeText(this, "Kamu sudah sampai tujuan", Toast.LENGTH_LONG).show()
            }
            RouteTracker.Status.OFF_ROUTE -> {
                // TODO: hitung ulang rute dari posisi sekarang lewat OpenRouteService
            }
            RouteTracker.Status.NO_ROUTE -> Unit
        }
    }

    private fun addDestinationLayer(style: Style) {
        style.addSource(GeoJsonSource("dest-src"))
        style.addLayer(
            CircleLayer("dest-layer", "dest-src").withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#0F4C5C"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        )
    }

    /** Tombol "Lokasiku": pusatkan kamera ke titik biru. */
    private fun centerOnMyLocation() {
        val m = map ?: return

        val hasPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            // Belum diizinkan (misalnya tadi memilih "Nanti saja"): minta izinnya sekarang
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val loc = lastGps
        if (loc == null) {
            Toast.makeText(
                this, "Lokasi belum ditemukan. Nyalakan GPS lalu coba lagi.", Toast.LENGTH_SHORT
            ).show()
            return
        }

        followUser = true
        m.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(loc.latitude, loc.longitude),
                maxOf(m.cameraPosition.zoom, 16.0)
            ),
            600
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
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastGps = location
                map?.locationComponent?.forceLocationUpdate(location)
                updateRouteProgress(location)
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
            lastGps = it
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

    // Backend: Untuk search point
    private val locationSearchEndpoint: LocationSearchEndpoint = JakartaLocationSearchService()

    // Daftar Hasil Search
    private lateinit var searchDropdownCard: View
    private lateinit var searchDropdownList: LinearLayout
}
