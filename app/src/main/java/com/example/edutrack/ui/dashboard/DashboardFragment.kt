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
import com.example.edutrack.databinding.FragmentDashboardBinding
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.app.NotificationCompat
import java.util.Calendar
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.*
import android.widget.Toast
import kotlin.math.ceil

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var streakManager: StreakManager
    private lateinit var taskViewModel: TaskViewModel

    private var selectedDeadline: String? = null

    private var currentCalendarMonth = Calendar.getInstance()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    private val kategoriList = arrayOf("Kuis", "UTS", "UAS", "Laporan", "Proyek", "Lainnya")

    private var allTasksList: List<Task> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterPriority: String = "Semua Prioritas"
    private var currentFilterCategory: String = "Semua Kategori"

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

        renderLearningChart()
        updateBadgesUI()

        streakManager.markTodayAsStudied()
        updateStreakUI()

        // ── 🌟 FITUR BARU: AKTIFKAN PENGINGAT STREAK SETIAP JAM 20:00 MALAM ──
        scheduleDailyStreakReminder()
    }

    override fun onResume() {
        super.onResume()
        renderLearningChart()
        updateBadgesUI()
    }

    // ── 🌟 FITUR BARU: LOGIKA PENJADWALAN ALARM NOTIFIKASI STREAK HARIAN ──
    private fun scheduleDailyStreakReminder() {
        try {
            val context = requireContext()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, StreakReminderReceiver::class.java)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                999, // ID Unik khusus alarm streak reminder
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20) // Notifikasi dipicu jam 8 malam
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            // Jika jam 8 malam hari ini sudah lewat, jadwalkan untuk besok malam
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Set alarm berulang setiap 24 jam secara presisi agar awet di background OS Android
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBadgesUI() {
        try {
            val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            val currentStreak = sharedPrefs.getInt("current_user_streak", 0)

            // ── 🌟 PERBAIKAN SAKLEK: SINTAKS PEMANGGILAN ID KOTLIN YANG BENAR ──
            val ivBadgePemula    = view?.findViewById<TextView>(R.id.ivBadgePemula)
            val ivBadgeKonsisten = view?.findViewById<TextView>(R.id.ivBadgeKonsisten)
            val ivBadgeJuara     = view?.findViewById<TextView>(R.id.ivBadgeJuara)

            if (currentStreak >= 7) {
                ivBadgePemula?.alpha = 1.0f
                ivBadgePemula?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                ivBadgePemula?.alpha = 0.3f
                ivBadgePemula?.setTextColor(android.graphics.Color.GRAY)
            }

            if (currentStreak >= 14) {
                ivBadgeKonsisten?.alpha = 1.0f
                ivBadgeKonsisten?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                ivBadgeKonsisten?.alpha = 0.3f
                ivBadgeKonsisten?.setTextColor(android.graphics.Color.GRAY)
            }

            if (currentStreak >= 30) {
                ivBadgeJuara?.alpha = 1.0f
                ivBadgeJuara?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                ivBadgeJuara?.alpha = 0.3f
                ivBadgeJuara?.setTextColor(android.graphics.Color.GRAY)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun renderLearningChart() {
        try {
            val sharedPrefs = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            val cal = Calendar.getInstance(Locale.getDefault())

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
            allTasksList = tasks
            applyFilterAndSearch()
        }

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, kategoriList)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTodoCategory.adapter = spinnerAdapter

        val priorityFilterOptions = arrayOf("Semua Prioritas", "High", "Medium", "Low")
        val categoryFilterOptions = arrayOf("Semua Kategori", "Kuis", "UTS", "UAS", "Laporan", "Proyek", "Lainnya")

        binding.spinnerFilterPriority.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorityFilterOptions)
        binding.spinnerFilterCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryFilterOptions)

        val filterItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilterPriority = binding.spinnerFilterPriority.selectedItem.toString()
                currentFilterCategory = binding.spinnerFilterCategory.selectedItem.toString()
                applyFilterAndSearch()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerFilterPriority.onItemSelectedListener = filterItemSelectedListener
        binding.spinnerFilterCategory.onItemSelectedListener = filterItemSelectedListener

        binding.etSearchTodo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().trim().lowercase()
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
            val titleText = binding.etTodoInput.text.toString().trim()
            val subjectText = binding.etTodoSubjectInput.text.toString().trim()
            val descriptionText = binding.etTodoDescriptionInput.text.toString().trim()
            val categoryText = binding.spinnerTodoCategory.selectedItem.toString()

            val selectedId = binding.rgPriority.checkedRadioButtonId
            val priority = when (selectedId) {
                R.id.rbLow -> "Low"
                R.id.rbHigh -> "High"
                else -> "Medium"
            }

            if (titleText.isNotEmpty() && subjectText.isNotEmpty() && selectedDeadline != null) {
                val newTask = Task(
                    title = titleText,
                    subject = subjectText,
                    priority = priority,
                    deadline = selectedDeadline!!,
                    description = descriptionText,
                    category = categoryText
                )
                taskViewModel.insert(newTask)

                setTodoAlarmH30(titleText, selectedDeadline!!)

                binding.etTodoInput.text?.clear()
                binding.etTodoSubjectInput.text?.clear()
                binding.etTodoDescriptionInput.text?.clear()
                binding.spinnerTodoCategory.setSelection(0)
                binding.tvSelectedDate.text = "📅 Atur Deadline & Jam"
                selectedDeadline = null
                binding.rgPriority.check(R.id.rbMedium)
                binding.llTodoInput.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "Harap isi Judul, Mata Kuliah, dan Deadline!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilterAndSearch() {
        try {
            var filteredTasks = allTasksList

            if (currentFilterPriority != "Semua Prioritas") {
                filteredTasks = filteredTasks.filter { it.priority == currentFilterPriority }
            }

            if (currentFilterCategory != "Semua Kategori") {
                filteredTasks = filteredTasks.filter { it.category == currentFilterCategory }
            }

            if (currentSearchQuery.isNotEmpty()) {
                filteredTasks = filteredTasks.filter {
                    it.title.lowercase().contains(currentSearchQuery) ||
                            it.subject.lowercase().contains(currentSearchQuery)
                }
            }

            val sortedTasks = filteredTasks.sortedWith(compareBy<Task> { it.deadline })

            val uncompletedTasks = sortedTasks.filter { !it.isDone }
            val completedTasks = sortedTasks.filter { it.isDone }

            renderTwoSectionsTasks(uncompletedTasks, completedTasks)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTaskCounters() {
        binding.tvTaskCount.text = allTasksList.count { !it.isDone }.toString()
        binding.tvDoneCount.text = allTasksList.count { it.isDone }.toString()
    }

    private fun renderTwoSectionsTasks(uncompleted: List<Task>, completed: List<Task>) {
        val containerUncompleted = binding.llTodoListUncompleted
        val containerCompleted = binding.llTodoListCompleted

        containerUncompleted.removeAllViews()
        containerCompleted.removeAllViews()

        binding.tvHeaderUncompleted.visibility = if (uncompleted.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvHeaderCompleted.visibility = if (completed.isNotEmpty()) View.VISIBLE else View.GONE

        if (uncompleted.isEmpty() && completed.isEmpty()) {
            binding.tvEmptyTodo.visibility = View.VISIBLE
        } else {
            binding.tvEmptyTodo.visibility = View.GONE
        }

        val inflater = LayoutInflater.from(requireContext())

        uncompleted.forEach { task ->
            val itemView = inflater.inflate(R.layout.item_todo, containerUncompleted, false)
            bindTaskToView(itemView, task)
            containerUncompleted.addView(itemView)
        }

        completed.forEach { task ->
            val itemView = inflater.inflate(R.layout.item_todo, containerCompleted, false)
            bindTaskToView(itemView, task)
            containerCompleted.addView(itemView)
        }

        updateTaskCounters()
    }

    private fun bindTaskToView(itemView: View, task: Task) {
        val tvText = itemView.findViewById<TextView>(R.id.tvTodoText)
        val tvSubject = itemView.findViewById<TextView>(R.id.tvTodoSubject)
        val tvDescription = itemView.findViewById<TextView>(R.id.tvTodoDescription)
        val tvCategoryBadge = itemView.findViewById<TextView>(R.id.tvTodoCategoryBadge)
        val cbDone = itemView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTodoDone)
        val tvDeadline = itemView.findViewById<TextView>(R.id.tvTodoDeadline)
        val tvPriority = itemView.findViewById<TextView>(R.id.tvPriorityLabel)

        val rootItem = itemView.findViewById<LinearLayout>(R.id.rootTodoItem)

        tvText.text = task.title
        tvSubject.text = "Mata Kuliah: ${task.subject}"
        cbDone.isChecked = task.isDone

        try {
            val sdfParser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateDeadline = sdfParser.parse(task.deadline)
            val currentTimeMs = System.currentTimeMillis()

            if (dateDeadline != null && dateDeadline.time < currentTimeMs && !task.isDone) {
                tvDeadline.text = "${task.deadline} ⚠️ (Terlambat!)"
                tvDeadline.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                tvDeadline.setTypeface(null, Typeface.BOLD)
            } else {
                tvDeadline.text = task.deadline
                tvDeadline.setTextColor(android.graphics.Color.parseColor("#334155"))
                tvDeadline.setTypeface(null, Typeface.NORMAL)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tvDeadline.text = task.deadline
            tvDeadline.setTextColor(android.graphics.Color.parseColor("#334155"))
        }

        if (task.description.isEmpty()) {
            tvDescription.visibility = View.GONE
        } else {
            tvDescription.visibility = View.VISIBLE
            tvDescription.text = task.description
        }

        tvCategoryBadge.text = task.category.uppercase()
        val badgeColor = when (task.category) {
            "Kuis" -> "#F59E0B"
            "UTS" -> "#EF4444"
            "UAS" -> "#DC2626"
            "Laporan" -> "#10B981"
            "Proyek" -> "#3B82F6"
            else -> "#64748B"
        }
        tvCategoryBadge.setBackgroundColor(android.graphics.Color.parseColor(badgeColor))

        val color = when (task.priority) {
            "Low" -> android.graphics.Color.parseColor("#22C55E")
            "High" -> android.graphics.Color.RED
            else -> android.graphics.Color.parseColor("#EAB308")
        }
        tvPriority.setTextColor(color)
        tvPriority.text = task.priority

        rootItem.setOnClickListener {
            showEditDialog(task)
        }

        rootItem.setOnLongClickListener {
            val context = itemView.context
            AlertDialog.Builder(context)
                .setTitle("Hapus Tugas Permanen?")
                .setMessage("Apakah kamu yakin ingin menghapus tugas '${task.title}'? Tindakan ini tidak bisa dibatalkan.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Hapus") { _, _ ->
                    taskViewModel.delete(task)
                    Toast.makeText(context, "Tugas '${task.title}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
            true
        }

        cbDone.setOnCheckedChangeListener { _, isChecked ->
            taskViewModel.update(task.copy(isDone = isChecked))
        }
    }

    private fun showEditDialog(task: Task) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val editTitle = EditText(context).apply {
            setText(task.title)
            hint = "Judul Tugas"
        }
        val editSubject = EditText(context).apply {
            setText(task.subject)
            hint = "Mata Kuliah"
        }
        val editDescription = EditText(context).apply {
            setText(task.description)
            hint = "Deskripsi Tugas"
        }

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

        layout.addView(editTitle)
        layout.addView(editSubject)
        layout.addView(editDescription)
        layout.addView(tvDate)

        AlertDialog.Builder(context)
            .setTitle("Edit Tugas")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newTitle = editTitle.text.toString().trim()
                val newSubject = editSubject.text.toString().trim()
                val newDescription = editDescription.text.toString().trim()

                if (newTitle.isNotEmpty() && newSubject.isNotEmpty()) {
                    taskViewModel.update(task.copy(
                        title = newTitle,
                        subject = newSubject,
                        description = newDescription,
                        deadline = newDeadline
                    ))
                    setTodoAlarmH30(newTitle, newDeadline)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupHeader() {
        try {
            val user = Firebase.auth.currentUser
            val emailUser = user?.email

            val username = if (!emailUser.isNullOrEmpty() && emailUser.contains("@")) {
                emailUser.substringBefore("@")
            } else {
                "Pengguna"
            }

            binding.tvGreeting.text = "Selamat belajar, $username! 👋"

            binding.btnLogout.setOnClickListener {
                Firebase.auth.signOut()
                findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvGreeting.text = "Selamat belajar, Pengguna! 👋"
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

            val currentTimeMs = System.currentTimeMillis()
            val alarmCalendar = Calendar.getInstance().apply {
                time = dateDeadline
            }

            val timeDifferenceMs = alarmCalendar.timeInMillis - currentTimeMs
            val triggerTimeMs: Long

            if (timeDifferenceMs > 30 * 60 * 1000) {
                triggerTimeMs = alarmCalendar.timeInMillis - (30 * 60 * 1000)
                Toast.makeText(requireContext(), "Alarm diset: 30 menit sebelum deadline", Toast.LENGTH_SHORT).show()
            } else if (timeDifferenceMs > 0) {
                triggerTimeMs = currentTimeMs + 5000
                Toast.makeText(requireContext(), "Jadwal mepet! Notif akan bunyi dalam 5 detik...", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Eror: Waktu deadline sudah terlewat!", Toast.LENGTH_SHORT).show()
                return
            }

            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(requireContext(), TodoAlarmReceiver::class.java).apply {
                putExtra("TASK_TITLE", taskTitle)
            }

            val uniqueId = taskTitle.hashCode()

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Gagal memasang alarm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildCalendarGrid() {
        try {
            val grid = binding.calendarGrid
            grid.removeAllViews()

            val cal = currentCalendarMonth.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val offset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance()
            val studiedDays = streakManager.getStudiedDays()

            grid.rowCount = ceil((offset + daysInMonth).toFloat() / 7f).toInt()

            for (i in 0 until (offset + daysInMonth)) {
                val dayNumber = i - offset + 1
                val cellView = TextView(requireContext())

                val screenWidth = resources.displayMetrics.widthPixels
                val paddingTotal = (40.dpToPx() * 2) + 16.dpToPx()
                val size = (screenWidth - paddingTotal) / 7

                cellView.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(2, 2, 2, 2)
                }
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
                        if (isToday) {
                            cellView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_streak_today)
                            cellView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white))
                        } else {
                            cellView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        }
                    }
                }
                grid.addView(cellView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Tugas Baru"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "edu_track_todo_channel"

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
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⚠️ Batas Pengumpulan Tugas!")
                .setContentText("Yuk, tugas '$taskTitle' harus segera diselesaikan sekarang! 🚀")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            notificationManager.notify(taskTitle.hashCode(), builder.build())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ── 🌟 FITUR BARU: BROADCAST RECEIVER KHUSUS UNTUK NOTIFIKASI PENGINGAT STREAK HARIAN ──
class StreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val sharedPrefs = context.getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val tanggalHariIni = sdfTanggal.format(Date())

            // Cek data ketersediaan menit belajar hari ini dari SharedPreferences Pomodoro
            val menitBelajarHariIni = sharedPrefs.getFloat("pomodoro_time_$tanggalHariIni", 0f)

            // ⚠️ KRITIS: Notifikasi hanya akan mencuat jika hari ini pengguna BELUM belajar sama sekali
            if (menitBelajarHariIni <= 0f) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "edu_track_streak_channel"

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Pengingat Streak Belajar",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Mengingatkan pengguna untuk mempertahankan streak belajar harian"
                        enableLights(true)
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // Intent untuk membuka aplikasi saat notifikasi di-klik oleh pengguna
                val openAppIntent = Intent(context, com.example.edutrack.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    995,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🔥 Jangan Biarkan Streak Belajarmu Putus!")
                    .setContentText("Kamu belum melakukan aktivitas belajar hari ini. Yuk, buka Pomodoro 25 menit sekarang! 🎯")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)

                notificationManager.notify(888, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}