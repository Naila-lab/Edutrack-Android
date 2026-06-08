package com.example.edutrack.ui.dashboard

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.edutrack.R
import com.example.edutrack.ui.dashboard.TaskViewModel
import com.example.edutrack.databinding.FragmentDashboardBinding
import com.example.edutrack.ui.dashboard.Task
import com.example.edutrack.utils.StreakManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar
import android.app.AlertDialog
import android.widget.EditText
import java.util.*
import android.widget.Toast
import com.example.edutrack.ui.pomodoro.PomodoroTimerActivity
import com.example.edutrack.ui.flashcard.FlashcardActivity

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var streakManager: StreakManager
    private lateinit var taskViewModel: TaskViewModel

    private var selectedDeadline: String? = null

    private var currentCalendarMonth = Calendar.getInstance()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        streakManager = StreakManager(requireContext())
        taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)

        setupHeader()
        setupStreak()
        setupCalendar()
        setupTodoDatabase()
        setupQuickActions()

        renderLearningChart()

        streakManager.markTodayAsStudied()
        updateStreakUI()

        binding.btnGoFlashcard.setOnClickListener {
            try {
                val intent = Intent(requireContext(), FlashcardActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.btnGoPomodoro.setOnClickListener {
            try {
                val intent = Intent(requireContext(), PomodoroTimerActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Gagal membuka halaman Pomodoro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderLearningChart()
    }

    // ── 🌟 REVISI SAKTI: LOGIKA GRAFIK DENGAN RENTANG TANGGAL OTOMATIS & DINAMIS ──
    private fun renderLearningChart() {
        try {
            val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            val cal = Calendar.getInstance(Locale.getDefault())

            // Kunci hari Senin di minggu berjalan
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val formatLabelTanggal = SimpleDateFormat("d MMM", Locale("id", "ID"))
            val tanggalSeninStr = formatLabelTanggal.format(cal.time)

            val barViews = arrayOf(
                binding.barMon, binding.barTue, binding.barWed,
                binding.barThu, binding.barFri, binding.barSat, binding.barSun
            )

            var totalMinutesThisWeek = 0f
            val maxBarHeightPx = 120.dpToPx()
            val targetMinutesPerDay = 60f

            for (i in 0 until 7) {
                val tanggalStr = sdf.format(cal.time)
                val minutesFocused = sharedPrefs.getFloat("pomodoro_time_$tanggalStr", 0f)
                totalMinutesThisWeek += minutesFocused

                val ratio = if (minutesFocused > targetMinutesPerDay) 1f else minutesFocused / targetMinutesPerDay
                val calculatedHeight = (ratio * maxBarHeightPx).toInt()

                val barView = barViews[i]
                val params = barView.layoutParams
                params.height = if (calculatedHeight < 4.dpToPx() && minutesFocused > 0f) 4.dpToPx() else calculatedHeight
                barView.layoutParams = params

                // Jika sudah mencapai loop terakhir (Hari Minggu), perbarui teks deskripsi grafik
                if (i == 6) {
                    val tanggalMingguStr = formatLabelTanggal.format(cal.time)
                    val tahunStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.time)

                    binding.tvTotalWeeklyTime.text = String.format(
                        Locale.getDefault(),
                        "Minggu Ini (%s - %s %s) • Total: %.1f menit",
                        tanggalSeninStr, tanggalMingguStr, tahunStr, totalMinutesThisWeek
                    )
                }

                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTodoDatabase() {
        taskViewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            renderTasks(tasks)
        }

        binding.btnAddTodo.setOnClickListener {
            binding.llTodoInput.visibility = View.VISIBLE
            binding.etTodoInput.requestFocus()
        }

        binding.tvSelectedDate.setOnClickListener {
            showDateTimePicker { dateTime ->
                selectedDeadline = dateTime
                binding.tvSelectedDate.text = dateTime
            }
        }

        binding.btnSaveTodo.setOnClickListener {
            val text = binding.etTodoInput.text.toString().trim()

            val selectedId = binding.rgPriority.checkedRadioButtonId
            val priority = when (selectedId) {
                R.id.rbLow -> "Low"
                R.id.rbHigh -> "High"
                else -> "Medium"
            }

            if (text.isNotEmpty() && selectedDeadline != null) {
                val newTask = Task(
                    title = text,
                    subject = "Umum",
                    priority = priority,
                    deadline = selectedDeadline!!
                )
                taskViewModel.insert(newTask)

                setTodoAlarmH30(text, selectedDeadline!!)

                binding.etTodoInput.text?.clear()
                binding.tvSelectedDate.text = "📅 Atur Deadline & Jam"
                selectedDeadline = null
                binding.rgPriority.check(R.id.rbMedium)
                binding.llTodoInput.visibility = View.GONE
            } else if (selectedDeadline == null) {
                Toast.makeText(requireContext(), "Pilih deadline dan jam terlebih dahulu!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderTasks(tasks: List<Task>) {
        val container = binding.llTodoList
        container.removeAllViews()

        if (tasks.isEmpty()) {
            binding.tvEmptyTodo.visibility = View.VISIBLE
        } else {
            binding.tvEmptyTodo.visibility = View.GONE
            tasks.forEach { task ->
                val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_todo, container, false)

                val tvText = itemView.findViewById<TextView>(R.id.tvTodoText)
                val cbDone = itemView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTodoDone)
                val btnDelete = itemView.findViewById<View>(R.id.btnTodoDelete)
                val btnEdit = itemView.findViewById<View>(R.id.btnTodoEdit)
                val tvDeadline = itemView.findViewById<TextView>(R.id.tvTodoDeadline)
                val tvPriority = itemView.findViewById<TextView>(R.id.tvPriorityLabel)

                tvText.text = task.title
                tvDeadline.text = task.deadline
                cbDone.isChecked = task.isDone
                tvPriority.text = task.priority

                val color = when (task.priority) {
                    "Low" -> android.graphics.Color.parseColor("#22C55E")
                    "High" -> android.graphics.Color.RED
                    else -> android.graphics.Color.parseColor("#EAB308")
                }
                tvPriority.setTextColor(color)

                btnDelete.setOnClickListener { taskViewModel.delete(task) }
                btnEdit.setOnClickListener { showEditDialog(task) }
                cbDone.setOnCheckedChangeListener { _, isChecked ->
                    taskViewModel.update(task.copy(isDone = isChecked))
                }

                container.addView(itemView)
            }
        }
        binding.tvTaskCount.text = tasks.count { !it.isDone }.toString()
        binding.tvDoneCount.text = tasks.count { it.isDone }.toString()
    }

    private fun showEditDialog(task: Task) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val editText = EditText(context).apply { setText(task.title) }
        val tvDate = TextView(context).apply {
            text = "📅 ${task.deadline}"
            setPadding(0, 20, 0, 20)
            textSize = 14f
        }

        var newDeadline = task.deadline

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, y, m, d ->
                val selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)

                TimePickerDialog(context, { _, hour, minute ->
                    val selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    newDeadline = "$selectedDate $selectedTime"
                    tvDate.text = "📅 $newDeadline"
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()

            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        layout.addView(editText)
        layout.addView(tvDate)

        AlertDialog.Builder(context)
            .setTitle("Edit Tugas")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    taskViewModel.update(task.copy(title = newTitle, deadline = newDeadline))
                    setTodoAlarmH30(newTitle, newDeadline)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupHeader() {
        val user = Firebase.auth.currentUser
        val username = user?.email?.substringBefore("@") ?: "Pengguna"
        binding.tvGreeting.text = "Selamat belajar, $username! 👋"
        binding.btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
        }
    }

    private fun setupStreak() {
        updateStreakUI()
        binding.btnPrevMonth.setOnClickListener { currentCalendarMonth.add(Calendar.MONTH, -1); setupCalendar() }
        binding.btnNextMonth.setOnClickListener { currentCalendarMonth.add(Calendar.MONTH, 1); setupCalendar() }
    }

    private fun updateStreakUI() {
        val streak = streakManager.getCurrentStreak()
        binding.tvStreakCount.text = "$streak hari berturut-turut"
        binding.tvStreakBadge.text = streakManager.getStreakBadge(streak)
        binding.tvStreakStat.text = streak.toString()
    }

    private fun setupCalendar() {
        binding.tvMonthYear.text = monthFormat.format(currentCalendarMonth.time)
        buildCalendarGrid()
    }

    private fun showDateTimePicker(onDateTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)

                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        val selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                        onDateTimeSelected("$selectedDate $selectedTime")
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun setTodoAlarmH30(taskTitle: String, deadlineStr: String) {
        try {
            val sdfParser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateDeadline = sdfParser.parse(deadlineStr) ?: return

            val calendar = Calendar.getInstance().apply {
                time = dateDeadline
                add(Calendar.MINUTE, -30)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) return

            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(requireContext(), TodoAlarmReceiver::class.java).apply {
                putExtra("TASK_TITLE", taskTitle)
            }

            val uniqueId = taskTitle.hashCode()

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildCalendarGrid() {
        val grid = binding.calendarGrid
        grid.removeAllViews()
        val cal = currentCalendarMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val studiedDays = streakManager.getStudiedDays()

        for (i in 0 until (offset + daysInMonth)) {
            val dayNumber = i - offset + 1
            val cellView = TextView(requireContext())
            val size = (resources.displayMetrics.widthPixels - 40.dpToPx() * 2 - 16.dpToPx()) / 7
            cellView.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(2, 2, 2, 2) }
            cellView.gravity = Gravity.CENTER
            cellView.textSize = 11f

            if (dayNumber in 1..daysInMonth) {
                cellView.text = dayNumber.toString()
                val cellCal = currentCalendarMonth.clone() as Calendar
                cellCal.set(Calendar.DAY_OF_MONTH, dayNumber)
                val dateStr = sdf.format(cellCal.time)
                val isToday = dayNumber == today.get(Calendar.DAY_OF_MONTH) && currentCalendarMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                val hasStudied = studiedDays.contains(dateStr)

                if (hasStudied) {
                    cellView.background = ContextCompat.getDrawable(requireContext(), if(isToday) R.drawable.bg_streak_today else R.drawable.bg_streak_active)
                    cellView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white))
                } else {
                    cellView.setTextColor(ContextCompat.getColor(requireContext(), if(isToday) R.drawable.bg_streak_today else R.color.text_secondary))
                }
            }
            grid.addView(cellView)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupQuickActions() {
        binding.btnGoSchedule.setOnClickListener {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(com.example.edutrack.R.id.jadwalFragment)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Navigasi Jadwal Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Tugas"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "edu_track_todo_channel"

        val sharedPrefs = context.getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
        val namaUser = sharedPrefs.getString("user_name", "Halo")?.trim() ?: "Halo"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pengingat Batas Waktu Tugas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mengingatkan batas waktu pengumpulan tugas EduTrack"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("⚠️ Batas Pengumpulan Tugas!")
            .setContentText("$namaUser, tugas '$taskTitle' tersisa 30 menit lagi! Jangan lupa dikumpulkan ya! 🚀")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(taskTitle.hashCode(), builder.build())
    }
}