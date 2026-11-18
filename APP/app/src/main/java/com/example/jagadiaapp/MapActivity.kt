package com.example.jagadiaapp

import android.Manifest
import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import com.google.android.material.card.MaterialCardView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
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
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private var lastGps: Location? = null
    private var trackingListenerAdded = false

    // Panel detail (ditarik dari handle bottom sheet)
    private lateinit var collapsedColumn: View
    private lateinit var detailSheet: View
    private lateinit var detailScrim: View
    private lateinit var detailHeader: View
    private lateinit var detailScroll: ScrollView
    private lateinit var tvDetailBadge: TextView
    private lateinit var tvDetailArea: TextView
    private lateinit var tvDetailSub: TextView
    private lateinit var tvDetailScore: TextView
    private lateinit var tvDetailLevel: TextView
    private lateinit var tvDetailAdvice: TextView
    private lateinit var tvDetailLighting: TextView
    private lateinit var tvDetailLightingDesc: TextView
    private lateinit var tvDetailTime: TextView
    private lateinit var tvIncidentCount: TextView
    private lateinit var incidentList: LinearLayout

    private var detailBottomBase = 0
    private var detailExpanded = false
    private var dragging = false
    private var dragMoved = false
    private var dragStartY = 0f
    private var dragStartProgress = 0f
    private var expandProgress = 0f   // 0 = kartu ringkas, 1 = kartu penuh (detail)
    private var collapsedH = 0
    private var expandedH = 0
    private var progressAnimator: ValueAnimator? = null
    private var velocity: VelocityTracker? = null
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = animateDetailTo(0f)
    }

    private data class Incident(val type: String, val place: String, val whenText: String)
    private data class Zone(
        val area: String,
        val lat: Double,
        val lng: Double,
        val risks: Map<String, Double>,
        val lighting: String,
        val incidents: List<Incident>
    )
    private var zones: List<Zone> = emptyList()

    // key = nama properti di risk_zones.json
    private enum class TimeMode(
        val key: String,
        val title: String,
        val chip: String,      // label singkat di panel detail
        val forText: String    // subjudul di panel detail
    ) {
        NOW("risk_now", "Risiko saat ini di sekitarmu", "Sekarang", "Prediksi untuk saat ini"),
        PLUS_1H("risk_1h", "Risiko 1 jam ke depan", "+1 Jam", "Prediksi untuk 1 jam ke depan"),
        TONIGHT("risk_night", "Risiko meningkat malam ini", "Malam Ini", "Prediksi untuk malam ini")
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
                    zones = parseZones(zonesJson)
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

        collapsedColumn = findViewById(R.id.collapsedColumn)
        detailSheet = findViewById(R.id.detailSheet)
        detailScrim = findViewById(R.id.detailScrim)
        detailHeader = findViewById(R.id.detailHeader)
        detailScroll = findViewById(R.id.detailScroll)
        tvDetailBadge = findViewById(R.id.tvDetailBadge)
        tvDetailArea = findViewById(R.id.tvDetailArea)
        tvDetailSub = findViewById(R.id.tvDetailSub)
        tvDetailScore = findViewById(R.id.tvDetailScore)
        tvDetailLevel = findViewById(R.id.tvDetailLevel)
        tvDetailAdvice = findViewById(R.id.tvDetailAdvice)
        tvDetailLighting = findViewById(R.id.tvDetailLighting)
        tvDetailLightingDesc = findViewById(R.id.tvDetailLightingDesc)
        tvDetailTime = findViewById(R.id.tvDetailTime)
        tvIncidentCount = findViewById(R.id.tvIncidentCount)
        incidentList = findViewById(R.id.incidentList)
    }

    /** Peta tampil sampai ke bawah status bar & nav bar, UI digeser sesuai inset. */
    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true

        val topBase = topBar.paddingTop
        detailBottomBase = detailSheet.paddingBottom
        val navBar = findViewById<View>(R.id.bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.updatePadding(top = topBase + bars.top)
            navBar.updatePadding(bottom = bars.bottom)
            detailSheet.updatePadding(bottom = detailBottomBase + bars.bottom)
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

        setupDetailSheet()
    }

    /** Tombol Lokasiku terisi (teal) saat kamera mengikuti pengguna, putih saat tidak. */
    private fun updateLocationButton(tracking: Boolean) {
        val card = findViewById<MaterialCardView>(R.id.btnMyLocation)
        val icon = findViewById<ImageView>(R.id.ivMyLocation)
        val bg = ContextCompat.getColor(this, if (tracking) R.color.jd_teal_dark else R.color.jd_sheet)
        val fg = ContextCompat.getColor(this, if (tracking) R.color.jd_sheet else R.color.jd_teal_dark)
        card.setCardBackgroundColor(bg)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(fg))
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
        if (detailExpanded) refreshDetail()
    }

    /** (label, warna latar, warna teks) untuk sebuah nilai risiko. */
    private fun levelStyle(risk: Double) = when {
        risk >= 0.7 -> Triple("Rawan", "#FBE0E0", "#D64545")
        risk >= 0.4 -> Triple("Waspada", "#FDEFD3", "#B7791F")
        else -> Triple("Aman", "#D7F0E3", "#1F7A4D")
    }

    private fun showLevel(risk: Double) {
        val (label, bg, fg) = levelStyle(risk)
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

        // Lepas mode tracking, kalau tidak kamera akan ditarik balik ke posisi pengguna
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }
            ?.cameraMode = CameraMode.NONE

        map?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 120)
        )
    }

    /** Tombol "Lokasiku": pusatkan kamera ke titik biru DAN aktifkan mode tracking (kamera mengikuti). */
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

        val lc = m.locationComponent
        if (!lc.isLocationComponentActivated) return

        // Pastikan titik biru ada di posisi terbaru, lalu pindahkan kamera sambil masuk mode tracking.
        // Selama tracking, kamera ikut bergerak tiap lokasi baru; geser peta manual = tracking berhenti.
        lc.forceLocationUpdate(loc)
        lc.setCameraMode(
            CameraMode.TRACKING,
            600L,                              // durasi animasi ke lokasi (ms)
            maxOf(m.cameraPosition.zoom, 16.0), // zoom: minimal 16
            null,                              // bearing: biarkan
            null,                              // tilt: biarkan
            null
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
        lc.cameraMode = CameraMode.TRACKING      // awal: kamera mengikuti titik biru
        lc.renderMode = RenderMode.COMPASS

        // Sinkronkan tampilan tombol dengan status tracking (mis. berhenti saat peta digeser manual)
        if (!trackingListenerAdded) {
            trackingListenerAdded = true
            lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() = updateLocationButton(false)
                override fun onCameraTrackingChanged(currentMode: Int) =
                    updateLocationButton(currentMode != CameraMode.NONE)
            })
        }
        updateLocationButton(true)
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

    // ---------- 4b. PANEL DETAIL (tarik handle bottom sheet ke atas) ----------
    private fun parseZones(json: String): List<Zone> = runCatching {
        val arr = JSONObject(json).getJSONArray("features")
        (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            val inc = p.optJSONArray("incidents")
            Zone(
                area = p.optString("area", p.optString("name", "Area ini")),
                lng = c.getDouble(0),
                lat = c.getDouble(1),
                risks = TimeMode.values().associate { it.key to p.optDouble(it.key, 0.0) },
                lighting = p.optString("lighting", "Memadai"),
                incidents = (0 until (inc?.length() ?: 0)).map { j ->
                    inc!!.getJSONObject(j).let {
                        Incident(it.optString("type"), it.optString("place"), it.optString("when"))
                    }
                }
            )
        }
    }.getOrDefault(emptyList())

    /** Zona terdekat dari posisi GPS (kalau belum ada GPS: dari titik tengah kamera). */
    private fun nearestZone(): Zone? {
        if (zones.isEmpty()) return null
        val ref = lastGps?.let { LatLng(it.latitude, it.longitude) }
            ?: map?.cameraPosition?.target
            ?: return zones.first()
        val out = FloatArray(1)
        return zones.minByOrNull {
            Location.distanceBetween(ref.latitude, ref.longitude, it.lat, it.lng, out)
            out[0]
        }
    }

    /** Isi panel detail dengan data zona terdekat + mode waktu yang sedang dipilih. */
    private fun refreshDetail() {
        val z = nearestZone()
        val risk = z?.risks?.get(timeMode.key) ?: maxRisk(timeMode.key)
        val (label, bg, fg) = levelStyle(risk)
        val fgColor = Color.parseColor(fg)
        val muted = ContextCompat.getColor(this, R.color.jd_muted)

        tvDetailBadge.text = label
        tvDetailBadge.setTextColor(fgColor)
        tvDetailBadge.background.mutate().setTint(Color.parseColor(bg))

        tvDetailArea.text = z?.area ?: "Area sekitarmu"
        tvDetailSub.text = timeMode.forText

        // Skor keamanan: makin tinggi risiko, makin rendah skor
        val safety = ((1.0 - risk) * 100).roundToInt().coerceIn(0, 100)
        val scoreText = SpannableString("$safety/100").apply {
            val start = safety.toString().length
            setSpan(RelativeSizeSpan(0.55f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.NORMAL), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvDetailScore.text = scoreText
        (tvDetailScore.background.mutate() as? GradientDrawable)?.setStroke(dp(3), fgColor)

        tvDetailLevel.text = label
        tvDetailAdvice.text = when (label) {
            "Rawan" -> "Hindari gang sepi dan pilih jalan utama"
            "Waspada" -> "Tetap waspada dan hindari jalan yang sepi"
            else -> "Area relatif aman, tetap jaga barang bawaanmu"
        }

        val lighting = z?.lighting ?: "Memadai"
        tvDetailLighting.text = lighting
        tvDetailLightingDesc.text = when (lighting) {
            "Terbatas" -> "Beberapa gang belum memiliki penerangan yang memadai"
            "Baik" -> "Penerangan jalan merata di area ini"
            else -> "Sebagian besar ruas jalan sudah cukup terang"
        }
        tvDetailTime.text = timeMode.chip

        // Riwayat kejadian
        val items = z?.incidents.orEmpty()
        tvIncidentCount.text = "${items.size} Laporan"
        incidentList.removeAllViews()
        if (items.isEmpty()) {
            incidentList.addView(TextView(this).apply {
                text = "Belum ada laporan kejadian di area ini"
                textSize = 12f
                setTextColor(muted)
                setPadding(dp(14), dp(16), dp(14), dp(16))
            })
        }
        val inflater = LayoutInflater.from(this)
        items.forEachIndexed { i, inc ->
            if (i > 0) {
                incidentList.addView(
                    View(this).apply {
                        setBackgroundColor(ContextCompat.getColor(this@MapActivity, R.color.jd_divider))
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                )
            }
            val row = inflater.inflate(R.layout.item_incident, incidentList, false)
            row.findViewById<TextView>(R.id.tvIncType).text = inc.type
            row.findViewById<TextView>(R.id.tvIncPlace).text = inc.place
            row.findViewById<TextView>(R.id.tvIncWhen).text = inc.whenText
            incidentList.addView(row)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDetailSheet() {
        // Satu kartu saja: tarik di mana pun yang bukan tombol/scroll (handle, judul, header detail)
        sheet.setOnTouchListener { _, ev -> onDetailDrag(ev) }
        detailScrim.setOnClickListener { animateDetailTo(0f) }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    /** Hitung tinggi kartu ringkas & kartu penuh. Hanya valid saat kartu sedang ringkas. */
    private fun measureSheetRange() {
        val root = findViewById<View>(R.id.root)
        if (expandProgress == 0f && sheet.height > 0) collapsedH = sheet.height
        expandedH = maxOf((root.height * 0.64f).toInt(), collapsedH + dp(160))
    }

    private fun onDetailDrag(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                progressAnimator?.cancel()
                dragging = true
                dragMoved = false
                dragStartY = ev.rawY
                dragStartProgress = expandProgress
                if (expandProgress == 0f) measureSheetRange()
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
                if (!detailExpanded) refreshDetail()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dy = ev.rawY - dragStartY
                if (!dragMoved && abs(dy) > touchSlop) dragMoved = true
                val range = (expandedH - collapsedH).toFloat()
                if (dragMoved && range > 0f) {
                    // tarik ke atas (dy negatif) = kartu makin tinggi
                    applyProgress(dragStartProgress - dy / range)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(ev)
                velocity?.computeCurrentVelocity(1000)
                val vy = velocity?.yVelocity ?: 0f
                velocity?.recycle()
                velocity = null

                val expand = when {
                    // ketukan: buka (kalau sudah terbuka, tetap terbuka)
                    !dragMoved -> ev.actionMasked == MotionEvent.ACTION_UP || detailExpanded
                    abs(vy) > 800f -> vy < 0f              // lemparan cepat
                    else -> expandProgress > 0.5f          // lepas: ikut sisi terdekat
                }
                dragging = false
                dragMoved = false
                animateDetailTo(if (expand) 1f else 0f)
            }
        }
        return true
    }

    private fun animateDetailTo(target: Float) {
        val opening = target >= 1f && !detailExpanded
        detailExpanded = target >= 1f
        backCallback.isEnabled = detailExpanded
        if (collapsedH == 0 || expandedH == 0) measureSheetRange()
        if (detailExpanded) refreshDetail()
        if (opening) detailScroll.scrollTo(0, 0)

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(expandProgress, target).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * p = 0..1. Satu kartu yang sama memanjang ke atas: tinggi kartu naik,
     * tampilan ringkas memudar, tampilan detail muncul di tempat yang sama.
     */
    private fun applyProgress(p: Float) {
        expandProgress = p.coerceIn(0f, 1f)
        val lp = sheet.layoutParams
        if (expandProgress <= 0f) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            detailSheet.visibility = View.GONE
            collapsedColumn.alpha = 1f
            collapsedColumn.visibility = View.VISIBLE
        } else {
            lp.height = (collapsedH + (expandedH - collapsedH) * expandProgress).toInt()
            detailSheet.visibility = View.VISIBLE
            detailSheet.alpha = ((expandProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
            val a = (1f - expandProgress * 2.5f).coerceIn(0f, 1f)
            collapsedColumn.alpha = a
            collapsedColumn.visibility = if (a > 0f) View.VISIBLE else View.INVISIBLE
        }
        sheet.layoutParams = lp

        // Peta digelapkan sedikit; kartu peringatan & tombol lokasi memudar lalu disembunyikan
        detailScrim.alpha = 0.32f * expandProgress
        detailScrim.visibility = if (expandProgress > 0f) View.VISIBLE else View.GONE

        val fade = (1f - expandProgress * 2f).coerceIn(0f, 1f)
        val btn = findViewById<View>(R.id.btnMyLocation)
        btn.alpha = fade
        btn.visibility = if (fade > 0f) View.VISIBLE else View.INVISIBLE
        if (alertCard.visibility != View.GONE) {   // GONE = sudah ditutup pengguna, jangan dimunculkan lagi
            alertCard.alpha = fade
            alertCard.visibility = if (fade > 0f) View.VISIBLE else View.INVISIBLE
        }
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