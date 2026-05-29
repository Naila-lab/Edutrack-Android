package com.example.edutrack.ui.jadwal

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.edutrack.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Model Data Lokal Jadwal Belajar
 * Dibuat independen tanpa anotasi @Entity Room Database
 */
data class Jadwal(
    val id: Long = System.currentTimeMillis(),
    val mataKuliah: String,
    val jamMulai: String,
    val jamSelesai: String,
    val keterangan: String
)

class JadwalFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var jadwalAdapter: InnerJadwalAdapter
    private val allJadwalList = mutableListOf<Jadwal>()

    // Infrastruktur penyimpanan lokal SharedPreferences kelompok
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

        // Inisialisasi Komponen UI RecyclerView
        recyclerView = view.findViewById(R.id.recyclerJadwal)
        jadwalAdapter = InnerJadwalAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = jadwalAdapter

        // Inisialisasi Floating Action Button (FAB)
        val fabAdd: FloatingActionButton = view.findViewById(R.id.fabAddJadwal)
        fabAdd.setOnClickListener {
            showAddJadwalDialog()
        }

        // Memuat data lokal awal
        loadAndFilterJadwal()
    }

    /**
     * Memuat data dari SharedPreferences dan memfilter jadwal kedaluwarsa secara otomatis
     */
    private fun loadAndFilterJadwal() {
        allJadwalList.clear()
        val sharedPrefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = sharedPrefs.getString(jadwalKey, null)

        if (json != null) {
            val type = object : TypeToken<MutableList<Jadwal>>() {}.type
            val savedList: List<Jadwal> = gson.fromJson(json, type)
            allJadwalList.addAll(savedList)
        }

        // Dapatkan jam sistem sekarang dengan format "HH:mm"
        val formatWaktu = SimpleDateFormat("HH:mm", Locale.getDefault())
        val jamSekarangString = formatWaktu.format(Date())

        // Mengeliminasi jadwal yang jam selesainya sudah terlewati jam sistem sekarang
        val jadwalAktif = allJadwalList.filter { it.jamSelesai >= jamSekarangString }

        jadwalAdapter.setData(jadwalAktif)
    }

    /**
     * Memunculkan Dialog Penginputan Jadwal Baru
     */
    private fun showAddJadwalDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_add_jadwal)

        val etMataKuliah = dialog.findViewById<EditText>(R.id.et_mata_kuliah)
        val etJamMulai = dialog.findViewById<EditText>(R.id.et_jam_mulai)
        val etJamSelesai = dialog.findViewById<EditText>(R.id.et_jam_selesai)
        val etKeterangan = dialog.findViewById<EditText>(R.id.et_keterangan)
        val btnSimpan = dialog.findViewById<Button>(R.id.btn_simpan_jadwal)

        btnSimpan.setOnClickListener {
            val matkul = etMataKuliah.text.toString().trim()
            val mulai = etJamMulai.text.toString().trim()
            val selesai = etJamSelesai.text.toString().trim()
            val ket = etKeterangan.text.toString().trim()

            if (matkul.isNotEmpty() && mulai.isNotEmpty() && selesai.isNotEmpty()) {
                val jadwalBaru = Jadwal(
                    mataKuliah = matkul,
                    jamMulai = mulai,
                    jamSelesai = selesai,
                    keterangan = ket
                )

                // Simpan ke SharedPreferences lokal
                allJadwalList.add(jadwalBaru)
                requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putString(jadwalKey, gson.toJson(allJadwalList))
                    .apply()

                // Daftarkan Notifikasi via AlarmManager
                setJadwalAlarm(matkul, mulai)

                Toast.makeText(requireContext(), "Jadwal sukses disimpan!", Toast.LENGTH_SHORT).show()
                loadAndFilterJadwal()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Harap isi semua kolom wajib!", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    /**
     * Mendaftarkan Waktu Pengingat Kuliah ke Sistem Alarm HP
     */
    private fun setJadwalAlarm(title: String, time: String) {
        try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (calendar.before(Calendar.getInstance())) {
                calendar.add(Calendar.DATE, 1)
            }

            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(requireContext(), AlarmReceiver2::class.java).apply {
                putExtra("TITLE", title)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── INNER CLASS RECYCLERVIEW ADAPTER ─────────────────────────────────────
    inner class InnerJadwalAdapter : RecyclerView.Adapter<InnerJadwalAdapter.JadwalVH>() {
        private val list = mutableListOf<Jadwal>()

        fun setData(newList: List<Jadwal>) {
            list.clear()
            list.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JadwalVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return JadwalVH(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: JadwalVH, position: Int) {
            holder.bind(list[position])
        }

        inner class JadwalVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val text1: TextView = itemView.findViewById(android.R.id.text1)
            private val text2: TextView = itemView.findViewById(android.R.id.text2)

            fun bind(jadwal: Jadwal) {
                text1.text = "${jadwal.mataKuliah} (${jadwal.jamMulai} - ${jadwal.jamSelesai})"
                text2.text = jadwal.keterangan
            }
        }
    }
}

/**
 * Receiver Mandiri Global untuk Memproses Trigger Notifikasi Kuliah
 */
class AlarmReceiver2 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TITLE") ?: "Jadwal Belajar"
        val builder = NotificationCompat.Builder(context, "edutrack_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktunya Kuliah/Belajar!")
            .setContentText("Sekarang masuk sesi: $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(
                System.currentTimeMillis().toInt(),
                builder.build()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}