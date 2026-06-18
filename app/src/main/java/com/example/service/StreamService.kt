package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.MotionLog
import com.example.data.model.SessionRecord
import com.example.data.model.StreamSettings
import com.example.data.repository.StreamRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.ServerSocket
import java.net.Socket
import java.util.StringTokenizer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

class StreamService : Service() {

    // Remote Stream WebSocket Relay
    private var okHttpClient: OkHttpClient? = null
    private var remoteWebSocket: WebSocket? = null
    private var isRemoteConnecting = false
    private var remoteConnectionJob: Job? = null
    
    // Background Locks to prevent service termination
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var repository: StreamRepository
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var reusablePixelArray: ByteArray? = null
    private var reusableBitmap: Bitmap? = null
    private val bitmapLock = Any()

    // Audio Sharing
    private var audioRecord: android.media.AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null
    private val audioSubscribers = java.util.concurrent.CopyOnWriteArrayList<OutputStream>()

    private var httpServer: SimpleHttpServer? = null
    private var currentSettings = StreamSettings()

    // Recording State
    private var isRecordingSessionActive = false
    private var sessionStartTimestamp = 0L
    private var frameCounter = 0
    private var sessionSizeBytes = 0L
    private var currentSessionDir: File? = null
    private var lastRecordedFrameTime = 0L

    // Motion Detection
    private var previousFrameBuffer: IntArray? = null
    private val lastAlertTime = AtomicLong(0L)

    // FPS computation
    private val frameCountInSecond = AtomicInteger(0)
    private var fpsTrackerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = StreamRepository(db.settingsDao(), db.sessionRecordDao(), db.motionLogDao())
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        createNotificationChannel()

        // Track and report actual FPS
        fpsTrackerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val count = frameCountInSecond.getAndSet(0)
                _currentFps.value = count.toFloat()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (_isStreaming.value) {
            setupVirtualDisplay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_PROJECTION && intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)
            }

            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                // Setup notifications and transition to the foreground immediately to satisfy
                // the background service constraints and OS rules. We do this ONLY when starting projection.
                val initialNotification = buildNotification(
                    "Screen Streaming Active",
                    "Initializing secure visual stream..."
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }

                try {
                    val mp = mediaProjectionManager?.getMediaProjection(resultCode, data)
                    if (mp == null) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    mediaProjection = mp
                    startStreaming()
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopSelf()
                }
            } else {
                stopSelf()
            }
        } else {
            // Null intent, ACTION_INITIALIZE, or fallback.
            // Under Android 8.0+, we can just stopSelf directly to release unused resources safely.
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun connectRemoteWebSocket() {
        val settings = currentSettings
        if (!settings.isRemoteStreamingEnabled) return
        if (remoteWebSocket != null || isRemoteConnecting) return

        isRemoteConnecting = true
        remoteConnectionJob?.cancel()
        remoteConnectionJob = serviceScope.launch(Dispatchers.IO) {
            val client = okHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build().also { okHttpClient = it }

            val url = "${settings.remoteStreamUrl}?key=${settings.remoteStreamKey}"
            android.util.Log.i("StreamService", "Connecting to remote stream WebSocket: ${settings.remoteStreamUrl}")

            val request = Request.Builder().url(url).build()
            
            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    android.util.Log.i("StreamService", "Remote stream WebSocket opened successfully.")
                    remoteWebSocket = webSocket
                    isRemoteConnecting = false
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    android.util.Log.i("StreamService", "Remote stream WebSocket closed.")
                    remoteWebSocket = null
                    isRemoteConnecting = false
                    scheduleRemoteReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    android.util.Log.e("StreamService", "Remote stream WebSocket failure: ${t.message}")
                    remoteWebSocket = null
                    isRemoteConnecting = false
                    scheduleRemoteReconnect()
                }
            })
        }
    }

    private fun scheduleRemoteReconnect() {
        if (!_isStreaming.value || !currentSettings.isRemoteStreamingEnabled) return
        remoteConnectionJob?.cancel()
        remoteConnectionJob = serviceScope.launch {
            delay(5000)
            if (_isStreaming.value && currentSettings.isRemoteStreamingEnabled) {
                connectRemoteWebSocket()
            }
        }
    }

    private fun disconnectRemoteWebSocket() {
        remoteConnectionJob?.cancel()
        remoteConnectionJob = null
        try {
            remoteWebSocket?.close(1000, "Streaming stopped")
        } catch (e: Exception) {}
        remoteWebSocket = null
        isRemoteConnecting = false
    }

    private fun startStreaming() {
        // Acquire WakeLock to prevent CPU sleep
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "PrismCast::StreamWakeLock").apply {
                acquire()
            }
            android.util.Log.i("StreamService", "Acquired partial WakeLock successfully")
        } catch (e: Exception) {
            android.util.Log.e("StreamService", "Failed to acquire WakeLock: ${e.message}")
        }

        // Acquire WifiLock to keep network connection high-performance
        try {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PrismCast::StreamWifiLock").apply {
                acquire()
            }
            android.util.Log.i("StreamService", "Acquired high-performance WifiLock successfully")
        } catch (e: Exception) {
            android.util.Log.e("StreamService", "Failed to acquire WifiLock: ${e.message}")
        }

        serviceScope.launch {
            // Load Settings from Room DB asynchronously without blocking
            currentSettings = repository.getSettings()

            // Update foreground notification display with server's access link
            val notification = buildNotification(
                "Screen Streaming Active",
                "Web Viewer is ready on http://${getLocalIp()}:${currentSettings.port}"
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            try {
                // Setup ImageReader & Virtual Display using the active mediaProjection
                setupVirtualDisplay()

                // Register listener for physical changes or user termination
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopStreaming()
                    }
                }, null)

                // Start Server threads
                startWebServer()
                startAudioRecording()

                _isStreaming.value = true
                _streamingUrl.value = "http://${getLocalIp()}:${currentSettings.port}"

                // Connect remote WebSocket if enabled
                connectRemoteWebSocket()

                // Manage settings updates reactively
                repository.settingsFlow.collect { updatedSettings ->
                    val portChanged = updatedSettings.port != currentSettings.port
                    val resolutionChanged = updatedSettings.resolution != currentSettings.resolution
                    val audioSourceChanged = try { updatedSettings.audioSource != currentSettings.audioSource } catch(e: Exception) { false }
                    val remoteStreamingChanged = updatedSettings.isRemoteStreamingEnabled != currentSettings.isRemoteStreamingEnabled ||
                            updatedSettings.remoteStreamUrl != currentSettings.remoteStreamUrl ||
                            updatedSettings.remoteStreamKey != currentSettings.remoteStreamKey

                    currentSettings = updatedSettings

                    if (portChanged) {
                        _streamingUrl.value = "http://${getLocalIp()}:${currentSettings.port}"
                        restartWebServer()
                    }

                    if (resolutionChanged) {
                        setupVirtualDisplay()
                    }

                    if (audioSourceChanged) {
                        android.util.Log.i("StreamService", "Audio source changed to ${updatedSettings.audioSource}. Restarting audio stream...")
                        stopAudioRecording()
                        startAudioRecording()
                    }

                    if (remoteStreamingChanged) {
                        android.util.Log.i("StreamService", "Remote streaming settings changed. Reconnecting remote WebSocket...")
                        disconnectRemoteWebSocket()
                        if (updatedSettings.isRemoteStreamingEnabled) {
                            connectRemoteWebSocket()
                        }
                    }

                    // Handles starting recording session on toggle
                    if (currentSettings.isRecordingEnabled && !isRecordingSessionActive) {
                        startRecordingSession()
                    } else if (!currentSettings.isRecordingEnabled && isRecordingSessionActive) {
                        stopRecordingSessionAndSave()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun startAudioRecording() {
        if (isAudioRecording) return
        isAudioRecording = true

        audioRecordingJob = serviceScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding).coerceAtLeast(4096)

            var record: android.media.AudioRecord? = null
            val sourceSetting = try { currentSettings.audioSource } catch (e: Exception) { "Microphone" }
            
            android.util.Log.i("StreamService", "Starting audio recording session with preferred source: $sourceSetting")

            if (sourceSetting != "Muted") {
                val attemptsList = if (sourceSetting == "System Audio") {
                    listOf("System Audio", "Microphone")
                } else {
                    listOf("Microphone", "System Audio")
                }

                for (attempt in attemptsList) {
                    if (record != null) break

                    if (attempt == "Microphone") {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@StreamService,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            android.util.Log.e("StreamService", "RECORD_AUDIO permission is NOT granted. Skipping MIC initialization.")
                            continue
                        }

                        try {
                            android.util.Log.d("StreamService", "Attempting MIC initialization...")
                            val tempRecord = android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sampleRate,
                                channelConfig,
                                encoding,
                                bufferSize
                            )
                            if (tempRecord.state == android.media.AudioRecord.STATE_INITIALIZED) {
                                record = tempRecord
                                android.util.Log.i("StreamService", "Successfully initialized MIC recording!")
                            } else {
                                android.util.Log.e("StreamService", "MIC record created but state is UNINITIALIZED.")
                                tempRecord.release()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("StreamService", "Failed to initialize MIC recording: ${e.message}", e)
                        }
                    } else if (attempt == "System Audio" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val mp = mediaProjection
                        if (mp != null) {
                            try {
                                android.util.Log.d("StreamService", "Attempting System Audio (Playback) initialization...")
                                val builderClass = Class.forName("android.media.AudioPlaybackCaptureConfig\$Builder")
                                val builderConstructor = builderClass.getConstructor(MediaProjection::class.java)
                                val builderInstance = builderConstructor.newInstance(mp)

                                val addMatchingUsageMethod = builderClass.getMethod("addMatchingUsage", java.lang.Integer.TYPE)
                                addMatchingUsageMethod.invoke(builderInstance, 1) // USAGE_MEDIA = 1
                                addMatchingUsageMethod.invoke(builderInstance, 14) // USAGE_GAME = 14
                                addMatchingUsageMethod.invoke(builderInstance, 0) // USAGE_UNKNOWN = 0

                                val buildMethod = builderClass.getMethod("build")
                                val captureConfig = buildMethod.invoke(builderInstance)

                                val recordBuilderClass = Class.forName("android.media.AudioRecord\$Builder")
                                val recordBuilderInstance = recordBuilderClass.getConstructor().newInstance()

                                val audioFormatBuilderClass = Class.forName("android.media.AudioFormat\$Builder")
                                val audioFormatBuilderInstance = audioFormatBuilderClass.getConstructor().newInstance()
                                
                                val setEncodingMethod = audioFormatBuilderClass.getMethod("setEncoding", java.lang.Integer.TYPE)
                                setEncodingMethod.invoke(audioFormatBuilderInstance, encoding) // ENCODING_PCM_16BIT = 2

                                val setSampleRateMethod = audioFormatBuilderClass.getMethod("setSampleRate", java.lang.Integer.TYPE)
                                setSampleRateMethod.invoke(audioFormatBuilderInstance, sampleRate) // 44100

                                val setChannelMaskMethod = audioFormatBuilderClass.getMethod("setChannelMask", java.lang.Integer.TYPE)
                                setChannelMaskMethod.invoke(audioFormatBuilderInstance, channelConfig) // CHANNEL_IN_MONO = 16

                                val formatBuildMethod = audioFormatBuilderClass.getMethod("build")
                                val audioFormatInstance = formatBuildMethod.invoke(audioFormatBuilderInstance)

                                val setAudioFormatMethod = recordBuilderClass.getMethod("setAudioFormat", Class.forName("android.media.AudioFormat"))
                                setAudioFormatMethod.invoke(recordBuilderInstance, audioFormatInstance)

                                val setBufferSizeInBytesMethod = recordBuilderClass.getMethod("setBufferSizeInBytes", java.lang.Integer.TYPE)
                                setBufferSizeInBytesMethod.invoke(recordBuilderInstance, bufferSize)

                                val setAudioPlaybackCaptureConfigMethod = recordBuilderClass.getMethod("setAudioPlaybackCaptureConfig", Class.forName("android.media.AudioPlaybackCaptureConfig"))
                                setAudioPlaybackCaptureConfigMethod.invoke(recordBuilderInstance, captureConfig)

                                val recordBuildMethod = recordBuilderClass.getMethod("build")
                                val tempRecord = recordBuildMethod.invoke(recordBuilderInstance) as android.media.AudioRecord
                                if (tempRecord.state == android.media.AudioRecord.STATE_INITIALIZED) {
                                    record = tempRecord
                                    android.util.Log.i("StreamService", "Successfully initialized system audio playback capture!")
                                } else {
                                    android.util.Log.e("StreamService", "System audio capture record created but state is UNINITIALIZED.")
                                    tempRecord.release()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("StreamService", "Failed to initialize system audio capture: ${e.message}", e)
                            }
                        } else {
                            android.util.Log.e("StreamService", "MediaProjection is null, skipping system audio capture setup")
                        }
                    }
                }
            }

            // 3. Last fallback: Silence heartbeat carrier generator
            if (record == null) {
                android.util.Log.e("StreamService", "All AudioRecord options failed (or source is Muted). Starting SILENT heartbeat stream fallback.")
                val silentBuffer = ByteArray(1024) // zeros
                while (isActive && isAudioRecording) {
                    val subscribersSnapshot = java.util.concurrent.CopyOnWriteArrayList(audioSubscribers)
                    for (subscriber in subscribersSnapshot) {
                        if (!audioSubscribers.contains(subscriber)) continue
                        try {
                            subscriber.write(silentBuffer)
                            subscriber.flush()
                        } catch (e: Exception) {
                            audioSubscribers.remove(subscriber)
                        }
                    }
                    delay(50) // sending 50ms chunks of silence
                }
                isAudioRecording = false
                return@launch
            }

            // 4. Start actual active recording loop
            audioRecord = record
            try {
                record.startRecording()
                android.util.Log.i("StreamService", "AudioRecord started recording successfully!")
                val buffer = ByteArray(2048)
                val silentBuffer = ByteArray(1024) // zeros
                while (isActive && isAudioRecording) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        // Broadcast to all active subscribers
                        val subscribersSnapshot = java.util.concurrent.CopyOnWriteArrayList(audioSubscribers)
                        for (subscriber in subscribersSnapshot) {
                            if (!audioSubscribers.contains(subscriber)) continue
                            try {
                                subscriber.write(data)
                                subscriber.flush()
                            } catch (e: Exception) {
                                audioSubscribers.remove(subscriber)
                            }
                        }

                        // Send audio to remote server if enabled
                        if (currentSettings.isRemoteStreamingEnabled) {
                            val socket = remoteWebSocket
                            if (socket != null) {
                                try {
                                    val payload = ByteArray(1 + data.size)
                                    payload[0] = 0x01
                                    System.arraycopy(data, 0, payload, 1, data.size)
                                    socket.send(payload.toByteString())
                                } catch (e: Exception) {
                                    // Ignore transmission errors
                                }
                            }
                        }
                    } else {
                        // Headless emulator/silent device fallback: stream zeros to keep Web Audio API connected and active
                        val subscribersSnapshot = java.util.concurrent.CopyOnWriteArrayList(audioSubscribers)
                        for (subscriber in subscribersSnapshot) {
                            if (!audioSubscribers.contains(subscriber)) continue
                            try {
                                subscriber.write(silentBuffer)
                                subscriber.flush()
                            } catch (e: Exception) {
                                audioSubscribers.remove(subscriber)
                            }
                        }

                        // Send silence to remote server if enabled
                        if (currentSettings.isRemoteStreamingEnabled) {
                            val socket = remoteWebSocket
                            if (socket != null) {
                                try {
                                    val payload = ByteArray(1 + silentBuffer.size)
                                    payload[0] = 0x01
                                    System.arraycopy(silentBuffer, 0, payload, 1, silentBuffer.size)
                                    socket.send(payload.toByteString())
                                } catch (e: Exception) {
                                    // Ignore transmission errors
                                }
                            }
                        }

                        delay(25) // sending steady sound chunks to preserve streaming rate
                    }
                    delay(10) // small breath
                }
            } catch (e: Exception) {
                android.util.Log.e("StreamService", "Error in main audio recording loop: ${e.message}", e)
            } finally {
                android.util.Log.i("StreamService", "Cleaning up and stopping AudioRecord")
                try {
                    record.stop()
                } catch (e: Exception) {}
                try {
                    record.release()
                } catch (e: Exception) {}
                audioRecord = null
                isAudioRecording = false
            }
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        audioSubscribers.clear()
    }

    private fun getWavHeader(sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = 0x7FFFFFFF_FFFFFFFL // pseudo-infinite
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        header[0] = 'R'.code.toByte()     // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        val fileLength = (totalDataLen + 36).coerceAtMost(0xFFFFFFFFL)
        header[4] = (fileLength and 0xff).toByte()
        header[5] = ((fileLength shr 8) and 0xff).toByte()
        header[6] = ((fileLength shr 16) and 0xff).toByte()
        header[7] = ((fileLength shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()     // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()    // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16                   // size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1                    // PCM format = 1
        header[21] = 0

        header[22] = channels.toByte()
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        val blockAlign = (channels * bitsPerSample / 8).toShort()
        header[32] = (blockAlign.toInt() and 0xff).toByte()
        header[33] = ((blockAlign.toInt() shr 8) and 0xff).toByte()

        header[34] = bitsPerSample.toByte()
        header[35] = 0

        header[36] = 'd'.code.toByte()    // 'data' chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        val dataLength = totalDataLen.coerceAtMost(0xFFFFFFFFL)
        header[40] = (dataLength and 0xff).toByte()
        header[41] = ((dataLength shr 8) and 0xff).toByte()
        header[42] = ((dataLength shr 16) and 0xff).toByte()
        header[43] = ((dataLength shr 24) and 0xff).toByte()

        return header
    }

    private fun setupVirtualDisplay() {
        // Recycle old display components
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null

        val metrics = resources.displayMetrics
        val densityDpi = metrics.densityDpi
        val orientation = resources.configuration.orientation
        var screenWidth = metrics.widthPixels
        var screenHeight = metrics.heightPixels

        // Dynamic orientation assertion to ensure screen bounds align correctly
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && screenWidth < screenHeight) {
            screenWidth = screenHeight.also { screenHeight = screenWidth }
        } else if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT && screenHeight < screenWidth) {
            screenWidth = screenHeight.also { screenHeight = screenWidth }
        }

        val scale = when (currentSettings.resolution) {
            "Low" -> 0.35f
            "High" -> 0.70f
            else -> 0.50f // "Medium"
        }

        val width = (screenWidth * scale).toInt()
        val height = (screenHeight * scale).toInt()

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (t: Throwable) {
                null
            } ?: return@setOnImageAvailableListener

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val imgWidth = image.width
                val imgHeight = image.height

                val rowSize = imgWidth * 4
                val totalBytesNeeded = imgWidth * imgHeight * 4
                
                synchronized(bitmapLock) {
                    if (!_isStreaming.value) {
                        return@setOnImageAvailableListener
                    }

                    var bitmap = reusableBitmap
                    if (bitmap == null || bitmap.isRecycled || bitmap.width != imgWidth || bitmap.height != imgHeight) {
                        bitmap?.recycle()
                        bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
                        reusableBitmap = bitmap
                    }

                    if (rowStride == rowSize && buffer.remaining() >= totalBytesNeeded) {
                        buffer.rewind()
                        bitmap.copyPixelsFromBuffer(buffer)
                    } else {
                        var array = reusablePixelArray
                        if (array == null || array.size < totalBytesNeeded) {
                            array = ByteArray(totalBytesNeeded)
                            reusablePixelArray = array
                        }
                        buffer.rewind()
                        if (rowStride == rowSize) {
                            val bytesToRead = kotlin.math.min(totalBytesNeeded, buffer.remaining())
                            if (bytesToRead > 0) {
                                buffer.get(array, 0, bytesToRead)
                            }
                            if (bytesToRead < totalBytesNeeded) {
                                java.util.Arrays.fill(array, bytesToRead, totalBytesNeeded, 0.toByte())
                            }
                        } else {
                            for (y in 0 until imgHeight) {
                                val srcPos = y * rowStride
                                if (srcPos < buffer.limit()) {
                                    buffer.position(srcPos)
                                    val bytesToRead = kotlin.math.min(rowSize, buffer.remaining())
                                    if (bytesToRead > 0) {
                                        buffer.get(array, y * rowSize, bytesToRead)
                                    }
                                }
                            }
                        }
                        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(array))
                    }

                    // Compress to JPEG for server stream
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val jpegBytes = stream.toByteArray()

                    latestFrameBytes = jpegBytes
                    frameCountInSecond.incrementAndGet()

                    // Send frame to remote server if enabled
                    if (currentSettings.isRemoteStreamingEnabled) {
                        val socket = remoteWebSocket
                        if (socket != null) {
                            try {
                                val payload = ByteArray(1 + jpegBytes.size)
                                payload[0] = 0x00
                                System.arraycopy(jpegBytes, 0, payload, 1, jpegBytes.size)
                                socket.send(payload.toByteString())
                            } catch (e: Exception) {
                                // Ignore transmission errors
                            }
                        }
                    }

                    // Check motion (using the optimized single bitmap)
                    detectMotion(bitmap, jpegBytes)

                    // Record frame
                    recordFrameIfEnabled(jpegBytes)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                try {
                    image.close()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }, handler)

        virtualDisplay = try {
            mediaProjection?.createVirtualDisplay(
                "ScreenStreamDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: SecurityException) {
            try {
                mediaProjection?.createVirtualDisplay(
                    "ScreenStreamDisplay",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    imageReader?.surface,
                    null,
                    null
                )
            } catch (ex: Exception) {
                mediaProjection?.createVirtualDisplay(
                    "ScreenStreamDisplay",
                    width,
                    height,
                    densityDpi,
                    0,
                    imageReader?.surface,
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            mediaProjection?.createVirtualDisplay(
                "ScreenStreamDisplay",
                width,
                height,
                densityDpi,
                0,
                imageReader?.surface,
                null,
                null
            )
        }
    }

    private fun startWebServer() {
        httpServer = SimpleHttpServer(
            port = currentSettings.port,
            passwordProvider = { currentSettings.passcode },
            isPasswordEnabledProvider = { currentSettings.isPasswordEnabled },
            onGetStream = { out ->
                val targetInterval = 1000L / currentSettings.fps.coerceAtLeast(1)
                var lastStreamFrameTime = 0L
                _activeClientCount.value = _activeClientCount.value + 1
                try {
                    while (isStreaming.value) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastStreamFrameTime >= targetInterval) {
                            val frame = latestFrameBytes
                            if (frame != null) {
                                out.write("--frame\r\n".toByteArray())
                                out.write("Content-Type: image/jpeg\r\n".toByteArray())
                                out.write("Content-Length: ${frame.size}\r\n".toByteArray())
                                out.write("\r\n".toByteArray())
                                out.write(frame)
                                out.write("\r\n".toByteArray())
                                out.flush()
                                lastStreamFrameTime = currentTime
                            }
                        }
                        Thread.sleep(20)
                    }
                } catch (e: Exception) {
                    // Client closed tab / left stream
                } finally {
                    _activeClientCount.value = (_activeClientCount.value - 1).coerceAtLeast(0)
                }
            },
            onGetAudio = { out ->
                // Add output stream to our list of subscribers
                audioSubscribers.add(out)
                try {
                    // Keep the response thread alive while streaming is active and the client remains connected
                    while (isStreaming.value && audioSubscribers.contains(out)) {
                        Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    // client disconnected
                } finally {
                    audioSubscribers.remove(out)
                }
            },
            onGetStatus = {
                // Populate server-side runtime stats
                val batteryPct = getBatteryPercentage()
                val usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val freeMemory = Runtime.getRuntime().maxMemory() - usedMemory
                """{"fps":${_currentFps.value},"activeClients":${activeClientCount.value},"battery":$batteryPct,"freeMemory":${freeMemory / (1024 * 1024)},"isRecording":$isRecordingSessionActive}"""
            },
            onUpdateSettings = { json ->
                // Apply update in repository
                parseAndUpdateSettings(json)
            },
            getHtmlPage = { getWebDashboardHtml() }
        ).apply { start() }
    }

    private fun stopWebServer() {
        httpServer?.stop()
        httpServer = null
    }

    private fun restartWebServer() {
        stopWebServer()
        startWebServer()
    }

    private fun parseAndUpdateSettings(json: String): Boolean {
        // Quick manual JSON parsing to avoid adding converter libs boilerplate in socket threads
        try {
            val keyValues = mutableMapOf<String, String>()
            val cleaned = json.replace("{", "").replace("}", "").replace("\"", "")
            val pairs = cleaned.split(",")
            for (pair in pairs) {
                val parts = pair.split(":")
                if (parts.size == 2) {
                    keyValues[parts[0].trim()] = parts[1].trim()
                }
            }

            serviceScope.launch {
                val existing = repository.getSettings()
                val updated = existing.copy(
                    port = keyValues["port"]?.toIntOrNull() ?: existing.port,
                    fps = keyValues["fps"]?.toIntOrNull() ?: existing.fps,
                    resolution = keyValues["resolution"] ?: existing.resolution,
                    isMotionDetectionEnabled = keyValues["isMotion"]?.toBoolean() ?: existing.isMotionDetectionEnabled,
                    isRecordingEnabled = keyValues["recording"]?.toBoolean() ?: existing.isRecordingEnabled,
                    isPasswordEnabled = keyValues["isPasswordEnabled"]?.toBoolean() ?: existing.isPasswordEnabled,
                    passcode = keyValues["passcode"] ?: existing.passcode
                )
                repository.saveSettings(updated)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Motion Alert Logging
    private fun detectMotion(bitmap: Bitmap, jpegBytes: ByteArray) {
        if (!currentSettings.isMotionDetectionEnabled) return

        val width = bitmap.width
        val height = bitmap.height
        val gridCount = 12
        val currentGrid = IntArray(gridCount * gridCount)

        var idx = 0
        for (y in 0 until gridCount) {
            for (x in 0 until gridCount) {
                val px = (x * (width - 1)) / (gridCount - 1)
                val py = (y * (height - 1)) / (gridCount - 1)
                currentGrid[idx++] = bitmap.getPixel(px, py)
            }
        }

        val prev = previousFrameBuffer
        if (prev != null && prev.size == currentGrid.size) {
            var diffSum = 0L
            for (i in currentGrid.indices) {
                val val1 = currentGrid[i]
                val val2 = prev[i]

                val r1 = (val1 shr 16) and 0xff
                val g1 = (val1 shr 8) and 0xff
                val b1 = val1 and 0xff

                val r2 = (val2 shr 16) and 0xff
                val g2 = (val2 shr 8) and 0xff
                val b2 = val2 and 0xff

                diffSum += kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
            }

            val maxPossibleDiff = currentGrid.size * 3 * 255.0
            val percentage = (diffSum.toDouble() / maxPossibleDiff) * 100

            // Configurable sensitivity mapping
            val threshold = (100 - currentSettings.motionSensitivity).coerceAtLeast(5)

            if (percentage > threshold) {
                val now = System.currentTimeMillis()
                // De-bounce notice spam every 10 seconds
                if (now - lastAlertTime.get() >= 10000L) {
                    lastAlertTime.set(now)
                    triggerMotionNotificationAndRecord(percentage.toInt(), jpegBytes)
                }
            }
        }

        previousFrameBuffer = currentGrid
    }

    private fun triggerMotionNotificationAndRecord(confidence: Int, jpegBytes: ByteArray) {
        val now = System.currentTimeMillis()
        val dir = File(filesDir, "motion")
        dir.mkdirs()
        val file = File(dir, "motion_${now}.jpg")
        try {
            file.writeBytes(jpegBytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Send alert notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Uses existing foreground drawable
            .setContentTitle("Motion Alert Triggered!")
            .setContentText("Screen change activity observed (Confidence: $confidence%). Tap to inspect.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ALERT_ID, notification)

        // Save entry in Room database
        serviceScope.launch {
            repository.insertMotionLog(
                MotionLog(
                    timestamp = now,
                    confidence = confidence,
                    snapshotPath = file.absolutePath
                )
            )
        }
    }

    // Session Recording Core Block
    private fun startRecordingSession() {
        isRecordingSessionActive = true
        sessionStartTimestamp = System.currentTimeMillis()
        frameCounter = 0
        sessionSizeBytes = 0L
        
        val dir = File(filesDir, "recordings/session_${sessionStartTimestamp}")
        dir.mkdirs()
        currentSessionDir = dir
        lastRecordedFrameTime = 0
    }

    private fun recordFrameIfEnabled(jpegBytes: ByteArray) {
        if (!isRecordingSessionActive) return
        val now = System.currentTimeMillis()
        // Save at most 1 frame per second to save store space
        if (now - lastRecordedFrameTime >= 1000L) {
            val sDir = currentSessionDir ?: return
            val file = File(sDir, "frame_${frameCounter}.jpg")
            try {
                file.writeBytes(jpegBytes)
                frameCounter++
                sessionSizeBytes += jpegBytes.size
                lastRecordedFrameTime = now
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecordingSessionAndSave() {
        if (!isRecordingSessionActive) return
        isRecordingSessionActive = false
        val sDir = currentSessionDir
        if (sDir != null && frameCounter > 0) {
            val record = SessionRecord(
                timestamp = sessionStartTimestamp,
                durationMs = System.currentTimeMillis() - sessionStartTimestamp,
                frameCount = frameCounter,
                sizeBytes = sessionSizeBytes,
                savedFramesDirectory = sDir.absolutePath
            )
            serviceScope.launch {
                repository.insertSession(record)
            }
        }
        currentSessionDir = null
    }

    private fun stopStreaming() {
        // Release WakeLock and WifiLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
            android.util.Log.i("StreamService", "Released WakeLock successfully")
        } catch (e: Exception) {
            android.util.Log.e("StreamService", "Failed to release WakeLock: ${e.message}")
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
            android.util.Log.i("StreamService", "Released WifiLock successfully")
        } catch (e: Exception) {
            android.util.Log.e("StreamService", "Failed to release WifiLock: ${e.message}")
        }

        try {
            disconnectRemoteWebSocket()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            stopAudioRecording()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            stopRecordingSessionAndSave()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            stopWebServer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            imageReader?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            handlerThread?.quitSafely()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            fpsTrackerJob?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        synchronized(bitmapLock) {
            try {
                reusableBitmap?.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            reusableBitmap = null
            reusablePixelArray = null
            previousFrameBuffer = null
        }

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        handlerThread = null

        _isStreaming.value = false
        _streamingUrl.value = null
        _activeClientCount.value = 0
        _currentFps.value = 0f
    }

    override fun onDestroy() {
        stopStreaming()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val streamChannel = NotificationChannel(
                CHANNEL_ID, "Screen Streaming Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Displays casting status control dashboard." }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID, "Screen Motion Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Pushes dynamic screen capture activity motion triggers." }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(streamChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val stopIntent = Intent(this, StreamService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop Broadcasting", pendingStopIntent)
            .build()
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryStatus = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
        } catch (e: Exception) {
            100
        }
    }

    private fun getLocalIp(): String {
        try {
            val list = java.net.NetworkInterface.getNetworkInterfaces()
            while (list.hasMoreElements()) {
                val element = list.nextElement()
                val addresses = element.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    // HTML embedded web console with password lock and full responsiveness built on Tailwind v4
    private fun getWebDashboardHtml(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Screencast Viewer Node</title>
    <script src="https://unpkg.com/@tailwindcss/browser@4"></script>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;600;700&family=JetBrains+Mono:wght@400;500;700&display=swap');
        body { font-family: 'Space Grotesk', sans-serif; background-color: #080a0f; color: #f3f4f6; }
        code, pre, .mono { font-family: 'JetBrains Mono', monospace; }
        .glass-dark { background: rgba(13, 16, 23, 0.75); backdrop-filter: blur(16px); border: 1px solid rgba(255, 255, 255, 0.06); }

        /* Immersive fullscreen scaling overrides */
        #player-container:fullscreen {
            width: 100vw !important;
            height: 100vh !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            aspect-ratio: auto !important;
            border-radius: 0 !important;
            background-color: #000000 !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            padding: 0 !important;
        }
        #player-container:fullscreen #stream-img {
            width: 100% !important;
            height: 100% !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            object-fit: contain !important;
            border-radius: 0 !important;
        }
    </style>
</head>
<body class="min-h-screen flex flex-col items-center justify-center p-4 md:p-8">
    <main class="w-full max-w-4xl flex flex-col items-center gap-6 transition-all duration-300">
        
        <!-- Passcode Locked screen -->
        <div id="login-screen" class="hidden fixed inset-0 z-50 flex items-center justify-center bg-[#080a0f] px-4">
            <div class="glass-dark w-full max-w-sm rounded-2xl p-6 text-center space-y-4 shadow-2xl">
                <span class="text-4xl">🔑</span>
                <div>
                    <h2 class="text-xl font-bold">Session Access Vault</h2>
                    <p class="text-sm text-gray-400 mt-1">Please enter your passcode to view the stream.</p>
                </div>
                <input type="password" id="auth-input" placeholder="Passcode" class="w-full bg-gray-950 border border-gray-800 rounded-xl py-3 px-4 text-center tracking-widest text-lg font-bold text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
                <button onclick="attemptAuthentication()" class="w-full bg-emerald-600 hover:bg-emerald-500 active:scale-98 transition text-white font-bold py-3 px-4 rounded-xl cursor-pointer">
                    Authorize Stream
                </button>
                <p id="login-error" class="text-rose-400 text-xs hidden">Unauthorized passcode credential.</p>
            </div>
        </div>

        <!-- Main Stream Video Player Screen -->
        <div id="stream-screen" class="hidden w-full flex flex-col items-center gap-6">
            
            <!-- Immersive Stream Player -->
            <div id="player-container" class="relative w-full rounded-2xl overflow-hidden shadow-2xl glass-dark flex items-center justify-center bg-gray-950 transition-all duration-300" style="aspect-ratio: 9/16; max-height: 80vh; max-width: 100%;">
                <img id="stream-img" class="max-h-full max-w-full object-contain cursor-pointer transition-all duration-300" alt="Live screen stream" onclick="togglePlayPause()" onload="adjustAspectRatio(this)" />
                
                <!-- Status Badge -->
                <div id="status-badge" class="absolute top-4 left-4 flex items-center gap-2 bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-full text-xs font-semibold select-none">
                    <span class="flex h-2 w-2 relative">
                        <span id="badge-pulse" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                        <span id="badge-dot" class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                    </span>
                    <span id="stream-status">LIVE</span>
                </div>

                <!-- FPS overlay badge -->
                <div id="info-badge" class="absolute top-4 right-4 bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-full text-xs font-semibold mono select-none font-sans">
                    <span id="rendered-fps">FPS: Computing...</span>
                </div>

                <!-- Center Play Event Overlay -->
                <div id="center-play-overlay" class="absolute inset-0 flex items-center justify-center pointer-events-none opacity-0 transition-opacity duration-300">
                    <div class="bg-black/85 p-5 rounded-full shadow-lg">
                        <svg id="center-play-icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-10 h-10 text-white">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25v13.5m-7.5-13.5v13.5" />
                        </svg>
                    </div>
                </div>
            </div>

            <!-- Sleek Control Panel bar -->
            <div class="flex flex-wrap items-center justify-center gap-3 w-full">
                <button onclick="togglePlayPause()" id="play-pause-btn" class="glass-dark hover:bg-gray-800 text-white font-semibold py-3 px-5 rounded-xl flex items-center gap-2 transition active:scale-95 cursor-pointer shadow-md select-none text-sm">
                    <span id="play-pause-icon">⏸️</span>
                    <span id="play-pause-text">Pause Stream</span>
                </button>

                <button onclick="toggleAudio()" id="audio-toggle-btn" class="glass-dark hover:bg-gray-800 text-white font-semibold py-3 px-5 rounded-xl flex items-center gap-2 transition active:scale-95 cursor-pointer shadow-md select-none text-sm">
                    <span id="audio-toggle-icon">🔊</span>
                    <span id="audio-toggle-text">Audio: Active</span>
                </button>

                <button onclick="toggleFullscreen()" class="glass-dark hover:bg-gray-800 text-white font-semibold py-3 px-5 rounded-xl flex items-center gap-2 transition active:scale-95 cursor-pointer shadow-md select-none text-sm">
                    <span>Fullscreen</span>
                </button>
                
                <button id="lock-btn" onclick="logout()" class="glass-dark hover:bg-gray-800 text-rose-400 font-semibold py-3 px-4 rounded-xl flex items-center justify-center transition active:scale-95 cursor-pointer shadow-md select-none text-sm" title="Lock and Disconnect">
                    <span>🔒 Disconnect</span>
                </button>
            </div>
            
        </div>

    </main>

    <script>
        let isAuthorizedNode = false;
        let passcodeKey = '';
        let isStreamingActive = true;
        let fpsIntervalId = null;

        // High-Fidelity Gapless PCM Audio Engine
        let audioCtx = null;
        let nextPlayTime = 0;
        let audioReader = null;
        let audioAbortController = null;
        let isAudioMuted = false;

        function ensureAudioContext() {
            if (!audioCtx) {
                try {
                    audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 44100 });
                    console.log("AudioContext created synchronously upon click/gesture!");
                } catch (e) {
                    console.error("Web Audio API not supported", e);
                }
            }
            if (audioCtx && audioCtx.state === 'suspended') {
                audioCtx.resume().then(() => {
                    console.log("AudioContext resumed, state is: " + audioCtx.state);
                });
            }
        }

        window.onload = function() {
            // Register explicit user gesture triggers
            document.addEventListener('click', ensureAudioContext, { passive: true });
            document.addEventListener('touchstart', ensureAudioContext, { passive: true });

            // Read stored credentials or authenticate
            const storedKey = localStorage.getItem('stream_passcode');
            if (storedKey) {
                passcodeKey = storedKey;
                attemptAuthentication(storedKey);
            } else {
                checkAuthenticationChallenge();
            }
        };

        function adjustAspectRatio(img) {
            if (img.naturalWidth && img.naturalHeight) {
                const aspect = img.naturalWidth / img.naturalHeight;
                document.getElementById('player-container').style.aspectRatio = aspect;
            }
        }

        function checkAuthenticationChallenge() {
            fetch('/api/status?auth=')
                .then(res => {
                    if (res.status === 401) {
                        document.getElementById('login-screen').classList.remove('hidden');
                    } else {
                        isAuthorizedNode = true;
                        document.getElementById('stream-screen').classList.remove('hidden');
                        initStream();
                    }
                })
                .catch(err => {
                    console.error("Communication error", err);
                    document.getElementById('login-screen').classList.remove('hidden');
                });
        }

        function attemptAuthentication(key = null) {
            const secret = key || document.getElementById('auth-input').value;
            fetch('/api/status?auth=' + encodeURIComponent(secret))
                .then(res => {
                    if (res.status === 200) {
                        isAuthorizedNode = true;
                        passcodeKey = secret;
                        localStorage.setItem('stream_passcode', secret);
                        document.getElementById('login-screen').classList.add('hidden');
                        document.getElementById('stream-screen').classList.remove('hidden');
                        initStream();
                    } else {
                        document.getElementById('login-error').classList.remove('hidden');
                        localStorage.removeItem('stream_passcode');
                    }
                })
                .catch(() => {
                    document.getElementById('login-error').classList.remove('hidden');
                });
        }

        function initStream() {
            isStreamingActive = true;
            document.getElementById('stream-img').src = '/stream?auth=' + encodeURIComponent(passcodeKey);
            
            if (!isAudioMuted) {
                startPCMPlayer();
            }

            pollFPS();
            if (!fpsIntervalId) {
                fpsIntervalId = setInterval(pollFPS, 1500);
            }
        }

        function startPCMPlayer() {
            stopPCMPlayer();
            
            ensureAudioContext();
            if (!audioCtx) {
                console.error("AudioContext is unavailable.");
                return;
            }
            
            nextPlayTime = audioCtx.currentTime;
            audioAbortController = new AbortController();
            const signal = audioAbortController.signal;
            const audioUrl = '/audio?auth=' + encodeURIComponent(passcodeKey);
            
            fetch(audioUrl, { signal })
                .then(response => {
                    if (!response.ok) throw new Error("Audio stream HTTP status error");
                    const reader = response.body.getReader();
                    audioReader = reader;
                    
                    let remainder = null;
                    
                    function read() {
                        if (!audioReader || isAudioMuted || !isStreamingActive) return;
                        audioReader.read().then(({ done, value }) => {
                            if (done || !isStreamingActive || isAudioMuted) return;
                            
                            let data = value;
                            if (remainder) {
                                const temp = new Uint8Array(remainder.length + data.length);
                                temp.set(remainder, 0);
                                temp.set(data, remainder.length);
                                data = temp;
                                remainder = null;
                            }
                            
                            const sampleCount = Math.floor(data.length / 2);
                            const byteLength = sampleCount * 2;
                            
                            if (byteLength < data.length) {
                                remainder = data.slice(byteLength);
                            }
                            
                            if (sampleCount > 0) {
                                const float32Array = new Float32Array(sampleCount);
                                for (let i = 0; i < sampleCount; i++) {
                                    const byteLow = data[i * 2];
                                    const byteHigh = data[i * 2 + 1];
                                    const rawVal = byteLow | (byteHigh << 8);
                                    const signedVal = (rawVal << 16) >> 16;
                                    float32Array[i] = signedVal / 32768.0;
                                }
                                
                                playFloat32Chunk(float32Array);
                            }
                            
                            read();
                        }).catch(err => {
                            console.log("Audio reading closed", err);
                        });
                    }
                    
                    read();
                })
                .catch(err => {
                    console.error("Audio stream initialization failed", err);
                });
        }

        function playFloat32Chunk(pcmFloatArray) {
            if (!audioCtx || isAudioMuted || !isStreamingActive) return;
            
            const buffer = audioCtx.createBuffer(1, pcmFloatArray.length, 44100);
            buffer.getChannelData(0).set(pcmFloatArray);
            
            const source = audioCtx.createBufferSource();
            source.buffer = buffer;
            source.connect(audioCtx.destination);
            
            const now = audioCtx.currentTime;
            if (nextPlayTime < now || nextPlayTime > now + 1.0) {
                nextPlayTime = now + 0.05; // gap mitigation safety and drift reset
            }
            
            source.start(nextPlayTime);
            nextPlayTime += buffer.duration;
        }

        function stopPCMPlayer() {
            if (audioAbortController) {
                audioAbortController.abort();
                audioAbortController = null;
            }
            if (audioReader) {
                try { audioReader.cancel(); } catch(e){}
                audioReader = null;
            }
            nextPlayTime = 0;
            if (audioCtx) {
                try { audioCtx.close(); } catch(e){}
                audioCtx = null;
            }
        }

        function toggleAudio() {
            ensureAudioContext();
            isAudioMuted = !isAudioMuted;
            const btnIcon = document.getElementById('audio-toggle-icon');
            const btnText = document.getElementById('audio-toggle-text');
            
            if (isAudioMuted) {
                stopPCMPlayer();
                btnIcon.innerText = '🔇';
                btnText.innerText = 'Audio: Muted';
            } else {
                startPCMPlayer();
                btnIcon.innerText = '🔊';
                btnText.innerText = 'Audio: Active';
            }
        }

        function togglePlayPause() {
            const img = document.getElementById('stream-img');
            const statusBadge = document.getElementById('stream-status');
            const pulse = document.getElementById('badge-pulse');
            const dot = document.getElementById('badge-dot');
            const btnIcon = document.getElementById('play-pause-icon');
            const btnText = document.getElementById('play-pause-text');
            const overlay = document.getElementById('center-play-overlay');
            const centerIcon = document.getElementById('center-play-icon');

            if (isStreamingActive) {
                isStreamingActive = false;
                img.src = '';
                stopPCMPlayer();

                statusBadge.innerText = 'PAUSED';
                statusBadge.className = 'text-gray-400 font-bold';
                pulse.classList.add('hidden');
                dot.className = 'relative inline-flex rounded-full h-2 w-2 bg-gray-500';
                btnIcon.innerText = '▶️';
                btnText.innerText = 'Resume Stream';
                
                centerIcon.innerHTML = `<path stroke-linecap="round" stroke-linejoin="round" d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.348a1.125 1.125 0 010 1.971l-11.54 6.347a1.125 1.125 0 01-1.667-.985V5.653z" />`;
                overlay.style.opacity = '1';
                setTimeout(() => { if (!isStreamingActive) overlay.style.opacity = '0.5'; }, 500);
            } else {
                isStreamingActive = true;
                img.src = '/stream?auth=' + encodeURIComponent(passcodeKey);
                if (!isAudioMuted) {
                    startPCMPlayer();
                }

                statusBadge.innerText = 'LIVE';
                statusBadge.className = '';
                pulse.classList.remove('hidden');
                dot.className = 'relative inline-flex rounded-full h-2 w-2 bg-emerald-500';
                btnIcon.innerText = '⏸️';
                btnText.innerText = 'Pause Stream';
                
                centerIcon.innerHTML = `<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25v13.5m-7.5-13.5v13.5" />`;
                overlay.style.opacity = '1';
                setTimeout(() => { overlay.style.opacity = '0'; }, 500);
            }
        }

        function pollFPS() {
            if (!isStreamingActive) return;
            fetch('/api/status?auth=' + encodeURIComponent(passcodeKey))
                .then(r => r.json())
                .then(data => {
                    document.getElementById('rendered-fps').innerText = 'FPS: ' + data.fps.toFixed(1);
                })
                .catch(err => console.error(err));
        }

        function toggleFullscreen() {
            const container = document.getElementById('player-container');
            if (!document.fullscreenElement) {
                container.requestFullscreen().catch(err => {
                    alert("Error enabling full-screen: " + err);
                });
            } else {
                document.exitFullscreen();
            }
        }

        function logout() {
            stopPCMPlayer();
            localStorage.removeItem('stream_passcode');
            window.location.reload();
        }
    </script>
</body>
</html>
"""
    }

    class SimpleHttpServer(
        private val port: Int,
        private val passwordProvider: () -> String,
        private val isPasswordEnabledProvider: () -> Boolean,
        private val onGetStream: (OutputStream) -> Unit,
        private val onGetAudio: (OutputStream) -> Unit,
        private val onGetStatus: () -> String,
        private val onUpdateSettings: (String) -> Boolean,
        private val getHtmlPage: () -> String
    ) {
        private var serverSocket: ServerSocket? = null
        private var isRunning = false

        fun start() {
            isRunning = true
            Thread {
                try {
                    serverSocket = ServerSocket(port)
                    while (isRunning) {
                        val clientSocket = serverSocket?.accept() ?: break
                        Thread { handleClient(clientSocket) }.start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        fun stop() {
            isRunning = false
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun handleClient(socket: Socket) {
            try {
                val reader = socket.getInputStream().bufferedReader()
                val out = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val tokenizer = StringTokenizer(requestLine)
                if (!tokenizer.hasMoreTokens()) return
                val method = tokenizer.nextToken()
                if (!tokenizer.hasMoreTokens()) return
                val pathAndQuery = tokenizer.nextToken()

                val headers = mutableMapOf<String, String>()
                var line: String? = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    val index = line.indexOf(":")
                    if (index != -1) {
                        headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
                    }
                    line = reader.readLine()
                }

                val path = pathAndQuery.split("?")[0]
                val queryParams = parseQueryParams(pathAndQuery)
                
                // Authorize checking (using URL parameter to avoid complex session headers)
                val isAuthorized = !isPasswordEnabledProvider() || queryParams["auth"] == passwordProvider()

                if (method == "GET" && path == "/stream") {
                    if (!isAuthorized) {
                        write401(out)
                        return
                    }
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n".toByteArray())
                    out.write("Cache-Control: no-cache, private\r\n".toByteArray())
                    out.write("Pragma: no-cache\r\n".toByteArray())
                    out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.flush()

                    onGetStream(out)
                    return
                }

                if (method == "GET" && path == "/audio") {
                    if (!isAuthorized) {
                        write401(out)
                        return
                    }
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: application/octet-stream\r\n".toByteArray())
                    out.write("Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n".toByteArray())
                    out.write("Pragma: no-cache\r\n".toByteArray())
                    out.write("Expires: 0\r\n".toByteArray())
                    out.write("X-Content-Type-Options: nosniff\r\n".toByteArray())
                    out.write("Connection: keep-alive\r\n".toByteArray())
                    out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.flush()

                    onGetAudio(out)
                    return
                }

                if (method == "GET" && path == "/api/status") {
                    if (!isAuthorized) {
                        write401(out)
                        return
                    }
                    val statusJson = onGetStatus()
                    writeJsonResponse(out, statusJson)
                    return
                }

                if (method == "POST" && path == "/api/settings") {
                    if (!isAuthorized) {
                        write401(out)
                        return
                    }
                    val body = if (headers.containsKey("content-length")) {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val charArray = CharArray(contentLength)
                        reader.read(charArray, 0, contentLength)
                        String(charArray)
                    } else ""
                    
                    val success = onUpdateSettings(body)
                    writeJsonResponse(out, "{\"success\":$success}")
                    return
                }

                if (method == "GET" && (path == "/" || path == "/index.html")) {
                    val html = getHtmlPage()
                    writeHtmlResponse(out, html)
                    return
                }

                write404(out)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {}
            }
        }

        private fun parseQueryParams(url: String): Map<String, String> {
            val params = mutableMapOf<String, String>()
            val parts = url.split("?")
            if (parts.size > 1) {
                val query = parts[1]
                val pairs = query.split("&")
                for (pair in pairs) {
                    val idx = pair.indexOf("=")
                    if (idx != -1) {
                        params[pair.substring(0, idx)] = pair.substring(idx + 1)
                    } else {
                        params[pair] = ""
                    }
                }
            }
            return params
        }

        private fun write401(out: OutputStream) {
            val body = "{\"error\": \"Unauthorized\"}"
            out.write("HTTP/1.1 401 Unauthorized\r\n".toByteArray())
            out.write("Content-Type: application/json\r\n".toByteArray())
            out.write("Content-Length: ${body.length}\r\n".toByteArray())
            out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(body.toByteArray())
            out.flush()
        }

        private fun write404(out: OutputStream) {
            val body = "Not Found"
            out.write("HTTP/1.1 404 Not Found\r\n".toByteArray())
            out.write("Content-Type: text/plain\r\n".toByteArray())
            out.write("Content-Length: ${body.length}\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(body.toByteArray())
            out.flush()
        }

        private fun writeJsonResponse(out: OutputStream, json: String) {
            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
            out.write("Content-Type: application/json\r\n".toByteArray())
            out.write("Content-Length: ${json.length}\r\n".toByteArray())
            out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(json.toByteArray())
            out.flush()
        }

        private fun writeHtmlResponse(out: OutputStream, html: String) {
            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
            out.write("Content-Type: text/html\r\n".toByteArray())
            out.write("Content-Length: ${html.toByteArray().size}\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(html.toByteArray())
            out.flush()
        }
    }



    companion object {
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_INITIALIZE = "com.example.ACTION_INITIALIZE"
        const val ACTION_START_PROJECTION = "com.example.ACTION_START_PROJECTION"
        const val EXTRA_RESULT_CODE = "com.example.EXTRA_RESULT_CODE"
        const val EXTRA_DATA_INTENT = "com.example.EXTRA_DATA_INTENT"

        private const val NOTIFICATION_ID = 2654
        private const val NOTIFICATION_ALERT_ID = 2655
        private const val CHANNEL_ID = "STREAM_CHANNEL"
        private const val CHANNEL_ALERTS_ID = "MOTION_ALERTS_CHANNEL"

        private val _isStreaming = MutableStateFlow(false)
        val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

        private val _activeClientCount = MutableStateFlow(0)
        val activeClientCount: StateFlow<Int> = _activeClientCount.asStateFlow()

        private val _streamingUrl = MutableStateFlow<String?>(null)
        val streamingUrl: StateFlow<String?> = _streamingUrl.asStateFlow()

        private val _currentFps = MutableStateFlow(0f)
        val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

        var latestFrameBytes: ByteArray? = null
    }
}
