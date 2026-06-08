package com.example.edutrack.ui.pomodoro

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import com.example.edutrack.R
import android.widget.ProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PomodoroTimerActivity : AppCompatActivity() {

    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvMinutes: TextView
    private lateinit var tvSeconds: TextView
    private lateinit var tvMode: TextView
    private lateinit var etMateri: EditText
    private lateinit var btnStartPause: ImageButton
    private lateinit var btnReset: ImageButton
    private lateinit var btnSkip: ImageButton
    private lateinit var btnEditDuration: ImageButton
    private lateinit var arcProgress: ProgressBar
    private lateinit var tvPomodoroDone: TextView
    private lateinit var tvTotalFocus: TextView
    private lateinit var containerTimer: View

    private lateinit var btnBack: ImageButton

    enum class TimerMode { FOCUS, BREAK }

    private var currentMode       = TimerMode.FOCUS
    private var isRunning         = false
    private var countDownTimer: CountDownTimer? = null

    private var focusMinutes      = 25
    private var breakMinutes      = 5

    private var timeLeftMs        = focusMinutes * 60 * 1000L
    private var totalDurationMs   = focusMinutes * 60 * 1000L
    private var pomodorosDone     = 0
    private var totalFocusSeconds = 0

    private var pulseAnimator: ObjectAnimator? = null

    private val CHANNEL_ID     = "pomodoro_channel"
    private val NOTIF_ID_BREAK = 1001
    private val NOTIF_ID_FOCUS = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pomodoro_timer)
        createNotificationChannel()
        bindViews()
        updateDisplay()
        updateModeColor()
        setupListeners()
    }

    private fun bindViews() {
        btnBack         = findViewById(R.id.btnBack)
        tvTimerDisplay  = findViewById(R.id.tvTimerDisplay)
        tvMinutes       = findViewById(R.id.tvMinutes)
        tvSeconds       = findViewById(R.id.tvSeconds)
        tvMode          = findViewById(R.id.tvMode)
        etMateri        = findViewById(R.id.etMateri)
        btnStartPause   = findViewById(R.id.btnStartPause)
        btnReset        = findViewById(R.id.btnReset)
        btnSkip         = findViewById(R.id.btnSkip)
        btnEditDuration = findViewById(R.id.btnEditDuration)
        arcProgress     = findViewById(R.id.arcProgress)
        tvPomodoroDone  = findViewById(R.id.tvPomodoroDone)
        tvTotalFocus    = findViewById(R.id.tvTotalFocus)
        containerTimer  = findViewById(R.id.containerTimer)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnStartPause.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        btnReset.setOnClickListener        { resetTimer()             }
        btnSkip.setOnClickListener         { skipToNext()             }
        btnEditDuration.setOnClickListener { showEditDurationDialog() }
    }

    // ── 🌟 INTEGRASI PERSISTENSI: KONTROL LIFECYCLE ONRESUME ──
    override fun onResume() {
        super.onResume()
        restoreTimerState()
    }

    // ── 🌟 INTEGRASI PERSISTENSI: KONTROL LIFECYCLE ONPAUSE ──
    override fun onPause() {
        super.onPause()
        saveTimerState()
        countDownTimer?.cancel() // Amankan instance timer agar tidak memicu kebocoran memori RAM
    }

    private fun startTimer() {
        isRunning = true
        btnStartPause.setImageResource(R.drawable.ic_pause)
        animatePulse(true)

        countDownTimer = object : CountDownTimer(timeLeftMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                updateDisplay()
                if (currentMode == TimerMode.FOCUS) {
                    totalFocusSeconds++
                    updateStats()
                }
            }
            override fun onFinish() {
                timeLeftMs = 0
                updateDisplay()
                onTimerFinished()
            }
        }.start()
    }

    private fun pauseTimer() {
        isRunning = false
        countDownTimer?.cancel()
        btnStartPause.setImageResource(R.drawable.ic_play)
        animatePulse(false)
    }

    private fun resetTimer() {
        pauseTimer()
        // Bersihkan juga sisa jejak data di cache lokal SharedPreferences
        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("pomo_is_running").remove("pomo_time_left").apply()

        timeLeftMs = totalDurationMs
        updateDisplay()
        arcProgress.progress = 100
    }

    private fun skipToNext() {
        pauseTimer()
        val nextMode = if (currentMode == TimerMode.FOCUS) TimerMode.BREAK else TimerMode.FOCUS
        switchMode(nextMode, autoStart = false)
    }

    private fun onTimerFinished() {
        isRunning = false
        animatePulse(false)
        triggerFinishAnimation()
        vibrate()
        playSound()

        // Hapus status penyimpanan sementara karena sesi sudah tuntas berakhir
        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("pomo_is_running").apply()

        if (currentMode == TimerMode.FOCUS) {
            pomodorosDone++
            updateStats()
            switchMode(TimerMode.BREAK, autoStart = true)
            sendNotification(
                id      = NOTIF_ID_BREAK,
                title   = "☕ Waktunya Break!",
                message = "Pomodoro selesai. Istirahat ${breakMinutes} menit dulu ya!"
            )
        } else {
            switchMode(TimerMode.FOCUS, autoStart = false)
            sendNotification(
                id      = NOTIF_ID_FOCUS,
                title   = "🎯 Yuk Fokus Lagi!",
                message = "Break selesai. Klik start kalau sudah siap belajar!"
            )
        }
    }

    private fun switchMode(mode: TimerMode, autoStart: Boolean) {
        currentMode = mode
        totalDurationMs = when (currentMode) {
            TimerMode.FOCUS -> focusMinutes * 60 * 1000L
            TimerMode.BREAK -> breakMinutes * 60 * 1000L
        }
        timeLeftMs = totalDurationMs
        updateDisplay()
        updateModeColor()
        if (autoStart) {
            startTimer()
        } else {
            btnStartPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateDisplay() {
        val totalSecs = ceil(timeLeftMs / 1000.0).toLong()
        tvTimerDisplay.text = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)

        arcProgress.progress = if (totalDurationMs > 0) {
            ((timeLeftMs.toFloat() / totalDurationMs.toFloat()) * 100).toInt()
        } else 100

        tvMode.text = when (currentMode) {
            TimerMode.FOCUS -> "Fokus"
            TimerMode.BREAK -> "Break"
        }
    }

    private fun updateModeColor() {
        val colorRes = when (currentMode) {
            TimerMode.FOCUS -> R.color.pomodoroFocus
            TimerMode.BREAK -> R.color.pomodoroShortBreak
        }
        containerTimer.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    private fun updateStats() {
        tvPomodoroDone.text = "× $pomodorosDone sesi"
        val mins = totalFocusSeconds / 60
        val secs = totalFocusSeconds % 60
        tvTotalFocus.text   = "Total fokus: ${mins}m ${secs}s"

        val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val tanggalHariIni = sdfTanggal.format(Date())
        val totalMenitBelajar = totalFocusSeconds.toFloat() / 60f

        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putFloat("pomodoro_time_$tanggalHariIni", totalMenitBelajar).apply()
    }

    // ── 🌟 JURUS SAKTI: AMANKAN STATE KETIKA USER MENINGGALKAN HALAMAN ──
    private fun saveTimerState() {
        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("pomo_is_running", isRunning)
            putLong("pomo_time_left", timeLeftMs)
            putLong("pomo_exit_time", System.currentTimeMillis())
            putInt("pomo_total_focus_seconds", totalFocusSeconds)
            putInt("pomo_done_sessions", pomodorosDone)
            putString("pomo_current_mode", currentMode.name)
            putInt("pomo_focus_minutes_setting", focusMinutes)
            putInt("pomo_break_minutes_setting", breakMinutes)
            putLong("pomo_total_duration", totalDurationMs)
            apply()
        }
    }

    // ── 🌟 JURUS SAKTI: HITUNG SELISIH WAKTU GHAIB DAN RESTORE SECARA AKURAT ──
    private fun restoreTimerState() {
        val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)

        // Jika tidak ada data tersimpan sebelumnya, abaikan pemulihan state
        if (!sharedPrefs.contains("pomo_time_left")) return

        val wasRunning = sharedPrefs.getBoolean("pomo_is_running", false)
        val savedTimeLeft = sharedPrefs.getLong("pomo_time_left", focusMinutes * 60 * 1000L)
        val exitTime = sharedPrefs.getLong("pomo_exit_time", 0L)

        totalFocusSeconds = sharedPrefs.getInt("pomo_total_focus_seconds", 0)
        pomodorosDone = sharedPrefs.getInt("pomo_done_sessions", 0)
        focusMinutes = sharedPrefs.getInt("pomo_focus_minutes_setting", 25)
        breakMinutes = sharedPrefs.getInt("pomo_break_minutes_setting", 5)
        totalDurationMs = sharedPrefs.getLong("pomo_total_duration", focusMinutes * 60 * 1000L)

        val savedModeStr = sharedPrefs.getString("pomo_current_mode", TimerMode.FOCUS.name)
        currentMode = TimerMode.valueOf(savedModeStr ?: TimerMode.FOCUS.name)

        updateModeColor()
        tvPomodoroDone.text = "× $pomodorosDone sesi"

        if (wasRunning && exitTime > 0L) {
            val timePassedMs = System.currentTimeMillis() - exitTime
            val newTimeLeft = savedTimeLeft - timePassedMs

            if (currentMode == TimerMode.FOCUS) {
                // Tambahkan akumulasi detik fokus yang berjalan di background secara matematis
                val secondsPassed = (timePassedMs / 1000).toInt()
                totalFocusSeconds += secondsPassed
            }

            if (newTimeLeft > 0) {
                timeLeftMs = newTimeLeft
                updateDisplay()
                updateStats()
                startTimer() // Lanjutkan jalannya hitung mundur otomatis
            } else {
                // Jika waktu habis saat ditinggalkan, tuntaskan sesi secara bersih
                timeLeftMs = 0
                updateDisplay()
                onTimerFinished()
            }
        } else {
            // Skenario jika posisi keluar dalam keadaan di-pause, kembalikan ke sisa waktu terakhir
            timeLeftMs = savedTimeLeft
            updateDisplay()
            updateStats()
            btnStartPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun showEditDurationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_duration, null)
        val etFocus    = dialogView.findViewById<EditText>(R.id.etFocusDuration)
        val etBreak    = dialogView.findViewById<EditText>(R.id.etShortBreak)

        etFocus.setText(focusMinutes.toString())
        etBreak.setText(breakMinutes.toString())

        AlertDialog.Builder(this, R.style.StudyApp_Dialog)
            .setTitle("Atur Durasi Timer")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val f = etFocus.text.toString().toIntOrNull()
                val b = etBreak.text.toString().toIntOrNull()
                if (f == null || b == null || f < 1 || b < 1) {
                    Toast.makeText(this, "Masukkan angka valid (min. 1 menit)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                focusMinutes = f
                breakMinutes = b
                pauseTimer()

                // Bersihkan cache lama agar setelan durasi baru tidak tertimpa data restore
                val sharedPrefs = getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().remove("pomo_time_left").apply()

                totalDurationMs = when (currentMode) {
                    TimerMode.FOCUS -> focusMinutes * 60 * 1000L
                    TimerMode.BREAK -> breakMinutes * 60 * 1000L
                }
                timeLeftMs = totalDurationMs
                updateDisplay()
                Toast.makeText(this, "Durasi diubah: Fokus ${f}m | Break ${b}m", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pomodoro Timer", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi Pomodoro Timer"
                enableVibration(true)
                enableLights(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        val intent = Intent(this, PomodoroTimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notif)
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri).play()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
                    )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 300, 200, 300), -1)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun animatePulse(start: Boolean) {
        pulseAnimator?.cancel()
        if (start) {
            pulseAnimator = ObjectAnimator.ofFloat(containerTimer, "scaleX", 1f, 1.012f, 1f).apply {
                duration     = 1000
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun triggerFinishAnimation() {
        containerTimer.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150)
            .withEndAction {
                containerTimer.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
    }
}