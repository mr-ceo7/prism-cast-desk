package com.example.data.database

import androidx.room.*
import com.example.data.model.StreamSettings
import com.example.data.model.SessionRecord
import com.example.data.model.MotionLog
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
