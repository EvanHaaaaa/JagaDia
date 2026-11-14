package com.example.jagadiaapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat

class OnboardingActivity : AppCompatActivity() {

    private data class OnboardingPage(
        val eyebrow: String,
        val title: String,
        val desc: String
    )

    // Posisi relatif (0.0 - 1.0) terhadap lebar/tinggi kartu ilustrasi
    private data class Spot(val x: Float, val y: Float)

    private data class MapScene(
        val pin: Spot,
        val markerA: Spot, val colorA: String,
        val markerB: Spot, val colorB: String
    )

    // Halaman 0-2 = intro (ada ilustrasi peta), halaman 3 = izin lokasi
    private val pages = listOf(
        OnboardingPage(
            "PERJALANAN LEBIH TENANG",
            "Rute tercepat belum tentu teraman",
            "JagaDia membantu kamu memilih perjalanan dengan mempertimbangkan keamanan jalan."
        ),
        OnboardingPage(
            "PERJALANAN LEBIH TENANG",
            "Prediksi risiko berdasarkan lokasi & waktu",
            "Teknologi ini membaca pola area rawan agar kamu bisa lebih siap sebelum berangkat"
        ),
        OnboardingPage(
            "PERJALANAN LEBIH TENANG",
            "Pilih rute yang kamu percaya dan lebih aman",
            "Bandingkan waktu, jarak dan skor aman. Keputusan tetap ada di tanganmu."
        ),
        OnboardingPage(
            "SATU LANGKAH LAGI",
            "Aktifkan Lokasimu",
            "Kami butuh lokasi untuk menemukan rute aman di sekitarmu. Lokasi hanya digunakan saat aplikasi aktif."
        )
    )

    // Posisi pin dan dua titik kecil tiap halaman, disesuaikan dengan desain Figma
    private val scenes = listOf(
        MapScene(Spot(0.19f, 0.33f), Spot(0.53f, 0.09f), "#F5A623", Spot(0.30f, 0.80f), "#2E9E6B"),
        MapScene(Spot(0.53f, 0.17f), Spot(0.53f, 0.07f), "#2E9E6B", Spot(0.79f, 0.82f), "#F5A623"),
        MapScene(Spot(0.28f, 0.46f), Spot(0.07f, 0.45f), "#2E9E6B", Spot(0.96f, 0.63f), "#E04B4B")
    )

    private val permissionPage = pages.lastIndex
    private var page = 0

    private lateinit var tvEyebrow: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvSkip: View
    private lateinit var btnNext: View
    private lateinit var btnAllow: View
    private lateinit var tvLater: View
    private lateinit var dots: List<View>

    private lateinit var mapContainer: View
    private lateinit var permissionContainer: View
    private lateinit var groupIntro: View
    private lateinit var groupPermission: View
    private lateinit var cardScore: View
    private lateinit var cardRisk: View
    private lateinit var cardRoute: View
    private lateinit var ivPin: View
    private lateinit var markerA: View
    private lateinit var markerB: View

    // Dialog izin lokasi sistem. Hasilnya (diizinkan atau tidak) tetap lanjut ke peta.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { goToMap() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        tvEyebrow = findViewById(R.id.tvEyebrow)
        tvTitle = findViewById(R.id.tvTitle)
        tvDesc = findViewById(R.id.tvDesc)
        tvSkip = findViewById(R.id.tvSkip)
        btnNext = findViewById(R.id.btnNext)
        btnAllow = findViewById(R.id.btnAllow)
        tvLater = findViewById(R.id.tvLater)
        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3)
        )
        mapContainer = findViewById(R.id.mapContainer)
        permissionContainer = findViewById(R.id.permissionContainer)
        groupIntro = findViewById(R.id.groupIntro)
        groupPermission = findViewById(R.id.groupPermission)
        cardScore = findViewById(R.id.cardScore)
        cardRisk = findViewById(R.id.cardRisk)
        cardRoute = findViewById(R.id.cardRoute)
        ivPin = findViewById(R.id.ivPin)
        markerA = findViewById(R.id.markerA)
        markerB = findViewById(R.id.markerB)

        // Lanjut: halaman 1 -> 2 -> 3 -> halaman izin
        btnNext.setOnClickListener {
            if (page < permissionPage) {
                page++
                render()
            }
        }

        // Lewati: dari halaman intro loncat ke halaman izin; dari halaman izin langsung ke peta
        tvSkip.setOnClickListener {
            if (page < permissionPage) {
                page = permissionPage
                render()
            } else {
                goToMap()
            }
        }

        // Izinkan Lokasi: baru di sini dialog izin sistem muncul
        btnAllow.setOnClickListener {
            val granted =
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                goToMap()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        // Nanti saja: langsung ke peta tanpa meminta izin
        tvLater.setOnClickListener { goToMap() }

        render()
    }

    private fun render() {
        val p = pages[page]
        val isPermission = page == permissionPage

        tvEyebrow.text = p.eyebrow
        tvTitle.text = p.title
        tvTitle.minLines = if (isPermission) 1 else 2
        tvDesc.text = p.desc

        // Area atas: kartu ilustrasi (halaman 1-3) atau ilustrasi izin (halaman 4)
        mapContainer.visibility = if (isPermission) View.GONE else View.VISIBLE
        permissionContainer.visibility = if (isPermission) View.VISIBLE else View.GONE

        // Kartu info di atas ilustrasi, berbeda tiap halaman
        cardScore.visibility = if (page == 0) View.VISIBLE else View.GONE
        cardRisk.visibility = if (page == 1) View.VISIBLE else View.GONE
        cardRoute.visibility = if (page == 2) View.VISIBLE else View.GONE

        // Area bawah: indikator + Lanjut, atau tombol izin
        groupIntro.visibility = if (isPermission) View.GONE else View.VISIBLE
        groupPermission.visibility = if (isPermission) View.VISIBLE else View.GONE

        if (!isPermission) applyScene(scenes[page])

        // Titik indikator: aktif memanjang (24dp), tidak aktif bulat (8dp)
        val density = resources.displayMetrics.density
        dots.forEachIndexed { i, v ->
            val lp = v.layoutParams
            lp.width = ((if (i == page) 24 else 8) * density).toInt()
            lp.height = (8 * density).toInt()
            v.layoutParams = lp
            v.setBackgroundResource(
                if (i == page) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    private fun applyScene(scene: MapScene) {
        place(ivPin, scene.pin)
        place(markerA, scene.markerA)
        place(markerB, scene.markerB)
        markerA.backgroundTintList = ColorStateList.valueOf(Color.parseColor(scene.colorA))
        markerB.backgroundTintList = ColorStateList.valueOf(Color.parseColor(scene.colorB))
    }

    // Geser view di dalam ConstraintLayout lewat bias horizontal dan vertikal
    private fun place(v: View, spot: Spot) {
        val lp = v.layoutParams as ConstraintLayout.LayoutParams
        lp.horizontalBias = spot.x
        lp.verticalBias = spot.y
        v.layoutParams = lp
    }

    private fun goToMap() {
        // Tandai onboarding sudah dilihat supaya tidak muncul lagi
        getSharedPreferences("jagadia", MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }
}