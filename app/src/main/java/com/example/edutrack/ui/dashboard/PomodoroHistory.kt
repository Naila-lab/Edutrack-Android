package com.example.edutrack.ui.dashboard

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "pomodoro_history_table",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL // Jika tugas dihapus, riwayat belajar tidak ikut hilang (id tugas jadi null)
        )
    ]
)
data class PomodoroHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int?,              // ID Tugas yang ditautkan (bisa null jika user belajar mandiri tanpa memilih tugas)
    val taskTitle: String,         // Menyimpan nama tugas secara mentah demi kemudahan visualisasi di halaman Progress
    val timestampStart: String,    // Waktu mulai (Format: yyyy-MM-dd HH:mm:ss)
    val timestampEnd: String,      // Waktu selesai
    val durationMinutes: Int       // Durasi fokus (misal: 25)
)