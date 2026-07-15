package com.example.edutrack.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class StreakManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Tandai hari ini sebagai hari belajar
    fun markTodayAsStudied() {
        val today = sdf.format(Date())
        val studiedDays = getStudiedDays().toMutableSet()
        studiedDays.add(today)
        prefs.edit().putStringSet("studied_days", studiedDays).apply()
        updateStreak()
    }

    // Ambil semua hari yang sudah belajar
    fun getStudiedDays(): Set<String> {
        return prefs.getStringSet("studied_days", emptySet()) ?: emptySet()
    }

    // Cek apakah tanggal tertentu sudah belajar
    fun hasStudiedOn(date: Date): Boolean {
        return getStudiedDays().contains(sdf.format(date))
    }

    // Hitung streak saat ini
    fun getCurrentStreak(): Int {
        val studiedDays = getStudiedDays()
        if (studiedDays.isEmpty()) return 0

        var streak = 0
        val cal = Calendar.getInstance()

        // Cek dari hari ini ke belakang
        while (true) {
            val dateStr = sdf.format(cal.time)
            if (studiedDays.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    // Update dan simpan streak
    private fun updateStreak() {
        val streak = getCurrentStreak()
        prefs.edit().putInt("current_streak", streak).apply()
    }

    // Ambil streak badge berdasarkan jumlah hari
    fun getStreakBadge(streak: Int): String {
        return when {
            streak >= 365 -> "👑"
            streak >= 100 -> "💎"
            streak >= 30  -> "🏆"
            streak >= 14  -> "🥇"
            streak >= 7   -> "⭐"
            streak >= 3   -> "🔥"
            streak >= 1   -> "✨"
            else          -> "💤"
        }
    }

    // Cek apakah hari ini sudah belajar
    fun hasStudiedToday(): Boolean {
        return getStudiedDays().contains(sdf.format(Date()))
    }
}
