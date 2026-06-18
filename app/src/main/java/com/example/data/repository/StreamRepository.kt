package com.example.data.repository

import com.example.data.database.StreamSettingsDao
import com.example.data.database.SessionRecordDao
import com.example.data.database.MotionLogDao
import com.example.data.model.StreamSettings
import com.example.data.model.SessionRecord
import com.example.data.model.MotionLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StreamRepository(
    private val settingsDao: StreamSettingsDao,
    private val sessionRecordDao: SessionRecordDao,
    private val motionLogDao: MotionLogDao
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
}
