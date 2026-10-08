package com.example.data.database

import androidx.room.*
import com.example.data.model.StreamSettings
import com.example.data.model.SessionRecord
import com.example.data.model.MotionLog
import com.example.data.model.TodoItem
import com.example.data.model.NotepadNote
import com.example.data.model.KpiCard
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSettingsDao {
    @Query("SELECT * FROM stream_settings WHERE id = 'active_settings' LIMIT 1")
    fun getSettingsFlow(): Flow<StreamSettings?>

    @Query("SELECT * FROM stream_settings WHERE id = 'active_settings' LIMIT 1")
    suspend fun getSettings(): StreamSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: StreamSettings)
}

@Dao
interface SessionRecordDao {
    @Query("SELECT * FROM session_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<SessionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SessionRecord): Long

    @Query("DELETE FROM session_records WHERE id = :id")
    suspend fun deleteRecord(id: Int)

    @Query("DELETE FROM session_records")
    suspend fun clearAllRecords()
}

@Dao
interface MotionLogDao {
    @Query("SELECT * FROM motion_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<MotionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MotionLog): Long

    @Query("DELETE FROM motion_logs WHERE id = :id")
    suspend fun deleteLog(id: Int)

    @Query("DELETE FROM motion_logs")
    suspend fun clearAllLogs()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllTodosFlow(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY isCompleted ASC, createdAt DESC")
    suspend fun getAllTodos(): List<TodoItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem): Long

    @Update
    suspend fun updateTodo(todo: TodoItem)

    @Query("UPDATE todo_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun toggleTodo(id: Int, isCompleted: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteTodo(id: Int)

    @Query("DELETE FROM todo_items WHERE isCompleted = 1")
    suspend fun clearCompleted()
}

@Dao
interface NotepadDao {
    @Query("SELECT * FROM notepad_notes WHERE id = 1 LIMIT 1")
    fun getNoteFlow(): Flow<NotepadNote?>

    @Query("SELECT * FROM notepad_notes WHERE id = 1 LIMIT 1")
    suspend fun getNote(): NotepadNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: NotepadNote)
}

@Dao
interface KpiCardDao {
    @Query("SELECT * FROM kpi_cards ORDER BY updatedAt DESC")
    fun getAllKpisFlow(): Flow<List<KpiCard>>

    @Query("SELECT * FROM kpi_cards ORDER BY updatedAt DESC")
    suspend fun getAllKpis(): List<KpiCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKpi(card: KpiCard)

    @Query("DELETE FROM kpi_cards WHERE id = :id")
    suspend fun deleteKpi(id: String)
}

