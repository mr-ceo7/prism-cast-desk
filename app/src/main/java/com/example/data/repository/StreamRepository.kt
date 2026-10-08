package com.example.data.repository

import com.example.data.database.StreamSettingsDao
import com.example.data.database.SessionRecordDao
import com.example.data.database.MotionLogDao
import com.example.data.database.TodoDao
import com.example.data.database.NotepadDao
import com.example.data.database.KpiCardDao
import com.example.data.model.StreamSettings
import com.example.data.model.SessionRecord
import com.example.data.model.MotionLog
import com.example.data.model.TodoItem
import com.example.data.model.NotepadNote
import com.example.data.model.KpiCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StreamRepository(
    private val settingsDao: StreamSettingsDao,
    private val sessionRecordDao: SessionRecordDao,
    private val motionLogDao: MotionLogDao,
    private val todoDao: TodoDao,
    private val notepadDao: NotepadDao,
    private val kpiCardDao: KpiCardDao
) {
    // Settings logic with defaults
    val settingsFlow: Flow<StreamSettings> = settingsDao.getSettingsFlow().map {
        it ?: StreamSettings().also { defaultSettings ->
            settingsDao.saveSettings(defaultSettings)
        }
    }

    suspend fun getSettings(): StreamSettings {
        return settingsDao.getSettings() ?: StreamSettings().also { defaultSettings ->
            settingsDao.saveSettings(defaultSettings)
        }
    }

    suspend fun saveSettings(settings: StreamSettings) {
        settingsDao.saveSettings(settings)
    }

    // Session Records logic
    val sessionRecords: Flow<List<SessionRecord>> = sessionRecordDao.getAllRecords()

    suspend fun insertSession(record: SessionRecord): Long {
        return sessionRecordDao.insertRecord(record)
    }

    suspend fun deleteSession(id: Int) {
        sessionRecordDao.deleteRecord(id)
    }

    suspend fun clearSessions() {
        sessionRecordDao.clearAllRecords()
    }

    // Motion Logs logic
    val motionLogs: Flow<List<MotionLog>> = motionLogDao.getAllLogs()

    suspend fun insertMotionLog(log: MotionLog): Long {
        return motionLogDao.insertLog(log)
    }

    suspend fun deleteMotionLog(id: Int) {
        motionLogDao.deleteLog(id)
    }

    suspend fun clearMotionLogs() {
        motionLogDao.clearAllLogs()
    }

    // --- To-Do Items ---
    val todosFlow: Flow<List<TodoItem>> = todoDao.getAllTodosFlow()

    suspend fun getAllTodos(): List<TodoItem> = todoDao.getAllTodos()

    suspend fun addTodo(text: String, priority: String = "NORMAL"): Long {
        return todoDao.insertTodo(TodoItem(text = text, priority = priority))
    }

    suspend fun toggleTodo(id: Int, isCompleted: Boolean) {
        todoDao.toggleTodo(id, isCompleted)
    }

    suspend fun deleteTodo(id: Int) {
        todoDao.deleteTodo(id)
    }

    suspend fun clearCompletedTodos() {
        todoDao.clearCompleted()
    }

    // --- Notepad ---
    val noteFlow: Flow<NotepadNote> = notepadDao.getNoteFlow().map {
        it ?: NotepadNote(id = 1, title = "Ambient Quick Notes", content = "Welcome to Prism Cast Ambient Wall HUD.\n- Ask Jarvis questions from your phone remote.\n- Stream sensor telemetry or monitor live metrics.")
    }

    suspend fun getNote(): NotepadNote {
        return notepadDao.getNote() ?: NotepadNote(id = 1, title = "Ambient Quick Notes", content = "")
    }

    suspend fun saveNote(content: String, title: String = "Ambient Quick Notes") {
        notepadDao.saveNote(NotepadNote(id = 1, title = title, content = content, updatedAt = System.currentTimeMillis()))
    }

    // --- KPI Cards ---
    val kpisFlow: Flow<List<KpiCard>> = kpiCardDao.getAllKpisFlow()

    suspend fun getAllKpis(): List<KpiCard> = kpiCardDao.getAllKpis()

    suspend fun upsertKpi(card: KpiCard) {
        kpiCardDao.upsertKpi(card)
    }

    suspend fun deleteKpi(id: String) {
        kpiCardDao.deleteKpi(id)
    }
}

