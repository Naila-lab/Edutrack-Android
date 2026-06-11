package com.example.edutrack.ui.jadwal

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.edutrack.R
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Data Model ──
data class Jadwal(
    val id: Long = System.currentTimeMillis(),
    val mataKuliah: String,
    val tanggalBelajar: String,
    val jamMulai: String,
    val jamSelesai: String,
    val keterangan: String
)

class JadwalFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var jadwalAdapter: InnerJadwalAdapter
    private val allJadwalList = mutableListOf<Jadwal>()

    private lateinit var tvSesiMatkul1: TextView
    private lateinit var tvSesiWaktu1: TextView
    private lateinit var tvSesiMatkul2: TextView
    private lateinit var tvSesiWaktu2: TextView

    private lateinit var calendarViewJadwal: CalendarView
    private lateinit var btnResetFilter: Button
    private lateinit var tvLabelDaftar: TextView
    private var selectedFilterTanggal: String? = null

    // 🌟 INTEGRASI FITUR: Tab Layout Filter
    private lateinit var tabLayoutJadwal: TabLayout
    private var currentSelectedTab = 0 // 0 = Akan Datang, 1 = Riwayat

    private val gson = Gson()
    private val prefsName = "study_prefs"
    private val jadwalKey = "jadwal_belajar_v1"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_jadwal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        tvSesiMatkul1 = view.findViewById(R.id.tvSesiMatkul1)
        tvSesiWaktu1 = view.findViewById(R.id.tvSesiWaktu1)
        tvSesiMatkul2 = view.findViewById(R.id.tvSesiMatkul2)
        tvSesiWaktu2 = view.findViewById(R.id.tvSesiWaktu2)

        calendarViewJadwal = view.findViewById(R.id.calendarViewJadwal)
        btnResetFilter     = view.findViewById(R.id.btnResetFilter)
        tvLabelDaftar      = view.findViewById(R.id.tvLabelDaftar)

        // 🌟 INTEGRASI FITUR: Inisialisasi & Listener Tab Filter
        tabLayoutJadwal = view.findViewById(R.id.tabLayoutJadwal)
        tabLayoutJadwal.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSelectedTab = tab?.position ?: 0
                loadJadwalTotal()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        recyclerView = view.findViewById(R.id.recyclerJadwal)
        jadwalAdapter = InnerJadwalAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = jadwalAdapter

        val fabAdd = view.findViewById<Button>(R.id.fabAddJadwal)
        fabAdd.setOnClickListener {
            showAddJadwalDialog()
        }

        calendarViewJadwal.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val formatTanggal = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            selectedFilterTanggal = formatTanggal

            tvLabelDaftar.text = "Jadwal Tanggal: $formatTanggal"
            btnResetFilter.visibility = View.VISIBLE
            loadJadwalTotal()
        }

        btnResetFilter.setOnClickListener {
            selectedFilterTanggal = null
            tvLabelDaftar.text = "Semua Daftar Jadwal"
            btnResetFilter.visibility = View.GONE
            loadJadwalTotal()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        loadJadwalTotal()
    }

    private fun loadJadwalTotal() {
        allJadwalList.clear()
        val sharedPrefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = sharedPrefs.getString(jadwalKey, null)

        if (json != null) {
            val type = object : TypeToken<MutableList<Jadwal>>() {}.type
            val savedList: List<Jadwal> = gson.fromJson(json, type)
            allJadwalList.addAll(savedList)
        }

        // Urutkan Kronologis
        allJadwalList.sortWith(
            compareBy<Jadwal> { it.tanggalBelajar }.thenBy { it.jamMulai }
        )

        val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val tanggalHariIni = sdfTanggal.format(Date())

        val sdfJam = SimpleDateFormat("HH:mm", Locale.getDefault())
        val jamSekarang = sdfJam.format(Date())

        // Filter Tahap 1: Kalender Pop-Up
        val dataSaringanKalender = if (selectedFilterTanggal != null) {
            allJadwalList.filter { it.tanggalBelajar == selectedFilterTanggal }
        } else {
            allJadwalList
        }

        // 🌟 INTEGRASI FITUR: Pemisahan Data Berdasarkan Tab (Akan Datang vs Riwayat)
        val dataFinalTampil = if (currentSelectedTab == 0) {
            dataSaringanKalender.filter {
                it.tanggalBelajar > tanggalHariIni || (it.tanggalBelajar == tanggalHariIni && it.jamSelesai >= jamSekarang)
            }
        } else {
            dataSaringanKalender.filter {
                it.tanggalBelajar < tanggalHariIni || (it.tanggalBelajar == tanggalHariIni && it.jamSelesai < jamSekarang)
            }
        }

        jadwalAdapter.setData(dataFinalTampil)
        updateSesiTerdekatRealTime()
    }

    private fun updateSesiTerdekatRealTime() {
        try {
            val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val tanggalHariIni = sdfTanggal.format(Date())

            val sdfJam = SimpleDateFormat("HH:mm", Locale.getDefault())
            val jamSekarang = sdfJam.format(Date())

            val jadwalMendatang = allJadwalList.filter {
                it.tanggalBelajar == tanggalHariIni && it.jamSelesai >= jamSekarang
            }.sortedBy { it.jamMulai }

            if (jadwalMendatang.isNotEmpty()) {
                val sesi1 = jadwalMendatang[0]
                tvSesiWaktu1.text = "${sesi1.jamMulai} - ${sesi1.jamSelesai}"
                tvSesiMatkul1.text = sesi1.mataKuliah

                if (jadwalMendatang.size > 1) {
                    val sesi2 = jadwalMendatang[1]
                    tvSesiWaktu2.text = "${sesi2.jamMulai} - ${sesi2.jamSelesai}"
                    tvSesiMatkul2.text = sesi2.mataKuliah
                } else {
                    tvSesiWaktu2.text = "--:--"
                    tvSesiMatkul2.text = "Tidak ada sesi berikutnya"
                }
            } else {
                tvSesiMatkul1.text = "Semua kuliah hari ini selesai!"
                tvSesiWaktu1.text = "Waktu istirahat"
                tvSesiMatkul2.text = "Tidak ada sesi terdekat"
                tvSesiWaktu2.text = "--:--"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAddJadwalDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_add_jadwal)

        val etMataKuliah = dialog.findViewById<EditText>(R.id.et_mata_kuliah)
        val etTanggal    = dialog.findViewById<EditText>(R.id.et_tanggal_belajar)
        val etJamMulai   = dialog.findViewById<EditText>(R.id.et_jam_mulai)
        val etJamSelesai = dialog.findViewById<EditText>(R.id.et_jam_selesai)
        val etKeterangan = dialog.findViewById<EditText>(R.id.et_keterangan)
        val btnSimpan    = dialog.findViewById<Button>(R.id.btn_simpan_jadwal)

        setupDateTimePickers(etTanggal, etJamMulai, etJamSelesai)

        btnSimpan.setOnClickListener {
            val matkul  = etMataKuliah.text.toString().trim()
            val tanggal = etTanggal.text.toString().trim()
            val mulai   = etJamMulai.text.toString().trim()
            val selesai = etJamSelesai.text.toString().trim()
            val ket     = etKeterangan.text.toString().trim()

            if (matkul.isNotEmpty() && tanggal.isNotEmpty() && mulai.isNotEmpty() && selesai.isNotEmpty()) {
                val jadwalBaru = Jadwal(
                    mataKuliah = matkul,
                    tanggalBelajar = tanggal,
                    jamMulai = mulai,
                    jamSelesai = selesai,
                    keterangan = ket
                )

                allJadwalList.add(jadwalBaru)
                saveToSharedPreferences()
                setJadwalAlarm(matkul, tanggal, mulai)
                loadJadwalTotal()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Harap isi semua kolom wajib!", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showEditJadwalDialog(jadwalLama: Jadwal) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_add_jadwal)

        val etMataKuliah = dialog.findViewById<EditText>(R.id.et_mata_kuliah)
        val etTanggal    = dialog.findViewById<EditText>(R.id.et_tanggal_belajar)
        val etJamMulai   = dialog.findViewById<EditText>(R.id.et_jam_mulai)
        val etJamSelesai = dialog.findViewById<EditText>(R.id.et_jam_selesai)
        val etKeterangan = dialog.findViewById<EditText>(R.id.et_keterangan)
        val btnSimpan    = dialog.findViewById<Button>(R.id.btn_simpan_jadwal)

        btnSimpan.text = "PERBARUI JADWAL"

        etMataKuliah.setText(jadwalLama.mataKuliah)
        etTanggal.setText(jadwalLama.tanggalBelajar)
        etJamMulai.setText(jadwalLama.jamMulai)
        etJamSelesai.setText(jadwalLama.jamSelesai)
        etKeterangan.setText(jadwalLama.keterangan)

        setupDateTimePickers(etTanggal, etJamMulai, etJamSelesai)

        btnSimpan.setOnClickListener {
            val matkul  = etMataKuliah.text.toString().trim()
            val tanggal = etTanggal.text.toString().trim()
            val mulai   = etJamMulai.text.toString().trim()
            val selesai = etJamSelesai.text.toString().trim()
            val ket     = etKeterangan.text.toString().trim()

            if (matkul.isNotEmpty() && tanggal.isNotEmpty() && mulai.isNotEmpty() && selesai.isNotEmpty()) {
                val index = allJadwalList.indexOfFirst { it.id == jadwalLama.id }
                if (index != -1) {
                    val jadwalUpdate = Jadwal(
                        id = jadwalLama.id,
                        mataKuliah = matkul,
                        tanggalBelajar = tanggal,
                        jamMulai = mulai,
                        jamSelesai = selesai,
                        keterangan = ket
                    )

                    allJadwalList[index] = jadwalUpdate
                    saveToSharedPreferences()
                    Toast.makeText(requireContext(), "Jadwal sukses diperbarui!", Toast.LENGTH_SHORT).show()
                    loadJadwalTotal()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(requireContext(), "Harap isi semua kolom wajib!", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun setupDateTimePickers(etTanggal: EditText, etMulai: EditText, etSelesai: EditText) {
        etTanggal.isFocusable = false
        etMulai.isFocusable = false
        etSelesai.isFocusable = false

        etTanggal.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                etTanggal.setText(formattedDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        etMulai.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                etMulai.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        etSelesai.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                etSelesai.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
    }

    private fun deleteJadwal(jadwal: Jadwal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Jadwal?")
            .setMessage("Apakah yakin ingin menghapus '${jadwal.mataKuliah}'?")
            .setPositiveButton("Hapus") { _, _ ->
                allJadwalList.remove(jadwal)
                saveToSharedPreferences()
                Toast.makeText(requireContext(), "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
                loadJadwalTotal()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveToSharedPreferences() {
        requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(jadwalKey, gson.toJson(allJadwalList))
            .apply()
    }

    // ── 🌟 REVISI SAKTI: LOGIKA ADAPTIF JADWAL H-30 & FITUR MEPEET 5 DETIK (ANTI-MOGOK) ──
    private fun setJadwalAlarm(title: String, tanggal: String, time: String) {
        try {
            if (!time.contains(":") || !tanggal.contains("-")) return

            val partsTime = time.split(":")
            val hour = partsTime[0].toInt()
            val minute = partsTime[1].toInt()

            val partsDate = tanggal.split("-")
            val year = partsDate[0].toInt()
            val month = partsDate[1].toInt() - 1
            val day = partsDate[2].toInt()

            val currentTimeMs = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Hitung selisih waktu antara jam mulai kuliah dengan jam asli saat ini
            val timeDifferenceMs = calendar.timeInMillis - currentTimeMs
            val triggerTimeMs: Long

            if (timeDifferenceMs > 30 * 60 * 1000) {
                // Skenario A: Jika kuliah masih lama (di atas 30 menit), kurangi 30 menit dengan akurat!
                triggerTimeMs = calendar.timeInMillis - (30 * 60 * 1000)
                Toast.makeText(requireContext(), "Alarm Jadwal diset: 30 menit sebelum kuliah", Toast.LENGTH_SHORT).show()
            } else if (timeDifferenceMs > 0) {
                // Skenario B: Jika jadwal mepeet (di bawah 30 menit), paksa langsung bunyi 5 detik lagi untuk demo!
                triggerTimeMs = currentTimeMs + 5000
                Toast.makeText(requireContext(), "Jadwal mepet! Notif kuliah bunyi dalam 5 detik...", Toast.LENGTH_LONG).show()
            } else {
                // Skenario C: Waktu kuliah sudah lewat, abaikan
                return
            }

            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(requireContext(), AlarmReceiver2::class.java).apply {
                putExtra("TITLE", title)
            }

            val uniqueId = title.hashCode() // Gunakan hashcode nama matkul agar ID unik per mata kuliah

            // 🔥 PERBAIKAN UTAMA: Ubah FLAG_IMMUTABLE menjadi FLAG_MUTABLE agar data putExtra tidak dibuang Android OS
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
            Toast.makeText(requireContext(), "Gagal set alarm jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // ── Adapter RecyclerView ──
    inner class InnerJadwalAdapter : RecyclerView.Adapter<InnerJadwalAdapter.JadwalVH>() {
        private val list = mutableListOf<Jadwal>()

        fun setData(newList: List<Jadwal>) {
            list.clear()
            list.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JadwalVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_jadwal_card, parent, false)
            return JadwalVH(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: JadwalVH, position: Int) {
            holder.bind(list[position])
        }

        inner class JadwalVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMatkul: TextView = itemView.findViewById(R.id.tvItemMataKuliah)
            private val tvKet: TextView = itemView.findViewById(R.id.tvItemKeterangan)
            private val tvWaktu: TextView = itemView.findViewById(R.id.tvItemWaktu)

            // 🌟 INTEGRASI FITUR: Komponen indikator warna dari XML kustom card baru
            private val viewLine: View = itemView.findViewById(R.id.viewIndicatorLine)
            private val tvLabelWaktu: TextView = itemView.findViewById(R.id.tvLabelWaktu)

            fun bind(jadwal: Jadwal) {
                tvMatkul.text = jadwal.mataKuliah
                tvKet.text = if (jadwal.keterangan.isEmpty()) "Tanpa keterangan" else jadwal.keterangan
                tvWaktu.text = "${jadwal.tanggalBelajar} | ${jadwal.jamMulai} - ${jadwal.jamSelesai}"

                // 🌟 INTEGRASI FITUR: Logika Deteksi Waktu Lampau Untuk Visual De-emphasis (Abu-abu)
                val sdfTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val tanggalHariIni = sdfTanggal.format(Date())

                val sdfJam = SimpleDateFormat("HH:mm", Locale.getDefault())
                val jamSekarang = sdfJam.format(Date())

                val isPast = jadwal.tanggalBelajar < tanggalHariIni ||
                        (jadwal.tanggalBelajar == tanggalHariIni && jadwal.jamSelesai < jamSekarang)

                if (isPast) {
                    viewLine.setBackgroundColor(android.graphics.Color.parseColor("#94A3B8"))
                    tvLabelWaktu.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                    tvMatkul.setTextColor(android.graphics.Color.parseColor("#64748B"))
                } else {
                    viewLine.setBackgroundColor(android.graphics.Color.parseColor("#1D4ED8"))
                    tvLabelWaktu.setTextColor(android.graphics.Color.parseColor("#1D4ED8"))
                    tvMatkul.setTextColor(android.graphics.Color.parseColor("#212121"))
                }

                itemView.setOnClickListener {
                    showEditJadwalDialog(jadwal)
                }

                itemView.setOnLongClickListener {
                    deleteJadwal(jadwal)
                    true
                }
            }
        }
    }
}

// ── RECEIVER NOTIFIKASI PENGINGAT JADWAL PREMIUM ──
// ── 🌟 REVISI SAKTI: RECEIVER JADWAL MANDIRI (ANTI-SILENT CRASH & TANPA JALUR SHAREDPREFS) ──
class AlarmReceiver2 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val titleMatkul = intent.getStringExtra("TITLE") ?: "Mata Kuliah Baru"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "edu_track_jadwal_channel"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Pengingat Jadwal Kuliah",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Mengingatkan aktivitas EduTrack"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Pakai icon default sistem agar 100% aman
                .setContentTitle("📚 Pengingat Aktivitas EduTrack")
                .setContentText("Agenda kuliah '$titleMatkul' sudah dekat! Yuk, persiapkan diri dan fokus! 🚀")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            notificationManager.notify(titleMatkul.hashCode(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}