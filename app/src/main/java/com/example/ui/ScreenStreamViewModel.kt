package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.MotionLog
import com.example.data.model.SessionRecord
import com.example.data.model.StreamSettings
import com.example.data.repository.StreamRepository
import com.example.service.StreamService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ScreenStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StreamRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = StreamRepository(db.settingsDao(), db.sessionRecordDao(), db.motionLogDao())
    }

    // Bind with the streaming service's static state flows
    val isStreaming: StateFlow<Boolean> = StreamService.isStreaming
    val activeClients: StateFlow<Int> = StreamService.activeClientCount
    val streamingUrl: StateFlow<String?> = StreamService.streamingUrl
    val currentFps: StateFlow<Float> = StreamService.currentFps

    // Dynamic Flow streams from Room DB tables
    val settingsState: StateFlow<StreamSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreamSettings()
        )

    val sessionRecords: StateFlow<List<SessionRecord>> = repository.sessionRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val motionLogs: StateFlow<List<MotionLog>> = repository.motionLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSettings(settings: StreamSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }

    fun deleteSession(record: SessionRecord) {
        viewModelScope.launch {
            record.savedFramesDirectory?.let { path ->
                val dir = File(path)
                dir.deleteRecursively()
            }
            repository.deleteSession(record.id)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearSessions()
            val dir = File(getApplication<Application>().filesDir, "recordings")
            dir.deleteRecursively()
        }
    }

    fun deleteMotionLog(log: MotionLog) {
        viewModelScope.launch {
            log.snapshotPath?.let { path ->
                val file = File(path)
                file.delete()
            }
            repository.deleteMotionLog(log.id)
        }
    }

    fun clearAllMotionLogs() {
        viewModelScope.launch {
            repository.clearMotionLogs()
            val dir = File(getApplication<Application>().filesDir, "motion")
            dir.deleteRecursively()
        }
    }
}
