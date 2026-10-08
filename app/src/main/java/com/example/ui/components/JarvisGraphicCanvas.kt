package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
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
import com.example.data.model.*

// Glowing Neon Cyber Palette optimized for wall projection
val CyberBlack = Color(0xFF030712)
val CyberCardBg = Color(0xEE0B1120)
val NeonCyan = Color(0xFF00F0FF)
val NeonEmerald = Color(0xFF10B981)
val NeonAmber = Color(0xFFF59E0B)
val NeonPurple = Color(0xFFA855F7)
val NeonPink = Color(0xFFEC4899)
val NeonTextMuted = Color(0xFF94A3B8)

@Composable
fun JarvisGraphicCanvas(
    state: JarvisState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    if (state.visualType == JarvisVisualType.NONE) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonPurple.copy(alpha = 0.5f))), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Title, Subtitle, and Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = state.title.ifEmpty { "JARVIS VISUAL DIRECTIVE" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        if (state.subtitle.isNotEmpty()) {
                            Text(
                                text = state.subtitle,
                                fontSize = 13.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Graphic",
                        tint = NeonTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Visual Renderer based on Visual Type
            when (state.visualType) {
                JarvisVisualType.ROUTE_MAP -> {
                    state.routeData?.let { route ->
                        RouteMapVisual(route = route)
                    }
                }
                JarvisVisualType.CIRCUIT_DIAGRAM -> {
                    state.diagramData?.let { diag ->
                        CircuitDiagramVisual(diagram = diag)
                    }
                }
                JarvisVisualType.METRIC_CARD, JarvisVisualType.COMPARISON_CHART -> {
                    state.metricsData?.let { metrics ->
                        MetricsComparisonVisual(metrics = metrics)
                    }
                }
                else -> {
                    GenericInfographicVisual(state = state)
                }
            }

            // Summary Metrics Bar below visual if available
            if (state.metricsData != null && state.visualType == JarvisVisualType.ROUTE_MAP) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.metricsData.forEach { metric ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = metric.label,
                                    fontSize = 11.sp,
                                    color = NeonTextMuted
                                )
                                Text(
                                    text = metric.value,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (metric.subtext.isNotEmpty()) {
                                    Text(
                                        text = metric.subtext,
                                        fontSize = 10.sp,
                                        color = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Neon Vector Route Map (e.g. Nairobi CBD to TRM)
 */
@Composable
fun RouteMapVisual(route: RouteMapData) {
    val infiniteTransition = rememberInfiniteTransition(label = "route_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Highway Vector Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF020617))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw glowing grid lines
                val gridColor = Color(0xFF0F172A)
                for (x in 0..(w.toInt()) step 40) {
                    drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), h), 1f)
                }
                for (y in 0..(h.toInt()) step 40) {
                    drawLine(gridColor, Offset(0f, y.toFloat()), Offset(w, y.toFloat()), 1f)
                }

                // S-curve highway path from origin (left) to destination (right)
                val path = Path().apply {
                    moveTo(w * 0.10f, h * 0.65f)
                    cubicTo(
                        w * 0.35f, h * 0.85f,
                        w * 0.65f, h * 0.25f,
                        w * 0.90f, h * 0.35f
                    )
                }

                // Highway background glow
                drawPath(
                    path = path,
                    color = NeonCyan.copy(alpha = 0.25f),
                    style = Stroke(width = 16f, cap = StrokeCap.Round)
                )

                // Main glowing highway lane
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        listOf(NeonEmerald, NeonCyan, NeonPurple)
                    ),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )

                // Draw waypoint nodes
                val waypointsCoords = listOf(
                    Offset(w * 0.10f, h * 0.65f), // CBD
                    Offset(w * 0.35f, h * 0.72f), // Pangani
                    Offset(w * 0.52f, h * 0.48f), // Muthaiga
                    Offset(w * 0.72f, h * 0.30f), // Garden City / Roysambu
                    Offset(w * 0.90f, h * 0.35f)  // TRM
                )

                waypointsCoords.forEachIndexed { index, coord ->
                    val isFirst = index == 0
                    val isLast = index == waypointsCoords.size - 1

                    if (isFirst || isLast) {
                        // Pulsing outer aura ring
                        drawCircle(
                            color = (if (isLast) NeonPink else NeonEmerald).copy(alpha = pulseAlpha * 0.5f),
                            radius = 16f,
                            center = coord
                        )
                        // Solid core
                        drawCircle(
                            color = if (isLast) NeonPink else NeonEmerald,
                            radius = 8f,
                            center = coord
                        )
                    } else {
                        // Intermediate waypoint
                        drawCircle(
                            color = NeonCyan,
                            radius = 5f,
                            center = coord
                        )
                    }
                }
            }

            // Labels overlay
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Origin badge
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "ORIGIN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                    Text(
                        text = route.origin,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Center Highway Pill
                Surface(
                    color = Color(0xCC0F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${route.distance} • ${route.duration}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Destination badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DESTINATION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPink
                    )
                    Text(
                        text = route.destination,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Waypoints Sequence Chain
        if (route.waypoints.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                route.waypoints.forEachIndexed { idx, wp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                            .border(
                                1.dp,
                                if (wp.isDestination) NeonPink.copy(alpha = 0.6f) else Color(0xFF1E293B),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "STEP ${idx + 1}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (wp.isDestination) NeonPink else NeonCyan
                            )
                            Text(
                                text = wp.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            if (wp.detail.isNotEmpty()) {
                                Text(
                                    text = wp.detail,
                                    fontSize = 9.sp,
                                    color = NeonTextMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circuit Diagram / Electronics Schematic Renderer
 */
@Composable
fun CircuitDiagramVisual(diagram: DiagramData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF020617))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw circuit board traces
                val tracePath = Path().apply {
                    moveTo(w * 0.15f, h * 0.5f)
                    lineTo(w * 0.40f, h * 0.5f)
                    lineTo(w * 0.65f, h * 0.5f)
                    lineTo(w * 0.90f, h * 0.5f)
                }
                drawPath(
                    path = tracePath,
                    color = NeonEmerald.copy(alpha = 0.6f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                // Draw component nodes
                diagram.nodes.forEach { node ->
                    val cx = w * node.xPercent
                    val cy = h * node.yPercent

                    drawCircle(color = NeonCyan, radius = 12f, center = Offset(cx, cy))
                    drawCircle(color = Color(0xFF0F172A), radius = 8f, center = Offset(cx, cy))
                    drawCircle(color = NeonEmerald, radius = 4f, center = Offset(cx, cy))
                }
            }

            // Labels for components
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                diagram.nodes.forEach { node ->
                    Surface(
                        color = Color(0xDD0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(node.type, fontSize = 9.sp, color = NeonEmerald, fontWeight = FontWeight.Bold)
                            Text(node.label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-Contrast Metrics Comparison Renderer
 */
@Composable
fun MetricsComparisonVisual(metrics: List<KeyMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.forEach { metric ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = metric.label, fontSize = 11.sp, color = NeonTextMuted)
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = metric.badge,
                                fontSize = 9.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = metric.value,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    if (metric.subtext.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = metric.subtext, fontSize = 11.sp, color = NeonEmerald)
                    }
                }
            }
        }
    }
}

/**
 * Generic Infographic Visual
 */
@Composable
fun GenericInfographicVisual(state: JarvisState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!state.stepsData.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.stepsData.forEachIndexed { idx, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
