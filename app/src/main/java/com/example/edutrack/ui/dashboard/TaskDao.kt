package com.example.edutrack.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM task_table ORDER BY id DESC")
    fun getAllTasks(): LiveData<List<Task>>

    // ── 🌟 FITUR BARU: MANAJEMEN RIWAYAT POMODORO DATABASE ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPomodoroHistory(history: PomodoroHistory)

    @Query("SELECT * FROM pomodoro_history_table ORDER BY id DESC")
    fun getAllPomodoroHistory(): LiveData<List<PomodoroHistory>>
}