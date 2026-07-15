package com.example.edutrack

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inisialisasi NavHostFragment dari XML bawaan timmu
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 2. Hubungkan Bottom Navigation View Global
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setupWithNavController(navController)

        // 3. Logika Deteksi Halaman Auth (Anti-Bocor Visual)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment, R.id.resetPasswordFragment -> {
                    bottomNav.visibility = View.GONE
                }
                else -> {
                    bottomNav.visibility = View.VISIBLE

                    // ── 🌟 UTAMA: HITUNG STREAK SETIAP KALI USER PINDAH HALAMAN NON-AUTH ──
                    // Ini memastikan angka streak selalu paling mutakhir saat Dashboard dimuat
                    val hitungStreak = getContinuousStreak()
                    // Kamu bisa simpan angka ini ke SharedPreferences global agar DashboardFragment tinggal baca langsung
                    getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("current_user_streak", hitungStreak).apply()
                }
            }
        }

        // 4. Cek status login pengguna menggunakan Firebase Auth
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // Jika sudah login, paksa navigasi langsung ke Dashboard
            navController.navigate(R.id.dashboardFragment)
        }
    }

    // ── 🌟 LOGIKA FITUR BARU: HITUNG STREAK BELAJAR DARI SHAREDPREFS ──
    private fun getContinuousStreak(): Int {
        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        var streakCount = 0

        // Pengecekan mundur dari hari ini ke belakang
        while (true) {
            val tanggalStr = sdf.format(calendar.time)
            val menitBelajar = sharedPrefs.getFloat("pomodoro_time_$tanggalStr", 0f)

            if (menitBelajar > 0f) {
                streakCount++
                calendar.add(Calendar.DAY_OF_YEAR, -1) // Mundur ke hari kemarin
            } else {
                // Toleransi: Jika hari ini belum belajar (0), tapi kemarin belajar, streak tidak langsung hangus
                if (streakCount == 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val menitKemarin = sharedPrefs.getFloat("pomodoro_time_${sdf.format(calendar.time)}", 0f)
                    if (menitKemarin > 0f) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1) // Reset pointer ke hari ini
                        streakCount = 0
                        continue
                    }
                }
                break
            }
        }
        return streakCount
    }

    // 5. Mengaktifkan tombol 'Back' bawaan toolbar agar sinkron dengan Nav Graph
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}