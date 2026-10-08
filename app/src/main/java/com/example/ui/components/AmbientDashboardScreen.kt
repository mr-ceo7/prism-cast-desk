package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.ScreenStreamViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

val WallPureBlack = Color(0xFF000000)
val WallCardSurface = Color(0xDD090D16)
val WallBorderColor = Color(0xFF1E293B)
val WallCyan = Color(0xFF00F0FF)
val WallEmerald = Color(0xFF10B981)
val WallAmber = Color(0xFFF59E0B)
val WallPurple = Color(0xFFA855F7)
val WallTextMuted = Color(0xFF94A3B8)

@Composable
fun AmbientDashboardScreen(
    viewModel: ScreenStreamViewModel,
    modifier: Modifier = Modifier
) {
    val todos by viewModel.todosState.collectAsStateWithLifecycle()
    val note by viewModel.noteState.collectAsStateWithLifecycle()
    val kpis by viewModel.kpisState.collectAsStateWithLifecycle()
    val multimeter by viewModel.multimeterState.collectAsStateWithLifecycle()
    val oscilloscope by viewModel.oscilloscopeState.collectAsStateWithLifecycle()
    val jarvisState by viewModel.jarvisState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isAmbientFullscreen.collectAsStateWithLifecycle()

    // Clock ticker state
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now).uppercase()
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WallPureBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── TOP AMBIENT STATUS BAR ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clock & Date
                    Column {
                        Text(
                            text = currentTimeString.ifEmpty { "12:00:00" },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = currentDateString.ifEmpty { "THU, 08 OCT" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WallCyan,
                            letterSpacing = 1.sp
                        )
                    }

                    // Remote URL Connection Badge
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WallCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(WallEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REMOTE: $serverUrl/remote",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }

                    // Fullscreen Toggle
                    IconButton(
                        onClick = { viewModel.toggleAmbientFullscreen() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.dp, WallBorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = WallCyan
                        )
                    }
                }
            }

            // ── JARVIS AI HUD INTERACTIVE SECTION ──
            item {
                JarvisHudSection(
                    state = jarvisState,
                    onQuery = { prompt -> viewModel.queryJarvis(prompt) },
                    onDismissVisual = { viewModel.dismissJarvisVisual() }
                )
            }

            // ── HARDWARE SENSORS & TELEMETRY ROW ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Wireless Multimeter Widget
                    MultimeterWidget(
                        telemetry = multimeter,
                        modifier = Modifier.weight(1f)
                    )

                    // Oscilloscope Waveform Display
                    OscilloscopeWidget(
                        telemetry = oscilloscope,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }

            // ── EXTENSIBLE KPI METRIC CARDS ROW ──
            if (kpis.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(kpis, key = { it.id }) { kpi ->
                            KpiCardWidget(kpi = kpi)
                        }
                    }
                }
            }

            // ── LIVE SYNCED TO-DO BOARD & NOTEPAD ROW ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Synced To-Do Board
                    TodoBoardWidget(
                        todos = todos,
                        onToggle = { id, done -> viewModel.toggleTodo(id, done) },
                        onDelete = { id -> viewModel.deleteTodo(id) },
                        onAdd = { text -> viewModel.addTodo(text) },
                        modifier = Modifier.weight(1.2f)
                    )

                    // Ambient Notepad Memo
                    NotepadWidget(
                        note = note,
                        onSave = { content -> viewModel.saveNote(content) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Jarvis Interactive Subtitle Bar & Vector Graphic Area
 */
@Composable
fun JarvisHudSection(
    state: JarvisState,
    onQuery: (String) -> Unit,
    onDismissVisual: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb")
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    var inputPrompt by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Glowing Caption HUD Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WallCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = WallCardSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Holographic AI Core Orb
                    Box(
                        modifier = Modifier
                            .size((20 * orbScale).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(WallCyan, WallPurple, Color.Transparent)
                                )
                            )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "JARVIS HUD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WallCyan,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = WallCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Surface(
                            color = WallCyan.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "AI ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WallCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Spoken Caption Subtitles
                Text(
                    text = state.replyText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Quick Voice / Prompt Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onQuery("How far is TRM from town") },
                        label = { Text("🗺️ TRM from Town", fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F172A))
                    )
                    AssistChip(
                        onClick = { onQuery("Wireless Multimeter probe test") },
                        label = { Text("⚡ Multimeter Probe", fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F172A))
                    )
                    AssistChip(
                        onClick = { onQuery("ESP32 sensor telemetry circuit schematic") },
                        label = { Text("🔧 Circuit Diagram", fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F172A))
                    )
                    AssistChip(
                        onClick = { onQuery("Projector ambient performance KPIs") },
                        label = { Text("📊 Projector KPIs", fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F172A))
                    )
                }
            }
        }

        // Generative Wall Graphic Canvas (Route map, schematic, comparative cards)
        AnimatedVisibility(
            visible = state.visualType != JarvisVisualType.NONE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            JarvisGraphicCanvas(
                state = state,
                onDismiss = onDismissVisual
            )
        }
    }
}

/**
 * Live Synced To-Do Board Widget
 */
@Composable
fun TodoBoardWidget(
    todos: List<TodoItem>,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTodoText by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WallBorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = WallCardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYNCED TASKS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WallCyan,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "${todos.count { !it.isCompleted }} pending",
                    fontSize = 11.sp,
                    color = WallTextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inline Add Task Input
            OutlinedTextField(
                value = newTodoText,
                onValueChange = { newTodoText = it },
                placeholder = { Text("Add task to wall...", fontSize = 12.sp, color = WallTextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newTodoText.isNotBlank()) {
                        onAdd(newTodoText.trim())
                        newTodoText = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WallCyan,
                    unfocusedBorderColor = WallBorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tasks List
            if (todos.isEmpty()) {
                Text(
                    text = "No active tasks. Add via remote on phone.",
                    fontSize = 12.sp,
                    color = WallTextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    todos.take(5).forEach { todo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = todo.isCompleted,
                                onCheckedChange = { onToggle(todo.id, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = WallEmerald,
                                    uncheckedColor = WallTextMuted,
                                    checkmarkColor = Color.Black
                                ),
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = todo.text,
                                fontSize = 13.sp,
                                color = if (todo.isCompleted) WallTextMuted else Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            IconButton(
                                onClick = { onDelete(todo.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
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
 * Ambient Synced Notepad Widget
 */
@Composable
fun NotepadWidget(
    note: NotepadNote,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WallPurple.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = WallCardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUICK NOTEPAD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WallPurple,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "AUTO-SYNC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WallEmerald
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(10.dp)
            ) {
                Text(
                    text = note.content.ifEmpty { "Type or dictate notes from your phone companion remote to project here on the wall." },
                    fontSize = 13.sp,
                    color = if (note.content.isEmpty()) WallTextMuted else Color.White,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * KPI Metric Card Widget
 */
@Composable
fun KpiCardWidget(kpi: KpiCard) {
    val statusColor = when (kpi.status) {
        "SUCCESS" -> WallEmerald
        "WARNING" -> WallAmber
        "INFO" -> WallCyan
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, WallBorderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = kpi.title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WallTextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = kpi.value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor
                )
                if (kpi.unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = kpi.unit,
                        fontSize = 11.sp,
                        color = WallTextMuted,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            if (!kpi.change.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = kpi.change,
                    fontSize = 10.sp,
                    color = WallEmerald
                )
            }
        }
    }
}
