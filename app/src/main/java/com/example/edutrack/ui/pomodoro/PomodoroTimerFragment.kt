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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.edutrack.R
import com.example.edutrack.databinding.FragmentPomodoroTimerBinding
import com.example.edutrack.ui.dashboard.PomodoroHistory
import com.example.edutrack.ui.dashboard.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

class PomodoroTimerFragment : Fragment() {

    private var _binding: FragmentPomodoroTimerBinding? = null
    private val binding get() = _binding!!

    enum class TimerMode { FOCUS, BREAK }

    private var currentMode       = TimerMode.FOCUS
    private var isRunning         = false
    private var countDownTimer: CountDownTimer? = null

    // ── 🌟 STATE DURASI TIMER SAKLEK ──
    private var focusMinutes      = 25
    private var breakMinutes      = 5
    private var longBreakMinutes  = 15 // State baru untuk Jeda Panjang kustom

    private var timeLeftMs        = focusMinutes * 60 * 1000L
    private var totalDurationMs   = focusMinutes * 60 * 1000L
    private var pomodorosDone     = 0
    private var totalFocusSeconds = 0

    private var pulseAnimator: ObjectAnimator? = null

    private val CHANNEL_ID     = "pomodoro_channel"
    private val NOTIF_ID_BREAK = 1001
    private val NOTIF_ID_FOCUS = 1002

    private lateinit var taskViewModel: TaskViewModel
    private var sessionStartTime: String = ""
    private var selectedTaskId: Int? = null
    private var selectedTaskTitle: String = "Belajar Mandiri"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPomodoroTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)

        createNotificationChannel()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.btnStartPause.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        binding.btnReset.setOnClickListener        { resetTimer()             }
        binding.btnSkip.setOnClickListener         { skipToNext()             }
        binding.btnEditDuration.setOnClickListener { showEditDurationDialog() }
    }

    override fun onResume() {
        super.onResume()
        restoreTimerState()
    }

    override fun onPause() {
        super.onPause()
        saveTimerState()
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
    }

    private fun startTimer() {
        isRunning = true
        binding.btnStartPause.setImageResource(R.drawable.ic_pause)
        animatePulse(true)

        if (sessionStartTime.isEmpty()) {
            val sdfTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sessionStartTime = sdfTimestamp.format(Date())
        }

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
        binding.btnStartPause.setImageResource(R.drawable.ic_play)
        animatePulse(false)
    }

    private fun resetTimer() {
        pauseTimer()
        val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("pomo_is_running").remove("pomo_time_left").apply()

        timeLeftMs = totalDurationMs
        updateDisplay()
        binding.arcProgress.progress = 100
        sessionStartTime = ""
    }

    private fun skipToNext() {
        pauseTimer()
        val nextMode = if (currentMode == TimerMode.FOCUS) TimerMode.BREAK else TimerMode.FOCUS
        val isLongBreakCondition = (currentMode == TimerMode.FOCUS && (pomodorosDone + 1) % 4 == 0)

        sessionStartTime = ""
        switchMode(nextMode, autoStart = false, isLongBreak = isLongBreakCondition)
    }

    private fun onTimerFinished() {
        isRunning = false
        animatePulse(false)
        triggerFinishAnimation()
        vibrate()
        playSound()

        val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("pomo_is_running").apply()

        if (currentMode == TimerMode.FOCUS) {
            pomodorosDone++
            updateStats()

            val sdfTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sessionEndTime = sdfTimestamp.format(Date())

            val materiInput = binding.etMateri.text.toString().trim()
            if (materiInput.isNotEmpty()) {
                selectedTaskTitle = materiInput
            } else {
                selectedTaskTitle = "Sesi Fokus Pomodoro"
            }

            val historyRecord = PomodoroHistory(
                taskId = selectedTaskId,
                taskTitle = selectedTaskTitle,
                timestampStart = if (sessionStartTime.isEmpty()) sessionEndTime else sessionStartTime,
                timestampEnd = sessionEndTime,
                durationMinutes = focusMinutes
            )

            taskViewModel.insertPomodoroHistory(historyRecord)
            sessionStartTime = ""

            if (pomodorosDone % 4 == 0) {
                switchMode(TimerMode.BREAK, autoStart = true, isLongBreak = true)
                sendNotification(
                    id      = NOTIF_ID_BREAK,
                    title   = "🎉 Hebat! Sesi Jeda Panjang!",
                    message = "Kamu sudah menyelesaikan 4 sesi fokus. Nikmati istirahat panjang $longBreakMinutes menit!"
                )
            } else {
                switchMode(TimerMode.BREAK, autoStart = true, isLongBreak = false)
                sendNotification(
                    id      = NOTIF_ID_BREAK,
                    title   = "☕ Waktunya Break Pendek!",
                    message = "Pomodoro selesai. Istirahat $breakMinutes menit dulu ya!"
                )
            }
        } else {
            switchMode(TimerMode.FOCUS, autoStart = false)
            sendNotification(
                id      = NOTIF_ID_FOCUS,
                title   = "🎯 Yuk Fokus Lagi!",
                message = "Break selesai. Klik start kalau sudah siap belajar!"
            )
        }
    }

    private fun switchMode(mode: TimerMode, autoStart: Boolean, isLongBreak: Boolean = false) {
        currentMode = mode
        totalDurationMs = when (currentMode) {
            TimerMode.FOCUS -> focusMinutes * 60 * 1000L
            TimerMode.BREAK -> {
                // ── 🌟 REVISI SAKLEK: EVALUASI VARIABEL JEDA PANJANG KUSTOM ──
                if (isLongBreak) longBreakMinutes * 60 * 1000L else breakMinutes * 60 * 1000L
            }
        }
        timeLeftMs = totalDurationMs
        updateDisplay()
        updateModeColor()

        if (autoStart) {
            startTimer()
        } else {
            binding.btnStartPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateDisplay() {
        val totalSecs = ceil(timeLeftMs / 1000.0).toLong()
        binding.tvTimerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", totalSecs / 60, totalSecs % 60)

        binding.arcProgress.progress = if (totalDurationMs > 0) {
            ((timeLeftMs.toFloat() / totalDurationMs.toFloat()) * 100).toInt()
        } else 100

        binding.tvMode.text = when (currentMode) {
            TimerMode.FOCUS -> "Fokus"
            TimerMode.BREAK -> if (totalDurationMs == longBreakMinutes * 60 * 1000L) "Jeda Panjang" else "Break"
        }
    }

    private fun updateModeColor() {
        val colorRes = when (currentMode) {
            TimerMode.FOCUS -> R.color.pomodoroFocus
            TimerMode.BREAK -> R.color.pomodoroShortBreak
        }
        context?.let {
            binding.containerTimer.setBackgroundColor(ContextCompat.getColor(it, colorRes))
        }
    }

    private fun updateStats() {
        binding.tvPomodoroDone.text = "× $pomodorosDone sesi"
        val mins = totalFocusSeconds / 60
        val secs = totalFocusSeconds % 60
        binding.tvTotalFocus.text   = "Total fokus: ${mins}m ${secs}s"

        val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val tanggalHariIni = sdfTanggal.format(Date())
        val totalMenitBelajar = totalFocusSeconds.toFloat() / 60f

        val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putFloat("pomodoro_time_$tanggalHariIni", totalMenitBelajar).apply()
    }

    private fun saveTimerState() {
        val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("pomo_is_running", isRunning)
            putLong("pomo_time_left", timeLeftMs)
            putLong("pomo_exit_time", System.currentTimeMillis())
            putInt("pomo_total_focus_seconds", totalFocusSeconds)
            putInt("pomo_done_sessions", pomodorosDone)
            putString("pomo_current_mode", currentMode.name)
            putInt("pomo_focus_minutes_setting", focusMinutes)
            putInt("pomo_break_minutes_setting", breakMinutes)
            putInt("pomo_long_break_setting", longBreakMinutes) // Amankan Jeda Panjang
            putLong("pomo_total_duration", totalDurationMs)
            putString("pomo_session_start_time", sessionStartTime)
            apply()
        }
    }

    private fun restoreTimerState() {
        val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.contains("pomo_time_left")) {
            updateDisplay()
            updateModeColor()
            return
        }

        val wasRunning = sharedPrefs.getBoolean("pomo_is_running", false)
        val savedTimeLeft = sharedPrefs.getLong("pomo_time_left", focusMinutes * 60 * 1000L)
        val exitTime = sharedPrefs.getLong("pomo_exit_time", 0L)

        totalFocusSeconds = sharedPrefs.getInt("pomo_total_focus_seconds", 0)
        pomodorosDone = sharedPrefs.getInt("pomo_done_sessions", 0)
        focusMinutes = sharedPrefs.getInt("pomo_focus_minutes_setting", 25)
        breakMinutes = sharedPrefs.getInt("pomo_break_minutes_setting", 5)
        longBreakMinutes = sharedPrefs.getInt("pomo_long_break_setting", 15) // Restore Jeda Panjang
        totalDurationMs = sharedPrefs.getLong("pomo_total_duration", focusMinutes * 60 * 1000L)
        sessionStartTime = sharedPrefs.getString("pomo_session_start_time", "") ?: ""

        val savedModeStr = sharedPrefs.getString("pomo_current_mode", TimerMode.FOCUS.name)
        currentMode = TimerMode.valueOf(savedModeStr ?: TimerMode.FOCUS.name)

        updateModeColor()
        binding.tvPomodoroDone.text = "× $pomodorosDone sesi"

        if (wasRunning && exitTime > 0L) {
            val timePassedMs = System.currentTimeMillis() - exitTime
            val newTimeLeft = savedTimeLeft - timePassedMs

            if (currentMode == TimerMode.FOCUS) {
                val secondsPassed = (timePassedMs / 1000).toInt()
                totalFocusSeconds += secondsPassed
            }

            if (newTimeLeft > 0) {
                timeLeftMs = newTimeLeft
                updateDisplay()
                updateStats()
                startTimer()
            } else {
                timeLeftMs = 0
                updateDisplay()
                onTimerFinished()
            }
        } else {
            timeLeftMs = savedTimeLeft
            updateDisplay()
            updateStats()
            binding.btnStartPause.setImageResource(R.drawable.ic_play)
        }
    }

    // ── 🌟 REVISI UTAMA: DIALOG EDIT DURASI DENGAN VALIDATION RENTANG SAKLEK ──
    private fun showEditDurationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_duration, null)
        val etFocus    = dialogView.findViewById<EditText>(R.id.etFocusDuration)
        val etBreak    = dialogView.findViewById<EditText>(R.id.etShortBreak)
        val etLong     = dialogView.findViewById<EditText>(R.id.etLongBreak)

        etFocus.setText(focusMinutes.toString())
        etBreak.setText(breakMinutes.toString())
        etLong.setText(longBreakMinutes.toString())

        context?.let {
            AlertDialog.Builder(it, R.style.StudyApp_Dialog)
                .setTitle("Atur Durasi Timer")
                .setView(dialogView)
                .setPositiveButton("Simpan") { _, _ ->
                    val f = etFocus.text.toString().toIntOrNull()
                    val b = etBreak.text.toString().toIntOrNull()
                    val l = etLong.text.toString().toIntOrNull()

                    // Gerbang Validasi Rentang Sesuai Kontrak Kebutuhan Pengguna
                    if (f == null || f !in 10..60) {
                        Toast.makeText(context, "Durasi Fokus harus berada di rentang 10 - 60 menit!", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    if (b == null || b !in 1..15) {
                        Toast.makeText(context, "Durasi Jeda Pendek harus berada di rentang 1 - 15 menit!", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    if (l == null || l !in 10..30) {
                        Toast.makeText(context, "Durasi Jeda Panjang harus berada di rentang 10 - 30 menit!", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }

                    focusMinutes = f
                    breakMinutes = b
                    longBreakMinutes = l
                    pauseTimer()

                    val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().remove("pomo_time_left").apply()

                    totalDurationMs = when (currentMode) {
                        TimerMode.FOCUS -> focusMinutes * 60 * 1000L
                        TimerMode.BREAK -> {
                            val wasLongBreak = (pomodorosDone > 0 && pomodorosDone % 4 == 0)
                            if (wasLongBreak) longBreakMinutes * 60 * 1000L else breakMinutes * 60 * 1000L
                        }
                    }
                    timeLeftMs = totalDurationMs
                    updateDisplay()
                    Toast.makeText(context, "Durasi diperbarui secara profesional!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
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
            activity?.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        val safeContext = context ?: return

        val intent = Intent(safeContext, com.example.edutrack.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pi = PendingIntent.getActivity(
            safeContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(safeContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val notificationManager = safeContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notif)
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context?.applicationContext, uri).play()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun vibrate() {
        try {
            context?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (it.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                        .defaultVibrator.vibrate(
                            VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
                        )
                } else {
                    @Suppress("DEPRECATION")
                    val v = it.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(longArrayOf(0, 300, 200, 300), -1)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun animatePulse(start: Boolean) {
        pulseAnimator?.cancel()
        if (start) {
            pulseAnimator = ObjectAnimator.ofFloat(binding.containerTimer, "scaleX", 1f, 1.012f, 1f).apply {
                duration     = 1000
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun triggerFinishAnimation() {
        binding.containerTimer.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150)
            .withEndAction {
                binding.containerTimer.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}