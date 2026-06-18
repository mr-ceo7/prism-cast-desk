package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_settings")
data class StreamSettings(
    @PrimaryKey val id: String = "active_settings",
    val port: Int = 8080,
    val isPasswordEnabled: Boolean = false,
    val passcode: String = "admin",
    val resolution: String = "Medium", // "Low", "Medium", "High"
    val fps: Int = 10,
    val isMotionDetectionEnabled: Boolean = false,
    val motionSensitivity: Int = 50, // threshold 1-100
    val isRecordingEnabled: Boolean = false,
    val audioSource: String = "Microphone", // "Microphone", "System Audio", "Muted"
    val isRemoteStreamingEnabled: Boolean = false,
    val remoteStreamUrl: String = "ws://10.0.2.2:8000/api/stream/ws/push",
    val remoteStreamKey: String = "tambuatips_stream_secret_key"
)

@Entity(tableName = "session_records")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val frameCount: Int = 0,
    val sizeBytes: Long = 0,
    val savedFramesDirectory: String? = null
)

@Entity(tableName = "motion_logs")
data class MotionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Int = 0, // motion dynamic difference
    val snapshotPath: String? = null // local file path to the saved screenshot
)
