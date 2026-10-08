package com.example.service

import android.content.Context
import android.net.wifi.WifiManager
import com.example.ai.JarvisAgent
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.StreamRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

object AmbientHubManager {
    private val hubScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repository: StreamRepository? = null
    private val jarvisAgent = JarvisAgent()
    private var httpServer: AmbientHttpServer? = null

    // Live Telemetry states
    private val _multimeterState = MutableStateFlow(MultimeterTelemetry())
    val multimeterState: StateFlow<MultimeterTelemetry> = _multimeterState.asStateFlow()

    private val _oscilloscopeState = MutableStateFlow(OscilloscopeTelemetry())
    val oscilloscopeState: StateFlow<OscilloscopeTelemetry> = _oscilloscopeState.asStateFlow()

    // Jarvis state
    private val _jarvisState = MutableStateFlow(
        JarvisState(
            replyText = "Jarvis Ambient Wall HUD is online. Speak or send prompts from your phone remote.",
            visualType = JarvisVisualType.ROUTE_MAP,
            title = "Nairobi CBD → TRM (Thika Road Mall)",
            subtitle = "13.8 km • ~25-35 mins via Thika Superhighway (A2)",
            routeData = RouteMapData(
                origin = "Nairobi CBD",
                destination = "TRM (Thika Road Mall)",
                distance = "13.8 km",
                duration = "25-35 mins",
                routeName = "Thika Superhighway (A2 Expressway)",
                trafficCondition = "Smooth to Moderate Flow",
                waypoints = listOf(
                    RoutePoint("Nairobi CBD", "Moi Ave / Murang'a Rd", isOrigin = true),
                    RoutePoint("Pangani Interchange", "Merge onto Thika Superhighway"),
                    RoutePoint("Muthaiga Flyover", "Central express overpass"),
                    RoutePoint("Garden City / Roysambu", "Pass Garden City flyover"),
                    RoutePoint("TRM (Thika Road Mall)", "Exit 8 ramp into TRM", isDestination = true)
                ),
                directions = listOf(
                    "Head north on Murang'a Rd toward Thika Superhighway",
                    "Merge onto the express highway lanes via Pangani",
                    "Continue past Muthaiga and Garden City",
                    "Take Exit 8 (Roysambu) off-ramp directly to TRM"
                )
            ),
            metricsData = listOf(
                KeyMetric("Distance", "13.8 km", "Direct highway", "FASTEST"),
                KeyMetric("Est. Duration", "25 - 35 min", "Normal flow", "ETA"),
                KeyMetric("Matatu Fare", "50 - 80 KES", "CBD Stage", "TRANSIT"),
                KeyMetric("Traffic", "Moderate", "Express clear", "FLOW")
            )
        )
    )
    val jarvisState: StateFlow<JarvisState> = _jarvisState.asStateFlow()

    // HUD Display Mode
    private val _isAmbientFullscreen = MutableStateFlow(false)
    val isAmbientFullscreen: StateFlow<Boolean> = _isAmbientFullscreen.asStateFlow()

    // Server IP & Port
    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun initialize(context: Context) {
        if (repository != null) return
        val db = AppDatabase.getDatabase(context.applicationContext)
        val repo = StreamRepository(
            db.settingsDao(),
            db.sessionRecordDao(),
            db.motionLogDao(),
            db.todoDao(),
            db.notepadDao(),
            db.kpiCardDao()
        )
        repository = repo

        // Seed initial default KPI cards if none exist
        hubScope.launch {
            val existingKpis = repo.getAllKpis()
            if (existingKpis.isEmpty()) {
                repo.upsertKpi(KpiCard("kpi_refresh", "REFRESH", "60 Hz", "FPS", "+0", "SUCCESS", "display"))
                repo.upsertKpi(KpiCard("kpi_cpu_temp", "SoC TEMP", "43.5", "°C", "-0.8", "NORMAL", "device_thermostat"))
                repo.upsertKpi(KpiCard("kpi_dmm_probe", "DMM VIN", "3.30", "V", "Auto", "INFO", "electric_bolt"))
                repo.upsertKpi(KpiCard("kpi_network", "LAN LATENCY", "12", "ms", "WiFi", "NORMAL", "wifi"))
            }

            // Seed initial todo items if empty
            val existingTodos = repo.getAllTodos()
            if (existingTodos.isEmpty()) {
                repo.addTodo("Align projection surface on wall", "NORMAL")
                repo.addTodo("Connect wireless multimeter probe", "HIGH")
                repo.addTodo("Ask Jarvis: 'how far is TRM from town'", "NORMAL")
            }
        }

        // Determine IP address and start HTTP server
        val localIp = getLocalIpAddress(context)
        val port = 8080
        _serverUrl.value = "http://$localIp:$port"

        startServer(context, port)
    }

    private fun startServer(context: Context, port: Int) {
        if (httpServer != null) return
        httpServer = AmbientHttpServer(
            port = port,
            localIpProvider = { getLocalIpAddress(context) },
            repositoryProvider = { repository },
            onJarvisQuery = { prompt ->
                hubScope.launch {
                    _jarvisState.value = _jarvisState.value.copy(isLoading = true)
                    val result = jarvisAgent.query(prompt)
                    _jarvisState.value = result
                }
            },
            onUpdateMultimeter = { newDmm ->
                _multimeterState.value = newDmm
            },
            onUpdateOscilloscope = { newScope ->
                _oscilloscopeState.value = newScope
            }
        ).apply { start() }
    }

    fun queryJarvis(prompt: String) {
        hubScope.launch {
            _jarvisState.value = _jarvisState.value.copy(isLoading = true)
            val result = jarvisAgent.query(prompt)
            _jarvisState.value = result
        }
    }

    fun dismissJarvisVisual() {
        _jarvisState.value = _jarvisState.value.copy(visualType = JarvisVisualType.NONE)
    }

    fun toggleAmbientFullscreen(fullscreen: Boolean? = null) {
        _isAmbientFullscreen.value = fullscreen ?: !_isAmbientFullscreen.value
    }

    fun updateMultimeter(telemetry: MultimeterTelemetry) {
        _multimeterState.value = telemetry
    }

    fun updateOscilloscope(telemetry: OscilloscopeTelemetry) {
        _oscilloscopeState.value = telemetry
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {}

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {}

        return "127.0.0.1"
    }
}
