package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.MotionLog
import com.example.data.model.SessionRecord
import com.example.data.model.StreamSettings
import com.example.data.repository.StreamRepository
import com.example.service.StreamService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class PlaybackState {
    IDLE,
    CONNECTING,
    PLAYING,
    ERROR
}

data class ScannedDevice(
    val ip: String,
    val port: Int,
    val name: String,
    val isPasswordRequired: Boolean
)

class ScreenStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StreamRepository

    // Stream Player States
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackBitmap = MutableStateFlow<Bitmap?>(null)
    val playbackBitmap: StateFlow<Bitmap?> = _playbackBitmap.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var playbackJob: Job? = null

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

    fun connectStream(url: String, passcode: String) {
        disconnectStream()
        
        _playbackState.value = PlaybackState.CONNECTING
        _playbackError.value = null
        _playbackBitmap.value = null
        
        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            var client: OkHttpClient? = null
            var inputStream: BufferedInputStream? = null
            var response: Response? = null
            try {
                var resolvedUrl = url.trim()
                if (resolvedUrl.isEmpty()) {
                    throw IllegalArgumentException("Stream URL cannot be empty")
                }
                if (!resolvedUrl.startsWith("http://") && !resolvedUrl.startsWith("https://")) {
                    resolvedUrl = "http://$resolvedUrl"
                }
                
                val uri = android.net.Uri.parse(resolvedUrl)
                val path = uri.path
                if (path.isNullOrEmpty() || path == "/") {
                    resolvedUrl = if (resolvedUrl.endsWith("/")) "${resolvedUrl}stream" else "$resolvedUrl/stream"
                }
                
                if (passcode.isNotEmpty()) {
                    resolvedUrl = if (resolvedUrl.contains("?")) {
                        "$resolvedUrl&auth=$passcode"
                    } else {
                        "$resolvedUrl?auth=$passcode"
                    }
                }
                
                client = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    
                val requestBuilder = Request.Builder().url(resolvedUrl)
                if (passcode.isNotEmpty()) {
                    requestBuilder.addHeader("Cookie", "prismcast_auth=$passcode")
                }
                
                response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        throw IOException("Unauthorized: Invalid passcode")
                    }
                    throw IOException("HTTP error code: ${response.code}")
                }
                
                val body = response.body ?: throw IOException("Response body is empty")
                inputStream = BufferedInputStream(body.byteStream())
                
                _playbackState.value = PlaybackState.PLAYING
                
                val buffer = ByteArrayOutputStream()
                var prevByte = -1
                var inFrame = false
                
                while (this@launch.isActive) {
                    val b = inputStream.read()
                    if (b == -1) break
                    
                    if (inFrame) {
                        buffer.write(b)
                        if (prevByte == 0xFF && b == 0xD9) {
                            val jpegBytes = buffer.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                            if (bitmap != null) {
                                _playbackBitmap.value = bitmap
                            }
                            buffer.reset()
                            inFrame = false
                        }
                        
                        if (buffer.size() > 5 * 1024 * 1024) {
                            buffer.reset()
                            inFrame = false
                        }
                    } else {
                        if (prevByte == 0xFF && b == 0xD8) {
                            buffer.write(0xFF)
                            buffer.write(0xD8)
                            inFrame = true
                        }
                    }
                    prevByte = b
                }
                
                _playbackState.value = PlaybackState.IDLE
            } catch (e: Exception) {
                if (this@launch.isActive) {
                    _playbackState.value = PlaybackState.ERROR
                    _playbackError.value = e.localizedMessage ?: "Unknown connection error"
                }
            } finally {
                try { inputStream?.close() } catch (ignored: Exception) {}
                try { response?.close() } catch (ignored: Exception) {}
            }
        }
    }
    
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startSubnetScan() {
        stopSubnetScan()
        _isScanning.value = true
        _scannedDevices.value = emptyList()

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val localIp = getLocalIp()
            if (localIp == "127.0.0.1" || !localIp.contains(".")) {
                _isScanning.value = false
                return@launch
            }

            val prefix = localIp.substringBeforeLast(".") + "."
            val ports = listOf(8080, 8081)
            val semaphore = Semaphore(60)

            val jobs = mutableListOf<Deferred<ScannedDevice?>>()
            for (host in 1..254) {
                val ip = prefix + host
                if (ip == localIp) continue
                
                for (port in ports) {
                    val deferred = async {
                        semaphore.withPermit {
                            checkPrismApp(ip, port)
                        }
                    }
                    jobs.add(deferred)
                }
            }

            jobs.forEach { deferred ->
                val device = deferred.await()
                if (device != null) {
                    _scannedDevices.value = _scannedDevices.value + device
                }
            }

            _isScanning.value = false
        }
    }

    fun stopSubnetScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    private fun getLocalIp(): String {
        try {
            for (ni in java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback || !ni.isUp) continue
                for (addr in java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotEmpty() && !ip.startsWith("127.")) return ip
                    }
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }

    private suspend fun checkPrismApp(ip: String, port: Int): ScannedDevice? {
        val client = OkHttpClient.Builder()
            .connectTimeout(350, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("http://$ip:$port/api/status")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.code == 200) {
                    if (body.contains("running") || body.contains("fps") || body.contains("activeClients") || body.contains("viewers")) {
                        val name = if (body.contains("battery") || body.contains("isRecording")) "Mobile Server" else "Desktop Server"
                        val isPasscodeReq = body.contains("\"passwordRequired\":true")
                        return ScannedDevice(ip, port, name, isPasscodeReq)
                    }
                } else if (response.code == 401 || response.code == 403) {
                    return ScannedDevice(ip, port, "Prism App (Protected)", true)
                }
            }
        } catch (e: Exception) {}

        val indexRequest = Request.Builder()
            .url("http://$ip:$port/")
            .build()
        try {
            client.newCall(indexRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.contains("Prism Cast") || body.contains("prismcast")) {
                    val name = if (body.contains("by Galvaniy Studios")) "Desktop Server" else "Mobile Server"
                    val isPasscodeReq = response.code == 401 || response.code == 403
                    return ScannedDevice(ip, port, name, isPasscodeReq)
                }
            }
        } catch (e: Exception) {}

        return null
    }

    fun disconnectStream() {
        playbackJob?.cancel()
        playbackJob = null
        _playbackState.value = PlaybackState.IDLE
        _playbackBitmap.value = null
        _playbackError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnectStream()
        stopSubnetScan()
    }
}
