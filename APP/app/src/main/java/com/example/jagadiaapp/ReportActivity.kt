package com.example.jagadiaapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Data satu laporan insiden. TODO: kirim ke backend (lihat submitReport). */
data class IncidentReport(
    val typeKey: String,
    val lat: Double,
    val lng: Double,
    val locationName: String,
    val timeMillis: Long,
    val note: String
)

class ReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"
        private val DEFAULT_CENTER = LatLng(-6.219990, 106.903173) // sama dengan MapActivity
    }

    private data class IncidentType(val key: String, val label: String, val icon: Int)

    private val types = listOf(
        IncidentType("pencurian", "Pencurian", R.drawable.ic_type_warning),
        IncidentType("jambret", "Jambret", R.drawable.ic_type_snatch),
        IncidentType("begal", "Begal", R.drawable.ic_type_hammer),
        IncidentType("pelecehan", "Pelecehan / Catcalling", R.drawable.ic_type_voice),
        IncidentType("jalan_gelap", "Jalan Gelap / Sepi", R.drawable.ic_type_moon),
        IncidentType("lainnya", "Lainnya", R.drawable.ic_type_plus)
    )

    // ---------- 1. PROPERTI ----------
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null

    private lateinit var successLayer: View
    private lateinit var tvLocationName: TextView
    private lateinit var tvTime: TextView
    private lateinit var etNote: EditText

    private val typeViews = linkedMapOf<String, View>()
    private var selectedType: IncidentType? = null

    private var locationName = ""          // nama jalan hasil reverse geocoding ("" = belum ada)
    private val eventTime: Calendar = Calendar.getInstance()

    private val handler = Handler(Looper.getMainLooper())
    private var geocodeRunnable: Runnable? = null
    private var geocodeSeq = 0

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) useMyLocation()
    }

    // ---------- 2. onCreate ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this) // wajib sebelum setContentView
        setContentView(R.layout.activity_report)

        successLayer = findViewById(R.id.successLayer)
        tvLocationName = findViewById(R.id.tvLocationName)
        tvTime = findViewById(R.id.tvTime)
        etNote = findViewById(R.id.etNote)

        setupEdgeToEdge()
        setupTypeGrid()
        setupMap(savedInstanceState)
        setupForm()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    // ---------- 3. UI ----------
    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true

        val header = findViewById<View>(R.id.reportHeader)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val headerBase = header.paddingTop
        val barBase = bottomBar.paddingBottom
        val succTop = successLayer.paddingTop
        val succBottom = successLayer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.updatePadding(top = headerBase + bars.top)
            // tombol kirim ikut naik di atas keyboard
            bottomBar.updatePadding(bottom = barBase + maxOf(bars.bottom, ime.bottom))
            successLayer.updatePadding(top = succTop + bars.top, bottom = succBottom + bars.bottom)
            insets
        }
    }

    /** Enam kartu jenis insiden (3 x 2). */
    private fun setupTypeGrid() {
        val rows = listOf(
            findViewById<LinearLayout>(R.id.typeRow1),
            findViewById<LinearLayout>(R.id.typeRow2)
        )
        val inflater = LayoutInflater.from(this)

        types.forEachIndexed { i, t ->
            val col = i % 3
            val v = inflater.inflate(R.layout.item_incident_type, rows[i / 3], false)
            v.layoutParams = LinearLayout.LayoutParams(0, dp(88), 1f).apply {
                marginStart = if (col == 0) 0 else dp(5)
                marginEnd = if (col == 2) 0 else dp(5)
            }
            v.findViewById<ImageView>(R.id.ivTypeIcon).setImageResource(t.icon)
            v.findViewById<TextView>(R.id.tvTypeLabel).text = t.label
            v.setOnClickListener { selectType(t) }
            rows[i / 3].addView(v)
            typeViews[t.key] = v
        }
    }

    private fun selectType(t: IncidentType?) {
        selectedType = t
        val on = ContextCompat.getColor(this, R.color.jd_teal_dark)
        val off = ContextCompat.getColor(this, R.color.jd_muted)
        typeViews.forEach { (key, v) ->
            val sel = key == t?.key
            v.isSelected = sel
            v.findViewById<View>(R.id.ivTypeRadio).visibility = if (sel) View.VISIBLE else View.GONE
            v.findViewById<TextView>(R.id.tvTypeLabel).setTextColor(if (sel) on else off)
        }
    }

    private fun setupForm() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnUseLocation).setOnClickListener { useMyLocation() }
        findViewById<View>(R.id.fieldTime).setOnClickListener { pickDateTime() }
        findViewById<View>(R.id.btnSubmit).setOnClickListener { onSubmit() }

        findViewById<View>(R.id.btnBackToMap).setOnClickListener { finish() }
        findViewById<View>(R.id.btnReportAgain).setOnClickListener { resetForm() }

        updateTimeLabel()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- 4. PETA & LOKASI ----------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap(savedInstanceState: Bundle?) {
        // Mode texture supaya peta ikut terpotong sudut membulat kartu
        val options = MapLibreMapOptions.createFromAttributes(this, null).textureMode(true)
        mapView = MapView(this, options)
        findViewById<FrameLayout>(R.id.mapContainer).addView(
            mapView, 0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        mapView.onCreate(savedInstanceState)

        // Peta ada di dalam ScrollView: kunci scroll induk selama peta disentuh
        mapView.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false

            m.setStyle(MAP_STYLE) {
                m.cameraPosition = CameraPosition.Builder()
                    .target(initialCenter())
                    .zoom(16.5)
                    .build()
            }
            // Pin di tengah layar = lokasi laporan; alamat dicari setelah peta berhenti digeser
            m.addOnCameraIdleListener { onMapIdle() }
        }
    }

    /** Titik awal: lokasi pengguna dari MapActivity -> lokasi terakhir perangkat -> titik default. */
    private fun initialCenter(): LatLng {
        if (intent.hasExtra(EXTRA_LAT) && intent.hasExtra(EXTRA_LNG)) {
            return LatLng(intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LNG, 0.0))
        }
        if (hasLocationPermission()) {
            bestLastKnown()?.let { return LatLng(it.latitude, it.longitude) }
        }
        return DEFAULT_CENTER
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? {
        val lm = getSystemService(LocationManager::class.java) ?: return null
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Tombol "Gunakan lokasi saya": geser peta ke posisi pengguna. */
    private fun useMyLocation() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        val loc = bestLastKnown()
        if (loc == null) {
            Toast.makeText(this, "Lokasi belum ditemukan. Nyalakan GPS lalu coba lagi.", Toast.LENGTH_SHORT).show()
            return
        }
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17.0),
            600
        )
    }

    private fun onMapIdle() {
        val center = map?.cameraPosition?.target ?: return
        locationName = ""
        tvLocationName.text = "Mencari alamat…"

        // tunda sebentar supaya tidak membanjiri server saat peta digeser berulang
        geocodeRunnable?.let { handler.removeCallbacks(it) }
        val seq = ++geocodeSeq
        val r = Runnable { reverseGeocode(center, seq) }
        geocodeRunnable = r
        handler.postDelayed(r, 500)
    }

    /** Nama jalan dari koordinat (OpenStreetMap Nominatim, gratis, maks ~1 permintaan/detik). */
    private fun reverseGeocode(ll: LatLng, seq: Int) {
        Thread {
            val name = runCatching {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=id" +
                            "&lat=${ll.latitude}&lon=${ll.longitude}"
                )
                val c = url.openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "JagaDiaApp/1.0 (tugas kuliah)")
                c.connectTimeout = 5000
                c.readTimeout = 5000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()

                val j = JSONObject(body)
                val a = j.optJSONObject("address")
                val road = a?.optString("road").orEmpty()
                val area = listOf("suburb", "neighbourhood", "village", "city_district")
                    .map { a?.optString(it).orEmpty() }
                    .firstOrNull { it.isNotBlank() }
                when {
                    road.isNotBlank() && area != null -> "$road, $area"
                    road.isNotBlank() -> road
                    area != null -> area
                    else -> j.optString("display_name").split(",").take(2).joinToString(",").trim()
                }
            }.getOrNull()

            runOnUiThread {
                if (seq != geocodeSeq || isFinishing) return@runOnUiThread
                if (name.isNullOrBlank()) {
                    // gagal / tidak ada internet: tampilkan koordinat saja
                    locationName = ""
                    tvLocationName.text = "%.5f, %.5f".format(Locale.US, ll.latitude, ll.longitude)
                } else {
                    locationName = name
                    tvLocationName.text = name
                }
            }
        }.start()
    }

    // ---------- 5. WAKTU ----------
    private fun pickDateTime() {
        val now = Calendar.getInstance()
        val dlg = DatePickerDialog(
            this,
            { _, y, mo, d ->
                TimePickerDialog(
                    this,
                    { _, h, mi ->
                        val picked = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }
                        if (picked.timeInMillis > System.currentTimeMillis()) {
                            Toast.makeText(this, "Waktu tidak boleh di masa depan", Toast.LENGTH_SHORT).show()
                            eventTime.timeInMillis = System.currentTimeMillis()
                        } else {
                            eventTime.timeInMillis = picked.timeInMillis
                        }
                        updateTimeLabel()
                    },
                    eventTime.get(Calendar.HOUR_OF_DAY),
                    eventTime.get(Calendar.MINUTE),
                    true
                ).show()
            },
            eventTime.get(Calendar.YEAR),
            eventTime.get(Calendar.MONTH),
            eventTime.get(Calendar.DAY_OF_MONTH)
        )
        dlg.datePicker.maxDate = now.timeInMillis
        dlg.show()
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** "Hari ini, 21.08" / "Kemarin, 21.08" / "12 Nov, 21.08" */
    private fun updateTimeLabel() {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val day = when {
            sameDay(eventTime, today) -> "Hari ini"
            sameDay(eventTime, yesterday) -> "Kemarin"
            else -> SimpleDateFormat("d MMM", Locale("id", "ID")).format(eventTime.time)
        }
        val hhmm = "%02d.%02d".format(
            Locale.US, eventTime.get(Calendar.HOUR_OF_DAY), eventTime.get(Calendar.MINUTE)
        )
        tvTime.text = "$day, $hhmm"
    }

    // ---------- 6. KIRIM & KONFIRMASI ----------
    private fun onSubmit() {
        val type = selectedType
        if (type == null) {
            Toast.makeText(this, "Pilih jenis insiden dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val center = map?.cameraPosition?.target
        if (center == null) {
            Toast.makeText(this, "Peta belum siap, coba sebentar lagi", Toast.LENGTH_SHORT).show()
            return
        }

        val report = IncidentReport(
            typeKey = type.key,
            lat = center.latitude,
            lng = center.longitude,
            locationName = locationName,
            timeMillis = eventTime.timeInMillis,
            note = etNote.text.toString().trim()
        )
        submitReport(report)
        showSuccess()
    }

    /** TODO: kirim ke backend (mis. POST /reports). Sementara hanya dicatat di Logcat. */
    private fun submitReport(report: IncidentReport) {
        Log.d("JagaDia", "Laporan dikirim: $report")
    }

    private fun showSuccess() {
        WindowCompat.getInsetsController(window, findViewById<View>(R.id.root))
            .hide(WindowInsetsCompat.Type.ime())
        etNote.clearFocus()

        val illustration = findViewById<View>(R.id.successIllustration)
        val check = findViewById<View>(R.id.successCheck)
        successLayer.alpha = 0f
        successLayer.visibility = View.VISIBLE
        successLayer.animate().alpha(1f).setDuration(200).start()

        illustration.scaleX = 0.7f
        illustration.scaleY = 0.7f
        illustration.animate().scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
        check.scaleX = 0f
        check.scaleY = 0f
        check.animate().scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(350)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }

    /** "Buat laporan lain": kosongkan form, peta tetap di lokasi terakhir. */
    private fun resetForm() {
        selectType(null)
        etNote.setText("")
        eventTime.timeInMillis = System.currentTimeMillis()
        updateTimeLabel()
        findViewById<View>(R.id.reportScroll).scrollTo(0, 0)
        successLayer.visibility = View.GONE
    }

    // ---------- 7. LIFECYCLE (wajib diteruskan ke MapView) ----------
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        geocodeRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
        mapView.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
