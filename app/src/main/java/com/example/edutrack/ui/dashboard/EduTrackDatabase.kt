package com.example.edutrack.ui.dashboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Pastikan hanya mengurus Task, karena Jadwal sudah pindah ke SharedPreferences
@Database(entities = [Task::class], version = 2, exportSchema = false)
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}