package com.example.edutrack.ui.dashboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 🔥 REVISI: Tambahkan entitas baru dan naikkan version ke 3
@Database(entities = [Task::class, PomodoroHistory::class], version = 3, exportSchema = false)
abstract class EduTrackDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: EduTrackDatabase? = null

        fun getDatabase(context: Context): EduTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EduTrackDatabase::class.java,
                    "edutrack_database"
                )
                    .fallbackToDestructiveMigration() // Aman menghapus cache tabel lama saat naik versi
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}