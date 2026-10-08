package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MultimeterTelemetry
import com.example.data.model.OscilloscopeTelemetry
import kotlin.math.sin

val ScopeGridGreen = Color(0xFF064E3B)
val ScopeTraceGreen = Color(0xFF10B981)
val MeterDcvAmber = Color(0xFFF59E0B)

/**
 * Wireless Multimeter Live Ambient Tile
 */
@Composable
fun MultimeterWidget(
    telemetry: MultimeterTelemetry,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MeterDcvAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Multimeter Mode & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MeterDcvAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WIRELESS DMM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeterDcvAmber,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    color = MeterDcvAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = telemetry.mode.replace("_", " "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeterDcvAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Digital Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.3f", telemetry.value),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )

                Text(
                    text = telemetry.unit,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeterDcvAmber,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Analog Bar Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = telemetry.barPercent.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MeterDcvAmber, Color(0xFFEF4444))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-status (Range, Hold, Auto)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = telemetry.rangeText,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = if (telemetry.isHold) "HOLD" else "LIVE PROBE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (telemetry.isHold) Color(0xFFEF4444) else ScopeTraceGreen
                )
            }
        }
    }
}

/**
 * Oscilloscope Live Vector Waveform Display
 */
@Composable
fun OscilloscopeWidget(
    telemetry: OscilloscopeTelemetry,
    modifier: Modifier = Modifier
) {
    // Continuous live sweep animation for fluid 60fps rendering on projector
    val infiniteTransition = rememberInfiniteTransition(label = "scope_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ScopeTraceGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF021C14)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Scope Channel, Trigger, Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ScopeTraceGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OSCILLOSCOPE ${telemetry.channel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScopeTraceGreen,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    color = ScopeTraceGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = telemetry.triggerStatus,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScopeTraceGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // CRT Scope Grid & Waveform Vector Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF01140E))
                    .border(1.dp, ScopeGridGreen, RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Grid lines (8x4 divisions)
                    val xStep = w / 8f
                    val yStep = h / 4f

                    for (i in 1..7) {
                        drawLine(
                            color = ScopeGridGreen.copy(alpha = 0.5f),
                            start = Offset(i * xStep, 0f),
                            end = Offset(i * xStep, h),
                            strokeWidth = 1f
                        )
                    }
                    for (i in 1..3) {
                        drawLine(
                            color = ScopeGridGreen.copy(alpha = 0.5f),
                            start = Offset(0f, i * yStep),
                            end = Offset(w, i * yStep),
                            strokeWidth = 1f
                        )
                    }

                    // Center graticule axes
                    drawLine(ScopeGridGreen, Offset(0f, midY), Offset(w, midY), 1.5f)
                    drawLine(ScopeGridGreen, Offset(w / 2f, 0f), Offset(w / 2f, h), 1.5f)

                    // Draw Waveform
                    val wavePath = Path()
                    val hasExternalData = telemetry.wavePoints.isNotEmpty()

                    if (hasExternalData) {
                        // Plot actual external hardware points
                        val points = telemetry.wavePoints
                        val dx = w / (points.size - 1).coerceAtLeast(1)
                        points.forEachIndexed { index, sample ->
                            val px = index * dx
                            val py = midY - (sample * (h * 0.4f)).coerceIn(-midY, midY)
                            if (index == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
                        }
                    } else {
                        // Live animated sine/square/triangle wave generator
                        val sampleCount = 100
                        val dx = w / sampleCount
                        for (i in 0..sampleCount) {
                            val x = i * dx
                            val angle = (i.toFloat() / sampleCount) * 4 * Math.PI + phase
                            val yOffset = when (telemetry.waveType) {
                                "SQUARE" -> if (sin(angle) >= 0) h * 0.35f else -h * 0.35f
                                "TRIANGLE" -> {
                                    val norm = (angle % (2 * Math.PI)) / (2 * Math.PI)
                                    (if (norm < 0.5) (norm * 4 - 1) else ((1 - norm) * 4 - 1)).toFloat() * (h * 0.35f)
                                }
                                else -> sin(angle).toFloat() * (h * 0.35f)
                            }
                            val y = midY - yOffset
                            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        }
                    }

                    // Trace glow
                    drawPath(
                        path = wavePath,
                        color = ScopeTraceGreen.copy(alpha = 0.3f),
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )

                    // Core bright phosphor line
                    drawPath(
                        path = wavePath,
                        color = ScopeTraceGreen,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Readouts: Vpp, Freq, Timebase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${telemetry.voltsPerDiv}V/div • ${telemetry.timePerDiv}",
                    fontSize = 11.sp,
                    color = Color(0xFF6EE7B7)
                )
                Text(
                    text = "f: ${telemetry.frequency.toInt()} Hz  Vpp: ${String.format(java.util.Locale.US, "%.2f", telemetry.vpp)}V",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}
