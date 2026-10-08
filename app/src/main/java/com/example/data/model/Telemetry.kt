package com.example.data.model

data class MultimeterTelemetry(
    val mode: String = "DC_VOLTS", // "DC_VOLTS", "AC_VOLTS", "RESISTANCE", "CURRENT_MA", "CONTINUITY", "FREQUENCY", "CAPACITANCE"
    val value: Double = 3.30,
    val unit: String = "V",
    val isHold: Boolean = false,
    val isAutoRange: Boolean = true,
    val rangeText: String = "60V AUTO",
    val barPercent: Float = 0.55f, // 0.0 .. 1.0 for analog bar gauge
    val statusText: String = "STABLE",
    val updatedAt: Long = System.currentTimeMillis()
)

data class OscilloscopeTelemetry(
    val channel: String = "CH1",
    val wavePoints: List<Float> = emptyList(), // Normalized -1.0 to 1.0 or voltage samples
    val voltsPerDiv: Float = 1.0f,
    val timePerDiv: String = "1.0ms",
    val frequency: Double = 1000.0, // Hz
    val vpp: Double = 3.3, // Volts peak-to-peak
    val vrms: Double = 2.33,
    val triggerStatus: String = "AUTO", // "AUTO", "TRIG", "STOP"
    val waveType: String = "SINE", // "SINE", "SQUARE", "TRIANGLE", "NOISE", "LIVE"
    val updatedAt: Long = System.currentTimeMillis()
)
