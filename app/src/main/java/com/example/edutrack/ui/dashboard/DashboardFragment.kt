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
import java.util.Calendar
import android.app.AlertDialog
import android.widget.EditText
import java.util.*
import android.content.Intent
import android.widget.Toast
import com.example.edutrack.ui.pomodoro.PomodoroTimerActivity

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var streakManager: StreakManager
    private lateinit var taskViewModel: TaskViewModel

    // Variabel untuk simpan deadline yang dipilih
    private var selectedDeadline = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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

        streakManager.markTodayAsStudied()
        updateStreakUI()

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

    private fun setupTodoDatabase() {
        taskViewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            renderTasks(tasks)
        }

        binding.btnAddTodo.setOnClickListener {
            binding.llTodoInput.visibility = View.VISIBLE
            binding.etTodoInput.requestFocus()
        }

        // Logic Pilih Tanggal
        binding.tvSelectedDate.setOnClickListener {
            showDatePicker { date ->
                selectedDeadline = date
                binding.tvSelectedDate.text = "$date"
            }
        }

        binding.btnSaveTodo.setOnClickListener {
            val text = binding.etTodoInput.text.toString().trim()

            // 1. Ambil nilai prioritas dari RadioGroup
            val selectedId = binding.rgPriority.checkedRadioButtonId
            val priority = when (selectedId) {
                R.id.rbLow -> "Low"
                R.id.rbHigh -> "High"
                else -> "Medium"
            }

            // 2. Validasi: Pastikan user mengisi teks dan memilih tanggal
            if (text.isNotEmpty() && selectedDeadline != null) {
                val newTask = Task(
                    title = text,
                    subject = "Umum",
                    priority = priority,
                    deadline = selectedDeadline!!
                )
                taskViewModel.insert(newTask)

                // 3. Reset UI setelah simpan
                binding.etTodoInput.text?.clear()
                binding.tvSelectedDate.text = "📅 Atur Deadline" // Reset teks
                selectedDeadline = null // Reset variabel tanggal
                binding.rgPriority.check(R.id.rbMedium) // Reset prioritas ke Medium
                binding.llTodoInput.visibility = View.GONE
            } else if (selectedDeadline == null) {
                // Beri peringatan kalau user lupa pilih tanggal
                binding.tvSelectedDate.error = "Pilih deadline dulu!"
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

        val roundedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(android.graphics.Color.WHITE)
            setStroke(2, android.graphics.Color.LTGRAY)
        }

        val editText = EditText(context).apply { setText(task.title) }
        val tvDate = TextView(context).apply {
            text = "📅 ${task.deadline}"
            setPadding(0, 20, 0, 20)
        }

        var newDeadline = task.deadline

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            // Hapus "listener = "
            DatePickerDialog(context, { _, y, m, d ->
                newDeadline = String.format("%04d-%02d-%02d", y, m + 1, d)
                tvDate.text = "📅 $newDeadline"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        layout.addView(editText)
        layout.addView(tvDate)

        AlertDialog.Builder(context)
            .setTitle("Edit Tugas")
            .setView(layout)
            // Hapus "text = "
            .setPositiveButton("Simpan") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    taskViewModel.update(task.copy(title = newTitle, deadline = newDeadline))
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

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
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
                    cellView.setTextColor(ContextCompat.getColor(requireContext(), if(isToday) R.color.text_primary else R.color.text_secondary))
                }
            }
            grid.addView(cellView)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupQuickActions() {
        binding.btnGoSchedule.setOnClickListener { }
        binding.btnGoPomodoro.setOnClickListener { }
        binding.btnGoFlashcard.setOnClickListener { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}